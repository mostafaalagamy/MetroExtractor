package project.pipepipe.extractor.services.youtube.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.youtube.YouTubeLinks.BROWSE_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.TAB_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.browseId
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getChannelInfoBody
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getContinuationBody
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser.parseFromShortsLockupViewModel
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser.parseFromVideoRenderer
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.extractor.utils.RequestHelper.replaceQueryValue
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
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString
import java.net.URLDecoder
import java.net.URLEncoder

class YouTubeChannelCommonVideoTabExtractor(
    url: String,
    val tabType: ChannelTabType
) : Extractor<Nothing, StreamInfo>(url) {
    override suspend fun fetchFirstPage(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?,
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.POST,
                            BROWSE_URL,
                            WEB_HEADER,
                            getChannelInfoBody(getQueryValue(url, "id")!!, tabType)
                        )
                    )
                ), PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val name = result.requireString("/metadata/channelMetadataRenderer/title")
            val nameEncoded = URLEncoder.encode(name, "UTF-8")
            var nextPageUrl: String? = null
            result.requireArray("/contents/twoColumnBrowseResultsRenderer/tabs").forEach {
                val id = runCatching { it.requireString("/tabRenderer/endpoint/browseEndpoint/browseId") }.getOrNull()
                if (runCatching{ it.requireString("/tabRenderer/endpoint/browseEndpoint/params") }.getOrNull() == tabType.browseId) {
                    it.requireArray("/tabRenderer/content/richGridRenderer/contents").mapNotNull {
                        runCatching{ it.requireObject("/richItemRenderer/content") }.getOrNull()?.let {
                            when (tabType) {
                                ChannelTabType.SHORTS -> commit { parseFromShortsLockupViewModel(it, name, id!!) }
                                else -> commit { parseFromVideoRenderer(it, name, id) }
                            }
                        }
                    }
                    runCatching{
                        it.requireArray("/tabRenderer/content/richGridRenderer/contents")
                            .last()
                            .requireString("/continuationItemRenderer/continuationEndpoint/continuationCommand/token")
                    }.getOrNull()?.let {
                        nextPageUrl = "$TAB_RAW_URL?id=$id&type=${tabType.name.lowercase()}&continuation=$it&name=$nameEncoded"
                    }
                }
            }
            return JobStepResult.CompleteWith(
                ExtractResult(errors = errors, pagedData = PagedData(
                    itemList = itemList,
                    nextPageUrl = nextPageUrl
                )))
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
            var nextPageUrl: String? = null
            result.requireArray("/onResponseReceivedActions/0/appendContinuationItemsAction/continuationItems").forEach {
                val videoRenderer = runCatching { it.requireObject("/richItemRenderer/content") }.getOrNull()
                if (videoRenderer != null) {
                    when (tabType) {
                        ChannelTabType.SHORTS -> commit { parseFromShortsLockupViewModel(
                            data = videoRenderer,
                            overrideChannelName = URLDecoder.decode(getQueryValue(url, "name"), "UTF-8"),
                            overrideChannelId = getQueryValue(url, "id")!!
                        ) }
                        else -> commit { parseFromVideoRenderer(
                            data = videoRenderer,
                            overrideChannelName = URLDecoder.decode(getQueryValue(url, "name"), "UTF-8"),
                            overrideChannelId = getQueryValue(url, "id")
                        ) }
                    }
                } else { //continuation item
                    nextPageUrl = replaceQueryValue(url, "continuation", it.requireString("/continuationItemRenderer/continuationEndpoint/continuationCommand/token"))
                }
            }
            return JobStepResult.CompleteWith(ExtractResult(
                errors = errors,
                pagedData = PagedData(
                    itemList = itemList,
                    nextPageUrl = nextPageUrl
                )
            ))
        }
    }
}