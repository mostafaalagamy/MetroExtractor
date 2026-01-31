package project.pipepipe.extractor.services.youtube.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.youtube.YouTubeLinks.BROWSE_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getChannelInfoBody
import project.pipepipe.extractor.services.youtube.dataparser.YouTubePlaylistInfoDataParser.parseFromLockupMetadataViewModel
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.ChannelTabType
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
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireString
import java.net.URLEncoder

class YouTubeChannelPlaylistTabExtractor(
    url: String,
) : Extractor<Nothing, PlaylistInfo>(url) {
    override suspend fun fetchFirstPage(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.POST,
                            BROWSE_URL,
                            WEB_HEADER,
                            getChannelInfoBody(getQueryValue(url, "id")!!, ChannelTabType.PLAYLISTS)
                        )
                    )
                ), PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val name = result.requireString("/metadata/channelMetadataRenderer/title")
            val nameEncoded = URLEncoder.encode(name, "UTF-8")
            result.requireArray("/contents/twoColumnBrowseResultsRenderer/tabs").forEach {
                if (runCatching{ it.requireString("/tabRenderer/title") }.getOrNull() == "Playlists") {
                    it.requireArray("/tabRenderer/content/sectionListRenderer/contents/0/itemSectionRenderer/contents/0/gridRenderer/items").forEach {
                        commit { parseFromLockupMetadataViewModel(it, name) }
                    }
                }
            }
            return JobStepResult.CompleteWith(
                ExtractResult(errors = errors, pagedData = PagedData(
                    itemList = itemList,
                    nextPageUrl = null
                )))
        }
    }
}