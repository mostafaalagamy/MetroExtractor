package project.pipepipe.extractor.services.soundcloud

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks.ALBUMS_PATH
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks.API_V2_URL
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks.CHARTS_URL
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks.PLAYLIST_PATH
import project.pipepipe.extractor.services.soundcloud.SoundCloudLinks.TRACKS_PATH
import project.pipepipe.extractor.services.soundcloud.extractor.*

object SoundCloudUrlRouter {
    private val CHANNEL_TABS = listOf(TRACKS_PATH, PLAYLIST_PATH, ALBUMS_PATH)

    private fun isChannelRawUrl(url: String): Boolean = url.startsWith("channel://soundcloud.raw?")
    private fun isSearchUrl(url: String): Boolean = url.startsWith("search://soundcloud.raw?")
    private fun isSoundCloudUrl(url: String): Boolean = 
        url.startsWith("https://soundcloud.com/") || url.startsWith("http://soundcloud.com/") ||
        url.startsWith("https://www.soundcloud.com/") || url.startsWith("http://www.soundcloud.com/") ||
        url.startsWith("https://m.soundcloud.com/") || url.startsWith("http://m.soundcloud.com/")
    
    private fun isPlaylistUrl(url: String): Boolean {
        val afterDomain = url.substringAfter("soundcloud.com/", "")
        return afterDomain.contains("/sets/")
    }
    
    private fun isStreamUrl(url: String): Boolean {
        val afterDomain = url.substringAfter("soundcloud.com/", "")
        val parts = afterDomain.split("/").filter { it.isNotEmpty() }
        if (parts.size != 2) return false
        val secondPart = parts[1]
        return secondPart !in listOf("tracks", "albums", "sets", "reposts", "followers", "following")
    }

    fun route(rawUrl: String): Extractor<*,*>? {
        val url = rawUrl.lowercase()
        return when {
            isChannelRawUrl(url) -> SoundCloudChannelExtractor(rawUrl)
            isSearchUrl(url) -> SoundCloudSearchExtractor(rawUrl)
            url.contains(CHARTS_URL) -> when {
                url.contains("$CHARTS_URL/new") -> SoundCloudChartsNewExtractor(rawUrl)
                else -> null
            }
            isPlaylistUrl(url) -> SoundCloudPlaylistExtractor(rawUrl)
            url.contains(API_V2_URL) -> {
                when {
                    url.contains("/comments") -> SoundCloudCommentExtractor(rawUrl)
                    url.contains(TRACKS_PATH) -> SoundCloudTracksTabExtractor(rawUrl)
                    url.contains("playlists_without_albums") -> SoundChannelPlaylistsTabExtractor(rawUrl)
                    url.contains(ALBUMS_PATH) -> SoundCloudAlbumsTabExtractor(rawUrl)
                    else -> null
                }
            }
            isSoundCloudUrl(url) -> {
                when {
                    url.contains("/playlists") -> SoundChannelPlaylistsTabExtractor(rawUrl)
                    url.contains(ALBUMS_PATH) -> SoundCloudAlbumsTabExtractor(rawUrl)
                    isStreamUrl(url) -> SoundCloudStreamExtractor(rawUrl)
                    else -> SoundCloudChannelExtractor(rawUrl)
                }
            }
            else -> null
        }
    }
}
