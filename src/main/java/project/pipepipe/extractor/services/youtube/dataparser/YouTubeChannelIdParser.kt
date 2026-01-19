package project.pipepipe.extractor.services.youtube.dataparser

import project.pipepipe.extractor.services.youtube.YouTubeUrlParser
import java.util.regex.Pattern

object YouTubeChannelIdParser {
    private val EXCLUDED_SEGMENTS = Pattern.compile(
        "playlist|watch|attribution_link|watch_popup|embed|feed|select_site|account|reporthistory|redirect"
    )

    fun parseChannelId(url: String): String? {
        // 调用封装好的统一判断逻辑
        if (!YouTubeUrlParser.isAnyYouTubeVariant(url)) return null

        val path = YouTubeUrlParser.getPath(url)
        val splitPath = if (path.isEmpty()) emptyList() else path.split('/')

        // 处理 Handle (@username)
        if (splitPath.isNotEmpty() && splitPath[0].startsWith("@")) {
            return splitPath[0]
        }

        // 处理短链接 c/ 格式
        val effectiveSplit = if (isCustomShortChannelUrl(splitPath)) {
            listOf("c", splitPath[0])
        } else {
            splitPath
        }

        if (effectiveSplit.size < 2) return null

        val prefix = effectiveSplit[0]
        val id = effectiveSplit[1]

        return when (prefix) {
            "user", "channel", "c" -> if (id.isNotBlank()) id else null
            else -> null
        }
    }

    private fun isCustomShortChannelUrl(segments: List<String>): Boolean {
        return segments.size == 1 &&
                segments[0].isNotEmpty() &&
                !EXCLUDED_SEGMENTS.matcher(segments[0]).matches()
    }
}