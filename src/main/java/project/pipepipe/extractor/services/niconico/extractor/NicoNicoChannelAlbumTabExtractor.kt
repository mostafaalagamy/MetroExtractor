package project.pipepipe.extractor.services.niconico.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.NICO_BASE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoPlaylistInfoDataParser.parseFromSeriesJson
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
import project.pipepipe.extractor.utils.json.requireObject
import java.net.URLDecoder

class NicoNicoChannelAlbumTabExtractor(url: String) : Extractor<Nothing, PlaylistInfo>(url) {
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
        val userId = getQueryValue(url, "id")!!
        val page = getQueryValue(url, "page") ?: "1"
        val channelName = URLDecoder.decode(getQueryValue(url, "name")!!, "UTF-8")

        if (currentState == null) {
            val realUrl = "https://nvapi.nicovideo.jp/v1/users/$userId/series?page=$page&pageSize=100"
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask("albums", Payload(RequestMethod.GET, realUrl, NICO_BASE_HEADER))
                ), PlainState(1)
            )
        } else {
            val data = clientResults!!.first { it.taskId == "albums" }.result!!.asJson()
            val items = data.requireObject("data").requireArray("items")
            items.forEach { item ->
                commit { parseFromSeriesJson(item, channelName) }
            }

            val nextPageUrl = if (items.size() > 0) url.incrementUrlParam("page") else null

            return JobStepResult.CompleteWith(
                ExtractResult(
                    errors = errors,
                    pagedData = PagedData(
                        itemList = itemList,
                        nextPageUrl = nextPageUrl
                    )
                )
            )
        }
    }
}
