package project.pipepipe.extractor.services.niconico

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.CHANNEL_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.DANMAKU_RAW_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.PLAYLIST_SEARCH_API_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.RELATED_VIDEO_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.SEARCH_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.TAB_RAW_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.TRENDING_RAW_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.TRENDING_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.USER_URL
import project.pipepipe.extractor.services.niconico.NicoNicoUrlParser.parseStreamId
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoChannelAlbumTabExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoChannelLiveTabExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoChannelMainTabExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoChannelPlaylistTabExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoDanmakuExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoPlaylistExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoPlaylistSearchExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoRelatedVideoExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoSearchExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoSeriesExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoStreamExtractor
import project.pipepipe.extractor.services.niconico.extractor.NicoNicoTrendingExtractor
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue

object NicoNicoUrlRouter {
    fun route(rawUrl: String): Extractor<*,*>? {
        val url = rawUrl.replace("sp.nicovideo.jp", "www.nicovideo.jp")
        return when {
            parseStreamId(url) != null -> NicoNicoStreamExtractor(url)
            url.contains(SEARCH_URL) -> NicoNicoSearchExtractor(url)
            url.contains(PLAYLIST_SEARCH_API_URL) -> NicoNicoPlaylistSearchExtractor(url)
            url.contains(TAB_RAW_URL) -> when {
                getQueryValue(url, "type") == "videos" -> NicoNicoChannelMainTabExtractor(url)
                getQueryValue(url, "type") == "playlists" -> NicoNicoChannelPlaylistTabExtractor(url)
                getQueryValue(url, "type") == "albums" -> NicoNicoChannelAlbumTabExtractor(url)
                getQueryValue(url, "type") == "lives" -> NicoNicoChannelLiveTabExtractor(url)
                else -> null
            }
            url.contains("/mylist/") -> NicoNicoPlaylistExtractor(url)
            url.contains("/series/") -> NicoNicoSeriesExtractor(url)
            url.contains(USER_URL) -> NicoNicoChannelMainTabExtractor(url)
            url.contains(CHANNEL_URL) -> NicoNicoChannelMainTabExtractor(url)
            url.contains(RELATED_VIDEO_URL) -> NicoNicoRelatedVideoExtractor(url)
            url.contains(TRENDING_RAW_URL) -> NicoNicoTrendingExtractor(url)
            url.contains(DANMAKU_RAW_URL) -> NicoNicoDanmakuExtractor(url)
            else -> null
        }
    }
}