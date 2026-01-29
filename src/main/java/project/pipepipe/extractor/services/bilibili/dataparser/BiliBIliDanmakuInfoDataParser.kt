package project.pipepipe.extractor.services.bilibili.dataparser

import com.fasterxml.jackson.databind.JsonNode
import org.jsoup.nodes.Element
import project.pipepipe.extractor.ExtractorContext
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireInt
import project.pipepipe.extractor.utils.json.requireString
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

object BiliBIliDanmakuInfoDataParser {
//    fun parseFromSuperChatJson(message: JsonNode, startTime: Long): DanmakuInfo {
//        val data = message.requireObject("data")
//        val danmaku = DanmakuInfo()
//
//        danmaku.text = "(¥${data.requireInt("price")}) ${data.requireString("message")}"
//        danmaku.argbColor = 0xFF000000.toInt() + data.requireString("background_bottom_color").split("#")[1].toInt(16)
////        danmaku.position = DanmakuInfo.Position.SUPERCHAT
//        danmaku.relativeFontSize = 0.64
//                danmaku.timestamp = (data.requireLong("start_time") - startTime).seconds
//        danmaku.isLive = true
//
//        return danmaku
//    }

    fun parseFromLiveDanmakuJson(message: JsonNode, startTime: Long): DanmakuInfo {
        val data = message.requireArray("info")
        val danmaku = DanmakuInfo(data.requireString(1))

        danmaku.argbColor = 0xFF000000.toInt() + data.requireArray(0).requireInt(3)
        danmaku.position = when (data.requireArray(0).requireInt(1)) {
            1 -> DanmakuInfo.Position.REGULAR
            4 -> DanmakuInfo.Position.BOTTOM
            else -> DanmakuInfo.Position.TOP
        }
        danmaku.relativeFontSize = 0.64
                danmaku.timestamp = ZERO
        danmaku.isLive = true

        return danmaku
    }

    fun parseFromDanmakuElement(element: Element): DanmakuInfo {
        val attr = element.attr("p").split(",")
        val text = element.text()
        val actualContent = runCatching {
            // Try to parse as JSON array first
            val jsonArray = ExtractorContext.objectMapper.readTree(text)
            if (jsonArray.isArray && jsonArray.size() > 4) {
                jsonArray.requireString(4)
            } else {
                text
            }
        }.getOrDefault(text)
        val danmaku = DanmakuInfo(actualContent)
        danmaku.argbColor = attr[3].toInt() + 0xFF000000.toInt()
        danmaku.position = when (attr[1]) {
            "4" -> DanmakuInfo.Position.BOTTOM
            "5" -> DanmakuInfo.Position.TOP
            else -> DanmakuInfo.Position.REGULAR
        }
        danmaku.relativeFontSize = when (attr[2]) {
            "18" -> 0.5
            "36" -> 0.7
            else -> 0.6 // "25" and default
        }
                danmaku.timestamp = ((attr[0].toDouble() * 1000).toLong() + 2500).milliseconds // 2500 for sync

        return danmaku
    }
}
