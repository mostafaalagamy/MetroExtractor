package project.pipepipe.extractor.services.bilibili.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString

enum class BiliBiliPlaylistType {
    SEASON,
    SERIES
}

object BiliBiliPlaylistInfoDataParser {
    fun parseFromPlaylistInfoJson(itemObject: JsonNode, type: BiliBiliPlaylistType,
                                  uploaderName: String?, uploaderUrl: String?, uploadAvatarUrl: String?): PlaylistInfo {
        val metaObject = itemObject.requireObject("meta")

        val url = when (type) {
            BiliBiliPlaylistType.SEASON -> String.format(
                BiliBiliLinks.GET_SEASON_ARCHIVES_LIST_RAW_URL,
                metaObject.requireLong("mid"),
                metaObject.requireLong("season_id"),
                metaObject.requireString("name")
            )
            BiliBiliPlaylistType.SERIES -> String.format(
                BiliBiliLinks.GET_SERIES_RAW_URL,
                metaObject.requireLong("mid"),
                metaObject.requireLong("series_id"),
                metaObject.requireString("name")
            )
        }

        return PlaylistInfo(
            url = url,
            name = metaObject.requireString("name"),
            serviceId = 5,
            thumbnailUrl = metaObject.requireString("cover").replace("http://", "https://"),
            uploaderName = uploaderName,
            uploaderUrl = uploaderUrl,
            uploaderAvatarUrl = uploadAvatarUrl,
            streamCount = metaObject.requireLong("total")
        )
    }
}
