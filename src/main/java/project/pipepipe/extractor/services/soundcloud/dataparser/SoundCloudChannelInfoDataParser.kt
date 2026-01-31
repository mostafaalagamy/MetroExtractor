package project.pipepipe.extractor.services.soundcloud.dataparser

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.utils.json.requireLong
import project.pipepipe.extractor.utils.json.requireString
import project.pipepipe.extractor.utils.json.requireBoolean
import project.pipepipe.shared.infoitem.ChannelInfo

object SoundCloudChannelInfoDataParser {
    fun parseFromChannelObject(data: JsonNode, userId: String? = null): ChannelInfo {
        return ChannelInfo(
            url = data.requireString("permalink_url").replace("http://", "https://").let { url ->
                if (userId != null) "$url?uid=${userId}" else url
            },
            name = data.requireString("username"),
            serviceId = 1,
            thumbnailUrl = runCatching { data.requireString("avatar_url") }.getOrNull()?.replace("large.jpg", "crop.jpg"),
            subscriberCount = runCatching { data.requireLong("followers_count") }.getOrNull(),
            description = runCatching { data.requireString("description") }.getOrNull()
        )
    }
}
