package project.pipepipe.extractor.services.soundcloud.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.shared.infoitem.StreamInfo
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object SoundCloudStreamInfoDataParser {
    private val ALTERNATE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss +0000")

    fun parseFromTrackObject(data: JsonNode): StreamInfo {
        val textualUploadDate = data.requireString("created_at")
        val artworkUrl = runCatching { data.requireString("artwork_url") }.getOrNull()
        val thumbnailUrl = if (!artworkUrl.isNullOrEmpty()) {
            artworkUrl.replace("large.jpg", "crop.jpg")
        } else {
            runCatching { data.requireObject("user").requireString("avatar_url") }.getOrNull()
        }

        return StreamInfo(
            url = data.requireString("permalink_url") + "?tid=${data.requireString("id")}",
            serviceId = 1,
            name = data.requireString("title"),
            uploaderName = data.requireObject("user").requireString("username"),
            uploaderUrl = data.requireObject("user").requireString("permalink_url"),
            uploaderAvatarUrl = data.requireObject("user").requireString("avatar_url"),
            duration = data.requireLong("duration") / 1000,
            viewCount = runCatching { data.requireLong("playback_count") }.getOrNull(),
            thumbnailUrl = thumbnailUrl,
            uploadDate = runCatching { parseDate(textualUploadDate) }.getOrNull()?.toInstant()?.toEpochMilli(),
            isPaid = data.requireString("policy") !in listOf("ALLOW", "MONETIZE")
        )
    }

    private fun parseDate(dateText: String): OffsetDateTime {
        return try {
            OffsetDateTime.parse(dateText)
        } catch (e: Exception) {
            OffsetDateTime.parse(dateText, ALTERNATE_DATE_FORMATTER)
        }
    }
}
