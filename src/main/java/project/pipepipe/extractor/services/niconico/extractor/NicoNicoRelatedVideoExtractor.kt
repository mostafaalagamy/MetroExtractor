package project.pipepipe.extractor.services.niconico.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoStreamInfoDataParser
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.shared.infoitem.RelatedItemInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.extractor.utils.json.requireObject

class NicoNicoRelatedVideoExtractor(
    url: String
) : Extractor<RelatedItemInfo, Info>(url) {

    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask( payload = Payload(
                    RequestMethod.GET,
                    url,
                    GOOGLE_HEADER
                ))
            ), state = PlainState(1))
        } else {
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson().requireArray("/data/items")
            data.forEach {
                commit { (NicoNicoStreamInfoDataParser.parseFromStreamCommonJson(it.requireObject("content"))) }
            }
            return JobStepResult.CompleteWith(ExtractResult(RelatedItemInfo(url), errors, PagedData(itemList, null)))
        }
    }
}