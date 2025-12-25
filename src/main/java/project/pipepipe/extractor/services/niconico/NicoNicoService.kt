package project.pipepipe.extractor.services.niconico

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.StreamingService
import project.pipepipe.extractor.base.CookieExtractor
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.PLAYLIST_SEARCH_API_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.SEARCH_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.SUGGESTION_URL
import project.pipepipe.extractor.services.niconico.NicoNicoLinks.TRENDING_RAW_URL
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.infoitem.TrendingInfo
import project.pipepipe.shared.infoitem.ExternalUrlType
import project.pipepipe.shared.infoitem.helper.SearchFilterGroup
import project.pipepipe.shared.infoitem.helper.SearchFilterItem
import project.pipepipe.shared.infoitem.helper.SearchType
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod

class NicoNicoService(id: Int): StreamingService(id)  {
    companion object {
        val GOOGLE_HEADER = mapOf(
            "User-Agent" to "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
        )
        val NICO_BASE_HEADER = mapOf(
            "Referer" to "https://www.nicovideo.jp/",
            "X-Frontend-Id" to "6",
            "X-Frontend-Version" to "0",
        )
        fun isChannel(watchData: JsonNode): Boolean {
            val owner = watchData.at("/data/response/owner")
            if (owner.isNull) {
                return true
            }
            val channel = watchData.at("/data/response/channel")
            return !channel.isNull && !channel.isMissingNode
        }
        const val TRENDING_RSS_STR: String = "^第\\d+位：(.*)$"
        const val SMILEVIDEO: String = "(nicovideo\\.jp\\/watch|nico\\.ms)\\/(.+)?"
    }
    override suspend fun getCookieExtractor(): CookieExtractor = CookieExtractor()
    override fun route(url: String): Extractor<*, *>?  = NicoNicoUrlRouter.route(url)

    val sortFilters = listOf(
        SearchFilterGroup(
            groupName = "sortby",
            onlyOneCheckable = true,
            availableSearchFilterItems = listOf(
                SearchFilterItem("sort_popular", "sort=h"),
                SearchFilterItem("sort_view", "sort=v"),
                SearchFilterItem("sort_bookmark", "sort=m"),
                SearchFilterItem("sort_likes", "sort=likeCount"),
                SearchFilterItem("sort_publish_time", "sort=f"),
                SearchFilterItem("sort_last_comment_time", "sort=n"),
            ),
            defaultFilter = SearchFilterItem("sort_popular", "sort=h")
        ),
        SearchFilterGroup(
            groupName = "duration",
            onlyOneCheckable = true,
            availableSearchFilterItems = listOf(
                SearchFilterItem("< 5 min", "l_range=1"),
                SearchFilterItem("> 20 min", "l_range=2")
            )
        ),
        SearchFilterGroup(
            groupName = "sortorder",
            onlyOneCheckable = true,
            availableSearchFilterItems = listOf(
                SearchFilterItem("sort_descending", "&order=d"),
                SearchFilterItem("sort_ascending", "&order=a"),
            ),
            defaultFilter = SearchFilterItem("sort_descending", "&order=d")
        )
    )

    val playListSortFilters = listOf(
        SearchFilterGroup(
            groupName = "sortby",
            onlyOneCheckable = true,
            availableSearchFilterItems = listOf(
                SearchFilterItem("sort_popular", "sortKey=_hotTotalScore"),
                SearchFilterItem("sort_video_count", "sortKey=videoCount"),
                SearchFilterItem("sort_publish_time", "sortKey=startTime")
            ),
            defaultFilter = SearchFilterItem("sort_popular", "sortKey=_hotTotalScore")
        )
    )

    override val serviceInfo: SupportedServiceInfo
        get() = SupportedServiceInfo(
            serviceId = 6,
            serviceName = "NicoNico",
            suggestionPayload = Payload(RequestMethod.GET, SUGGESTION_URL),
            suggestionStringPath = Pair("/candidates", "/"),
            suggestionJsonBetween = null,
            availableSearchTypes = listOf(
                SearchType("video", SEARCH_URL, sortFilters),
                SearchType("playlist", "$PLAYLIST_SEARCH_API_URL&keyword=", playListSortFilters),
            ),
            themeColor = "#9e9e9e",
            trendingList = listOf(
                TrendingInfo("$TRENDING_RAW_URL?name=trending", 6, "trending")
            ),
            urlPatterns = mapOf(
                ExternalUrlType.STREAM to listOf(
                    "nicovideo\\.jp/watch/",
                    "nico\\.ms/"
                ),
                ExternalUrlType.CHANNEL to listOf(
                    "nicovideo\\.jp/user/",
                    "ch\\.nicovideo\\.jp/"
                ),
                ExternalUrlType.PLAYLIST to listOf(
                    "/mylist/",
                    "/series/"
                )
            )
        )
}