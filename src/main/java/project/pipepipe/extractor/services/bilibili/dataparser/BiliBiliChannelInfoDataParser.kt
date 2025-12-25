package project.pipepipe.extractor.services.bilibili.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.shared.utils.json.requireLong
import project.pipepipe.shared.utils.json.requireString
import project.pipepipe.shared.infoitem.ChannelInfo

object BiliBiliChannelInfoDataParser {
    fun parseFromChannelSearchJson(data: JsonNode): ChannelInfo {
        return ChannelInfo(
            url = BiliBiliLinks.CHANNEL_BASE_URL + data.requireLong("mid"),
            name = data.requireString("uname"),
            serviceId = 5,
            thumbnailUrl = "https:" + data.requireString("upic"),
            description = data.requireString("usign"),
            subscriberCount = data.requireLong("fans"),
        )
    }
}
