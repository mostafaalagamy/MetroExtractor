package project.pipepipe.extractor.services.youtube.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.services.youtube.YouTubeLinks.BASE_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.CHANNEL_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.PLAYLIST_BASE_URL
import project.pipepipe.extractor.utils.extractDigitsAsLong
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString

object YouTubePlaylistInfoDataParser {
    fun parseFromLockupMetadataViewModel(data: JsonNode, overrideName: String? = null): PlaylistInfo {
        val playlistUrl = runCatching {
            PLAYLIST_BASE_URL + data.requireString("/lockupViewModel/rendererContext/commandContext/onTap/innertubeCommand/watchEndpoint/playlistId")
        }.getOrNull() ?: data.requireArray("/lockupViewModel/metadata/lockupMetadataViewModel/metadata/contentMetadataViewModel/metadataRows")
            .firstNotNullOf {
                runCatching {
                    PLAYLIST_BASE_URL + it.requireString("/metadataParts/0/text/commandRuns/0/onTap/innertubeCommand/watchEndpoint/playlistId")
                }.getOrNull()
            }

        val uploaderUrl = data.requireArray("/lockupViewModel/metadata/lockupMetadataViewModel/metadata/contentMetadataViewModel/metadataRows")
            .firstNotNullOf {
                runCatching {
                    CHANNEL_URL + it.requireString("/metadataParts/0/text/commandRuns/0/onTap/innertubeCommand/browseEndpoint/browseId")
                }.getOrNull()
            }
        return PlaylistInfo(
            thumbnailUrl = data.requireArray("/lockupViewModel/contentImage/collectionThumbnailViewModel/primaryThumbnail/thumbnailViewModel/image/sources")
                .last().requireString("url"),
            streamCount = data.requireString("/lockupViewModel/contentImage/collectionThumbnailViewModel/primaryThumbnail/thumbnailViewModel/overlays/0/thumbnailOverlayBadgeViewModel/thumbnailBadges/0/thumbnailBadgeViewModel/text")
                .extractDigitsAsLong(),
            url = playlistUrl,
            serviceId = 0,
            name = data.requireString("/lockupViewModel/metadata/lockupMetadataViewModel/title/content"),
            uploaderName = overrideName?:data.requireString("/lockupViewModel/metadata/lockupMetadataViewModel/metadata/contentMetadataViewModel/metadataRows/0/metadataParts/0/text/content"),
            uploaderUrl = uploaderUrl,
        )
    }
}