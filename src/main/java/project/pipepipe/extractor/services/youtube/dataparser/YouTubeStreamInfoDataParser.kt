package project.pipepipe.extractor.services.youtube.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.IgnoreException
import project.pipepipe.extractor.services.youtube.YouTubeLinks.CHANNEL_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.SHORTS_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.STREAM_URL
import project.pipepipe.extractor.utils.TimeAgoParser
import project.pipepipe.extractor.utils.extractDigitsAsLong
import project.pipepipe.extractor.utils.mixedNumberWordToLong
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.utils.json.requireObject

object YouTubeStreamInfoDataParser {
    fun parseFromVideoRenderer(data: JsonNode, overrideChannelName: String? = null, overrideChannelId: String? = null): StreamInfo {
        if (!data.has("videoRenderer")) {
            return parseFromLockupViewModel(data, overrideChannelName,  overrideChannelName)
        }
        if (data.requireObject("videoRenderer").has("upcomingEventData")) throw IgnoreException()
        val isLive = when {
            runCatching { data.requireString("/videoRenderer/badges/0/metadataBadgeRenderer/style") }.getOrNull() == "BADGE_STYLE_TYPE_LIVE_NOW" -> true
            runCatching{ data.requireString("/videoRenderer/badges/0/metadataBadgeRenderer/icon/iconType") }.getOrNull()?.startsWith("LIVE") == true -> true
            else -> runCatching {
                data.requireObject("/videoRenderer/viewCountText")
                    .toString()
                    .let {
                        it.contains(" watching", ignoreCase = true) || it.contains("bukele", ignoreCase = true)
                    }
            }.getOrDefault(false)
        }

        val _isShort = when {
            runCatching { data.requireString("/videoRenderer/navigationEndpoint/commandMetadata/webCommandMetadata/webPageType") }.getOrNull() == "WEB_PAGE_TYPE_SHORTS" -> true
            runCatching{ data.requireObject("navigationEndpoint").has("reelWatchEndpoint") }.getOrNull() == true -> true
            else -> false
        }

        return StreamInfo(
            url = STREAM_URL + data.requireString("/videoRenderer/videoId"),
            serviceId = 0,
            name = data.requireString("/videoRenderer/title/runs/0/text"),
            uploaderName = runCatching{ data.requireString("/videoRenderer/longBylineText/runs/0/text") }.getOrDefault(overrideChannelName),
            uploaderUrl = runCatching{ CHANNEL_URL + data.requireString("/videoRenderer/longBylineText/runs/0/navigationEndpoint/browseEndpoint/browseId") }.getOrDefault(CHANNEL_URL + overrideChannelId),
            uploaderAvatarUrl = runCatching{
                data.requireArray("/videoRenderer/channelThumbnailSupportedRenderers/channelThumbnailWithLinkRenderer/thumbnail/thumbnails")
                    .first().requireString("url")
            }.getOrNull(),
            thumbnailUrl = data.requireArray("/videoRenderer/thumbnail/thumbnails").last().requireString("url"),
            isPaid = runCatching{ data.requireString("/videoRenderer/badges/0/metadataBadgeRenderer/style") == "BADGE_STYLE_TYPE_MEMBERS_ONLY" }.getOrDefault(false),
        ).apply {
            when (isLive) {
                false -> {
                    uploadDate =
                       runCatching { TimeAgoParser.parseToTimestamp(data.requireString("/videoRenderer/publishedTimeText/simpleText")) }.getOrNull()
                    duration = parseDurationString(data.requireString("/videoRenderer/lengthText/simpleText"))
                    viewCount = runCatching { data.requireString("/videoRenderer/viewCountText/simpleText").extractDigitsAsLong() }.getOrNull()
                    isShort = _isShort
                }
                true -> {
                    this.isLive = true
                    viewCount = runCatching{ data.requireString("/videoRenderer/viewCountText/runs/0/text").extractDigitsAsLong() }.getOrNull()
                }
            }
        }
    }

    fun parseFromPlaylistVideoRenderer(data: JsonNode): StreamInfo {
        val videoInfo = runCatching { data.requireObject("/playlistVideoRenderer/videoInfo") }.getOrNull()
        var viewCount: Long? = null
        var uploadDate: Long? = null

        when {
            videoInfo?.has("simpleText") == true -> {
                uploadDate = runCatching {
                    TimeAgoParser.parseToTimestamp(videoInfo.requireString("simpleText"))
                }.getOrNull()
            }
            videoInfo?.has("runs") == true -> {
                val runs = videoInfo.requireArray("runs")
                runs.forEach { run ->
                    val text = runCatching{ run.requireString("text") }.getOrNull() ?: return@forEach

                    if (viewCount == null && (text.contains("view", ignoreCase = true) || text.contains("ukubukwa", ignoreCase = true))) {
                        viewCount = text.extractDigitsAsLong()
                    }

                    if (uploadDate == null && text.contains("ago", ignoreCase = true) || text.endsWith("dlule", ignoreCase = true)) {
                        uploadDate = runCatching { TimeAgoParser.parseToTimestamp(text) }.getOrNull()
                    }
                }
            }
        }
        return StreamInfo(
            url = STREAM_URL + data.requireString("/playlistVideoRenderer/videoId"),
            serviceId = 0,
            viewCount = viewCount,
            duration = runCatching{ parseDurationString(data.requireString("/playlistVideoRenderer/lengthText/simpleText")) }.getOrNull(),
            uploadDate = uploadDate,
            name = data.requireString("/playlistVideoRenderer/title/runs/0/text"),
            uploaderName = runCatching { data.requireString("/playlistVideoRenderer/shortBylineText/runs/0/text") }.getOrNull(),
            uploaderUrl = runCatching{ CHANNEL_URL + data.requireString("/playlistVideoRenderer/shortBylineText/runs/0/navigationEndpoint/browseEndpoint/browseId") }.getOrNull(),
            uploaderAvatarUrl = runCatching{
                data.requireArray("/playlistVideoRenderer/thumbnail/thumbnails").last().requireString("url")
            }.getOrNull(),
            thumbnailUrl = data.requireArray("/playlistVideoRenderer/thumbnail/thumbnails").last().requireString("url"),
            isPaid = runCatching{ data.requireString("/playlistVideoRenderer/badges/0/metadataBadgeRenderer/style") == "BADGE_STYLE_TYPE_MEMBERS_ONLY" }.getOrDefault(false),
        )
    }

    fun parseFromLockupViewModel(data: JsonNode, overrideChannelName: String? = null, overrideChannelId: String? = null): StreamInfo {
        val useOverride = overrideChannelName != null && overrideChannelId != null
        val metadataRows = data.requireArray("/lockupViewModel/metadata/lockupMetadataViewModel/metadata/contentMetadataViewModel/metadataRows")

        //  collect
        val allMetadataTexts = mutableListOf<String>()
        for (row in metadataRows) {
            val parts = row.get("metadataParts")?.takeIf { it.isArray } ?: continue
            for (part in parts) {
                val content = part.get("text")?.get("content")?.asText()
                if (content != null) {
                    allMetadataTexts.add(content)
                }
            }
        }

        // analyze
        val isLive = allMetadataTexts.any { it.contains("watching", ignoreCase = true) || it.contains("bukele", ignoreCase = true) }
        val uploadDate = allMetadataTexts.firstOrNull { it.contains("ago", ignoreCase = true) || it.endsWith("dlule", ignoreCase = true) }?.let {
            TimeAgoParser.parseToTimestamp(it)
        }
        val viewCount = allMetadataTexts.firstOrNull {
            it.contains("view", ignoreCase = true) || it.contains("ukubukwa", ignoreCase = true)
        }?.let {
            runCatching { mixedNumberWordToLong(it) }.getOrNull()
        }
        val uploaderName = overrideChannelName ?: data.requireString("/lockupViewModel/metadata/lockupMetadataViewModel/metadata/contentMetadataViewModel/metadataRows/0/metadataParts/0/text/content")


        return StreamInfo(
            url = STREAM_URL + data.requireString("/lockupViewModel/contentId"),
            serviceId = 0,
            name = data.requireString("/lockupViewModel/metadata/lockupMetadataViewModel/title/content"),
            uploaderName = uploaderName,
            uploaderUrl = if (useOverride) {
                CHANNEL_URL + overrideChannelId
            } else {
                runCatching { CHANNEL_URL + data.requireString("/lockupViewModel/metadata/lockupMetadataViewModel/image/decoratedAvatarViewModel/rendererContext/commandContext/onTap/innertubeCommand/browseEndpoint/browseId") }.getOrNull()
            },
            uploaderAvatarUrl = if (useOverride) {
                null
            } else {
                runCatching{
                    data.requireArray("/lockupViewModel/metadata/lockupMetadataViewModel/image/decoratedAvatarViewModel/avatar/avatarViewModel/image/sources")
                        .last().requireString("url")
                }.getOrNull()
            },
            thumbnailUrl = data.requireArray("/lockupViewModel/contentImage/thumbnailViewModel/image/sources").last().requireString("url"),
            viewCount = viewCount
        ).apply {
            if (isLive) {
                this.isLive = true
            } else {
                duration = extractDuration(data)
                uploadDate?.let { this.uploadDate = it }
            }
        }
    }

    fun parseFromShortsLockupViewModel(data: JsonNode, overrideChannelName: String, overrideChannelId: String): StreamInfo {
        return StreamInfo(
            url = SHORTS_URL + data.requireString("/shortsLockupViewModel/onTap/innertubeCommand/reelWatchEndpoint/videoId"),
            serviceId = 0,
            name = data.requireString("/shortsLockupViewModel/overlayMetadata/primaryText/content"),
            uploaderName = overrideChannelName,
            uploaderUrl = CHANNEL_URL + overrideChannelId,
            thumbnailUrl = data.requireArray("/shortsLockupViewModel/onTap/innertubeCommand/reelWatchEndpoint/thumbnail/thumbnails").last().requireString("url"),
            isPaid = false, // todo: there do exist paid shorts but I have no data point
            isShort = true,
            viewCount = runCatching { data.requireString("/shortsLockupViewModel/overlayMetadata/secondaryText/content").extractDigitsAsLong() }.getOrNull()
        )
    }


    private fun extractDuration(data: JsonNode): Long? {
        val paths = listOf(
            "/lockupViewModel/contentImage/thumbnailViewModel/overlays/0/thumbnailOverlayBadgeViewModel/thumbnailBadges/0/thumbnailBadgeViewModel/text",
            "/lockupViewModel/contentImage/thumbnailViewModel/overlays/0/thumbnailBottomOverlayViewModel/badges/0/thumbnailBadgeViewModel/text"
        )

        return paths.firstNotNullOfOrNull { path ->
            runCatching { parseDurationString(data.requireString(path)) }.getOrNull()
        }
    }


    private fun parseDurationString(input: String): Long {
        val parts = input.split(if (":" in input) ":" else ".")
        val units = listOf(24, 60, 60, 1)

        require(parts.size <= units.size) {
            "Error duration string with unknown format: $input"
        }

        val offset = units.size - parts.size

        return parts.foldIndexed(0) { index, duration, part ->
            units[index + offset] * (duration + part.extractDigitsAsLong())
        }
    }
}