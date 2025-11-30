package project.pipepipe.extractor.services.youtube

import project.pipepipe.extractor.utils.generateRandomString

object YouTubeLinks {
    const val VIDEO_INFO_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false&\$fields=microformat,playabilityStatus,storyboards,videoDetails"
    fun getAndroidFetchStreamUrl(id: String) = "https://youtubei.googleapis.com/youtubei/v1/reel/reel_item_watch?prettyPrint=false&t=${generateRandomString(12)}&id=$id&\$fields=playerResponse"
    const val NEXT_URL = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false"
    const val SEARCH_RAW_URL = "search://youtube.raw"
    const val TAB_RAW_URL = "tab://youtube.raw"
    const val TRENDING_RAW_URL = "trending://youtube.raw"
    const val COMMENT_RAW_URL = "comment://youtube.raw"
    const val REPLY_RAW_URL = "reply://youtube.raw"
    const val SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    const val GET_SUGGESTION_URL = "https://suggestqueries.google.com/complete/search?client=youtube&jsonp=JP&ds=yt&hl=en&gl=us&q="
    const val STREAM_URL = "https://www.youtube.com/watch?v="
    const val BASE_URL = "https://www.youtube.com"
    const val CHANNEL_URL = "https://www.youtube.com/channel/"
    const val SHORTS_URL = "https://www.youtube.com/shorts/"
    const val PLAYLIST_BASE_URL = "https://www.youtube.com/playlist?list="
    const val BROWSE_URL = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
    const val RESOLVE_CHANNEL_ID_URL = "https://www.youtube.com/youtubei/v1/navigation/resolve_url?prettyPrint=false"
}