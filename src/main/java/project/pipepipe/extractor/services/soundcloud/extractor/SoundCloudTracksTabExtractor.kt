package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudStreamInfoDataParser
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.ExtractResult
import project.pipepipe.shared.job.JobStepResult
import project.pipepipe.shared.job.PagedData
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod
import project.pipepipe.shared.job.TaskResult
import project.pipepipe.shared.job.isDefaultTask
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.extractor.services.soundcloud.SoundCloudService.Companion.DEFAULT_HEADER


private const val ITEMS_PER_PAGE = 20

class SoundCloudTracksTabExtractor(
    url: String,
) : Extractor<Nothing, StreamInfo>(url) {
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
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            method = RequestMethod.GET,
                            url = "$url&client_id=$clientId",
                            headers = DEFAULT_HEADER
                        )
                    )
                ),
                PlainState(1)
            )
        } else {
            val jsonData = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val collection = jsonData.requireArray("collection")
            collection.forEach { track ->
                val kind = runCatching { track.requireString("kind") }.getOrNull() ?: "track"
                if (kind == "track") {
                    commit { SoundCloudStreamInfoDataParser.parseFromTrackObject(track) }
                }
            }
            val nextPageUrl = runCatching { 
                jsonData.requireString("/next_href")
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