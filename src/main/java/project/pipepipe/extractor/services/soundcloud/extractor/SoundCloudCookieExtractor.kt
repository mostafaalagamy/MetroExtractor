package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.base.CookieExtractor
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks
import project.pipepipe.shared.infoitem.CookieInfo
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.ExtractResult
import project.pipepipe.shared.job.JobStepResult
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod
import project.pipepipe.shared.job.isDefaultTask
import project.pipepipe.shared.state.CookieState
import project.pipepipe.shared.state.State
import java.util.regex.Pattern

class SoundCloudCookieExtractor : CookieExtractor() {
    private val clientIdPattern = Pattern.compile(",client_id:\"(.*?)\"")
    private val scriptPattern = Pattern.compile("<script[^>]*src=\"([^\"]*sndcdn[^\"]*\\.js)\"")
    private val defaultHeaders = hashMapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )

    override suspend fun refreshCookie(
        sessionId: String,
        currentState: State?,
        clientResults: List<project.pipepipe.shared.job.TaskResult>?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            method = RequestMethod.GET,
                            url = SoundCloudLinks.BASE_URL,
                            headers = defaultHeaders
                        )
                    )
                ),
                state = CookieState(1, CookieInfo(null, -1))
            )
        } else if (currentState.step == 1) {
            val response = clientResults!!.first { it.taskId.isDefaultTask() }
            val html = response.result.toString()
            val scriptMatcher = scriptPattern.matcher(html)
            val scriptUrls = mutableListOf<String>()

            while (scriptMatcher.find()) {
                scriptUrls.add(scriptMatcher.group(1))
            }

            if (scriptUrls.isEmpty()) {
                throw IllegalStateException("No script URLs found")
            }

            val scriptUrl = scriptUrls.last()
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            method = RequestMethod.GET,
                            url = scriptUrl,
                            headers = hashMapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                "Range" to "bytes=0-50000"
                            )
                        )
                    )
                ),
                state = CookieState(2, CookieInfo(null, -1))
            )
        } else if (currentState.step == 2) {
            val response = clientResults!!.first { it.taskId.isDefaultTask() }
            val scriptContent = response.result.toString()
            val clientIdMatcher = clientIdPattern.matcher(scriptContent)

            if (!clientIdMatcher.find()) {
                throw IllegalStateException("Client ID not found in script")
            }

            val clientId = clientIdMatcher.group(1)
            val oneDayInMillis = 24 * 60 * 60 * 1000
            val expiresAt = System.currentTimeMillis() + oneDayInMillis

            return JobStepResult.CompleteWith(
                ExtractResult(
                    CookieInfo(
                        cookie = "client_id=$clientId",
                        timeOut = expiresAt
                    )
                )
            )
        }

        throw IllegalArgumentException("Invalid state")
    }
}
