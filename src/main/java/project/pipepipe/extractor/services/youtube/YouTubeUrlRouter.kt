package project.pipepipe.extractor.services.youtube

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.youtube.YouTubeLinks.COMMENT_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.PLAYLIST_BASE_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.REPLY_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.SEARCH_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.TAB_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.TRENDING_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeUrlParser.parseStreamId
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeChannelIdParser.parseChannelId
import project.pipepipe.extractor.services.youtube.extractor.YouTubeChannelLiveTabExtractor
import project.pipepipe.extractor.services.youtube.extractor.YouTubeChannelMainTabExtractor
import project.pipepipe.extractor.services.youtube.extractor.YouTubeChannelPlaylistTabExtractor
import project.pipepipe.extractor.services.youtube.extractor.YouTubeCommentExtractor
import project.pipepipe.extractor.services.youtube.extractor.YouTubePlaylistExtractor
import project.pipepipe.extractor.services.youtube.extractor.YouTubeSearchExtractor
import project.pipepipe.extractor.services.youtube.extractor.YouTubeStreamExtractor
import project.pipepipe.extractor.services.youtube.extractor.YouTubeTrendingExtractor
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue

object YouTubeUrlRouter {
    fun route(rawUrl: String): Extractor<*,*>? {
        val url = rawUrl.replace("://youtube.com", "://www.youtube.com").replace("://m.youtube.com", "://www.youtube.com")
        return when {
            url.contains(TRENDING_RAW_URL) -> YouTubeTrendingExtractor(url)
            url.contains(PLAYLIST_BASE_URL) -> YouTubePlaylistExtractor(url)
            url.contains(SEARCH_RAW_URL) -> YouTubeSearchExtractor(url)
            url.contains(TAB_RAW_URL) -> when {
                getQueryValue(url, "type") == "videos" -> YouTubeChannelMainTabExtractor(url)
                getQueryValue(url, "type") == "lives" -> YouTubeChannelLiveTabExtractor(url)
                getQueryValue(url, "type") == "playlists" -> YouTubeChannelPlaylistTabExtractor(url)
                else -> null
            }
            url.contains(COMMENT_RAW_URL) || url.contains(REPLY_RAW_URL) -> YouTubeCommentExtractor(url)
            parseStreamId(url) != null -> YouTubeStreamExtractor(url)
            parseChannelId(url) != null -> YouTubeChannelMainTabExtractor(url)
            else -> null
        }
    }
}