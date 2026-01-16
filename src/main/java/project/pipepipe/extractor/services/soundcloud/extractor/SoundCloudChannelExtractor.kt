package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudChannelInfoDataParser
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudStreamInfoDataParser
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.ChannelTabInfo
import project.pipepipe.shared.infoitem.ChannelTabType
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
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks.API_V2_URL
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.extractor.services.soundcloud.SoundCloudService.Companion.DEFAULT_HEADER
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.state.ChannelExtractState
import java.net.URLEncoder


private const val ITEMS_PER_PAGE = 20

class SoundCloudChannelExtractor(
    url: String,
) : Extractor<ChannelInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            val clientId = cookie!!.substringAfter("client_id=")
            val apiUrl = "${SoundCloudLinks.RESOLVE_URL}?client_id=$clientId&format=json&url=${URLEncoder.encode(url, "UTF-8")}"
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
        } else if (currentState.step == 1) {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val userId = result.requireString("id")
            val tracksApiUrl = "${SoundCloudLinks.API_V2_URL}/users/$userId/tracks?client_id=${cookie!!.substringAfter("client_id=")}&limit=$ITEMS_PER_PAGE&linked_partitioning=1"
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            method = RequestMethod.GET,
                            url = tracksApiUrl,
                            headers = DEFAULT_HEADER
                        )
                    )
                ),
                ChannelExtractState(2, SoundCloudChannelInfoDataParser.parseFromChannelObject(result, userId))
            )
        } else {
            val tracksResult = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val channelInfo = (currentState as ChannelExtractState).channelInfo
            val channelUrl = channelInfo.url
            val userId = getQueryValue(channelUrl, "uid")

            val collection = tracksResult.requireArray("collection")
            collection.forEach { track ->
                commit { SoundCloudStreamInfoDataParser.parseFromTrackObject(track) }
            }
            
            val nextPageUrl = runCatching { 
                tracksResult.requireString("next_href")
            }.getOrNull()
            
            val tabs = listOf(
                ChannelTabInfo(
                    url = "$channelUrl/tracks",
                    type = ChannelTabType.VIDEOS
                ),
                ChannelTabInfo(
                    url = "$API_V2_URL/users/$userId/playlists_without_albums",
                    type = ChannelTabType.PLAYLISTS
                ),
                ChannelTabInfo(
                    url = "$API_V2_URL/users/$userId/albums",
                    type = ChannelTabType.ALBUMS
                )
            )
            
            return JobStepResult.CompleteWith(
                ExtractResult(
                    info = channelInfo.apply { this.tabs = tabs },
                    errors = errors,
                    pagedData = PagedData(itemList, nextPageUrl)
                )
            )
        }
    }
}