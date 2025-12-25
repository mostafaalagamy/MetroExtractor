package project.pipepipe.extractor.services.bilibili.extractor

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.Router.setType
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.DANMAKU_RAW_URL
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.VIDEO_BASE_URL
import project.pipepipe.extractor.services.bilibili.BiliBiliUrlParser
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.Utils
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.extractor.utils.createMultiStreamDashManifest
import project.pipepipe.shared.infoitem.StaffInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.helper.stream.AudioStream
import project.pipepipe.shared.infoitem.helper.stream.Description
import project.pipepipe.shared.infoitem.helper.stream.Frameset
import project.pipepipe.shared.infoitem.helper.stream.VideoStream
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.State
import project.pipepipe.shared.state.StreamExtractState
import project.pipepipe.shared.utils.json.*

class BiliBiliStreamExtractor(
    url: String,
) : Extractor<StreamInfo, Nothing>(url) {
    val id = url.split("/").last().split("?")[0]
    lateinit var bvid: String

    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val headers = BilibiliService.getHeadersWithCookie(url, cookie!!)
        bvid = Utils.getPureBV(id)
        val apiUrl = Utils.getUrl(url, id)

        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(taskId = "info", payload = Payload(RequestMethod.GET, apiUrl, headers)),
                ClientTask(taskId = "tags", payload = Payload(
                    RequestMethod.GET,
                    BiliBiliLinks.FETCH_TAGS_URL + Utils.getPureBV(id),
                    headers = headers
                )),
                ClientTask(taskId = "videoshot", payload = Payload(
                    RequestMethod.GET,
                    BiliBiliLinks.VIDEOSHOT_API_URL + bvid,
                    headers = headers
                )),
            ), state = StreamExtractState(
                1, StreamInfo(
                    url,
                    5
                )
            ))
        } else if (currentState.step == 1) {
            return step_1(sessionId, currentState as StreamExtractState, clientResults!!, cookie)
        } else if (currentState.step == 2) {
            return step_final(sessionId, currentState as StreamExtractState, clientResults!!)
        } else throw IllegalArgumentException()
    }

    suspend fun step_1(
        sessionId: String,
        currentState: StreamExtractState,
        clientResults: List<TaskResult>,
        cookie: String
    ): JobStepResult {
        val watchDataRaw = clientResults.first { it.taskId == "info" }.result!!.asJson()
        if (watchDataRaw.requireInt("code") != 0) {
            return JobStepResult.FailWith(
                ErrorDetail(
                    code = "UNAV_001",
                    stackTrace = IllegalStateException(runCatching{ watchDataRaw.requireString("message") }.getOrDefault("Content not available")).stackTraceToString()
                )
            )
        }
        val watchData = watchDataRaw.requireObject("data")
        val tagData = clientResults.first { it.taskId == "tags" }.result!!.asJson().requireArray("data")
        val videoshotData = clientResults.firstOrNull { it.taskId == "videoshot" }?.result?.asJson()
        val pageNumString = getQueryValue(
            url,
            "p"
        )
        val pageNum = pageNumString?.toIntOrNull() ?: 1

        val streamInfo = StreamInfo("$VIDEO_BASE_URL$bvid?p=$pageNum", 5)
        val headers = BilibiliService.getHeadersWithCookie(url, cookie)

        val page = watchData.requireArray("pages")[pageNum - 1]
        val cid = page.requireLong("cid")
        setMetadata(watchData, streamInfo, page, tagData, sessionId, cookie, cid, videoshotData)
        val baseUrl = BiliBiliLinks.FREE_VIDEO_API_URL
        val streamParams = linkedMapOf<String, String>().apply {
            put("avid", Utils.bv2av(bvid).toString())
            put("bvid", bvid)
            put("cid", cid.toString())
            put("qn", "120")
            put("fnver", "0")
            put("fnval", "4048")
            put("fourk", "1")
            put("web_location", "1315873")
            put("try_look", "1")
            putAll(Utils.getDmImgParams())
        }

        val streamRequestHeaders = headers.toMutableMap()
//        if (ExtractorContext.ServiceList.BiliBili.hasTokens) {
//            streamRequestHeaders["Cookie"] = ExtractorContext.ServiceList.BiliBili.tokens!!
//            streamParams.remove("try_look")
//        }

        return JobStepResult.ContinueWith(listOf(ClientTask("stream_data", Payload(
            RequestMethod.GET, Utils.getWbiResult(baseUrl, streamParams, cookie), headers = streamRequestHeaders
        ))), state = StreamExtractState(2, streamInfo))
    }

    fun step_final(sessionId: String, currentState: StreamExtractState, clientResults: List<TaskResult>): JobStepResult {
        val playData = clientResults.first { it.taskId == "stream_data" }.result!!.asJson()
        val streamInfo = currentState.streamInfo
        when (playData.requireInt("code")) {
            0 -> {} // Success
            -10403 -> {
                val message = playData.requireString("message")
                if (message.contains("地区")) {
                    return JobStepResult.FailWith(
                        ErrorDetail(
                            code = "GEO_001",
                            stackTrace = IllegalStateException(message).stackTraceToString()
                        )
                    )
                }
            }
            else -> {
                val message = playData.requireString("message")
                if (message.contains("地区")) {
                    return JobStepResult.FailWith(
                        ErrorDetail(
                            code = "GEO_001",
                            stackTrace = IllegalStateException(message).stackTraceToString()
                        )
                    )
                }
                return JobStepResult.FailWith(
                    ErrorDetail(
                        code = "PARSE_001",
                        stackTrace = IllegalStateException(message).stackTraceToString()
                    )
                )
            }
        }

        val streamData = playData.requireObject("data").requireObject("dash")

        if (streamData.size() == 0 ||
            (streamInfo.isPaid && (streamData.requireArray("video").size() + streamData.requireArray("audio").size() == 0))) {
            return JobStepResult.FailWith(
                ErrorDetail(
                    code = "PAID_001",
                    stackTrace = IllegalStateException("Paid content").stackTraceToString()
                )
            )
        }

        streamInfo.isPortrait = streamData.requireArray("video")[0].let {
            it.requireInt("width") < it.requireInt("height")
        }

        streamInfo.dashManifest = createMultiStreamDashManifest(
            streamInfo.duration!! * 1.0,
            streamData.requireArray("video").map {
                VideoStream(
                    it.requireInt("id").toString(),
                    it.requireString("baseUrl"),
                    it.requireString("mimeType"),
                    it.requireString("codecs"),
                    it.requireLong("bandwidth"),
                    it.requireInt("width"),
                    it.requireInt("height"),
                    it.requireString("frameRate"),
                    it.requireString("/SegmentBase/indexRange"),
                    it.requireString("/SegmentBase/Initialization"),
                )
            }, streamData.requireArray("audio").map {
                AudioStream(
                    it.requireInt("id").toString(),
                    it.requireString("baseUrl"),
                    it.requireString("mimeType"),
                    it.requireString("codecs"),
                    it.requireLong("bandwidth"),
                    it.requireString("/SegmentBase/indexRange"),
                    it.requireString("/SegmentBase/Initialization"),
                )
            })
        return JobStepResult.CompleteWith(ExtractResult(streamInfo, errors))
    }
    fun setMetadata(
        watch: JsonNode,
        streamInfo: StreamInfo,
        page: JsonNode,
        tagData: JsonNode,
        sessionId: String,
        cookie: String,
        cid: Long,
        videoshotData: JsonNode?
    ) {
        val title = watch.requireString("title")
        streamInfo.name = if (watch.requireArray("pages").size() > 1) {
            "P${page.requireInt("page")} ${page.requireString("part")} | $title"
        } else {
            title
        }

        streamInfo.thumbnailUrl = watch.requireString("pic").replace("http:", "https:")

        val owner = watch.requireObject("owner")
        streamInfo.uploaderName = owner.requireString("name")
        streamInfo.uploaderUrl = "https://space.bilibili.com/${owner.requireLong("mid")}"
        streamInfo.uploaderAvatarUrl = owner.requireString("face").replace("http:", "https:")

        val stat = watch.requireObject("stat")
        streamInfo.viewCount = stat.requireLong("view")
        streamInfo.likeCount = stat.requireLong("like")
        streamInfo.description = Description(watch.requireString("desc"), Description.Companion.PLAIN_TEXT)
        streamInfo.uploadDate = watch.requireLong("pubdate") * 1000

        val staffArray = watch.get("staff")
        streamInfo.staffs = if (staffArray != null && staffArray.isArray && staffArray.size() > 0) {
            staffArray.map { staff ->
                StaffInfo(
                    "https://space.bilibili.com/${staff.requireLong("mid")}",
                    staff.requireString("name"),
                    staff.requireString("face"),
                    staff.requireString("title")
                )
            }
        } else {
            emptyList()
        }

        streamInfo.tags = tagData.map { it.requireString("tag_name") }


        streamInfo.initialTimestamp = try {
            streamInfo.url.split("#timestamp=")[1].toLong()
        } catch (e: Exception) {
            0
        }
        streamInfo.duration = page.requireLong("duration")
        streamInfo.isPaid = watch.requireObject("rights").requireInt("pay") == 1
        streamInfo.commentUrl = safeGet { BiliBiliUrlParser.urlFromCommentsId(BiliBiliUrlParser.parseCommentsId(url)!!, cookie) }
        streamInfo.danmakuUrl = "${DANMAKU_RAW_URL}?cid=${cid}"
        streamInfo.relatedItemUrl = url.setType("related")
        streamInfo.sponsorblockUrl = "sponsorblock://bilibili.raw?id=$bvid"
        streamInfo.headers = hashMapOf("Referer" to "https://www.bilibili.com")
        streamInfo.previewFrames = parseBiliBiliFrames(videoshotData)
    }

    private fun parseBiliBiliFrames(videoshotData: JsonNode?): List<Frameset> {
        try {
            if (videoshotData == null || videoshotData.requireInt("code") != 0) {
                return emptyList()
            }

            val data = videoshotData.get("data")
            if (data == null || data.isNull || data.isMissingNode) {
                return emptyList()
            }

            val imageUrls = data.get("image")
            val timeIndex = data.get("index")

            if (imageUrls == null || timeIndex == null ||
                !imageUrls.isArray || !timeIndex.isArray ||
                imageUrls.isEmpty || timeIndex.isEmpty) {
                return emptyList()
            }

            // Get frame properties
            val frameWidth = data.requireInt("img_x_size")
            val frameHeight = data.requireInt("img_y_size")
            val framesPerPageX = runCatching { data.requireInt("img_x_len") }.getOrDefault(10)
            val framesPerPageY = runCatching { data.requireInt("img_y_len") }.getOrDefault(10)
            val totalFrames = timeIndex.size() - 1  // Last index is the end time

            // Calculate average duration per frame in milliseconds
            val durationPerFrame = if (totalFrames > 1) {
                val totalDuration = timeIndex[totalFrames].asInt() - timeIndex[0].asInt()
                (totalDuration * 1000) / totalFrames
            } else {
                0
            }

            // Prepare URLs with https protocol
            val urls = mutableListOf<String>()
            for (i in 0 until imageUrls.size()) {
                var url = imageUrls[i].asText()
                if (url.startsWith("//")) {
                    url = "https:$url"
                }
                urls.add(url)
            }

            return listOf(
                Frameset(
                    urls = urls,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    totalCount = totalFrames,
                    durationPerFrame = durationPerFrame,
                    framesPerPageX = framesPerPageX,
                    framesPerPageY = framesPerPageY
                )
            )
        } catch (e: Exception) {
            return emptyList()
        }
    }
}