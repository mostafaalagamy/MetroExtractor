package project.pipepipe.extractor.baseextractor

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.Json
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.utils.RandomStringFromAlphabetGenerator
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.helper.SponsorBlockCategory
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.PreFetchPayloadState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireDouble
import project.pipepipe.extractor.utils.json.requireString
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

class SponsorBlockExtractor(url: String) : Extractor<Info, SponsorBlockSegmentInfo>(url) {
    private val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val NUMBER_GENERATOR = SecureRandom()
    private val BILIBILI_SPONSORBLOCK_API_URL = "https://bsbsb.top/api/"
    private val YOUTUBE_SPONSORBLOCK_API_URL = "https://sponsor.ajay.app/api/"
    private val BILIBILI_SPONSORBLOCK_RAW_URL = "sponsorblock://bilibili.raw"
    private val YOUTUBE_SPONSORBLOCK_RAW_URL = "sponsorblock://youtube.raw"

    suspend fun fetchResult(
        currentState: State?,
        clientResults: List<TaskResult>?,
    ): JobStepResult {
        val apiUrl = when  {
            url.contains(YOUTUBE_SPONSORBLOCK_RAW_URL) -> YOUTUBE_SPONSORBLOCK_API_URL
            url.contains(BILIBILI_SPONSORBLOCK_RAW_URL) -> BILIBILI_SPONSORBLOCK_API_URL
            else -> throw IllegalArgumentException()
        }
        val videoId = getQueryValue(url, "id")!!
        if (currentState == null) {
            val categoryParams = encodeUrlUtf8(
                "[\"sponsor\",\"intro\",\"outro\",\"interaction\",\"highlight\",\"selfpromo\",\"music_offtopic\",\"preview\",\"filler\"]"
            )
            val actionParams = encodeUrlUtf8("[\"skip\",\"poi\"]")
            val videoIdHash = toSha256(videoId)

            val queryUrl = "${apiUrl}skipSegments/${videoIdHash.take(4)}" +
                    "?categories=$categoryParams&actionTypes=$actionParams&userAgent=Mozilla/5.0"
            return JobStepResult.ContinueWith(listOf(
                ClientTask(
                    payload = Payload(
                        RequestMethod.GET, queryUrl, mapOf("Origin" to "PipePipe")
                    )
                )
            ), state = PlainState(0))
        } else {
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            data.filter { it.requireString("videoID") == videoId }.forEach {
                it.requireArray("segments").forEach { entry ->
                    val segmentInfo = entry.requireArray("segment")
                    val startTime = segmentInfo.requireDouble(0) * 1000
                    val endTime = segmentInfo.requireDouble(1) * 1000
                    commit {
                        parseFromSponsorBlockJson(entry, startTime, endTime)
                    }
                }
            }
            return JobStepResult.CompleteWith(ExtractResult(errors=errors, pagedData = PagedData(itemList, null)))
        }
    }


    fun submitSponsorBlockSegment(
        currentState: State?,
    ): JobStepResult {
        val apiUrl = when  {
            url.contains(YOUTUBE_SPONSORBLOCK_RAW_URL) -> YOUTUBE_SPONSORBLOCK_API_URL
            url.contains(BILIBILI_SPONSORBLOCK_RAW_URL) -> BILIBILI_SPONSORBLOCK_API_URL
            else -> throw IllegalArgumentException()
        }
        if (currentState!!.step == -1) {
            val segment = Json.decodeFromString<SponsorBlockSegmentInfo>((currentState as PreFetchPayloadState).payload)
            require (segment.category != SponsorBlockCategory.PENDING)
            val videoId = getQueryValue(url, "id")
            val localUserId = RandomStringFromAlphabetGenerator.generate(ALPHABET, 32, NUMBER_GENERATOR)
            val actionType = if (segment.category == SponsorBlockCategory.HIGHLIGHT) "poi" else "skip"
            val startInSeconds = segment.startTime / 1000.0
            val endInSeconds = if (segment.category == SponsorBlockCategory.HIGHLIGHT) {
                startInSeconds
            } else {
                segment.endTime.coerceAtLeast(segment.startTime + 1050.0) / 1000.0
            }
            val queryUrl = "${apiUrl}skipSegments?" +
                    "videoID=$videoId" +
                    "&startTime=$startInSeconds" +
                    "&endTime=$endInSeconds" +
                    "&category=${segment.category.apiName}" +
                    "&userID=$localUserId" +
                    "&userAgent=PipePipe/5.0.0" +
                    "&actionType=$actionType"
            return JobStepResult.ContinueWith(listOf(
                ClientTask(
                    payload = Payload(
                        RequestMethod.POST, queryUrl, mapOf("Origin" to "PipePipe")
                    )
                )
            ), state = PlainState(0))
        } else {
            return JobStepResult.CompleteWith(ExtractResult())
        }
    }

    fun submitSponsorBlockSegmentVote(
        currentState: State?,
    ): JobStepResult {
        val apiUrl = when {
            url.contains(YOUTUBE_SPONSORBLOCK_RAW_URL) -> YOUTUBE_SPONSORBLOCK_API_URL
            url.contains(BILIBILI_SPONSORBLOCK_RAW_URL) -> BILIBILI_SPONSORBLOCK_API_URL
            else -> throw IllegalArgumentException()
        }

        if (currentState == null) {
            val uuid = getQueryValue(url, "uuid")
            val vote = getQueryValue(url, "vote")
            val localUserId = RandomStringFromAlphabetGenerator.generate(ALPHABET, 32, NUMBER_GENERATOR)
            val queryUrl = "${apiUrl}voteOnSponsorTime?UUID=$uuid&userID=$localUserId&type=$vote"

            return JobStepResult.ContinueWith(listOf(
                ClientTask(
                    payload = Payload(
                        RequestMethod.POST, queryUrl, mapOf("Origin" to "PipePipe")
                    )
                )
            ), state = PlainState(0))
        } else {
            return JobStepResult.CompleteWith(ExtractResult())
        }
    }

    fun parseFromSponsorBlockJson(itemObject: JsonNode, startTime: Double, endTime: Double):  SponsorBlockSegmentInfo {
        return SponsorBlockSegmentInfo(
            itemObject.requireString("UUID"), startTime, endTime,
            SponsorBlockCategory.fromApiName(itemObject.requireString("category")),
        )
    }

    fun encodeUrlUtf8(string: String?): String? {
        return URLEncoder.encode(string, "UTF-8")
    }

    fun toSha256(videoId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(videoId.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder()

        for (b in bytes) {
            val hex = Integer.toHexString(0xff and b.toInt())

            if (hex.length == 1) {
                sb.append('0')
            }

            sb.append(hex)
        }

        return sb.toString()
    }
}