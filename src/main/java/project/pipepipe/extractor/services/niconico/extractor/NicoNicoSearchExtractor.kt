package project.pipepipe.extractor.services.niconico.extractor

import org.jsoup.Jsoup
import project.pipepipe.extractor.ExtractorContext.objectMapper
import project.pipepipe.extractor.base.SearchExtractor
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoStreamInfoDataParser.parseFromStreamCommonJson
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.ExtractResult
import project.pipepipe.shared.job.JobStepResult
import project.pipepipe.shared.job.PagedData
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod
import project.pipepipe.shared.job.TaskResult
import project.pipepipe.shared.job.isDefaultTask
import project.pipepipe.shared.state.PlainState

class NicoNicoSearchExtractor(url: String): SearchExtractor(url) {
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
            val response = clientResults!!.first { it.taskId.isDefaultTask() }.result!!
            val page = objectMapper.readTree(Jsoup.parse(response).selectFirst("meta[name=server-response]")!!.attr("content"))
            val data = page.requireArray("/data/response/\$getSearchVideoV2/data/items")
            data.forEach {
                commit { parseFromStreamCommonJson(it) }
            }
            val nextPageUrl = if (data.isEmpty) null else url.incrementUrlParam("page")

            return JobStepResult.CompleteWith(ExtractResult(errors = errors, pagedData = PagedData(
                itemList, nextPageUrl
            )))
        }
    }
}