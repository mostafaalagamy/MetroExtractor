package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudPlaylistInfoDataParser
import project.pipepipe.shared.infoitem.PlaylistInfo
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
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks
import project.pipepipe.extractor.services.soundcloud.SoundCloudService.Companion.DEFAULT_HEADER
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireString


private const val ITEMS_PER_PAGE = 20

class SoundCloudAlbumsTabExtractor(
    url: String,
) : Extractor<Nothing, PlaylistInfo>(url) {
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
                            url = "$url?client_id=$clientId&limit=20&linked_partitioning=1",
                            headers = DEFAULT_HEADER
                        )
                    )
                ),
                PlainState(1)
            )
        } else {
            val jsonData = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val collection = jsonData.requireArray("collection")
            collection.forEach { item ->
                val kind = runCatching { item.requireString("kind") }.getOrNull() ?: return@forEach
                if (kind == "playlist") {
                    commit { SoundCloudPlaylistInfoDataParser.parseFromPlaylistObject(item) }
                }
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