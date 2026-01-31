package project.pipepipe.extractor.services.bilibili.extractor

import project.pipepipe.extractor.base.SearchExtractor
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliChannelInfoDataParser
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliStreamInfoDataParser
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireObject
import project.pipepipe.extractor.utils.json.requireString
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState

class BiliBiliSearchExtractor(url: String): SearchExtractor(url) {
    override suspend fun fetchFirstPage(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        return fetchGivenPage(url, sessionId, currentState, clientResults, cookie)
    }

    override suspend fun fetchGivenPage(
        url: String,
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(
                    payload = Payload(
                        RequestMethod.GET, url,
                        BilibiliService.getHeadersWithCookie(url, cookie!!)
                    )
                )
            ), state = PlainState(1))
        } else {
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
                .requireObject("data").requireArray("result")

            data.forEach { item ->
                val type = item.requireString("type")
                when (type) {
                    "video" -> commit { (BiliBiliStreamInfoDataParser.parseFromStreamInfoJson(item)) }
                    "live_room" -> commit { (BiliBiliStreamInfoDataParser.parseFromLiveInfoJson(item, 0)) }
                    "bili_user" -> commit{BiliBiliChannelInfoDataParser.parseFromChannelSearchJson(item)}
                    "media_bangumi", "media_ft" -> commit{BiliBiliStreamInfoDataParser.parseFromPremiumContentJson(item)}
                }
            }
            return JobStepResult.CompleteWith(ExtractResult(errors = errors, pagedData = PagedData(
                itemList, url.incrementUrlParam("page")
            )))
        }
    }
}