package project.pipepipe.extractor.services.youtube.extractor

import org.jsoup.Jsoup
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.youtube.YouTubeLinks.FAST_FEED_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_UA
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeChannelIdParser.parseChannelId
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State

class YouTubeFastFeedExtractor(url: String): Extractor<Nothing, StreamInfo>(url) {
    override suspend fun fetchFirstPage(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            val id = parseChannelId(url)!!
            if (!url.contains("/channel/")) return JobStepResult.FailWith(ErrorDetail("ROUTE_FAILED", IllegalStateException("Incorrect feed url").stackTraceToString()))
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.GET,
                            FAST_FEED_URL + id,
                            hashMapOf("User-Agent" to WEB_UA)
                        )
                    )
                ), PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!
            val document = Jsoup.parse(result)
            document.select("feed > entry").forEach{
                commit { YouTubeStreamInfoDataParser.parseFromFastFeedEntry(it) }
            }
            return JobStepResult.CompleteWith(
                ExtractResult(errors = errors, pagedData = PagedData(itemList, null))
            )
        }
    }
}