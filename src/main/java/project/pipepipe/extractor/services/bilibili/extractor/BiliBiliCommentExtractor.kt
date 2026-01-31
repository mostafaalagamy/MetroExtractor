package project.pipepipe.extractor.services.bilibili.extractor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.commons.lang3.StringEscapeUtils
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.Utils
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliCommentsInfoDataParser
import project.pipepipe.shared.state.State
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.utils.incrementUrlParam
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireBoolean
import project.pipepipe.extractor.utils.json.requireInt
import project.pipepipe.extractor.utils.json.requireObject
import project.pipepipe.extractor.utils.json.requireString
import kotlin.math.ceil

class BiliBiliCommentExtractor(url: String) : Extractor<Nothing, CommentInfo>(url) {
    override suspend fun fetchFirstPage(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(
                    payload = Payload(
                        RequestMethod.GET, url, BilibiliService.getUserAgentHeaders(BiliBiliLinks.WWW_REFERER)
                    )
                )
            ), state = PlainState(1))
        } else {
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson().requireObject("data")
            runCatching {
                data.requireArray("top_replies").forEach { comment ->
                    (comment as ObjectNode).put("isTop", true)
                    commit<CommentInfo>(BiliBiliCommentsInfoDataParser.parseFromCommentJson(comment))
                }
            }
            val replies = data.requireArray("replies")
            replies.forEach { reply ->
                commit<CommentInfo>(BiliBiliCommentsInfoDataParser.parseFromCommentJson(reply))
            }
            return JobStepResult.CompleteWith(ExtractResult(null, errors, PagedData(itemList, getNextPageUrl(data, replies, cookie!!))))
        }
    }

    override suspend fun fetchGivenPage(
        url: String,
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(
                    payload = Payload(
                        RequestMethod.GET, url, BilibiliService.getUserAgentHeaders(BiliBiliLinks.WWW_REFERER)
                    )
                )
            ), state = PlainState(1))
        } else {
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson().requireObject("data")
            val replies = data.requireArray("replies")
            replies.forEach { reply ->
                commit<CommentInfo>(BiliBiliCommentsInfoDataParser.parseFromCommentJson(reply))
            }
            return JobStepResult.CompleteWith(ExtractResult(null, errors, PagedData(itemList, getNextPageUrl(data, replies, cookie!!))))
        }
    }

    private fun isRepliesOfComment(requestUrl: String): Boolean {
        return !requestUrl.contains(BiliBiliLinks.FETCH_COMMENTS_URL)
    }

    private fun buildNextPageParam(url: String, offset: String): LinkedHashMap<String, String> {
        return linkedMapOf(
            "oid" to url.split("oid=")[1].split("&")[0],
            "type" to "1",
            "mode" to "3",
            "pagination_str" to "{\"offset\":\"${StringEscapeUtils.escapeJson(offset)}\"}",
            "plat" to "1",
            "web_location" to "1315875"
        )
    }

    private fun getNextPageUrl(data: JsonNode, replies: JsonNode, cookie: String) = if (!isRepliesOfComment(url)) {
        val cursor = data.requireObject("cursor")
        if (!cursor.requireBoolean("is_end")) {
            val currentOffset = cursor.requireObject("pagination_reply").requireString("next_offset")
            Utils.getWbiResult(BiliBiliLinks.FETCH_COMMENTS_URL, buildNextPageParam(url, currentOffset), cookie)
        } else { null }
    } else {
        val pn = runCatching{ data.requireInt("/page/num") }.getOrDefault(1)
        val hasNext = runCatching{
            ceil(
                data.requireInt("/page/count").toDouble() / data.requireInt("/page/size")
            ).toInt() != pn
        }.getOrDefault(false)
        if(hasNext) {
            url.incrementUrlParam("pn")
        } else { null }
    }
}