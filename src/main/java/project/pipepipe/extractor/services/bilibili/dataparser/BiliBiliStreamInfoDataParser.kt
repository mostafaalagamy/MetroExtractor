package project.pipepipe.extractor.services.bilibili.dataparser

import com.fasterxml.jackson.databind.JsonNode
import org.apache.commons.lang3.StringEscapeUtils
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks.CHANNEL_BASE_URL
import project.pipepipe.extractor.services.bilibili.BiliBiliUrlParser
import project.pipepipe.extractor.services.bilibili.Utils
import project.pipepipe.extractor.utils.getDurationFromString
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireInt
import project.pipepipe.extractor.utils.json.requireLong
import project.pipepipe.extractor.utils.json.requireObject
import project.pipepipe.extractor.utils.json.requireString

object BiliBiliStreamInfoDataParser {
    fun parseFromStreamInfoJson(item: JsonNode): StreamInfo {
        return StreamInfo(
            url = BiliBiliUrlParser.urlFromStreamID(BiliBiliUrlParser.parseStreamId(item.requireString("arcurl"))!!),
            name = StringEscapeUtils.unescapeHtml4(
                item.requireString("title")
                    .replace("<em class=\"keyword\">", "")
                    .replace("</em>", "")
            ),
            serviceId = 5,
            thumbnailUrl = "https:" + item.requireString("pic"),
            duration = getDurationFromString(item.requireString("duration")),
            viewCount = item.requireLong("play"),
            uploaderName = item.requireString("author"),
            uploaderUrl = CHANNEL_BASE_URL + item.requireString("mid"),
            uploaderAvatarUrl = item.requireString("upic").replace("http:", "https:"),
            uploadDate = item.requireLong("pubdate") * 1000,
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }

    fun parseFromTrendingInfoJson(item: JsonNode): StreamInfo {
        return StreamInfo(
            url = BiliBiliLinks.VIDEO_BASE_URL + item.requireString("bvid") + "?p=1",
            name = item.requireString("title"),
            serviceId = 5,
            thumbnailUrl = item.requireString("pic").replace("http:", "https:"),
            duration = item.requireLong("duration"),
            viewCount = item.requireObject("stat").requireLong("view"),
            uploaderName = item.requireObject("owner").requireString("name"),
            uploaderUrl = CHANNEL_BASE_URL + item.requireString("/owner/mid"),
            uploaderAvatarUrl = item.requireObject("owner").requireString("face"),
            uploadDate = item.requireLong("pubdate") * 1000,
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }

    fun parseFromRelatedInfoJson(item: JsonNode): StreamInfo {
        val actualId = try {
            item.requireString("bvid")
        } catch (e: Exception) {
            Utils.av2bv(item.requireLong("aid"))
        }

        return StreamInfo(
            url = "${BiliBiliLinks.VIDEO_BASE_URL}$actualId?p=1",
            name = item.requireString("title"),
            serviceId = 5,
            thumbnailUrl = item.requireString("pic").replace("http", "https"),
            duration = item.requireLong("duration"),
            viewCount = item.requireObject("stat").requireLong("view"),
            uploaderName = item.requireObject("owner").requireString("name"),
            uploaderUrl = CHANNEL_BASE_URL + item.requireString("/owner/mid"),
            uploaderAvatarUrl = item.requireObject("owner").requireString("face").replace("http", "https"),
            uploadDate = item.requireLong("pubdate") * 1000,
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }

    fun parseFromPartitionRelatedInfoJson(
        item: JsonNode,
        bvid: String,
        thumbnailUrl: String? = null,
        uploaderName: String? = null,
        uploaderUrl: String? = null,
        uploaderAvatarUrl: String? = null
    ): StreamInfo {
        return StreamInfo(
            url = "${BiliBiliLinks.VIDEO_BASE_URL}$bvid?p=${item.requireInt("page")}",
            name = item.requireString("part"),
            serviceId = 5,
            thumbnailUrl = thumbnailUrl,
            duration = item.requireLong("duration"),
            uploaderName = uploaderName,
            uploaderUrl = uploaderUrl,
            uploaderAvatarUrl = uploaderAvatarUrl,
            uploadDate = item.requireLong("ctime") * 1000,
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }

    fun parseFromPartitionInfoJson(
        item: JsonNode,
        id: String,
        p: Int = 1,
    ): StreamInfo {
        return StreamInfo(
            url = "${BiliBiliLinks.VIDEO_BASE_URL}$id?p=$p",
            name = "P$p ${item.requireString("part")}",
            serviceId = 5,
            duration = item.requireLong("duration"),
            viewCount = null,
            uploadDate = item.requireLong("ctime") * 1000,
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }


    fun parseFromRecommendLiveInfoJson(data: JsonNode): StreamInfo {
        val thumbnailUrl = try {
            data.requireString("user_cover")
        } catch (e: Exception) {
            data.requireString("system_cover")
        }
        return StreamInfo(
            url = "https://${BiliBiliLinks.LIVE_BASE_URL}/${data.requireLong("roomid")}",
            name = data.requireString("title"),
            serviceId = 5,
            thumbnailUrl = thumbnailUrl.replace("http:", "https:"),
            isLive = true,
            viewCount = data.requireObject("watched_show").requireLong("num"),
            uploaderName = data.requireString("uname"),
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }

    fun parseFromPremiumContentJson(data: JsonNode):  StreamInfo {
        val getPubtime = {
            try {
                data.requireLong("pubtime") / 1000
            } catch (e: Exception) {
                data.requireLong("pub_time")
            }
        }

        val url = try {
            data.requireString("url")
        } catch (e: Exception) {
            data.requireString("share_url")
        }

        return StreamInfo(
            url = url,
            name = try {
                data.requireString("share_copy")
            } catch (e: Exception) {
                data.requireString("title")
                    .replace("<em class=\"keyword\">", "")
                    .replace("</em>", "")
            },
            serviceId = 5,
            thumbnailUrl = data.requireString("cover").replace("http:", "https:"),
            duration = data.requireLong("duration") / 1000,
            uploaderName = data.requireString("org_title")
                .replace("<em class=\"keyword\">", "")
                .replace("</em>", ""),
            uploadDate = getPubtime(),
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }

    fun parseFromLiveInfoJson(item: JsonNode, type: Int): StreamInfo {
        val name = if (item.requireInt("live_status") == 2) {
            item.requireString("uname") + "的投稿视频轮播"
        } else {
            item.requireString("title")
                .replace("<em class=\"keyword\">", "")
                .replace("</em>", "")
        }

        val roomIdField = if (type == 0) "roomid" else "room_id"
        val url = "https://live.bilibili.com/" + item.requireLong(roomIdField)

        return StreamInfo(
            url = url,
            name = name,
            serviceId = 5,
            thumbnailUrl = if (type == 1) {
                item.requireString("cover_from_user")
            } else {
                "https:" + item.requireString("user_cover")
            },
            isLive = true,
            duration = -1,
            viewCount = item.requireLong("online"),
            uploaderName = item.requireString("uname"),
            uploaderUrl = CHANNEL_BASE_URL + item.requireString("uid"),
            uploaderAvatarUrl = if (type == 1) {
                item.requireString("face")
            } else {
                "https:" + item.requireString("uface")
            },
            uploadDate = null,
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }

    fun parseFromClientChannelInfoResponseJson(
        item: JsonNode,
        uploaderName: String,
        uploaderAvatarUrl: String?
    ): () -> StreamInfo = {
        val bvid = item.requireString("bvid")

        StreamInfo(
            url = "${BiliBiliLinks.VIDEO_BASE_URL}$bvid?p=1",
            name = item.requireString("title"),
            serviceId = 5,
            thumbnailUrl = item.requireString("cover").replace("http:", "https:"),
            duration = try {
                item.requireLong("duration")
            } catch (e: Exception) {
                getDurationFromString(item.requireString("length"))
            },
            viewCount = try {
                item.requireLong("play")
            } catch (e: Exception) {
                item.requireObject("stat").requireLong("view")
            },
            uploaderName = uploaderName,
            uploaderAvatarUrl = uploaderAvatarUrl,
            uploadDate = item.requireLong("ctime"),
            isPaid = item.requireArray("badges").toString().contains("充电专属"),
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }

    fun parseFromWebChannelInfoResponseJson(item: JsonNode,
                                            overrideChannelName: String? = null,
                                            overrideChannelId: String? = null): StreamInfo {
        return StreamInfo(
            url = BiliBiliLinks.VIDEO_BASE_URL + item.requireString("bvid") + "?p=1",
            name = item.requireString("title"),
            serviceId = 5,
            thumbnailUrl = item.requireString("pic").replace("http:", "https:"),
            duration = try {
                item.requireLong("duration")
            } catch (e: Exception) {
                getDurationFromString(item.requireString("length"))
            },
            viewCount = try {
                item.requireLong("play")
            } catch (e: Exception) {
                item.requireObject("stat").requireLong("view")
            },
            uploaderName = runCatching{ item.requireString("author") }.getOrDefault(overrideChannelName),
            uploaderUrl = CHANNEL_BASE_URL + runCatching{ item.requireString("mid") }.getOrDefault(overrideChannelId),
            uploadDate = if (!item.has("created")) item.requireLong("pubdate") * 1000 else item.requireLong("created") * 1000,
            isPaid = runCatching{ item.requireInt("elec_arc_type") == 1 }.getOrElse { item.requireInt("ugc_pay") == 1 },
            headers = hashMapOf("Referer" to "https://www.bilibili.com")
        )
    }
}
