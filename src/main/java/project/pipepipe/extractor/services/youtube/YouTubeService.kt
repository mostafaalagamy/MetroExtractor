package project.pipepipe.extractor.services.youtube

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.StreamingService
import project.pipepipe.extractor.base.CookieExtractor
import project.pipepipe.extractor.services.youtube.YouTubeLinks.TRENDING_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.GET_SUGGESTION_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.SEARCH_RAW_URL
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.infoitem.TrendingInfo
import project.pipepipe.shared.infoitem.ExternalUrlType
import project.pipepipe.shared.infoitem.helper.SearchFilterGroup
import project.pipepipe.shared.infoitem.helper.SearchFilterItem
import project.pipepipe.shared.infoitem.helper.SearchType
import project.pipepipe.shared.job.Payload
import project.pipepipe.shared.job.RequestMethod

class YouTubeService(id: String) : StreamingService(id)  {
    companion object {

    }

    override suspend fun getCookieExtractor(): CookieExtractor = CookieExtractor()

    override fun route(url: String): Extractor<*, *>? {
        return YouTubeUrlRouter.route(url)
    }

    val sortByFilterGroup = SearchFilterGroup(
        groupName = "sortby",
        onlyOneCheckable = true,
        availableSearchFilterItems = listOf(
            SearchFilterItem("sort_relevance", "sort_by=relevance"),
            SearchFilterItem("sort_rating", "sort_by=rating"),
            SearchFilterItem("sort_publish_time", "sort_by=date"),
            SearchFilterItem("sort_view", "sort_by=views"),
        ),
        defaultFilter = SearchFilterItem("sort_relevance", "sort_by=relevance")
    )

    val sortFilters = listOf(
        sortByFilterGroup,
        SearchFilterGroup(
            groupName = "upload_date",
            onlyOneCheckable = true,
            availableSearchFilterItems = listOf(
                SearchFilterItem("hour", "upload_date=hour"),
                SearchFilterItem("day", "upload_date=day"),
                SearchFilterItem("week", "upload_date=week"),
                SearchFilterItem("month", "upload_date=month"),
                SearchFilterItem("year", "upload_date=year"),
            )
        ),
        SearchFilterGroup(
            groupName = "duration",
            onlyOneCheckable = true,
            availableSearchFilterItems = listOf(
                SearchFilterItem("duration_short", "duration=duration_short"),
                SearchFilterItem("duration_medium", "duration=duration_medium"),
                SearchFilterItem("duration_long", "duration=duration_long"),
            )
        ),
        SearchFilterGroup(
            groupName = "features",
            onlyOneCheckable = false,
            availableSearchFilterItems = listOf(
                SearchFilterItem("HD", "is_hd=1"),
                SearchFilterItem("Subtitles", "subtitles=1"),
                SearchFilterItem("Ccommons", "ccommons=1"),
                SearchFilterItem("3d", "is_3d=1"),
                SearchFilterItem("Live", "live=1"),
                SearchFilterItem("Purchased", "purchased=1"),
                SearchFilterItem("4k", "is_4k=1"),
                SearchFilterItem("360°", "is_360=1"),
                SearchFilterItem("Location", "location=1"),
                SearchFilterItem("HDR", "is_hdr=1")
            )
        )

    )

    override val serviceInfo: SupportedServiceInfo
        get() = SupportedServiceInfo(
            serviceId = "YOUTUBE",
            suggestionPayload = Payload(
              RequestMethod.GET,
                GET_SUGGESTION_URL
            ),
            suggestionStringPath = Pair("/1", "/0"),
            suggestionJsonBetween = Pair("JP(", ")"),
            availableSearchTypes = listOf(
//                SearchType("all", "$SEARCH_RAW_URL?query=", sortFilters),
                SearchType("video", "$SEARCH_RAW_URL?type=video&query=", sortFilters),
                SearchType("channel", "$SEARCH_RAW_URL?type=channel&query=", listOf(sortByFilterGroup)),
                SearchType("playlist", "$SEARCH_RAW_URL?type=playlist&query=", listOf(sortByFilterGroup)),
                SearchType("movie", "$SEARCH_RAW_URL?type=movie&query=", sortFilters),
            ),
            trendingList = listOf(
                TrendingInfo("$TRENDING_RAW_URL?name=recommended_lives", "YOUTUBE", "recommended_lives"),
            ),
            themeColor = "#e53935",
            urlPatterns = mapOf(
                ExternalUrlType.STREAM to listOf(
                    "youtube\\.com/watch",
                    "youtu\\.be/",
                    "youtube\\.com/(embed|live|shorts|v|w)/",
                    "youtube-nocookie\\.com/embed/",
                    "hooktube\\.com",
                    "invidio\\.us",
                    "invidious\\.",
                    "yewtu\\.be",
                    "piped\\.",
                    "y2u\\.be"
                ),
                ExternalUrlType.CHANNEL to listOf(
                    "youtube\\.com/@",
                    "youtube\\.com/(user|channel|c)/"
                ),
                ExternalUrlType.PLAYLIST to listOf(
                    "youtube\\.com/playlist\\?list="
                )
            )
        )
}