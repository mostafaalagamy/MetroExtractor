package project.pipepipe.extractor.services.niconico.extractor

import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.base.SearchExtractor
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoPlaylistInfoDataParser.parseFromMylistJson
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireBoolean

class NicoNicoPlaylistSearchExtractor(url: String): SearchExtractor(url) {
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
                        RequestMethod.GET, url, GOOGLE_HEADER
                    )
                )
            ), state = PlainState(1))
        } else {
            val response = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val data = response.requireArray("/data/items")
            data.forEach {
                commit { parseFromMylistJson(it) }
            }
            val nextPageUrl = if (response.requireBoolean("/data/hasNext")) url.incrementUrlParam("page") else null

            return JobStepResult.CompleteWith(ExtractResult(errors = errors, pagedData = PagedData(
                itemList, nextPageUrl
            )))
        }
    }
}