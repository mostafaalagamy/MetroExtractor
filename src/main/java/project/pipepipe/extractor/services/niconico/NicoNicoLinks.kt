package project.pipepipe.extractor.services.niconico

object NicoNicoLinks {
    const val WATCH_URL = "https://www.nicovideo.jp/watch/"
    const val MOBILE_WATCH_URL = "https://sp.nicovideo.jp/watch/"
    const val TRENDING_URL = "https://www.nicovideo.jp/ranking/genre"
    const val TRENDING_RAW_URL = "trending://niconico.raw"
    const val MYLIST_URL = "https://nvapi.nicovideo.jp/v2/mylists/"
    const val USER_URL = "https://www.nicovideo.jp/user/"
    const val MOBILE_USER_URL = "https://sp.nicovideo.jp/user/"
    const val CHANNEL_URL = "https://ch.nicovideo.jp/"
    const val SUGGESTION_URL = "https://sug.search.nicovideo.jp/suggestion/expand/"
    const val SEARCH_URL = "https://www.nicovideo.jp/search/"
    const val PLAYLIST_SEARCH_API_URL = "https://nvapi.nicovideo.jp/v1/search/list?_frontendId=6&_frontendVersion=0&types=mylist" //todo: series and users
    const val RELATED_VIDEO_URL = "https://nvapi.nicovideo.jp/v1/recommend?recipeId=video_watch_recommendation&site=nicovideo&_frontendId=6&_frontendVersion=0&videoId="
    const val SERIES_URL = "https://www.nicovideo.jp/series/"
    const val MOBILE_SERIES_URL = "https://sp.nicovideo.jp/series/"
    const val USER_MYLIST_BASE_URL = "https://www.nicovideo.jp/user/"
    const val TAB_RAW_URL = "tab://niconico.raw"
    fun getAccessUrl(id: String, trackId: String) = "https://nvapi.nicovideo.jp/v1/watch/${id}/access-rights/hls?actionTrackId=$trackId"
    fun getUserMylistUrl(userId: String, mylistId: String) = "${USER_MYLIST_BASE_URL}${userId}/mylist/${mylistId}"
}