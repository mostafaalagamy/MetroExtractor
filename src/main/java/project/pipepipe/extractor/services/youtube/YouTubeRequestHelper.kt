package project.pipepipe.extractor.services.youtube

import project.pipepipe.extractor.ExtractorContext.objectMapper
import project.pipepipe.extractor.utils.generateRandomString
import project.pipepipe.shared.infoitem.ChannelTabType

object YouTubeRequestHelper {
    const val ANDROID_UA = "com.google.android.youtube/19.28.35 (Linux; U; Android 15; GB) gzip"
    const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    val ANDROID_HEADER = mapOf(
        "User-Agent" to ANDROID_UA,
        "X-Goog-Api-Format-Version" to "2",
        "Accept-Language" to "en-GB, en;q=0.9"
    )
    val WEB_HEADER = mapOf(
        "User-Agent" to WEB_UA,
        "X-YouTube-Client-Name" to "1",
        "Accept-Language" to "en-GB, en;q=0.9",
        "Referer" to "https://www.youtube.com",
        "Origin" to "https://www.youtube.com",
    )

    val DESKTOP_CONTEXT = mapOf(
        "request" to mapOf(
            "internalExperimentFlags" to emptyList<String>(),
            "useSsl" to true
        ),
        "client" to mapOf(
            "utcOffsetMinutes" to 0,
            "hl" to "en-US",
            "gl" to "US",
            "clientName" to "WEB",
            "originalUrl" to "https://www.youtube.com",
            "clientVersion" to "2.20251029.01.00",
            "platform" to "DESKTOP"
        ),
        "user" to mapOf(
            "lockedSafetyMode" to false
        )
    )

    val ChannelTabType.browseId: String
        get() = when(this) {
            ChannelTabType.VIDEOS -> "EgZ2aWRlb3PyBgQKAjoA"
            ChannelTabType.SHORTS -> "EgZzaG9ydHPyBgUKA5oBAA%3D%3D"
            ChannelTabType.LIVE -> "EgdzdHJlYW1z8gYECgJ6AA%3D%3D"
            ChannelTabType.ALBUMS -> "EghyZWxlYXNlc_IGBQoDsgEA"
            ChannelTabType.PLAYLISTS -> "EglwbGF5bGlzdHPyBgQKAkIA"
            else -> throw IllegalArgumentException()
        }

    fun getAndroidFetchStreamBody(id: String): String {
        val bodyMap = mapOf(
            "context" to mapOf(
                "request" to mapOf(
                    "internalExperimentFlags" to emptyList<String>(),
                    "useSsl" to true
                ),
                "client" to mapOf(
                    "androidSdkVersion" to 35,
                    "utcOffsetMinutes" to 0,
                    "osVersion" to "15",
                    "hl" to "en-US",
                    "clientName" to "ANDROID",
                    "gl" to "US",
                    "clientScreen" to "WATCH",
                    "clientVersion" to "20.10.38",
                    "osName" to "Android",
                    "platform" to "MOBILE"
                ),
                "user" to mapOf(
                    "lockedSafetyMode" to false
                )
            ),
            "playerRequest" to mapOf(
                "cpn" to generateRandomString(16),
                "contentCheckOk" to true,
                "racyCheckOk" to true,
                "videoId" to id
            ),
            "disablePlayerResponse" to false
        )

        return objectMapper.writeValueAsString(bodyMap)
    }

    fun getVideoInfoBody(id: String): String {
        val bodyMap = mapOf(
            "contentCheckOk" to true,
            "context" to DESKTOP_CONTEXT,
            "racyCheckOk" to true,
            "videoId" to id
        )
        return objectMapper.writeValueAsString(bodyMap)
    }

    fun getChannelInfoBody(id: String, type: ChannelTabType): String {
        val bodyMap = mapOf(
            "params" to when(type) {
                ChannelTabType.VIDEOS -> "EgZ2aWRlb3PyBgQKAjoA"
                ChannelTabType.SHORTS -> "EgZzaG9ydHPyBgUKA5oBAA%3D%3D"
                ChannelTabType.LIVE -> "EgdzdHJlYW1z8gYECgJ6AA%3D%3D"
                ChannelTabType.ALBUMS -> "EghyZWxlYXNlc_IGBQoDsgEA"
                ChannelTabType.PLAYLISTS -> "EglwbGF5bGlzdHPyBgQKAkIA"
                else -> throw IllegalArgumentException()
            },
            "context" to DESKTOP_CONTEXT,
            "browseId" to id
        )
        return objectMapper.writeValueAsString(bodyMap)
    }


    fun getChannelIdBody(url: String): String {
        val bodyMap = mapOf(
            "context" to DESKTOP_CONTEXT,
            "url" to url
        )
        return objectMapper.writeValueAsString(bodyMap)
    }

    fun getContinuationBody(continuation: String): String {
        return objectMapper.writeValueAsString(mapOf(
            "context" to DESKTOP_CONTEXT,
            "continuation" to continuation
        ))
    }

    fun getPlaylistInfoBody(id: String): String {
        val bodyMap = mapOf(
            "params" to "wgYCCAA%3D",
            "context" to DESKTOP_CONTEXT,
            "browseId" to if (id.startsWith("VL")) id else "VL$id"
        )
        return objectMapper.writeValueAsString(bodyMap)
    }

    fun getTrendingInfoBody(browseId: String = "FEtrending"): String {
        val bodyMap = mapOf(
            "context" to DESKTOP_CONTEXT,
            "browseId" to browseId
        )
        return objectMapper.writeValueAsString(bodyMap)
    }
}