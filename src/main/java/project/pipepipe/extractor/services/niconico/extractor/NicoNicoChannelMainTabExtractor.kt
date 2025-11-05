package project.pipepipe.extractor.services.niconico.extractor

import org.jsoup.Jsoup
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.CHANNEL_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.TAB_RAW_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.USER_URL
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoStreamInfoDataParser.parseFromRSSXml
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.ChannelTabInfo
import project.pipepipe.shared.infoitem.ChannelTabType
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireString
import java.net.URLDecoder
import java.net.URLEncoder

class NicoNicoChannelMainTabExtractor(url: String) : Extractor<ChannelInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val safeUrl = url.substringBefore("?")
        val userId = getQueryValue(url, "id") ?: safeUrl.substringAfterLast("/")

        val isChannel = url.contains(CHANNEL_URL)

        if (currentState == null) {
            val userUrl = if (url.contains(TAB_RAW_URL) || url.contains(USER_URL)) {
                "$USER_URL$userId"
            } else {
                "$CHANNEL_URL$userId"
            }
            return JobStepResult.ContinueWith(listOf(
                ClientTask("info", Payload(RequestMethod.GET, userUrl, GOOGLE_HEADER)),
                ClientTask("videos", Payload(RequestMethod.GET, "$userUrl/video?rss=2.0&page=1", GOOGLE_HEADER))
            ), PlainState(1))
        } else {
            val infoDoc = Jsoup.parse(clientResults!!.first { it.taskId == "info" }.result!!)
            val userVideoData = Jsoup.parse(clientResults.first { it.taskId == "videos" }.result!!)

            val channelName: String
            val channelId: String
            val thumbnailUrl: String
            val description: String
            val subscriberCount: Long?

            if (isChannel) {
                channelName = infoDoc.select("meta[property=og:site_name]").attr("content")
                channelId = userId
                thumbnailUrl = infoDoc.select("meta[property=og:image]").attr("content")
                description = infoDoc.select("meta[name=description]").attr("content")
                subscriberCount = null
            } else {
                val infoData = ExtractorContext.objectMapper.readTree(
                    infoDoc.getElementById("js-initial-userpage-data")!!
                        .attr("data-initial-data")
                )
                channelName = infoData.requireString("/state/userDetails/userDetails/user/nickname")
                channelId = infoData.requireString("/state/userDetails/userDetails/user/id")
                thumbnailUrl = infoData.requireString("/state/userDetails/userDetails/user/icons/large")
                description = infoData.requireString("/state/userDetails/userDetails/user/description")
                subscriberCount = infoData.requireLong("/state/userDetails/userDetails/user/followerCount")
            }

            val nameEncoded = URLEncoder.encode(channelName, "UTF-8")

            userVideoData.select("item").forEach {
                commit { parseFromRSSXml(it, channelName,
                    if (isChannel) CHANNEL_URL + channelId else USER_URL + channelId) }
            }

            val tabs = mutableListOf(
                ChannelTabInfo("$TAB_RAW_URL?id=$channelId&type=videos&name=$nameEncoded", ChannelTabType.VIDEOS)
            )

            if (!isChannel) {
                tabs.add(ChannelTabInfo(
                    url = "$TAB_RAW_URL?id=$userId&type=playlists&name=$nameEncoded",
                    type = ChannelTabType.PLAYLISTS
                ))

                tabs.add(ChannelTabInfo(
                    url = "$TAB_RAW_URL?id=$userId&type=albums&name=$nameEncoded",
                    type = ChannelTabType.ALBUMS
                ))
            }

            val channelUrl = if (isChannel) "$CHANNEL_URL$channelId" else "https://www.nicovideo.jp/user/$channelId"

            return JobStepResult.CompleteWith(ExtractResult(
                info = ChannelInfo(
                    url = channelUrl,
                    name = channelName,
                    serviceId = "NICONICO",
                    thumbnailUrl = thumbnailUrl,
                    subscriberCount = subscriberCount,
                    description = description,
                    tabs = tabs
                ),
                errors = errors,
                pagedData = PagedData(itemList, "$TAB_RAW_URL?id=$userId&type=videos&page=2&name=$nameEncoded")
            ))
        }
    }

    override suspend fun fetchGivenPage(
        url: String,
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val userId = getQueryValue(url, "id")!!
        val page = getQueryValue(url, "page") ?: "1"
        val channelName = URLDecoder.decode(getQueryValue(url, "name")!!, "UTF-8")

        if (currentState == null) {
            val realUrl = "https://www.nicovideo.jp/user/$userId/video?rss=2.0&page=$page"
            return JobStepResult.ContinueWith(listOf(
                ClientTask("videos", Payload(RequestMethod.GET, realUrl, GOOGLE_HEADER))
            ), PlainState(1))
        } else {
            val userVideoData = Jsoup.parse(clientResults!!.first { it.taskId == "videos" }.result!!)
            userVideoData.select("item").forEach {
                commit { parseFromRSSXml(it, channelName, USER_URL + userId) }
            }
            val nextPageUrl = if (itemList.isNotEmpty()) url.incrementUrlParam("page") else null
            return JobStepResult.CompleteWith(ExtractResult(
                errors = errors,
                pagedData = PagedData(itemList, nextPageUrl)
            ))
        }
    }
}