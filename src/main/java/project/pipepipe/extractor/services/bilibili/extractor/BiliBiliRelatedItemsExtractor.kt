package project.pipepipe.extractor.services.bilibili.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.Router.setType
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliStreamInfoDataParser
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.shared.infoitem.RelatedItemInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState

class BiliBiliRelatedItemsExtractor(
    url: String
) : Extractor<RelatedItemInfo, Info>(url) {
    val partitionList = ArrayList<StreamInfo>()
    fun commitPartition(itemProvider: ()->StreamInfo) {
        try {
            partitionList.add(itemProvider())
        } catch (e: Exception) {
            _errors.add(e)
        }
    }

    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val bvid = url.split("/").last().split("?")[0]
        val headers = BilibiliService.getHeadersWithCookie(url, cookie!!)
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(taskId = "related", payload = Payload(
                    RequestMethod.GET,
                    BiliBiliLinks.GET_RELATED_URL + bvid,
                    headers
                )),
                ClientTask(taskId = "partition", payload = Payload(
                    RequestMethod.GET,
                    BiliBiliLinks.GET_PARTITION_URL + bvid,
                    headers
                )),
            ), state = PlainState(1))
        } else {
            val relatedData = clientResults!!.first { it.taskId == "related" }.result!!.asJson().requireArray("data")
            val partitionData = clientResults.first { it.taskId == "partition" }.result!!.asJson().requireArray("data")
            relatedData.forEach {
                commit { (BiliBiliStreamInfoDataParser.parseFromRelatedInfoJson(it)) }
            }
            partitionData.forEachIndexed {i, e ->
                commitPartition { (BiliBiliStreamInfoDataParser.parseFromPartitionInfoJson(e, bvid, i + 1)) }
            }
            return JobStepResult.CompleteWith(ExtractResult(RelatedItemInfo(url.setType("related"), if (partitionList.size == 1)null else partitionList), errors, PagedData(itemList, null)))
        }
    }
}