package project.pipepipe.extractor.services.niconico.extractor

import org.jsoup.Jsoup
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext
import project.pipepipe.extractor.ExtractorContext.objectMapper
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.CHANNEL_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.WATCH_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.getAccessUrl
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.isChannel
import project.pipepipe.extractor.services.niconico.NicoNicoUrlParser.parseStreamId
import project.pipepipe.shared.state.State
import project.pipepipe.shared.state.StreamExtractState
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.ExtractorContext.isLoggedInCookie
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.DANMAKU_RAW_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.RELATED_VIDEO_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.USER_URL
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireBoolean
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.helper.stream.Description
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import java.time.ZonedDateTime
import project.pipepipe.shared.job.ErrorDetail
import project.pipepipe.shared.utils.json.requireObject
import java.net.URLEncoder


class NicoNicoStreamExtractor(
    url: String,
) : Extractor<StreamInfo, Nothing>(url)  {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val id = parseStreamId(url)
        if (currentState == null) {
            var header = HashMap(GOOGLE_HEADER)
            if (cookie?.isLoggedInCookie() == true) {
                header["Cookie"] = cookie
            }
            return JobStepResult.ContinueWith(listOf(
                ClientTask(payload = Payload(RequestMethod.GET, WATCH_URL + id, header)),
            ), state = PlainState(1))
        } else if (currentState.step == 1) {
            val response = clientResults!!.first { it.taskId.isDefaultTask()}.result!!
            val page = Jsoup.parse(response)

            // Check for errors in HTML page (when meta tag is missing)
            val metaElement = page.select("meta[name=\"server-response\"]").first()
            if (metaElement == null) {
                // Need login or other errors
                val responseBody = response

                when {
                    responseBody.contains("チャンネル会員専用動画") -> {
                        return JobStepResult.FailWith(
                            ErrorDetail(
                                code = "PAID_002",
                                stackTrace = IllegalStateException("Channel member limited videos").stackTraceToString()
                            )
                        )
                    }
                    responseBody.contains("地域と同じ地域からのみ視聴") -> {
                        return JobStepResult.FailWith(
                            ErrorDetail(
                                code = "GEO_001",
                                stackTrace = IllegalStateException("Sorry, this video can only be viewed in the same region where it was uploaded.").stackTraceToString()
                            )
                        )
                    }
                    responseBody.contains("この動画を視聴するにはログインが必要です。") -> {
                        return JobStepResult.FailWith(
                            ErrorDetail(
                                code = "LOGIN_002",
                                stackTrace = IllegalStateException("This video requires login to view.").stackTraceToString()
                            )
                        )
                    }
                    else -> {
                        val errorMessage = page.select("p.fail-message").text()
                        return JobStepResult.FailWith(
                            ErrorDetail(
                                code = "UNAV_001",
                                stackTrace = IllegalStateException(errorMessage.ifEmpty { "Content not available" }).stackTraceToString()
                            )
                        )
                    }
                }
            }

            val watchData = try {
                objectMapper.readTree(metaElement.attr("content"))
            } catch (e: Exception) {
                return JobStepResult.FailWith(
                    ErrorDetail(
                        code = "PARSE_001",
                        stackTrace = IllegalStateException("Failed to parse content", e).stackTraceToString()
                    )
                )
            }

            // Check for errorCode in JSON response (like old code line 101-116)
            val responseData = watchData.get("data")?.get("response")
            if (responseData != null) {
                val errorCode = responseData.get("errorCode")?.asText()
                val reasonCode = responseData.get("reasonCode")?.asText()
                val okReason = responseData.get("okReason")?.asText()

                // Check for FORBIDDEN errors
                if (errorCode == "FORBIDDEN") {
                    when (reasonCode) {
                        "DOMESTIC_VIDEO" -> {
                            return JobStepResult.FailWith(
                                ErrorDetail(
                                    code = "GEO_001",
                                    stackTrace = IllegalStateException("This video is only available in Japan").stackTraceToString()
                                )
                            )
                        }
                        "HARMFUL_VIDEO" -> {
                            return JobStepResult.FailWith(
                                ErrorDetail(
                                    code = "LOGIN_002",
                                    stackTrace = IllegalStateException("This content need an account to view").stackTraceToString()
                                )
                            )
                        }
                        else -> {
                            return JobStepResult.FailWith(
                                ErrorDetail(
                                    code = "UNAV_001",
                                    stackTrace = IllegalStateException(reasonCode ?: "Content not available").stackTraceToString()
                                )
                            )
                        }
                    }
                }

                // Check for paid content (PAYMENT_PREVIEW_SUPPORTED)
                if (okReason == "PAYMENT_PREVIEW_SUPPORTED") {
                    val payment = responseData.get("payment")?.get("video")
                    if (payment != null) {
                        if (payment.get("isPremium")?.asBoolean() == true) {
                            return JobStepResult.FailWith(
                                ErrorDetail(
                                    code = "PAID_001",
                                    stackTrace = IllegalStateException("This content is limited to premium users").stackTraceToString()
                                )
                            )
                        } else if (payment.get("billingType")?.asText() == "member_only") {
                            return JobStepResult.FailWith(
                                ErrorDetail(
                                    code = "PAID_002",
                                    stackTrace = IllegalStateException("This content is limited to channel members").stackTraceToString()
                                )
                            )
                        }
                    }
                }
            }

            val streamInfo = StreamInfo(
                url = WATCH_URL + id,
                serviceId = 6,
                name = watchData.requireString("/data/response/video/title"),
                uploaderName =
                    if (isChannel(watchData)) runCatching {
                        watchData.requireString("/data/response/channel/name")
                    }.getOrDefault("Removed")
                    else watchData.requireString(
                        "/data/response/owner/nickname"
                    ),
                uploaderUrl = safeGet {
                    if (isChannel(watchData)) CHANNEL_URL + watchData.requireString("/data/response/channel/id")
                    else USER_URL + watchData.requireString(
                        "/data/response/owner/id"
                    )
                },
                uploaderAvatarUrl = safeGet {
                    if (isChannel(watchData)) watchData.requireString("/data/response/channel/thumbnail/url")
                    else watchData.requireString(
                        "/data/response/owner/iconUrl"
                    )
                },
                uploadDate = safeGet { ZonedDateTime.parse(watchData.requireString("/data/response/video/registeredAt")).toInstant().toEpochMilli() },
                duration = watchData.requireLong("/data/response/video/duration"),
                viewCount = safeGet { watchData.requireLong("/data/response/video/count/view") },
                likeCount = safeGet { watchData.requireLong("/data/response/video/count/like") },
                thumbnailUrl = safeGet { watchData.requireString("/data/response/video/thumbnail/ogp") },
                description = safeGet { Description(watchData.requireString("/data/response/video/description"),
                    Description.HTML) },
                tags = safeGet { watchData.requireArray("/data/response/tag/items").map { it.requireString("name") } },
                relatedItemUrl = RELATED_VIDEO_URL + id,
                danmakuUrl = safeGet{ "$DANMAKU_RAW_URL?data=${URLEncoder.encode(watchData.requireObject("/data/response/comment/nvComment").toString(), "UTF-8")}" }
            )
            val audioId = watchData.requireArray("/data/response/media/domand/audios")
                .first { it.requireBoolean("isAvailable") }.requireString("id")
            val resolutionArray = watchData.requireArray("/data/response/media/domand/videos")
                .filter { it.requireBoolean("isAvailable") }
                .map { listOf(it.requireString("id"), audioId) }

            val body = objectMapper.writeValueAsString(mapOf(
                "outputs" to resolutionArray
            ))
            return JobStepResult.ContinueWith(listOf(
                ClientTask(payload = Payload(
                    RequestMethod.POST,
                    getAccessUrl(id!!, watchData.requireString("/data/response/client/watchTrackId")),
                    getAccessHeaders(watchData.requireString("/data/response/media/domand/accessRightKey"), cookie),
                    body
                ))), state = StreamExtractState(2, streamInfo))
        } else {
            val streamInfo = (currentState as StreamExtractState).streamInfo
            val response = clientResults!!.first { it.taskId.isDefaultTask()}
            streamInfo.hlsUrl = response.result!!.asJson().requireString("/data/contentUrl")
            streamInfo.headers = hashMapOf("Cookie" to response.responseHeader!!["set-cookie"]!![0].split(";").first { it.trim().startsWith("domand_bid=") })
            if (cookie?.isLoggedInCookie() == true) {
                streamInfo.headers["Cookie"] += ";$cookie"
            }
            return JobStepResult.CompleteWith(ExtractResult(streamInfo))
        }
    }
    fun getAccessHeaders(key: String, cookie: String?) = hashMapOf(
        "Referer" to "https://www.nicovideo.jp/",
        "X-Frontend-Id" to "6",
        "X-Frontend-Version" to "0",
        "X-Request-With" to "nicovideo",
        "X-Access-Right-Key" to key,
    ).apply {
        cookie?.let {
            put("Cookie", cookie)
        }
    }
}