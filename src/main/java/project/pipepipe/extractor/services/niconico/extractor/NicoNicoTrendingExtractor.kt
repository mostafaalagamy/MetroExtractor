package project.pipepipe.extractor.services.niconico.extractor

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.TRENDING_URL
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoStreamInfoDataParser
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireObject

class NicoNicoTrendingExtractor(url: String) : Extractor<Nothing, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.GET,
                            TRENDING_URL,
                            GOOGLE_HEADER
                        )
                    ),
                ), state = PlainState(1)
            )
        } else {
            val htmlResponse = clientResults!!.first { it.taskId.isDefaultTask() }.result!!
            val document = Jsoup.parse(htmlResponse)

            // Extract the meta tag with server response data
            val metaElement = document.select("meta[name=server-response]").first()
                ?: error("Could not find server-response meta tag")

            val escapedContent = metaElement.attr("content")

            // Unescape HTML entities
            val unescapedContent = Parser.unescapeEntities(escapedContent, false)

            // Parse the JSON data
            val jsonData = unescapedContent.asJson()
            val items = jsonData
                .requireObject("data")
                .requireObject("response")
                .requireObject("\$getTeibanRanking")
                .requireObject("data")
                .requireArray("items")

            // Parse each item
            items.forEach { item ->
                commit { NicoNicoStreamInfoDataParser.parseFromStreamCommonJson(item) }
            }

            return JobStepResult.CompleteWith(
                result = ExtractResult(
                    errors = errors,
                    pagedData = PagedData(
                        itemList, null
                    )
                )
            )
        }
    }
}