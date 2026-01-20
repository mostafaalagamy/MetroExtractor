package project.pipepipe.extractor.services.niconico.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireString
import kotlin.time.Duration.Companion.milliseconds

object NiconicoDanmakuInfoDataParser {
    private val colorMap = mapOf(
        "white" to 0xFFFFFF,
        "red" to 0xFF0000,
        "pink" to 0xFF8080,
        "orange" to 0xFFC000,
        "yellow" to 0xFFFF00,
        "green" to 0x00FF00,
        "cyan" to 0x00FFFF,
        "blue" to 0x0000FF,
        "purple" to 0xC000FF,
        "black" to 0x000000,
        "white2" to 0xCCCCCC,
        "niconicoWhite" to 0xCCCC99,
        "red2" to 0xCC0033,
        "truered" to 0xCC0033,
        "pink2" to 0xFF33CC,
        "orange2" to 0xFF6600,
        "passionorange" to 0xFF7F00,
        "yellow2" to 0x999900,
        "madyellow" to 0x999900,
        "green2" to 0x00CC66,
        "elementalgreen" to 0x00CC66,
        "cyan2" to 0x00CCCC,
        "blue2" to 0x3399FF,
        "marineblue" to 0x3399FF,
        "purple2" to 0x6633FF,
        "nobleviolet" to 0x6633FF,
        "black2" to 0x666666,
    )

    private val positionMap = mapOf(
        "top" to DanmakuInfo.Position.TOP,
        "bottom" to DanmakuInfo.Position.BOTTOM,
        "ue" to DanmakuInfo.Position.TOP,
        "shita" to DanmakuInfo.Position.BOTTOM,
    )

    private val sizeMap = mapOf(
        "small" to 0.5,
        "big" to 0.7,
    )

    fun parseFromCommentJson(json: JsonNode, startAt: Long? = null, isLive: Boolean = false): DanmakuInfo {
        val mailStyles = runCatching {
            json.requireString("mail").split(" ")
        }.getOrElse {
            json.requireArray("commands")
        }
        val text = json.takeIf { it.has("content") }?.requireString("content")
            ?: json.requireString("body").let { if (it.startsWith("/emotion ")) it.substring(9) else it }
        val argbColor = mailStyles.firstNotNullOfOrNull { colorMap[it] }?.plus(0xFF000000)?.toInt() ?: 0xFFFFFFFF.toInt()
        val position = mailStyles.firstNotNullOfOrNull { positionMap[it] } ?: DanmakuInfo.Position.REGULAR
        val relativeFontSize = mailStyles.firstNotNullOfOrNull { sizeMap[it] } ?: 0.7
        val timestamp = if (isLive) {
            (System.currentTimeMillis() - startAt!!).milliseconds
        } else {
            (runCatching { json.requireLong("vpos") * 10 }.getOrNull()
                ?: runCatching { json.requireLong("vposMs") }.getOrNull())!!.milliseconds
        }

        return DanmakuInfo(text).apply {
            this.argbColor = argbColor
            this.position = position
            this.relativeFontSize = relativeFontSize
            this.timestamp = timestamp
            this.isLive = isLive
        }
    }
}
