package project.pipepipe.extractor.services.youtube.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.services.youtube.YouTubeLinks.CHANNEL_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.COMMENT_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeLinks.REPLY_RAW_URL
import project.pipepipe.extractor.utils.TimeAgoParser
import project.pipepipe.extractor.utils.parseNumberWithSuffix
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.shared.utils.json.requireBoolean
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString

object YouTubeCommentInfoDataParser {
    fun parseFromCommentData(data: JsonNode, commentViewModel: JsonNode? = null): CommentInfo {
        val replyUrl = runCatching{
            "$REPLY_RAW_URL?continuation=${commentViewModel!!.requireString("/replies/commentRepliesRenderer/contents/0/continuationItemRenderer/continuationEndpoint/continuationCommand/token")}"
        }.getOrNull()
        return CommentInfo(
            content = data.requireString("/payload/commentEntityPayload/properties/content/content"),
            authorName = data.requireString("/payload/commentEntityPayload/author/displayName"),
            authorAvatarUrl = data.requireString("/payload/commentEntityPayload/author/avatarThumbnailUrl"),
            authorUrl = "$CHANNEL_URL${data.requireString("/payload/commentEntityPayload/author/channelCommand/innertubeCommand/browseEndpoint/browseId")}",
            authorVerified = data.requireBoolean("/payload/commentEntityPayload/author/isVerified"),
            uploadDate = TimeAgoParser.parseToTimestamp(data.requireString("/payload/commentEntityPayload/properties/publishedTime")),
            likeCount = parseNumberWithSuffix(data.requireString("/payload/commentEntityPayload/toolbar/likeCountLiked")).toInt(),
            isHeartedByUploader = runCatching{ data.requireString("/payload/engagementToolbarStateEntityPayload/heartState") == "TOOLBAR_HEART_STATE_HEARTED" }.getOrNull(),
            isPinned = runCatching{ commentViewModel?.requireObject("/commentViewModel/commentViewModel") }.getOrNull()?.has("pinnedText"),
            replyCount = runCatching{ parseNumberWithSuffix(data.requireString("/payload/commentEntityPayload/toolbar/replyCount")).toInt()}.getOrNull(),
            replyInfo = if (replyUrl != null) {
                CommentInfo(url = replyUrl, serviceId = 0)} else null,
            images = null,
            serviceId = 0,
            url = "$COMMENT_RAW_URL?id=${data.requireString("entityKey")}"
        )
    }
}