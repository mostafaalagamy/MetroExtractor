package project.pipepipe.extractor.services.youtube.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.services.youtube.YouTubeLinks.CHANNEL_URL
import project.pipepipe.extractor.utils.mixedNumberWordToLong
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString

object YouTubeChannelInfoDataParser {
    fun parseFromChannelRenderer(item: JsonNode): ChannelInfo {
        return ChannelInfo(
            url = CHANNEL_URL + item.requireString("/channelRenderer/navigationEndpoint/browseEndpoint/browseId"),
            name = item.requireString("/channelRenderer/title/simpleText"),
            serviceId = 0,
            thumbnailUrl = "https:" + item.requireArray("/channelRenderer/thumbnail/thumbnails").last().requireString("url"),
            description = runCatching { item.requireString("/channelRenderer/descriptionSnippet/runs/0/text") }.getOrNull(),
            subscriberCount = runCatching{ mixedNumberWordToLong(item.requireString("/channelRenderer/videoCountText/simpleText")) }.getOrNull(),
        )
    }

    fun parseFromMusicResponsiveListItemRenderer(item: JsonNode): ChannelInfo {
        return ChannelInfo(
            url = CHANNEL_URL + item.requireString("/musicResponsiveListItemRenderer/navigationEndpoint/browseEndpoint/browseId"),
            name = item.requireString("/musicResponsiveListItemRenderer/flexColumns/0/musicResponsiveListItemFlexColumnRenderer/text/runs/0/text"),
            serviceId = 0,
            thumbnailUrl = item.requireArray("/musicResponsiveListItemRenderer/thumbnail/musicThumbnailRenderer/thumbnail/thumbnails").last().requireString("url"),
            description = null,
            subscriberCount = runCatching {
                val text = item.requireString("/musicResponsiveListItemRenderer/flexColumns/1/musicResponsiveListItemFlexColumnRenderer/text/runs/2/text")
                mixedNumberWordToLong(text)
            }.getOrNull(),
        )
    }
}