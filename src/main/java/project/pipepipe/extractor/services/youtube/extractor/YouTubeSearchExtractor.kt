package project.pipepipe.extractor.services.youtube.extractor

import project.pipepipe.extractor.base.SearchExtractor
import project.pipepipe.extractor.services.youtube.YouTubeLinks.SEARCH_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.SEARCH_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeSearchLinkParser.getSearchBody
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser

import project.pipepipe.shared.state.State
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeChannelInfoDataParser
import project.pipepipe.extractor.services.youtube.dataparser.YouTubePlaylistInfoDataParser
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.ExtractResult
import project.pipepipe.shared.job.JobStepResult
import project.pipepipe.shared.job.PagedData
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod
import project.pipepipe.shared.job.TaskResult
import project.pipepipe.shared.job.isDefaultTask
import project.pipepipe.shared.state.PlainState


class YouTubeSearchExtractor(url: String): SearchExtractor(url) {
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
                        RequestMethod.POST, SEARCH_URL, WEB_HEADER, getSearchBody(url)
                    )
                )
            ), state = PlainState(1))
        } else {
            val jsonNode = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val data = runCatching {
                jsonNode.requireArray("/contents/twoColumnSearchResultsRenderer/primaryContents/sectionListRenderer/contents")
            }.getOrNull() ?: jsonNode.requireArray("/onResponseReceivedCommands/0/appendContinuationItemsAction/continuationItems")

            when (getQueryValue(url, "type")) {
                "video", "movie" -> data.first { it.has("itemSectionRenderer") }.requireArray("/itemSectionRenderer/contents").filter { it.has("videoRenderer") }.forEach {
                    commit { YouTubeStreamInfoDataParser.parseFromVideoRenderer(it) }
                }
                "playlist" -> data.first { it.has("itemSectionRenderer") }.requireArray("/itemSectionRenderer/contents").filter { it.has("lockupViewModel") }.forEach{
                    commit { YouTubePlaylistInfoDataParser.parseFromLockupMetadataViewModel(it) }
                }
                "channel" -> data.first { it.has("itemSectionRenderer") }.requireArray("/itemSectionRenderer/contents").filter { it.has("channelRenderer") }.forEach {
                    commit { YouTubeChannelInfoDataParser.parseFromChannelRenderer(it) }
                }
            }
            var nextPageUrl: String? = null
            val continuationItemRenderer = data.firstOrNull {it.has("continuationItemRenderer")}
            continuationItemRenderer?.let { nextPageUrl = "$SEARCH_RAW_URL?continuation=${it.requireString("/continuationItemRenderer/continuationEndpoint/continuationCommand/token")}&type=${getQueryValue(url, "type")}"}
            return JobStepResult.CompleteWith(ExtractResult(errors = errors, pagedData = PagedData(
                itemList, nextPageUrl
            )))
        }
    }
}