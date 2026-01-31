package project.pipepipe.extractor.services.soundcloud.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.utils.json.requireInt
import project.pipepipe.extractor.utils.json.requireLong
import project.pipepipe.extractor.utils.json.requireObject
import project.pipepipe.extractor.utils.json.requireString
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.extractor.utils.json.requireBoolean
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object SoundCloudCommentInfoDataParser {
    private val ALTERNATE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss +0000")

    fun parseFromCommentObject(data: JsonNode): CommentInfo {
        val textualUploadDate = data.requireString("created_at")
        val user = data.requireObject("user")

        return CommentInfo(
            content = data.requireString("body"),
            authorName = user.requireString("username"),
            authorAvatarUrl = user.requireString("avatar_url"),
            authorUrl = user.requireString("permalink_url"),
            authorVerified = user.requireBoolean("verified"),
            uploadDate = runCatching { parseDate(textualUploadDate) }.getOrNull()?.toInstant()?.toEpochMilli(),
            serviceId = 1,
            url = "comment://soundcloud.raw?id=" + data.requireString("id")
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
