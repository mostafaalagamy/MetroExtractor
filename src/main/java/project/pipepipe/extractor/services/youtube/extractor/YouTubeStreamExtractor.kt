package project.pipepipe.extractor.services.youtube.extractor

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext
import project.pipepipe.extractor.services.youtube.YouTubeLinks.CHANNEL_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.COMMENT_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.NEXT_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.STREAM_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.VIDEO_INFO_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.getAndroidFetchStreamUrl
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.ANDROID_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getAndroidFetchStreamBody
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getVideoInfoBody
import project.pipepipe.extractor.services.youtube.YouTubeUrlParser
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser.parseFromLockupViewModel
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.createMultiStreamDashManifest
import project.pipepipe.extractor.utils.parseMediaType
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.shared.infoitem.RelatedItemInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.StreamType
import project.pipepipe.shared.infoitem.helper.stream.AudioStream
import project.pipepipe.shared.infoitem.helper.stream.Description
import project.pipepipe.shared.infoitem.helper.stream.Description.Companion.PLAIN_TEXT
import project.pipepipe.shared.infoitem.helper.stream.Frameset
import project.pipepipe.shared.infoitem.helper.stream.SubtitleStream
import project.pipepipe.shared.infoitem.helper.stream.VideoStream
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.CachedExtractState
import project.pipepipe.shared.state.PlainState
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.utils.mixedNumberWordToLong
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireBoolean
import project.pipepipe.shared.utils.json.requireInt
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString
import java.time.OffsetDateTime

class YouTubeStreamExtractor(
    url: String,
) : Extractor<StreamInfo, Nothing>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val id = YouTubeUrlParser.parseStreamId(url)!!
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(taskId = "info", payload = Payload(
                    RequestMethod.POST,
                    VIDEO_INFO_URL,
                    WEB_HEADER,
                    getVideoInfoBody(id)
                )),
                ClientTask(taskId = "play_data", payload = Payload(
                    RequestMethod.POST,
                    getAndroidFetchStreamUrl(id),
                    ANDROID_HEADER,
                    getAndroidFetchStreamBody(id)
                )),
                ClientTask(taskId = "next_data", payload = Payload(
                    RequestMethod.POST,
                    NEXT_URL,
                    WEB_HEADER,
                    getVideoInfoBody(id)
                )),
            ), state = PlainState(1))
        } else {
            val info = clientResults!!.first { it.taskId == "info" }.result!!.asJson() // don't use this if possible as it get risk control very easily
            val nextData = clientResults.first { it.taskId == "next_data" }.result!!.asJson()
            val playData = clientResults.first { it.taskId == "play_data" }.result!!.asJson()

            // Check playability status for errors
            val playabilityStatus = playData.requireObject("/playerResponse/playabilityStatus")
            val status = runCatching { playabilityStatus.requireString("status") }.getOrNull()

            // If status is not OK, check for various error conditions
            if (status != null && !status.equals("OK", ignoreCase = true)) {
                val reason = runCatching { playabilityStatus.requireString("reason") }.getOrNull()

                when {
                    // LOGIN_REQUIRED status
                    status.equals("LOGIN_REQUIRED", ignoreCase = true) -> {
                        when {
                            reason == null -> {
                                val message = runCatching { playabilityStatus.requireString("/messages/0") }.getOrNull()
                                if (message != null && message.contains("private", ignoreCase = true)) {
                                    return JobStepResult.FailWith(
                                        ErrorDetail(
                                            code = "PRIV_001",
                                            stackTrace = IllegalStateException("This video is private").stackTraceToString()
                                        )
                                    )
                                }
                            }
                            reason.contains("age", ignoreCase = true) -> {
                                return JobStepResult.FailWith(
                                    ErrorDetail(
                                        code = "LOGIN_002",
                                        stackTrace = IllegalStateException("This age-restricted video cannot be watched anonymously").stackTraceToString()
                                    )
                                )
                            }
                        }
                    }

                    // UNPLAYABLE or ERROR status
                    status.equals("UNPLAYABLE", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> {
                        when {
                            reason != null && reason.contains("Music Premium", ignoreCase = true) -> {
                                return JobStepResult.FailWith(
                                    ErrorDetail(
                                        code = "PAID_001",
                                        stackTrace = IllegalStateException(reason).stackTraceToString()
                                    )
                                )
                            }
                            reason != null && reason.contains("payment", ignoreCase = true) -> {
                                return JobStepResult.FailWith(
                                    ErrorDetail(
                                        code = "PAID_003",
                                        stackTrace = IllegalStateException("This video is a paid video").stackTraceToString()
                                    )
                                )
                            }
                            reason != null && reason.contains("members-only", ignoreCase = true) -> {
                                return JobStepResult.FailWith(
                                    ErrorDetail(
                                        code = "PAID_002",
                                        stackTrace = IllegalStateException("This video is only available for members of the channel of this video").stackTraceToString()
                                    )
                                )
                            }
                            reason != null && reason.contains("unavailable", ignoreCase = true) -> {
                                val detailedErrorMessage = runCatching {
                                    playabilityStatus.requireString("/errorScreen/playerErrorMessageRenderer/subreason/simpleText")
                                }.getOrNull()

                                if (detailedErrorMessage != null && detailedErrorMessage.contains("country", ignoreCase = true)) {
                                    return JobStepResult.FailWith(
                                        ErrorDetail(
                                            code = "GEO_001",
                                            stackTrace = IllegalStateException("This video is not available in client's country.").stackTraceToString()
                                        )
                                    )
                                } else {
                                    return JobStepResult.FailWith(
                                        ErrorDetail(
                                            code = "UNAV_001",
                                            stackTrace = IllegalStateException(detailedErrorMessage ?: reason).stackTraceToString()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Additional specific error checks
                when {
                    reason != null && reason.contains("Sign in to confirm", ignoreCase = true) -> {
                        return JobStepResult.FailWith(
                            ErrorDetail(
                                code = "RISK_002",
                                stackTrace = IllegalStateException(reason).stackTraceToString()
                            )
                        )
                    }
                    reason != null && reason.contains("This live event will begin in", ignoreCase = true) -> {
                        return JobStepResult.FailWith(
                            ErrorDetail(
                                code = "TIME_001",
                                stackTrace = IllegalStateException(reason).stackTraceToString()
                            )
                        )
                    }
                    reason != null && reason.contains("Premieres in", ignoreCase = true) -> {
                        return JobStepResult.FailWith(
                            ErrorDetail(
                                code = "TIME_002",
                                stackTrace = IllegalStateException(reason).stackTraceToString()
                            )
                        )
                    }
                    reason != null -> {
                        return JobStepResult.FailWith(
                            ErrorDetail(
                                code = "UNAV_001",
                                stackTrace = IllegalStateException("Got error: \"$reason\"").stackTraceToString()
                            )
                        )
                    }
                }
            }

            val savedRelatedData = nextData.requireArray("/contents/twoColumnWatchNextResults/secondaryResults/secondaryResults/results").mapNotNull {
                runCatching { parseFromLockupViewModel(it) }.getOrNull()
            }
            val isLive = playabilityStatus.has("liveStreamability")
            var previewFrames = runCatching{ parseYouTubeFrames(info) }.getOrNull() // prefer as it's higher quality
            if (previewFrames.isNullOrEmpty()) {
                previewFrames = safeGet {
                    parseYouTubeFrames(playData.requireObject("/playerResponse"))
                }
            }
            val streamInfo = StreamInfo(
                url = STREAM_URL + id,
                serviceId = "YOUTUBE",
                name = playData.requireString("/playerResponse/videoDetails/title"),
                uploaderName = playData.requireString("/playerResponse/videoDetails/author"),
                uploadDate = safeGet { OffsetDateTime.parse(info.requireString("/microformat/playerMicroformatRenderer/uploadDate")).toInstant().toEpochMilli() },
                viewCount = safeGet { playData.requireString("/playerResponse/videoDetails/viewCount").toLong() },
                uploaderUrl = safeGet { CHANNEL_URL + playData.requireString("/playerResponse/videoDetails/channelId") },
                uploaderAvatarUrl = safeGet { nextData.requireArray("/contents/twoColumnWatchNextResults/results/results/contents/1/videoSecondaryInfoRenderer/owner/videoOwnerRenderer/thumbnail/thumbnails").last().requireString("url") },
                likeCount = safeGet { info.requireLong("/microformat/playerMicroformatRenderer/likeCount") },
                uploaderSubscriberCount = safeGet { mixedNumberWordToLong(nextData.requireString("/contents/twoColumnWatchNextResults/results/results/contents/1/videoSecondaryInfoRenderer/owner/videoOwnerRenderer/subscriberCountText/simpleText")) },
                streamSegments = null,
                previewFrames = previewFrames,
                thumbnailUrl = safeGet { playData.requireArray("/playerResponse/videoDetails/thumbnail/thumbnails").last().requireString("url") },
                description = safeGet { Description(playData.requireString("/playerResponse/videoDetails/shortDescription"), PLAIN_TEXT) },
                tags = safeGet{ playData.requireArray("/playerResponse/videoDetails/keywords").map { it.asText() } },
                commentUrl = safeGet { "$COMMENT_RAW_URL?continuation=${nextData.requireArray("/contents/twoColumnWatchNextResults/results/results/contents").firstNotNullOfOrNull {
                   runCatching { it.requireString("/itemSectionRenderer/contents/0/continuationItemRenderer/continuationEndpoint/continuationCommand/token") }.getOrNull()
                }!!}" },
                relatedItemUrl = "cache://${sessionId}",
                headers = hashMapOf("User-Agent" to "com.google.android.youtube/19.28.35 (Linux; U; Android 15; GB) gzip")
            ).apply {
                when (isLive) {
                    false -> {
                        streamType = StreamType.VIDEO_STREAM
                        duration = playData.requireLong("/playerResponse/videoDetails/lengthSeconds")
                        sponsorblockUrl = "sponsorblock://youtube.raw?id=$id"
                        dashManifest = createMultiStreamDashManifest(
                            playData.requireLong("/playerResponse/videoDetails/lengthSeconds") * 1.0,
                            playData.requireArray("/playerResponse/streamingData/adaptiveFormats")
                                .filter { it.requireString("mimeType").startsWith("video") }
                                .map {
                                    VideoStream(
                                        it.requireInt("itag").toString(),
                                        it.requireString("url"),
                                        parseMediaType(it.requireString("mimeType")).first,
                                        parseMediaType(it.requireString("mimeType")).second!!,
                                        it.requireLong("bitrate"),
                                        it.requireInt("width"),
                                        it.requireInt("height"),
                                        it.requireInt("fps").toString(),
                                        "${it.requireInt("/indexRange/start")}-${it.requireInt("/indexRange/end")}",
                                        "${it.requireInt("/initRange/start")}-${it.requireInt("/initRange/end")}",
                                        runCatching { it.requireString("qualityLabel").contains("HDR", ignoreCase = true) }.getOrDefault(false)
                                    )
                                }, playData.requireArray("/playerResponse/streamingData/adaptiveFormats")
                                .filter { it.requireString("mimeType").startsWith("audio") }
                                .filter { !runCatching{ it.requireBoolean("isDrc") }.getOrDefault(false) }
                                .map {
                                    val hasMultiTracks = it.has("audioTrack")
                                    AudioStream(
                                        if (hasMultiTracks) it.requireString("/audioTrack/displayName") else it.requireInt("itag").toString(),
                                        it.requireString("url"),
                                        parseMediaType(it.requireString("mimeType")).first,
                                        parseMediaType(it.requireString("mimeType")).second!!,
                                        it.requireLong("bitrate"),
                                        "${it.requireInt("/indexRange/start")}-${it.requireInt("/indexRange/end")}",
                                        "${it.requireInt("/initRange/start")}-${it.requireInt("/initRange/end")}",
                                        if (hasMultiTracks) it.requireString("audioSampleRate") else null,
                                        if (hasMultiTracks) it.requireString("/audioTrack/id").substringBefore("-") else null,
                                        if (hasMultiTracks) it.requireString("/audioTrack/displayName").contains("original", true) else false,
                                    )
                                }, playData.at("/playerResponse/captions/playerCaptionsTracklistRenderer/captionTracks")
                                .map {
                                    SubtitleStream(
                                        it.requireString("vssId"),
                                        "application/ttml+xml",
                                        it.requireString("baseUrl").replace("&fmt=[^&]*".toRegex(), "")
                                            .replace("&tlang=[^&]*".toRegex(), "") + "&fmt=ttml",
                                        it.requireString("languageCode")
                                    )
                                }
                        )
                        isPortrait = playData.requireArray("/playerResponse/streamingData/adaptiveFormats")
                            .filter { it.requireString("mimeType").startsWith("video") }[0].let {
                                it.requireInt("width") < it.requireInt("height")
                            }
                    }
                    true -> {
                        streamType = StreamType.LIVE_STREAM
                        hlsUrl = playData.requireString("/playerResponse/streamingData/hlsManifestUrl")
                    }
                }
            }
            return JobStepResult.CompleteWith(ExtractResult(streamInfo, errors), state = CachedExtractState(
                step = 0,
                ExtractResult(info = RelatedItemInfo("cache://${sessionId}"), pagedData = PagedData(savedRelatedData, null))
            ))
        }
    }

    private fun parseYouTubeFrames(playData: JsonNode): List<Frameset> {
        try {
            val storyboards = playData.at("/storyboards")
            if (storyboards.isMissingNode || storyboards.isNull) {
                return emptyList()
            }

            val storyboardsRenderer = when {
                storyboards.has("playerLiveStoryboardSpecRenderer") ->
                    storyboards.get("playerLiveStoryboardSpecRenderer")
                storyboards.has("playerStoryboardSpecRenderer") ->
                    storyboards.get("playerStoryboardSpecRenderer")
                else -> return emptyList()
            }

            if (storyboardsRenderer.isMissingNode || storyboardsRenderer.isNull) {
                return emptyList()
            }

            val spec = storyboardsRenderer.get("spec")?.asText() ?: return emptyList()
            val parts = spec.split("|")
            if (parts.isEmpty()) return emptyList()

            val baseUrl = parts[0]
            val result = mutableListOf<Frameset>()

            for (i in 1 until parts.size) {
                val specParts = parts[i].split("#")
                if (specParts.size != 8) continue

                val frameWidth = specParts[0].toIntOrNull() ?: continue
                val frameHeight = specParts[1].toIntOrNull() ?: continue
                val totalCount = specParts[2].toIntOrNull() ?: continue
                val framesPerPageX = specParts[3].toIntOrNull() ?: continue
                val framesPerPageY = specParts[4].toIntOrNull() ?: continue
                val durationPerFrame = specParts[5].toIntOrNull() ?: continue

                if (durationPerFrame == 0) continue

                val urlTemplate = baseUrl.replace("\$L", (i - 1).toString())
                    .replace("\$N", specParts[6]) + "&sigh=" + specParts[7]

                val urls = if (urlTemplate.contains("\$M")) {
                    val totalPages = kotlin.math.ceil(totalCount.toDouble() / (framesPerPageX * framesPerPageY)).toInt()
                    (0 until totalPages).map { j ->
                        urlTemplate.replace("\$M", j.toString())
                    }
                } else {
                    listOf(urlTemplate)
                }

                result.add(Frameset(
                    urls = urls,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    totalCount = totalCount,
                    durationPerFrame = durationPerFrame,
                    framesPerPageX = framesPerPageX,
                    framesPerPageY = framesPerPageY
                ))
            }
            return result
        } catch (e: Exception) {
            return emptyList()
        }
    }

}