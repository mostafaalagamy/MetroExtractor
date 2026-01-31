package project.pipepipe.extractor.services.youtube.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.youtube.YouTubeLinks.BROWSE_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getContinuationBody
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getPlaylistInfoBody
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser.parseFromPlaylistVideoRenderer
import project.pipepipe.extractor.utils.extractDigitsAsLong
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
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
import project.pipepipe.extractor.utils.RequestHelper.replaceQueryValue
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.Router.setType
import project.pipepipe.extractor.services.youtube.YouTubeLinks.CHANNEL_URL
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireString

class YouTubePlaylistExtractor(
    url: String,
) : Extractor<PlaylistInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
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
                            getPlaylistInfoBody(getQueryValue(url, "list")!!)
                        )
                    )
                ), PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            var nextPageUrl: String? = null
            result.requireArray("/contents/twoColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/sectionListRenderer/contents/0/itemSectionRenderer/contents/0/playlistVideoListRenderer/contents")
                .forEach {
                    if (it.has("playlistVideoRenderer")) {
                        commit { parseFromPlaylistVideoRenderer(it) }
                    } else {
                        nextPageUrl =
                            "$url&continuation=${it.requireString("/continuationItemRenderer/continuationEndpoint/commandExecutorCommand/commands/1/continuationCommand/token")}"
                    }
                }
            return JobStepResult.CompleteWith(
                ExtractResult(
                    info = PlaylistInfo(
                        serviceId = 0,
                        url = result.requireString("/microformat/microformatDataRenderer/urlCanonical")
                            .setType("https"),
                        name = result.requireString("/microformat/microformatDataRenderer/title"),
                        thumbnailUrl = result.requireString("/microformat/microformatDataRenderer/thumbnail/thumbnails/0/url"),
                        streamCount = result.requireString("/sidebar/playlistSidebarRenderer/items/0/playlistSidebarPrimaryInfoRenderer/stats/0/runs/0/text")
                            .extractDigitsAsLong(),
                        uploaderName = listOf(
                            "/header/playlistHeaderRenderer/ownerText/runs/0/text",
                            "/sidebar/playlistSidebarRenderer/items/1/playlistSidebarSecondaryInfoRenderer/videoOwner/videoOwnerRenderer/title/runs/0/text"
                        ).firstNotNullOfOrNull { path -> runCatching { result.requireString(path) }.getOrNull() },
                        uploaderUrl = listOf(
                            "/header/playlistHeaderRenderer/ownerText/runs/0/navigationEndpoint/browseEndpoint/browseId",
                            "/sidebar/playlistSidebarRenderer/items/1/playlistSidebarSecondaryInfoRenderer/videoOwner/videoOwnerRenderer/title/runs/0/navigationEndpoint/browseEndpoint/browseId"
                        ).firstNotNullOfOrNull { path -> runCatching { result.requireString(path) }.getOrNull() }
                            ?.let { CHANNEL_URL + it },
                    ),
                    errors = errors,
                    pagedData = PagedData(itemList, nextPageUrl)
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
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.POST,
                            BROWSE_URL,
                            WEB_HEADER,
                            getContinuationBody(getQueryValue(url, "continuation")!!)
                        )
                    )
                ), PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            var nextUrl: String? = null
            result.requireArray("/onResponseReceivedActions/0/appendContinuationItemsAction/continuationItems")
                .forEach {
                    if (it.has("playlistVideoRenderer")) {
                        commit { parseFromPlaylistVideoRenderer(it) }
                    } else { //continuation
                        nextUrl = "${
                            replaceQueryValue(
                                url,
                                "continuation",
                                it.requireString("/continuationItemRenderer/continuationEndpoint/continuationCommand/token")
                            )
                        }"
                    }

                }
            return JobStepResult.CompleteWith(
                ExtractResult(errors = errors, pagedData = PagedData(itemList, nextUrl))
            )
        }
    }
}