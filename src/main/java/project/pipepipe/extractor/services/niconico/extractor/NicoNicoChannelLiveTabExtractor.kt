package project.pipepipe.extractor.services.niconico.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoLiveStreamInfoDataParser.parseFromLiveHistoryJson
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.ExtractResult
import project.pipepipe.shared.job.JobStepResult
import project.pipepipe.shared.job.PagedData
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod
import project.pipepipe.shared.job.TaskResult
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireObject

class NicoNicoChannelLiveTabExtractor(url: String) : Extractor<Nothing, StreamInfo>(url) {
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
        val userId = getQueryValue(url, "id")!!
        val offset = getQueryValue(url, "offset") ?: "0"

        if (currentState == null) {
            val realUrl = "https://live.nicovideo.jp/front/api/v1/user-broadcast-history?providerId=$userId&providerType=user&isIncludeNonPublic=false&offset=$offset&limit=10&withTotalCount=true"
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask("lives", Payload(RequestMethod.GET, realUrl, GOOGLE_HEADER))
                ), PlainState(1)
            )
        } else {
            val data = clientResults!!.first { it.taskId == "lives" }.result!!.asJson()
            val programsList = data.requireObject("data").requireArray("programsList")
            programsList.forEach { program ->
                commit { parseFromLiveHistoryJson(program) }
            }

            val currentOffset = offset.toIntOrNull() ?: 0
            val nextPageUrl = if (programsList.size() > 0) {
                url.replace("offset=$offset", "offset=${currentOffset + 10}")
            } else null

            return JobStepResult.CompleteWith(
                ExtractResult(
                    errors = errors,
                    pagedData = PagedData(
                        itemList = itemList,
                        nextPageUrl = nextPageUrl
                    )
                )
            )
        }
    }
}
