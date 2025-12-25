package project.pipepipe.extractor.services.niconico.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.niconico.NicoNicoLinks
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.GOOGLE_HEADER
import project.pipepipe.extractor.services.niconico.NicoNicoService.Companion.NICO_BASE_HEADER
import project.pipepipe.extractor.services.niconico.dataparser.NicoNicoStreamInfoDataParser
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString
import java.util.regex.Pattern

class NicoNicoPlaylistExtractor(url: String) : Extractor<PlaylistInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            val playlistId = url.split("/mylist/")[1].split(Pattern.quote("?"))[0]
            val apiUrl = "${NicoNicoLinks.MYLIST_URL}$playlistId?pageSize=100&page=1"
            val headers = NICO_BASE_HEADER

            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        taskId = "info",
                        payload = Payload(RequestMethod.GET, apiUrl, headers = headers)
                    )
                ),
                state = PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId == "info" }.result!!.asJson()
            val data = result.requireObject("data").requireObject("mylist")
            val items = data.requireArray("items")
            val owner = data.requireObject("owner")

            // Parse playlist items
            items.forEach { item ->
                commit {
                    NicoNicoStreamInfoDataParser.parseFromStreamCommonJson(item)
                }
            }

            // Determine uploader URL based on owner type
            val uploaderUrl = if (owner.requireString("ownerType") == "user") {
                "${NicoNicoLinks.USER_URL}${owner.requireString("id")}"
            } else {
                "${NicoNicoLinks.CHANNEL_URL}${owner.requireString("id")}"
            }

            // Prepare next page URL if there are items
            val nextPageUrl = if (items.size() > 0) {
                val playlistId = url.split("/mylist/")[1].split(Pattern.quote("?"))[0]
                "${NicoNicoLinks.MYLIST_URL}$playlistId?pageSize=100&page=2"
            } else {
                null
            }

            return JobStepResult.CompleteWith(
                ExtractResult(
                    info = PlaylistInfo(
                        serviceId = 6,
                        url = url,
                        name = data.requireString("name"),
                        thumbnailUrl = safeGet { owner.requireString("iconUrl") },
                        streamCount = data.requireLong("totalItemCount"),
                        uploaderUrl = uploaderUrl,
                        uploaderName = safeGet { owner.requireString("name") },
                        uploaderAvatarUrl = safeGet { owner.requireString("iconUrl") }
                    ),
                    errors = errors,
                    pagedData = PagedData(itemList, nextPageUrl)
                )
            )
        }
    }

    override suspend fun fetchGivenPage(
        url: String,
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        taskId = "page",
                        payload = Payload(RequestMethod.GET, url, headers = GOOGLE_HEADER)
                    )
                ),
                state = PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId == "page" }.result!!.asJson()
            val data = result.requireObject("data").requireObject("mylist")
            val items = data.requireArray("items")

            // Parse items
            items.forEach { item ->
                commit {
                    NicoNicoStreamInfoDataParser.parseFromStreamCommonJson(item)
                }
            }

            // Calculate next page URL
            val nextPageUrl = if (items.size() > 0) {
                url.incrementUrlParam("page")
            } else {
                null
            }

            return JobStepResult.CompleteWith(
                ExtractResult(
                    errors = errors,
                    pagedData = PagedData(itemList, nextPageUrl)
                )
            )
        }
    }
}