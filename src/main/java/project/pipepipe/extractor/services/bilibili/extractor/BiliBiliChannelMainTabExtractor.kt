package project.pipepipe.extractor.services.bilibili.extractor

import project.pipepipe.shared.job.ExtractResult
import project.pipepipe.extractor.Extractor
import project.pipepipe.shared.job.PagedData
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.CHANNEL_BASE_URL
import project.pipepipe.extractor.services.bilibili.BiliBiliUrlParser.parseChannelId
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.Utils.buildUserVideosUrlWebAPI
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliStreamInfoDataParser
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.ChannelTabInfo
import project.pipepipe.shared.infoitem.ChannelTabType
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.JobStepResult
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod
import project.pipepipe.shared.job.TaskResult
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.shared.job.ErrorDetail
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireInt
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString
import kotlin.math.ceil

class BiliBiliChannelMainTabExtractor(url: String) : Extractor<ChannelInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val headers = BilibiliService.getHeadersWithCookie(url, cookie!!)
        val id = parseChannelId(url)!!
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask("info", Payload(RequestMethod.GET, BiliBiliLinks.QUERY_USER_INFO_URL + id, headers)),
                ClientTask("videos", Payload(RequestMethod.GET, buildUserVideosUrlWebAPI(id, cookie), headers))
            ), PlainState(1))
        } else {
            val userInfoData = clientResults!!.first { it.taskId == "info" }.result!!.asJson()
            val userVideoDataRaw = clientResults.first { it.taskId == "videos" }.result!!
            if (userVideoDataRaw.contains("由于触发哔哩哔哩安全风控策略，该次访问请求被拒绝。 ")) {
                return JobStepResult.FailWith(
                    ErrorDetail(
                        code = "RISK_001",
                        stackTrace = IllegalStateException("由于触发哔哩哔哩安全风控策略，该次访问请求被拒绝。").stackTraceToString()
                    )
                )
            }
            val userVideoData = userVideoDataRaw.asJson()
            if (userVideoData.requireInt("code") !=  0) {
                return JobStepResult.FailWith(
                    ErrorDetail(
                        code = "REQ_001",
                        stackTrace = IllegalStateException(userVideoData.requireString("message")).stackTraceToString()
                    )
                )
            }
            val cardData = userInfoData.requireObject("/data/card")
            val videosArray = userVideoData.requireArray("/data/list/vlist")
            val newUrl = CHANNEL_BASE_URL + id
            videosArray.forEach { video ->
                commit { (BiliBiliStreamInfoDataParser.parseFromWebChannelInfoResponseJson(video)) }
            }
            val pn = runCatching{ userVideoData.requireInt("/data/page/pn") }.getOrDefault(1)
            val hasNext = runCatching{
                userVideoData.requireInt("/data/page/count").toDouble() / userVideoData.requireInt("/data/page/ps") > pn
            }.getOrDefault(false)
            return JobStepResult.CompleteWith(ExtractResult(
                info = ChannelInfo(
                    url = newUrl,
                    name = cardData.requireString("name"),
                    serviceId = 5,
                    thumbnailUrl = safeGet { cardData.requireString("face").replace("http:", "https:") },
                    bannerUrl = safeGet { userInfoData.requireString("/data/space/l_img").replace("http:", "https:") },
                    subscriberCount = safeGet { cardData.requireLong("fans") },
                    description = safeGet { cardData.requireString("sign") },
                    tabs = listOf(
                        ChannelTabInfo(newUrl, ChannelTabType.VIDEOS),
                        ChannelTabInfo("${BiliBiliLinks.GET_SEASON_ARCHIVES_LIST_BASE_URL}?mid=$id&page_num=1&page_size=10", ChannelTabType.PLAYLISTS)
                    )
                ),
                errors = errors,
                pagedData = PagedData(itemList, if (hasNext)newUrl.incrementUrlParam("pn") else null)
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
        val headers = BilibiliService.getHeadersWithCookie(url, cookie!!)
        val id = parseChannelId(url)!!
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask("videos", Payload(RequestMethod.GET, buildUserVideosUrlWebAPI(id, cookie, getQueryValue(url, "pn")!!), headers))
            ), PlainState(1))
        } else {
            val userVideoData = clientResults!!.first { it.taskId == "videos" }.result!!.asJson()
            val videosArray = userVideoData.requireArray("/data/list/vlist")
            videosArray.forEach { video ->
                commit { (BiliBiliStreamInfoDataParser.parseFromWebChannelInfoResponseJson(video)) }
            }
            val pn = runCatching{ userVideoData.requireInt("/data/page/pn") }.getOrDefault(1)
            val hasNext = runCatching{
                ceil(
                    userVideoData.requireInt("/data/page/count").toDouble() / userVideoData.requireInt("/data/page/ps")
                ).toInt() != pn
            }.getOrDefault(false)
            return JobStepResult.CompleteWith(ExtractResult(
                errors = errors,
                pagedData = PagedData(itemList, if (hasNext)url.incrementUrlParam("pn") else null)
            ))
        }
    }
}