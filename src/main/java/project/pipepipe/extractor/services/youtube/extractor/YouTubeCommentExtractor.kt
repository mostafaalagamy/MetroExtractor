package project.pipepipe.extractor.services.youtube.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.youtube.YouTubeLinks.COMMENT_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.NEXT_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getContinuationBody
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeCommentInfoDataParser
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.CommentInfo
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
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireObject
import project.pipepipe.extractor.utils.json.requireString

class YouTubeCommentExtractor(url: String) : Extractor<Nothing, CommentInfo>(url) {
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
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.POST,
                            NEXT_URL,
                            WEB_HEADER,
                            getContinuationBody(getQueryValue(url, "continuation")!!)
                        )
                    )
                ), PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val metaContinuationItems = result.requireArray("/onResponseReceivedEndpoints")
                .firstNotNullOfOrNull { item ->
                    listOf(
                        "/reloadContinuationItemsCommand/continuationItems",
                        "/appendContinuationItemsAction/continuationItems"
                    ).firstNotNullOfOrNull { path ->
                        val result = runCatching { item.requireArray(path) }.getOrNull()
                        if (result != null) {
                            if (path.startsWith("/reload") &&
                                item.requireString("/reloadContinuationItemsCommand/slot").contains("HEADER")) {
                                return@firstNotNullOfOrNull null
                            } else {
                                return@firstNotNullOfOrNull result
                            }
                        }
                        null
                    }
                }?.map {
                    if (it.has("commentThreadRenderer")) it.requireObject("commentThreadRenderer")
                    else it
                }

            result.requireArray("/frameworkUpdates/entityBatchUpdate/mutations").forEach {
                if (!it.at("/payload/commentEntityPayload").isMissingNode) {
                    val commentViewModel = metaContinuationItems?.find { item ->
                        runCatching{
                            item.requireString("/commentViewModel/commentViewModel/commentKey") == it.requireString(
                                "entityKey"
                            )
                        }.getOrDefault(false) }
                    commit { YouTubeCommentInfoDataParser.parseFromCommentData(it, commentViewModel) }
                }
            }

            val nextPageUrl = metaContinuationItems?.firstNotNullOfOrNull { item ->
                listOf(
                    "/continuationItemRenderer/continuationEndpoint/continuationCommand/token",
                    "/continuationItemRenderer/button/buttonRenderer/command/continuationCommand/token"
                ).firstNotNullOfOrNull { path ->
                    runCatching {
                        "$COMMENT_RAW_URL?continuation=${item.requireString(path)}"
                    }.getOrNull()
                }
            }

            return JobStepResult.CompleteWith(ExtractResult(
                info = null,
                errors = errors,
                pagedData = PagedData(itemList, nextPageUrl)
            ))
        }
    }
}