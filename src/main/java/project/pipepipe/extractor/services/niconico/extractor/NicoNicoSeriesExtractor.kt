package project.pipepipe.extractor.services.niconico.extractor

import org.jsoup.Jsoup
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoStreamInfoDataParser
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.requireArray

class NicoNicoSeriesExtractor(url: String) : Extractor<PlaylistInfo, StreamInfo>(url) {

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
                        taskId = "series",
                        payload = Payload(RequestMethod.GET, url)
                    )
                ),
                state = PlainState(1)
            )
        } else {
            val htmlResponse = clientResults!!.first { it.taskId == "series" }.result!!
            val document = Jsoup.parse(htmlResponse)

            val uploaderName = safeGet {
                document.select("meta[property=profile:username]").attr("content")
            }

            val uploaderUrl = safeGet {
                document.select("meta[property=og:url]").attr("content").split("/series/")[0]
            }

            val avatar = safeGet {
                document.select("meta[property=og:image]").attr("content")
            }

            val count = safeGet {
                document.select("meta[property=og:title]").attr("content").split("（全")[1].split("件）")[0].toLong()
            } ?: 0L

            val name = document.select("meta[property=og:description]").attr("content").split("の「")[1].split("（全")[0]


            // Parse JSON data from data attribute
            val initialData = safeGet {
                document.select("div#js-initial-userpage-data").attr("data-initial-data")
            }

            initialData!!.asJson().requireArray("/nvapi/0/body/data/items").forEach { item ->
                commit {
                    NicoNicoStreamInfoDataParser.parseFromStreamCommonJson(item)
                }
            }

            return JobStepResult.CompleteWith(
                ExtractResult(
                    info = PlaylistInfo(
                        serviceId = 6,
                        url = url,
                        name = name,
                        thumbnailUrl = avatar,
                        streamCount = count,
                        uploaderUrl = uploaderUrl,
                        uploaderName = uploaderName,
                        uploaderAvatarUrl = avatar
                    ),
                    errors = errors,
                    pagedData = PagedData(itemList, null)
                )
            )
        }
    }
}