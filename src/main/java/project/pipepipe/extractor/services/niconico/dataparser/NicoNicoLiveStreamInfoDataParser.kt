package project.pipepipe.extractor.services.niconico.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.WATCH_URL
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString

object NicoNicoLiveStreamInfoDataParser {
    fun parseFromLiveHistoryJson(item: JsonNode) = StreamInfo(
        url = WATCH_URL + item.requireObject("linkedContent").requireString("contentId"),
        serviceId = 6,
        name = item.requireObject("program").requireString("title"),
        uploaderName = runCatching {
            item.requireObject("programProvider").requireString("name")
        }.getOrNull(),
        uploaderUrl = runCatching {
            item.requireObject("programProvider").requireString("profileUrl")
        }.getOrNull(),
        uploaderAvatarUrl = runCatching {
            item.requireObject("programProvider").requireObject("icons").requireString("uri150x150")
        }.getOrNull(),
        uploadDate = runCatching {
            item.requireObject("program").requireObject("schedule")
                .requireObject("endTime").requireLong("seconds") * 1000
        }.getOrNull(),
        duration = runCatching {
            val schedule = item.requireObject("program").requireObject("schedule")
            schedule.requireObject("endTime").requireLong("seconds") -
                    schedule.requireObject("beginTime").requireLong("seconds")
        }.getOrNull(),
        viewCount = runCatching {
            item.requireObject("statistics").requireObject("viewers").requireLong("value")
        }.getOrNull(),
        thumbnailUrl = runCatching {
            item.requireObject("thumbnail").requireObject("huge").requireString("s352x198")
        }.getOrNull()
    )
}
