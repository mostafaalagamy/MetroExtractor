package project.pipepipe.extractor.services.niconico.dataparser

import com.fasterxml.jackson.databind.JsonNode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.CHANNEL_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.USER_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.WATCH_URL
import project.pipepipe.extractor.services.niconico.NicoNicoService
import project.pipepipe.extractor.utils.getDurationFromString
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.utils.json.requireBoolean
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object NicoNicoStreamInfoDataParser {
    fun parseFromStreamCommonJson(itemRaw: JsonNode): StreamInfo {
        val item = when {
            itemRaw.has("content") -> itemRaw.requireObject("content")
            itemRaw.has("video") -> itemRaw.requireObject("video")
            else -> itemRaw
        }
        return StreamInfo(
            url = WATCH_URL + item.requireString("id"),
            serviceId = 6,
            name = item.requireString("title"),
            uploaderName = runCatching { item.requireString("/owner/name") }.getOrNull(),
            uploaderUrl = runCatching {
                val owner = item.requireObject("owner")
                val ownerType = owner.requireString("ownerType")
                val ownerId = owner.requireString("id")
                if (ownerType == "user") {
                    USER_URL + ownerId
                } else {
                    CHANNEL_URL + ownerId
                }
            }.getOrNull(),
            uploaderAvatarUrl = runCatching { item.requireString("/owner/iconUrl") }.getOrNull(),
            uploadDate = java.time.OffsetDateTime.parse(item.requireString("registeredAt")).toInstant().toEpochMilli(),
            duration = item.requireLong("duration"),
            viewCount = item.requireLong("/count/view"),
            thumbnailUrl = item.requireObject("thumbnail").let { thumbnail ->
                listOf("nHdUrl", "url", "listingUrl")
                    .firstNotNullOfOrNull { key ->
                        runCatching { thumbnail.requireString(key) }.getOrNull()
                    }
            },
            isPaid = runCatching { item.requireBoolean("isPaymentRequired") }.getOrDefault(false),
        )
    }

    fun parseFromRSSXml(item: Element, userName: String, userUrl: String): StreamInfo {
        val cdata = Jsoup.parse(item.selectFirst("description")?.text().orEmpty())
        val trendingRegex = Regex(NicoNicoService.TRENDING_RSS_STR)
        val rawTitle = item.selectFirst("title")?.text().orEmpty()
        val title = trendingRegex.find(rawTitle)?.groupValues?.getOrNull(1).orEmpty()
            .ifEmpty { rawTitle }

        val smileVideoRegex = Regex(NicoNicoService.SMILEVIDEO)
        val url = item.textNodes()
            .firstOrNull { smileVideoRegex.containsMatchIn(it.text()) }
            ?.text() ?: item.selectFirst("link")!!.text()

        val thumbnailUrl = cdata.getElementsByClass("nico-thumbnail")
            .selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }

        val duration = getDurationFromString(cdata.getElementsByClass("nico-info-length").text())

        val uploadDate = parseUploadDateMillis(
            cdata.getElementsByClass("nico-info-date").text()
        )

        val isPaid = item.getElementsByTag("nicoch:isPremium")
            .text().trim().equals("true", ignoreCase = true)

        return StreamInfo(
            url = url.substringBefore("?"),
            serviceId = 6,
            name = title,
            uploaderName = userName,
            uploaderUrl = userUrl,
            uploadDate = uploadDate,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            isPaid = isPaid,
        )
    }

    private fun parseUploadDateMillis(text: String): Long? {
        if (text.isBlank()) return null

        val normalized = text
            .replace("投稿日：", "")
            .replace("投稿日時：", "")
            .replace("投稿：", "")
            .replace("最終更新：", "")
            .replace("更新：", "")
            .replace("：", ":")
            .replace("年", "/")
            .replace("月", "/")
            .replace("日", "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val zone = ZoneId.of("Asia/Tokyo")
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy/M/d H:m:s", Locale.JAPAN),
            DateTimeFormatter.ofPattern("yyyy/M/d H:m", Locale.JAPAN),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.JAPAN),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.JAPAN)
        )

        return formatters.asSequence()
            .mapNotNull { formatter ->
                runCatching { LocalDateTime.parse(normalized, formatter) }
                    .getOrNull()
            }
            .map { it.atZone(zone).toInstant().toEpochMilli() }
            .firstOrNull()
    }
}