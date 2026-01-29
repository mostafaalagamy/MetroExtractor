package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudCommentInfoDataParser
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.services.soundcloud.SoundCloudService.Companion.DEFAULT_HEADER

class SoundCloudCommentExtractor(
    url: String,
) : Extractor<Nothing, CommentInfo>(url) {
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
            val clientId = cookie!!.substringAfter("client_id=")
            val apiUrl = "$url?client_id=$clientId&threaded=0&filter_replies=1"
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            method = RequestMethod.GET,
                            url = apiUrl,
                            headers = DEFAULT_HEADER
                        )
                    )
                ),
                PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }
            val jsonData = result.result?.asJson() ?: throw IllegalStateException("Result is not JSON")
            val collection = jsonData.requireArray("collection")
            collection.forEach { comment ->
                commit { SoundCloudCommentInfoDataParser.parseFromCommentObject(comment) }
            }
            val nextPageUrl = runCatching {
                jsonData.at("/next_href").asText()
            }.getOrNull()
            return JobStepResult.CompleteWith(
                ExtractResult(
                    info = null,
                    errors = errors,
                    pagedData = PagedData(itemList, nextPageUrl)
                )
            )
        }
    }
}