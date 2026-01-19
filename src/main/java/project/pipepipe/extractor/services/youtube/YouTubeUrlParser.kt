package project.pipepipe.extractor.services.youtube

import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamIdParser
import java.util.Locale

object YouTubeUrlParser {
    val YOUTUBE_URLS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
    val INVIDIOUS_URLS = setOf(
        "invidio.us", "dev.invidio.us", "www.invidio.us", "redirect.invidious.io",
        "invidious.snopyta.org", "yewtu.be", "tube.connect.cafe", "tubus.eduvid.org",
        "invidious.kavin.rocks", "invidious.site", "invidious-us.kavin.rocks",
        "piped.kavin.rocks", "vid.mint.lgbt", "invidiou.site", "invidious.fdn.fr",
        "invidious.048596.xyz", "invidious.zee.li", "vid.puffyan.us", "ytprivate.com",
        "invidious.namazso.eu", "invidious.silkky.cloud", "ytb.trom.tf", "invidious.exonip.de",
        "inv.riverside.rocks", "invidious.blamefran.net", "y.com.cm", "invidious.moomoo.me",
        "yt.cyberhost.uk"
    )

    fun getHost(url: String): String {
        var host = url.lowercase(Locale.ROOT).substringAfter("://").substringBefore('/')
        return host.substringBefore(':')
    }

    /**
     * 获取路径，移除开头的 '/'
     */
    fun getPath(url: String): String {
        val withoutScheme = url.substringAfter("://")
        if (!withoutScheme.contains('/')) return ""
        return withoutScheme.substringAfter('/').substringBefore('?').substringBefore('#')
    }

    /**
     * 简单的查询参数获取工具
     */
    fun getParam(url: String, key: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
            .map { it.split('=') }
            .firstOrNull { it.size >= 2 && it[0] == key }
            ?.get(1)
    }

    fun isHTTP(url: String): Boolean {
        val low = url.lowercase(Locale.ROOT)
        return low.startsWith("http://") || low.startsWith("https://")
    }

    /**
     * 合并之前愚蠢的逻辑判断
     */
    fun isAnyYouTubeVariant(url: String): Boolean {
        if (!isHTTP(url)) return false
        val host = getHost(url)
        return YOUTUBE_URLS.contains(host) ||
                INVIDIOUS_URLS.contains(host) ||
                host == "youtu.be" ||
                host == "y2u.be" ||
                host == "hooktube.com" ||
                host == "www.youtube-nocookie.com"
    }

    fun parseStreamId(url: String): String? {
        return YouTubeStreamIdParser.getId(url)
    }
}