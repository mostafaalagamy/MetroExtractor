package project.pipepipe.extractor.services.soundcloud

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.base.CookieExtractor
import project.pipepipe.extractor.services.soundcloud.extractor.SoundCloudCookieExtractor
import project.pipepipe.shared.infoitem.ExternalUrlType
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.infoitem.TrendingInfo
import project.pipepipe.shared.infoitem.helper.SearchFilterGroup
import project.pipepipe.shared.infoitem.helper.SearchFilterItem
import project.pipepipe.shared.infoitem.helper.SearchType
import project.pipepipe.extractor.StreamingService

class SoundCloudService(id: Int) : StreamingService(id) {
    companion object {
        val DEFAULT_HEADER = hashMapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
    }
    override suspend fun getCookieExtractor(): CookieExtractor = SoundCloudCookieExtractor()

    override fun route(url: String): Extractor<*, *>? {
        return SoundCloudUrlRouter.route(url)
    }

    private val sortByFilterGroup = SearchFilterGroup(
        groupName = "sortby",
        onlyOneCheckable = true,
        availableSearchFilterItems = listOf(
            SearchFilterItem("all", ""),
            SearchFilterItem("Past hour", "filter.created_at=last_hour"),
            SearchFilterItem("Past day", "filter.created_at=last_day"),
            SearchFilterItem("Past week", "filter.created_at=last_week"),
            SearchFilterItem("Past month", "filter.created_at=last_month"),
            SearchFilterItem("Past year", "filter.created_at=last_year"),
        ),
        defaultFilter = SearchFilterItem("all", "")
    )

    private val durationFilterGroup = SearchFilterGroup(
        groupName = "duration",
        onlyOneCheckable = true,
        availableSearchFilterItems = listOf(
            SearchFilterItem("all", ""),
            SearchFilterItem("< 2 min", "filter.duration=short"),
            SearchFilterItem("2-10 min", "filter.duration=medium"),
            SearchFilterItem("10-30 min", "filter.duration=long"),
            SearchFilterItem("> 30 min", "filter.duration=epic"),
        )
    )

    private val sortFilters = listOf(
        sortByFilterGroup,
        durationFilterGroup
    )

    override val serviceInfo: SupportedServiceInfo
        get() = SupportedServiceInfo(
            serviceId = 1,
            serviceName = "SoundCloud (Experimental)",
            availableSearchTypes = listOf(
//                SearchType("all", "search://soundcloud.raw?", sortFilters),
                SearchType("tracks", "search://soundcloud.raw?type=tracks&query="),
                SearchType("users", "search://soundcloud.raw?type=users&query="),
                SearchType("playlists", "search://soundcloud.raw?type=playlists&query="),
            ),
//            trendingList = listOf(
//                TrendingInfo("trending://soundcloud.raw?name=new", 1, "New & hot"),
//            ),
            themeColor = "#FF5500",
            urlPatterns = mapOf(
                ExternalUrlType.STREAM to listOf(
                    "https?://(?:www\\.|m\\.)?soundcloud\\.com/[0-9a-z_-]+/(?!(?:tracks|albums|sets|reposts|followers|following)/?$)[0-9a-z_-]+"
                ),
                ExternalUrlType.CHANNEL to listOf(
                    "https?://(?:www\\.|m\\.)?soundcloud\\.com/[0-9a-z_-]+"
                ),
                ExternalUrlType.PLAYLIST to listOf(
                    "https?://(?:www\\.|m\\.)?soundcloud\\.com/[0-9a-z_-]+/sets/[0-9a-z_-]+"
                )
            )
        )
}
