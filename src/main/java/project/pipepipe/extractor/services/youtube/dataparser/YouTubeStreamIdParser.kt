package project.pipepipe.extractor.services.youtube.dataparser

import project.pipepipe.extractor.services.youtube.YouTubeUrlParser
import java.util.Locale
import java.util.regex.Pattern

object YouTubeStreamIdParser {
    val YOUTUBE_VIDEO_ID_REGEX_PATTERN = Pattern.compile("^([a-zA-Z0-9_-]{11})")
    val SUBPATHS = listOf("embed/", "live/", "shorts/", "watch/", "v/", "w/")

    fun extractId(id: String?): String? {
        if (id == null) return null
        val matcher = YOUTUBE_VIDEO_ID_REGEX_PATTERN.matcher(id)
        return if (matcher.find()) matcher.group(1) else null
    }

    fun getId(url: String): String? {
        var currentUrl = url

        // 1. 处理自定义 Schema
        val lowUrl = url.lowercase(Locale.ROOT)
        if (lowUrl.startsWith("vnd.youtube")) {
            val part = url.substringAfter(":")
            if (part.startsWith("//")) {
                val extracted = extractId(part.substring(2))
                if (extracted != null) return extracted
                currentUrl = "https:$part"
            } else {
                return extractId(part)
            }
        }

        // 2. 统一验证 (内聚原有愚蠢逻辑)
        if (!YouTubeUrlParser.isAnyYouTubeVariant(currentUrl)) return null

        val host = YouTubeUrlParser.getHost(currentUrl)
        val path = YouTubeUrlParser.getPath(currentUrl)

        // 3. 根据 Host 分类解析
        return when (host) {
            "www.youtube-nocookie.com" -> {
                if (path.startsWith("embed/")) extractId(path.substring(6)) else null
            }

            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> {
                if (path == "attribution_link") {
                    val u = YouTubeUrlParser.getParam(currentUrl, "u")
                    if (u != null) {
                        // 递归调用处理内部 URL
                        extractId(YouTubeUrlParser.getParam("https://www.youtube.com$u", "v"))
                    } else null
                } else {
                    getIdFromSubpathsInPath(path) ?: extractId(YouTubeUrlParser.getParam(currentUrl, "v"))
                }
            }

            "y2u.be", "youtu.be" -> {
                // youtu.be/xxxx?v=yyyy 这种混杂情况优先取 v 参数，没有则取 path
                YouTubeUrlParser.getParam(currentUrl, "v")?.let { extractId(it) } ?: extractId(path)
            }

            else -> { // Invidious & Hooktube
                if (path == "watch") {
                    extractId(YouTubeUrlParser.getParam(currentUrl, "v"))
                } else {
                    getIdFromSubpathsInPath(path) ?:
                    extractId(YouTubeUrlParser.getParam(currentUrl, "v")) ?: extractId(path)
                }
            }
        }
    }

    private fun getIdFromSubpathsInPath(path: String): String? {
        for (subpath in SUBPATHS) {
            if (path.startsWith(subpath)) {
                return extractId(path.substring(subpath.length))
            }
        }
        return null
    }
}