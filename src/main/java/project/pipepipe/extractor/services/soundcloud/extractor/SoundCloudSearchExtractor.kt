package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.base.SearchExtractor
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudChannelInfoDataParser
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudPlaylistInfoDataParser
import project.pipepipe.extractor.services.soundcloud.dataparser.SoundCloudStreamInfoDataParser
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
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.extractor.services.soundcloud.SoundCloudService.Companion.DEFAULT_HEADER
import project.pipepipe.extractor.utils.RequestHelper.replaceQueryValue


private const val ITEMS_PER_PAGE = 10

class SoundCloudSearchExtractor(url: String) : SearchExtractor(url) {
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
            val query = getQueryValue(url, "query") ?: throw IllegalStateException("Query parameter not found")
            val type = getQueryValue(url, "type") ?: "all"
            val clientId = cookie!!.substringAfter("client_id=")
            
            val searchType = when (type) {
                "tracks" -> "/tracks"
                "users" -> "/users"
                "playlists" -> "/playlists"
                else -> ""
            }
            
            val filters = extractFilters(url)
            val offset = getQueryValue(url, "offset")?.toIntOrNull() ?: 0
            val apiUrl = "${SoundCloudLinks.API_V2_URL}/search${searchType}?q=${java.net.URLEncoder.encode(query, "UTF-8")}${filters}&client_id=$clientId&limit=$ITEMS_PER_PAGE&offset=$offset"
            
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            method = RequestMethod.GET,
                            url = apiUrl,
                            headers = DEFAULT_HEADER
                        )
                    )
                ),
                PlainState(1)
            )
        } else {
            val jsonData = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            val collection = jsonData.requireArray("collection")
            val totalResults = runCatching { jsonData.at("/total_results").asInt() }.getOrNull() ?: 0
            
            val type = getQueryValue(url, "type") ?: "all"
            when (type) {
                "users" -> collection.forEach { item ->
                    commit { SoundCloudChannelInfoDataParser.parseFromChannelObject(item) }
                }
                "playlists" -> collection.forEach { item ->
                    commit { SoundCloudPlaylistInfoDataParser.parseFromPlaylistObject(item) }
                }
                "all" -> collection.forEach { item ->
                    val kind = runCatching { item.requireString("kind") }.getOrNull() ?: return@forEach
                    when (kind) {
                        "user" -> commit { SoundCloudChannelInfoDataParser.parseFromChannelObject(item) }
                        "track" -> commit { SoundCloudStreamInfoDataParser.parseFromTrackObject(item) }
                        "playlist" -> commit { SoundCloudPlaylistInfoDataParser.parseFromPlaylistObject(item) }
                    }
                }
                else -> collection.forEach { item ->
                    commit { SoundCloudStreamInfoDataParser.parseFromTrackObject(item) }
                }
            }
            
            val currentOffset = getQueryValue(url, "offset")?.toIntOrNull() ?: 0
            val nextPageUrl = if (currentOffset + ITEMS_PER_PAGE < totalResults) {
                val newOffset = currentOffset + ITEMS_PER_PAGE
                replaceQueryValue(url, "offset", newOffset.toString())
            } else {
                null
            }
            
            return JobStepResult.CompleteWith(
                ExtractResult(
                    info = null,
                    errors = errors,
                    pagedData = PagedData(itemList, nextPageUrl)
                )
            )
        }
    }

    private fun extractFilters(url: String): String {
        val filters = StringBuilder()
        val sort = getQueryValue(url, "sortby")
        val duration = getQueryValue(url, "duration")
        val license = getQueryValue(url, "license")
        
        if (!sort.isNullOrBlank() && sort != "all") {
            filters.append("&$sort")
        }
        if (!duration.isNullOrBlank() && duration != "all") {
            filters.append("&$duration")
        }
        if (!license.isNullOrBlank() && license != "all") {
            filters.append("&$license")
        }
        
        return filters.toString()
    }
}