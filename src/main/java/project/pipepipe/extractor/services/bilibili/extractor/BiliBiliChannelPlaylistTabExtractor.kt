package project.pipepipe.extractor.services.bilibili.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.CHANNEL_BASE_URL
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliPlaylistInfoDataParser
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliPlaylistType
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.ExtractResult
import project.pipepipe.shared.job.JobStepResult
import project.pipepipe.shared.job.PagedData
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod
import project.pipepipe.shared.job.TaskResult
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireInt
import project.pipepipe.extractor.utils.json.requireLong
import project.pipepipe.extractor.utils.json.requireObject
import project.pipepipe.extractor.utils.json.requireString

class BiliBiliChannelPlaylistTabExtractor(
    url: String,
) : Extractor<Nothing, PlaylistInfo>(url) {
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
        val headers = BilibiliService.getHeadersWithCookie(url, cookie!!)
        val mid = getQueryValue(url, "mid")!!

        if (currentState == null) {
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask("info", Payload(RequestMethod.GET, BiliBiliLinks.QUERY_USER_INFO_URL + mid, headers)),
                    ClientTask("playlists", Payload(RequestMethod.GET, url, headers))
                ), PlainState(1)
            )
        } else {
            val userInfoData = clientResults!!.first { it.taskId == "info" }.result!!.asJson() //todo: use url to pass them
            val playlistsData = clientResults.first { it.taskId == "playlists" }.result!!.asJson()

            // Extract uploader information
            val cardData = userInfoData.requireObject("/data/card")
            val uploaderName = safeGet { cardData.requireString("name") }
            val uploaderUrl = CHANNEL_BASE_URL + mid
            val uploaderAvatarUrl = safeGet { cardData.requireString("face").replace("http:", "https:") }

            // Parse playlists
            val data = playlistsData.requireObject("data")
            val itemsLists = data.requireObject("items_lists")
            val seasonsList = itemsLists.requireArray("seasons_list")
            val seriesList = itemsLists.requireArray("series_list")

            // Process seasons
            seasonsList.forEach { season ->
                val streamCount = runCatching { season.requireObject("meta").requireLong("total") }.getOrDefault(0)
                if (streamCount > 0) {
                    commit {
                        BiliBiliPlaylistInfoDataParser.parseFromPlaylistInfoJson(
                            season,
                            BiliBiliPlaylistType.SEASON,
                            uploaderName,
                            uploaderUrl,
                            uploaderAvatarUrl
                        )
                    }
                }
            }

            // Process series
            seriesList.forEach { series ->
                val streamCount = runCatching { series.requireObject("meta").requireLong("total") }.getOrDefault(0)
                if (streamCount > 0) {
                    commit {
                        BiliBiliPlaylistInfoDataParser.parseFromPlaylistInfoJson(
                            series,
                            BiliBiliPlaylistType.SERIES,
                            uploaderName,
                            uploaderUrl,
                            uploaderAvatarUrl
                        )
                    }
                }
            }

            val hasNext = itemsLists.requireObject("page").let {
                it.requireInt("page_num") * it.requireInt("page_size")  < it.requireInt("total")
            }

            return JobStepResult.CompleteWith(
                ExtractResult(
                    errors = errors,
                    pagedData = PagedData(
                        itemList = itemList,
                        nextPageUrl = if (hasNext) url.incrementUrlParam("page_num") else null
                    )
                )
            )
        }
    }
}
