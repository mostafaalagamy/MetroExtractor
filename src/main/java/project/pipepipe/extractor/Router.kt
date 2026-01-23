package project.pipepipe.extractor

import project.pipepipe.extractor.baseextractor.SponsorBlockExtractor
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.State
import java.util.*

object Router {
    fun String.getType() = this.substringBefore("://")
    fun String.resetType(): String {
        if (this.isBlank()) {
            return this
        }
        val content = this.substringAfter("://")
        return "https://$content"
    }
    fun String.setType(type: String): String {
        if (this.isBlank()) {
            return this
        }
        val content = this.substringAfter("://")
        return "$type://$content"
    }
    fun getExtractor(url: String): Extractor<*, *> = ExtractorContext.ServiceList.all.firstNotNullOf { it.route(url) }

    suspend fun route(request: JobRequest, sessionId: String, currentState: State?): JobStepResult {
        return when(request.jobType) {
            SupportedJobType.FETCH_INFO -> {
                val extractor = try { getExtractor(request.url!!) } catch (e: Exception) { return JobStepResult.FailWith(ErrorDetail("ROUTE_FAILED", e.stackTraceToString())) }
                extractor.fetchInfo(
                    sessionId,
                    currentState,
                    request.results,
                    request.cookie
                )
            }
            SupportedJobType.FETCH_FIRST_PAGE -> {
                val extractor = try { getExtractor(request.url!!) } catch (e: Exception) { return JobStepResult.FailWith(ErrorDetail("ROUTE_FAILED", e.stackTraceToString())) }
                extractor.fetchFirstPage(
                    sessionId,
                    currentState,
                    request.results,
                    request.cookie
                )
            }
            SupportedJobType.FETCH_GIVEN_PAGE -> {
                val extractor = try { getExtractor(request.url!!) } catch (e: Exception) { return JobStepResult.FailWith(ErrorDetail("ROUTE_FAILED", e.stackTraceToString())) }
                extractor.fetchGivenPage(
                    request.url!!,
                    sessionId,
                    currentState,
                    request.results,
                    request.cookie
                )
            }
            SupportedJobType.GET_SUGGESTION -> JobStepResult.CompleteWith(ExtractResult())
            SupportedJobType.REFRESH_COOKIE -> {
                val extractor = try {
                    ExtractorContext.ServiceList.all.first { it.serviceId == request.serviceId!! }.getCookieExtractor()
                } catch (e: Exception) {
                    return JobStepResult.FailWith(ErrorDetail("ROUTE_FAILED", e.stackTraceToString()))
                }
                extractor.refreshCookie(sessionId, currentState, request.results)
            }
            SupportedJobType.GET_SUPPORTED_SERVICES -> JobStepResult.CompleteWith(
                ExtractResult(pagedData = PagedData(ExtractorContext.ServiceList.all.map { it.serviceInfo }, null)))
            SupportedJobType.VOTE_SPONSORBLOCK_SEGMENT -> SponsorBlockExtractor(request.url!!).submitSponsorBlockSegmentVote(currentState)
            SupportedJobType.SUBMIT_SPONSORBLOCK_SEGMENT -> SponsorBlockExtractor(request.url!!).submitSponsorBlockSegment(currentState)
            SupportedJobType.FETCH_SPONSORBLOCK_SEGMENT_LIST -> SponsorBlockExtractor(request.url!!).fetchResult(currentState, request.results)
        }
    }
    suspend fun execute(request: JobRequest): JobResponse<out Info, out Info> {
        var sessionId = request.sessionId
        val currentState: State?

        if (sessionId != null && request.results == null) {
            return JobResponse(
                sessionId = sessionId,
                status = JobStatus.FAILED,
                result = ExtractResult(
                    fatalError = ErrorDetail(
                        code = "NET_002",
                        stackTrace = IllegalStateException("Empty client result received").stackTraceToString()
                    )
                )
            )
        }

        try {
            if (sessionId == null) { // new task
                sessionId = UUID.randomUUID().toString()
                currentState = request.state
            } else { // continue task
                currentState = request.state ?: throw IllegalStateException("Session ID provided but no state in request")
            }

            val stepResult = route(request, sessionId, currentState)

            when (stepResult) {
                is JobStepResult.ContinueWith -> {
                    return JobResponse(
                        sessionId = sessionId,
                        status = JobStatus.CONTINUE,
                        tasks = stepResult.tasks,
                        state = stepResult.state ?: currentState // return updated state or current state for client to cache
                    )

                }
                is JobStepResult.CompleteWith<*, *> -> {
                    stepResult.result.errors.forEach { println(it) }
                    return JobResponse(
                        sessionId = sessionId,
                        status = JobStatus.COMPLETE,
                        result = stepResult.result,
                        state = stepResult.state // return final state if any
                    )
                }
                is JobStepResult.FailWith -> {
                    return JobResponse(
                        sessionId = sessionId,
                        status = JobStatus.FAILED,
                        result = ExtractResult(fatalError = stepResult.fatalError)
                    )
                }
            }
        } catch (e: Exception) {
            val errorCode = if (e.message?.contains("Too many failed commits") == true) "PARSE_002" else "UNKNOWN_001"
            return JobResponse(
                sessionId = sessionId?:"",
                status = JobStatus.FAILED,
                result = ExtractResult(fatalError = ErrorDetail(errorCode, e.stackTraceToString()))
            )
        }
    }
}