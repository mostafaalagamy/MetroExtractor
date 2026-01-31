package project.pipepipe.extractor.services.niconico.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.NICO_BASE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoPlaylistInfoDataParser.parseFromMylistJson
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
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

class NicoNicoChannelPlaylistTabExtractor(url: String) : Extractor<Nothing, PlaylistInfo>(url) {
    override suspend fun fetchFirstPage(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val userId = getQueryValue(url, "id")!!

        if (currentState == null) {
            val realUrl = "https://nvapi.nicovideo.jp/v1/users/$userId/mylists?sampleItemCount=3"
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask("playlists", Payload(RequestMethod.GET, realUrl, NICO_BASE_HEADER))
                ), PlainState(1)
            )
        } else {
            val data = clientResults!!.first { it.taskId == "playlists" }.result!!.asJson()
            val mylists = data.requireObject("data").requireArray("mylists")
            mylists.forEach { mylist ->
                commit { parseFromMylistJson(mylist) }
            }

            return JobStepResult.CompleteWith(
                ExtractResult(
                    errors = errors,
                    pagedData = PagedData(
                        itemList = itemList,
                        nextPageUrl = null
                    )
                )
            )
        }
    }
}
