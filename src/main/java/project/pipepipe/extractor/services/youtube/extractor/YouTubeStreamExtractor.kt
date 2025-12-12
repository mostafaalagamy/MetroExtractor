package project.pipepipe.extractor.services.youtube.extractor

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.youtube.YouTubeDecryptionHelper
import project.pipepipe.extractor.services.youtube.YouTubeDecryptionHelper.processAdaptiveFormats
import project.pipepipe.extractor.services.youtube.YouTubeLinks.CHANNEL_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.COMMENT_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.NEXT_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.STREAM_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.VIDEO_INFO_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.VIDEO_PLAYER_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.getAndroidFetchStreamUrl
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.ANDROID_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.ANDROID_UA
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_UA
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getAndroidFetchStreamBody
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getAuthorizationHeader
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getVideoInfoBody
import project.pipepipe.extractor.services.youtube.YouTubeUrlParser
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser.parseFromLockupViewModel
import project.pipepipe.extractor.utils.createMultiStreamDashManifest
import project.pipepipe.extractor.utils.mixedNumberWordToLong
import project.pipepipe.extractor.utils.parseMediaType
import project.pipepipe.shared.downloader.isLoggedInCookie
import project.pipepipe.shared.infoitem.RelatedItemInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.StreamType
import project.pipepipe.shared.infoitem.helper.stream.*
import project.pipepipe.shared.infoitem.helper.stream.Description.Companion.PLAIN_TEXT
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.CachedExtractState
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.PreFetchPayloadState
import project.pipepipe.shared.state.State
import project.pipepipe.shared.utils.json.*
import java.time.OffsetDateTime
import kotlin.math.ceil

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
        val isLoggedIn = cookie?.isLoggedInCookie() == true

        if (currentState == null) {
            var playerResult: Pair<String, Int>?
            var state: State = PlainState(1)
            val maybeAuthedWebHeader = HashMap(WEB_HEADER)
            if (isLoggedIn) {
                maybeAuthedWebHeader.apply {
                    put("Authorization", getAuthorizationHeader(cookie))
                    put("Cookie", cookie)
                }
            }
            val taskList = arrayListOf(
                ClientTask(taskId = "info", payload = Payload(
                    RequestMethod.POST,
                    VIDEO_INFO_URL,
                    maybeAuthedWebHeader,
                    getVideoInfoBody(id)
                )),
                ClientTask(taskId = "next_data", payload = Payload(
                    RequestMethod.POST,
                    NEXT_URL,
                    WEB_HEADER,
                    getVideoInfoBody(id)
                )),
            )
            if (isLoggedIn) {
                playerResult = YouTubeDecryptionHelper.getLatestPlayer() ?: return JobStepResult.FailWith(
                    ErrorDetail(
                        code = "REQ_002",
                        stackTrace = IllegalStateException("Failed to request sts api").stackTraceToString()
                    )
                )
                state = PreFetchPayloadState(1, playerResult.first)
                taskList.add(ClientTask(
                    taskId = "play_data", payload = Payload(
                        RequestMethod.POST,
                        VIDEO_PLAYER_URL,
                        maybeAuthedWebHeader,
                        getVideoInfoBody(id, isTvClient = true, sts = playerResult.second)
                    )
                ))
                taskList.add(ClientTask(
                    taskId = "backup_data", payload = Payload(
                        RequestMethod.POST,
                        VIDEO_PLAYER_URL,
                        maybeAuthedWebHeader,
                        getVideoInfoBody(id, isTvClient = false, sts = playerResult.second)
                    )
                ))
            } else {
                taskList.add(ClientTask(taskId = "play_data", payload = Payload(
                    RequestMethod.POST,
                    getAndroidFetchStreamUrl(id),
                    ANDROID_HEADER,
                    getAndroidFetchStreamBody(id)
                )),)
            }
            return JobStepResult.ContinueWith(taskList, state = state)
        } else {
            val info = clientResults!!.first { it.taskId == "info" }.result!!.asJson() // don't use this if possible as it get risk control very easily
            val nextData = clientResults.first { it.taskId == "next_data" }.result!!.asJson()
            val playData = clientResults.first { it.taskId == "play_data" }.result!!.asJson()
                .requireObject(if (isLoggedIn) "/" else "/playerResponse")
            val player = (currentState as? PreFetchPayloadState)?.payload
            val backupData = runCatching { clientResults.first { it.taskId == "backup_data" }.result!!.asJson() }.getOrNull()

            // Check playability status for errors
            val playabilityStatus = playData.requireObject("/playabilityStatus")
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
            val isLive = playabilityStatus.has("liveStreamability") || runCatching{ playabilityStatus.requireString("reason").contains("live") }.getOrDefault(false)
            var previewFrames = runCatching{ parseYouTubeFrames(info) }.getOrNull() // prefer as it's higher quality
            if (previewFrames.isNullOrEmpty()) {
                previewFrames = safeGet {
                    parseYouTubeFrames(playData)
                }
            }

            val UA = when {
                isLoggedIn -> WEB_UA
                else -> ANDROID_UA
            }

            val streamInfo = StreamInfo(
                url = STREAM_URL + id,
                serviceId = "YOUTUBE",
                name =  nextData.requireString("/contents/twoColumnWatchNextResults/results/results/contents/0/videoPrimaryInfoRenderer/title/runs/0/text"),
                uploaderName = nextData.requireString("/contents/twoColumnWatchNextResults/results/results/contents/1/videoSecondaryInfoRenderer/owner/videoOwnerRenderer/title/runs/0/text"),
                uploadDate = safeGet { OffsetDateTime.parse(info.requireString("/microformat/playerMicroformatRenderer/uploadDate")).toInstant().toEpochMilli() },
                viewCount = safeGet { playData.requireString("/videoDetails/viewCount").toLong() },
                uploaderUrl = safeGet { CHANNEL_URL + playData.requireString("/videoDetails/channelId") },
                uploaderAvatarUrl = safeGet { nextData.requireArray("/contents/twoColumnWatchNextResults/results/results/contents/1/videoSecondaryInfoRenderer/owner/videoOwnerRenderer/thumbnail/thumbnails").last().requireString("url") },
                likeCount = safeGet { info.requireLong("/microformat/playerMicroformatRenderer/likeCount") },
                uploaderSubscriberCount = safeGet { mixedNumberWordToLong(nextData.requireString("/contents/twoColumnWatchNextResults/results/results/contents/1/videoSecondaryInfoRenderer/owner/videoOwnerRenderer/subscriberCountText/simpleText")) },
                streamSegments = null,
                previewFrames = previewFrames,
                thumbnailUrl = safeGet { playData.requireArray("/videoDetails/thumbnail/thumbnails").last().requireString("url") },
                description = safeGet { Description(playData.requireString("/videoDetails/shortDescription"), PLAIN_TEXT) },
                tags = safeGet{ playData.requireArray("/videoDetails/keywords").map { it.asText() } },
                commentUrl = safeGet { "$COMMENT_RAW_URL?continuation=${nextData.requireArray("/contents/twoColumnWatchNextResults/results/results/contents").firstNotNullOfOrNull {
                   runCatching { it.requireString("/itemSectionRenderer/contents/0/continuationItemRenderer/continuationEndpoint/continuationCommand/token") }.getOrNull()
                }!!}" },
                relatedItemUrl = "cache://${sessionId}",
                headers = hashMapOf("User-Agent" to UA)
            ).apply {
                when (isLive) {
                    false -> {
                        streamType = StreamType.VIDEO_STREAM
                        duration = playData.requireLong("/videoDetails/lengthSeconds")
                        sponsorblockUrl = "sponsorblock://youtube.raw?id=$id"

                        // Process adaptive formats and decrypt URLs in batch
                        val adaptiveFormats = playData.requireArray("/streamingData/adaptiveFormats")
                        val formatUrlMap =  runCatching{ processAdaptiveFormats(adaptiveFormats, player).toMap() }.getOrElse {
                            return JobStepResult.FailWith(
                                ErrorDetail(
                                    code = "REQ_003",
                                    stackTrace = it.stackTraceToString()
                                )
                            )
                        }

                        dashManifest = createMultiStreamDashManifest(
                            playData.requireLong("/videoDetails/lengthSeconds") * 1.0,
                            adaptiveFormats
                                .filter { it.requireString("mimeType").startsWith("video") }
                                .mapNotNull {
                                    val url = formatUrlMap[it] ?: return@mapNotNull null
                                    VideoStream(
                                        it.requireInt("itag").toString(),
                                        url,
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
                                }, adaptiveFormats
                                .filter { it.requireString("mimeType").startsWith("audio") }
                                .filter { !runCatching{ it.requireBoolean("isDrc") }.getOrDefault(false) }
                                .mapNotNull {
                                    val url = formatUrlMap[it] ?: return@mapNotNull null
                                    val hasMultiTracks = it.has("audioTrack")
                                    AudioStream(
                                        if (hasMultiTracks) it.requireString("/audioTrack/displayName") else it.requireInt("itag").toString(),
                                        url,
                                        parseMediaType(it.requireString("mimeType")).first,
                                        parseMediaType(it.requireString("mimeType")).second!!,
                                        it.requireLong("bitrate"),
                                        "${it.requireInt("/indexRange/start")}-${it.requireInt("/indexRange/end")}",
                                        "${it.requireInt("/initRange/start")}-${it.requireInt("/initRange/end")}",
                                        if (hasMultiTracks) it.requireString("audioSampleRate") else null,
                                        if (hasMultiTracks) it.requireString("/audioTrack/id").substringBefore("-") else null,
                                        if (hasMultiTracks) it.requireString("/audioTrack/displayName").contains("original", true) else false,
                                    )
                                }, playData.at("/captions/playerCaptionsTracklistRenderer/captionTracks")
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
                        isPortrait = playData.requireArray("/streamingData/adaptiveFormats")
                            .filter { it.requireString("mimeType").startsWith("video") }[0].let {
                                it.requireInt("width") < it.requireInt("height")
                            }
                    }
                    true -> {
                        streamType = StreamType.LIVE_STREAM
                        hlsUrl = if (isLoggedIn) {
                            backupData!!.requireString("/streamingData/hlsManifestUrl")
                        } else {
                            playData.requireString("/streamingData/hlsManifestUrl")
                        }
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
                    val totalPages = ceil(totalCount.toDouble() / (framesPerPageX * framesPerPageY)).toInt()
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