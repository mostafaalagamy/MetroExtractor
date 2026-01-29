package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks.API_V2_URL
import project.pipepipe.extractor.services.soundcloud.SoundCloudService.Companion.DEFAULT_HEADER
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudStreamInfoDataParser
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.helper.stream.Description
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.ErrorDetail
import project.pipepipe.shared.job.ExtractResult
import project.pipepipe.shared.job.JobStepResult
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod
import project.pipepipe.shared.job.isDefaultTask
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.StreamExtractState
import project.pipepipe.extractor.utils.json.requireLong
import project.pipepipe.extractor.utils.json.requireObject
import project.pipepipe.extractor.utils.json.requireString
import java.net.URLEncoder
import kotlin.text.ifEmpty

class SoundCloudStreamExtractor(
    url: String,
) : Extractor<StreamInfo, Nothing>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: project.pipepipe.shared.state.State?,
        clientResults: List<project.pipepipe.shared.job.TaskResult>?,
        cookie: String?
    ): project.pipepipe.shared.job.JobStepResult {
        val clientId = cookie!!.substringAfter("client_id=")
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(payload = Payload(RequestMethod.GET, "${SoundCloudLinks.RESOLVE_URL}?client_id=$clientId&format=json&url=${URLEncoder.encode(url, "UTF-8")}", DEFAULT_HEADER))
            ), PlainState(1))
        } else if (currentState.step == 1) {
            val response = clientResults!!.first { it.taskId.isDefaultTask()}.result!!.asJson()
            when (response.requireString("policy")) {
                "ALLOW", "MONETIZE" -> {}
                "SNIP" -> return JobStepResult.FailWith(
                    ErrorDetail(
                        code = "PAID_001",
                        stackTrace = IllegalStateException("Requires SoundCloud Go+").stackTraceToString()
                    )
                )
                "BLOCK" -> return JobStepResult.FailWith(
                    ErrorDetail(
                        code = "GEO_001",
                        stackTrace = IllegalStateException("Sorry, this video can only be viewed in the same region where it was uploaded.").stackTraceToString()
                    )
                )
                else -> return JobStepResult.FailWith(
                    ErrorDetail(
                        code = "UNAV_001",
                        stackTrace = IllegalStateException("Content unavailable, reason: "+ response.requireString("policy")).stackTraceToString()
                    )
                )
            }
            return JobStepResult.ContinueWith(listOf(
                ClientTask(payload = Payload(RequestMethod.GET, response.requireString("/media/transcodings/0/url") + "?client_id=$clientId"))),
                StreamExtractState(2, SoundCloudStreamInfoDataParser.parseFromTrackObject(response).apply {
                    this.likeCount = response.requireLong("likes_count")
                    this.uploaderSubscriberCount = response.requireLong("/user/followers_count")
                    this.description = Description( response.requireString("description"), 3)
                    this.tags = "\"([^\"]*)\"|(\\S+)".toRegex().findAll(response.requireString("tag_list"))
                        .map { it.groups[1]?.value ?: it.groups[2]?.value ?: "" }
                        .toList().takeIf { it.isNotEmpty() }
//                    this.relatedItemUrl = "$API_V2_URL/tracks/${response.requireString("id")}/related"
                    this.commentUrl ="$API_V2_URL/tracks/${response.requireString("id")}/comments"
                }
            ))
        } else if (currentState.step == 2) {
            val response = clientResults!!.first { it.taskId.isDefaultTask()}.result!!.asJson()
            val streamInfo = (currentState as StreamExtractState).streamInfo.apply { this.hlsUrl = response.requireString("url") }
            return JobStepResult.CompleteWith(ExtractResult(streamInfo))
        }
        else throw java.lang.IllegalStateException()
    }
}
