package project.pipepipe.extractor.services.niconico.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.SERIES_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.USER_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.getUserMylistUrl
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.utils.json.requireInt
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString

object NicoNicoPlaylistInfoDataParser {
    fun parseFromMylistJson(item: JsonNode) = PlaylistInfo(
        url = getUserMylistUrl(
            item.requireObject("owner").requireString("id"),
            item.requireLong("id").toString()
        ),
        serviceId = 6,
        name = runCatching {
            item.requireString("name")
        }.getOrElse {
            item.requireString("title")
        },
        thumbnailUrl = runCatching { item.requireString("thumbnailUrl") }.getOrNull(),
        uploaderName = runCatching { item.requireObject("owner").requireString("name") }.getOrNull(),
        uploaderUrl = runCatching {
            USER_URL + item.requireObject("owner").requireString("id")
        }.getOrNull(),
        streamCount = runCatching {
            item.requireLong("itemsCount")
        }.getOrElse {
            item.requireLong("videoCount")
        }
    )

    fun parseFromSeriesJson(item: JsonNode, uploaderName: String) = PlaylistInfo(
        url = SERIES_URL + item.requireLong("id"),
        serviceId = 6,
        name = item.requireString("title"),
        thumbnailUrl = runCatching { item.requireString("thumbnailUrl") }.getOrNull(),
        uploaderName = uploaderName,
        streamCount = runCatching { item.requireInt("itemsCount").toLong() }.getOrDefault(0)
    )
}
