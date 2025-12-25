package project.pipepipe.extractor.services.bilibili.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.CHANNEL_BASE_URL
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.LIVE_REFERER
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.QUERY_LIVEROOM_STATUS_URL
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.Utils
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.shared.state.StreamExtractState
import project.pipepipe.shared.utils.json.*

class BiliBiliLiveStreamExtractor(
    url: String,
) : Extractor<StreamInfo, Nothing>(url) {

    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val roomId =  url.split("/").last().split("?").first()
        val headers = BilibiliService.getHeadersWithCookie(url, cookie!!)

        if (currentState == null) {
            val baseUrl = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo"
            val liveParams = linkedMapOf<String, String>().apply {
                put("room_id", roomId)
                put("protocol", "0,1")
                put("format", "0,1,2")
                put("codec", "0,1,2")
                put("qn", "0")
                put("platform", "web")
                put("ptype", "8")
                put("dolby", "5")
                put("panorama", "1")
                put("hdr_type", "0,1,6")
                put("req_reason", "0")
                put("supported_drms", "0,1,2,3")
                put("special_scenario", "2")
            }

            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.GET,
                            Utils.getWbiResult(baseUrl, liveParams, cookie),
                            headers
                        )
                    )
                ),
                state = PlainState(1)
            )
        } else if (currentState.step == 1){
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val hlsUrl = data
                .requireArray("/data/playurl_info/playurl/stream")
                .first { it.requireString("protocol_name") == "http_hls" }
                .requireArray("format")
                .first { it.requireString("format_name") == "fmp4" }
                .requireString("master_url")

            val streamInfo = StreamInfo(
                url = LIVE_REFERER + roomId,
                serviceId = 5,
                isLive = true,
                uploadDate = data.requireLong("/data/live_time") * 1000,
                hlsUrl = hlsUrl,
                isPortrait = data.requireBoolean("/data/is_portrait"),
                isPaid = data.requireBoolean("/data/encrypted"),
                headers = hashMapOf("Referer" to "https://www.bilibili.com")
            )
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.GET,
                            QUERY_LIVEROOM_STATUS_URL + data.requireString("/data/uid"),
                            headers
                        )
                    )
                ),
                state = StreamExtractState(2, streamInfo)
            )
        } else if (currentState.step == 2) {
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
                .requireObject("data").properties().single().value
            val streamInfo = (currentState as StreamExtractState).streamInfo
            return JobStepResult.CompleteWith(ExtractResult(streamInfo.apply {
                name = data.requireString("title")
                uploaderName = data.requireString("uname")
                uploaderUrl =  CHANNEL_BASE_URL + data.requireString("uid")
                uploaderAvatarUrl = data.requireString("face")
                viewCount = data.requireLong("online")
                thumbnailUrl = runCatching { data.requireString("cover_from_user") }.getOrElse { data.requireString("keyframe") }
                tags = data.requireString("tags").split(",")
            }))
        } else error("illegal step")
    }
}