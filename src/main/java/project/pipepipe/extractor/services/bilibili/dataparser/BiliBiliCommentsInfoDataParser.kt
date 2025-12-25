package project.pipepipe.extractor.services.bilibili.dataparser

import com.fasterxml.jackson.databind.JsonNode
import org.apache.commons.lang3.StringEscapeUtils
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireBoolean
import project.pipepipe.shared.utils.json.requireInt
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString

object BiliBiliCommentsInfoDataParser {
    fun parseFromCommentJson(data: JsonNode): () -> CommentInfo = {
        val content = data.requireObject("content")

        val getCommentText = {
            // Parse comment text with potential URL
            var commentText = StringEscapeUtils.unescapeHtml4(content.requireString("message"))
            try {
                if (commentText.endsWith("...") && content.has("jump_url")) {
                    val jumpUrl = content.requireObject("jump_url")
                    val httpsUrls = jumpUrl.fieldNames().asSequence()
                        .filter { it.startsWith("https://") }
                        .toList()
                    if (httpsUrls.isNotEmpty()) {
                        commentText += "\n\n" + httpsUrls.first()
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
            commentText
        }

        val getImage = {
            runCatching {
                val pictureArray = content.requireArray("pictures")
                val pictures = pictureArray.map { it.requireString("img_src") }
                pictures
            }
        }

        val getReplyMetaInfo = {
            runCatching {
                val hasReplies = data.requireArray("replies").size() > 0 &&
                        !(data.requireLong("root") == data.requireLong("parent") &&
                                data.requireLong("root") == data.requireLong("rpid"))

                if (hasReplies) {
                    "${BiliBiliLinks.COMMENT_REPLIES_URL}${data.requireLong("oid")}&pn=1&root=${data.requireLong("rpid")}"
                } else null
            }
        }

        CommentInfo(
            url = "${BiliBiliLinks.COMMENT_REPLIES_URL}${data.requireLong("oid")}&pn=1&root=${data.requireLong("rpid")}",
            content = getCommentText(),
            authorName = data.requireObject("member").requireString("uname"),
            authorAvatarUrl = data.requireObject("member").requireString("avatar").replace("http:", "https:"),
            authorUrl = "${BiliBiliLinks.CHANNEL_BASE_URL}${data.get("mid")}",
            uploadDate = data.requireLong("ctime") * 1000,
            likeCount = data.requireInt("like"),
            replyCount = data.requireInt("rcount"),
            replyInfo = getReplyMetaInfo().getOrNull()?.let { CommentInfo(it, 5) },
            isHeartedByUploader = data.requireObject("up_action").requireBoolean("like"),
            isPinned = runCatching { data.requireBoolean("isTop") }.getOrNull(),
            images = getImage().getOrNull(),
            authorVerified = null,
            serviceId = 5
        )
    }
}
