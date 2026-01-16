package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudPlaylistInfoDataParser
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudStreamInfoDataParser
import project.pipepipe.shared.infoitem.PlaylistInfo
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
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.extractor.services.soundcloud.SoundCloudService.Companion.DEFAULT_HEADER
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.extractor.utils.RequestHelper.replaceQueryValue

private const val STREAMS_PER_REQUESTED_PAGE = 15

class SoundCloudPlaylistExtractor(
    url: String,
) : Extractor<PlaylistInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            val clientId = cookie!!.substringAfter("client_id=")
            val id = getQueryValue(url, "pid")!!
            val apiUrl = "${SoundCloudLinks.API_V2_URL}/playlists/$id?client_id=$clientId&representation=compact"
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
            val jsonData = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val tracksArray = jsonData.requireArray("tracks")

            val playlistInfo = SoundCloudPlaylistInfoDataParser.parseFromPlaylistObject(jsonData)
            val trackIds = tracksArray.mapNotNull { track ->
                runCatching { track.requireLong("id") }.getOrNull()
            }
            tracksArray.forEach { track ->
                if (track.has("created_at")) commit { SoundCloudStreamInfoDataParser.parseFromTrackObject(track) }
            }
            val nextPageUrl = if (trackIds.size == itemList.size) null else {
                "$url&ids=${trackIds.joinToString(",")}"
            }

            val pagedData = PagedData(itemList, nextPageUrl)

            return JobStepResult.CompleteWith(
                ExtractResult(
                    info = playlistInfo,
                    errors = errors,
                    pagedData = pagedData
                )
            )
        }
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
            val apiUrl = "${SoundCloudLinks.API_V2_URL}/tracks?client_id=$clientId&ids=${getQueryValue(url, "ids")}"
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
            val tracksArray = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val trackIds = tracksArray.mapNotNull { track ->
                runCatching { track.requireLong("id") }.getOrNull()
            }
            tracksArray.forEach { track ->
                if (track.has("created_at")) commit { SoundCloudStreamInfoDataParser.parseFromTrackObject(track) }
            }
            val nextPageUrl = if (trackIds.size == itemList.size) null else replaceQueryValue(url, "ids", trackIds.joinToString(","))
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