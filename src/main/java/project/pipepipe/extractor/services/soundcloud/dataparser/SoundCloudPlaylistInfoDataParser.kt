package project.pipepipe.extractor.services.soundcloud.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.infoitem.PlaylistInfo

object SoundCloudPlaylistInfoDataParser {
    fun parseFromPlaylistObject(data: JsonNode): PlaylistInfo {
        val artworkUrl = runCatching { data.requireString("artwork_url") }.getOrNull()
        val thumbnailUrl = when {
            !artworkUrl.isNullOrEmpty() -> artworkUrl.replace("large.jpg", "crop.jpg")
            else -> {
                val tracksArray = data.requireArray("tracks")
                tracksArray.firstNotNullOfOrNull { track ->
                    val trackArtwork = runCatching { track.requireString("artwork_url") }.getOrNull()
                    when {
                        !trackArtwork.isNullOrEmpty() -> trackArtwork.replace("large.jpg", "crop.jpg")
                        else -> runCatching { track.requireObject("user").requireString("avatar_url") }.getOrNull()
                    }
                } ?: runCatching { data.requireObject("user").requireString("avatar_url") }.getOrNull()
            }
        }

        return PlaylistInfo(
            url = data.requireString("permalink_url") + "?pid=${data.requireLong("id")}",
            name = data.requireString("title"),
            serviceId = 1,
            thumbnailUrl = thumbnailUrl,
            uploaderName = runCatching { data.requireObject("user").requireString("username") }.getOrNull(),
            streamCount = data.requireLong("track_count")
        )
    }
}
