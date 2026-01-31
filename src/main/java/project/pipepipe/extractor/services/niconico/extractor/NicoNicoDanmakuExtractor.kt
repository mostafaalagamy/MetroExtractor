package project.pipepipe.extractor.services.niconico.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.niconico.NicoNicoLinks
import project.pipepipe.extractor.services.niconico.NicoNicoService
import project.pipepipe.extractor.services.niconico.dataparser.NiconicoDanmakuInfoDataParser
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray
import java.net.URLDecoder

class NicoNicoDanmakuExtractor(url: String): Extractor<Nothing, DanmakuInfo>(url) {
    override suspend fun fetchFirstPage(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(
                    payload = Payload(
                        RequestMethod.POST,
                        NicoNicoLinks.DANMAKU_API_URL,
                        NicoNicoService.NICO_BASE_HEADER,
                        body = URLDecoder.decode(getQueryValue(url, "data"), "UTF-8")
                    )
                )
            ), state = PlainState(1))
        } else {
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson().requireArray("/data/threads")
            data.forEach {
                it.requireArray("comments").forEach{ commit { NiconicoDanmakuInfoDataParser.parseFromCommentJson(it) } }
            }
            return JobStepResult.CompleteWith(ExtractResult(errors = errors, pagedData = PagedData(itemList, null)))
        }
    }
}