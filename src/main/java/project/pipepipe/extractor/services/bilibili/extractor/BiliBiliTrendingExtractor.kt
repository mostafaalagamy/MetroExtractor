package project.pipepipe.extractor.services.bilibili.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.FETCH_RECOMMENDED_LIVES_URL
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.FETCH_TOP_100_URL
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.Utils
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliStreamInfoDataParser
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireObject

class BiliBiliTrendingExtractor(url: String) : Extractor<Nothing, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val baseUrl = when {
            getQueryValue(url, "name") == "trending" -> FETCH_TOP_100_URL
            getQueryValue(url, "name") == "recommended_lives" -> FETCH_RECOMMENDED_LIVES_URL
            else -> error("")
        }
        val params = when {
            getQueryValue(url, "name") == "trending" -> linkedMapOf(
                "rid" to "0",
                "type" to "all",
                "web_location" to "333.934"
            )
            getQueryValue(url, "name") == "recommended_lives" -> linkedMapOf(
                "page_size" to "30",
                "page" to "1",
                "platform" to "web"
            )
            else -> error("")
        }
        if (currentState == null) {
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.GET,
                            Utils.getWbiResult(baseUrl, params, cookie!!),
                            headers = BilibiliService.getHeadersWithCookie(baseUrl, cookie).apply {
                                when {
                                    getQueryValue(url, "name") == "trending" -> set("Referer", "https://www.bilibili.com/v/popular/rank/all")
                                    getQueryValue(url, "name") == "recommended_lives" -> set("Referer", "https://live.bilibili.com/")
                                    else -> error("")
                                }

                            }
                        )
                    ),
                ), state = PlainState(1)
            )
        } else {
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            when {
                getQueryValue(url, "name") == "recommended_lives" -> {
                    val results = data.requireObject("data").requireArray("list")
                    results.forEach { item ->
                        commit { BiliBiliStreamInfoDataParser.parseFromRecommendLiveInfoJson(item) }
                    }
                }

                getQueryValue(url, "name") == "trending" -> {
                    val results = data.requireObject("data").requireArray("list")
                    results.forEach { item ->
                        commit { BiliBiliStreamInfoDataParser.parseFromTrendingInfoJson(item) }
                    }
                }
            }
            return JobStepResult.CompleteWith(
                result = ExtractResult(
                    errors = errors,
                    pagedData = PagedData(
                        itemList, null
                    )
                )
            )
        }
    }
}