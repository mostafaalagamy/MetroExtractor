package project.pipepipe.extractor.services.bilibili.extractor

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.bilibili.Utils
import project.pipepipe.extractor.services.bilibili.dataparser.BiliBiliStreamInfoDataParser

import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.utils.json.*
import java.net.URLDecoder

class BiliBiliPlaylistExtractor(url: String) : Extractor<PlaylistInfo, StreamInfo>(url) {

    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        if (clientResults == null) {
            val headers = BilibiliService.getHeadersWithCookie(url, cookie!!)
            val requestList = mutableListOf(
                ClientTask(taskId = "info", payload = Payload(RequestMethod.GET, url, headers = headers))
            ).apply {
                if (!url.contains(BiliBiliLinks.GET_PARTITION_URL)) {
                    add(ClientTask(taskId = "user_data", payload = Payload(RequestMethod.GET, BiliBiliLinks.QUERY_USER_INFO_URL + Utils.getMidFromRecordApiUrl(url), headers = headers)))
                }
            }
            return JobStepResult.ContinueWith(requestList, state = PlainState(1))
        } else {
            val data = clientResults.first { it.taskId == "info" }.result!!.asJson()

            val type = when {
                url.contains(BiliBiliLinks.GET_SEASON_ARCHIVES_ARCHIVE_BASE_URL) -> "seasons_archives"
                url.contains(BiliBiliLinks.GET_SERIES_BASE_URL) -> "series"
                url.contains(BiliBiliLinks.GET_PARTITION_URL) -> "partition"
                else -> "archives"
            }

            return if (type == "partition") {
                handlePartitionPlaylist(data)
            } else {
                handleRegularPlaylist(data, clientResults.first { it.taskId == "user_data" }.result!!.asJson(), type)
            }
        }
    }

    private fun handlePartitionPlaylist(data: JsonNode):JobStepResult {
        val relatedArray = data.requireArray("data")
        val bvid = safeGet{ url.split("bvid=")[1].split("&")[0] }
        val thumbnailUrl = safeGet{URLDecoder.decode(url.split("thumbnail=")[1].split("&")[0], "UTF-8")}
        val uploaderName = safeGet{ URLDecoder.decode(url.split("uploaderName=")[1].split("&")[0], "UTF-8") }
        val uploaderUrl = safeGet{ URLDecoder.decode(url.split("uploaderUrl=")[1].split("&")[0], "UTF-8") }
        val uploaderAvatarUrl = safeGet{URLDecoder.decode(url.split("uploaderAvatar=")[1].split("&")[0], "UTF-8")}

        relatedArray.forEachIndexed { index, item ->
            commit {
                BiliBiliStreamInfoDataParser.parseFromPartitionRelatedInfoJson(
                    item = item,
                    bvid = bvid!!,
                    thumbnailUrl = thumbnailUrl,
                    uploaderName = uploaderName,
                    uploaderUrl = uploaderUrl,
                    uploaderAvatarUrl = uploaderAvatarUrl
                )
            }
        }

        return JobStepResult.CompleteWith(ExtractResult(PlaylistInfo(
            url = url,
            name = URLDecoder.decode(url.split("name=")[1].split("&")[0], "UTF-8"),
            serviceId = 5,
            thumbnailUrl = thumbnailUrl,
            uploaderUrl = uploaderUrl,
            uploaderAvatarUrl = uploaderAvatarUrl,
            uploaderName = uploaderName,
            streamCount = relatedArray.size().toLong()
        ), errors, PagedData(itemList, null)))
    }

    private fun handleRegularPlaylist(data: JsonNode, userData: JsonNode, type: String): JobStepResult {
        val playlistData = data.requireObject("data")
        val userCard = userData.requireObject("data").requireObject("card")
        val uploaderName = safeGet{ userCard.requireString("name") }
        val uploaderId = safeGet{ userCard.requireString("mid") }
        val uploaderAvatarUrl = safeGet{ userCard.requireString("face").replace("http:", "https:") }

        val archives = playlistData.requireArray("archives")
        archives.forEach { archive ->
            commit{
                BiliBiliStreamInfoDataParser.parseFromWebChannelInfoResponseJson(archive, uploaderName, uploaderId)
            }
        }
        return JobStepResult.CompleteWith(ExtractResult(PlaylistInfo(
            url = url,
            name = URLDecoder.decode(url.split("name=")[1].split("&")[0], "UTF-8"),
            serviceId = 5,
            thumbnailUrl  = runCatching{ playlistData.requireString("/meta/cover") }.getOrElse { itemList[0].thumbnailUrl },
            streamCount  = playlistData.requireLong("/page/total"),
            uploaderUrl = BiliBiliLinks.CHANNEL_BASE_URL + Utils.getMidFromRecordApiUrl(url),
            uploaderName = uploaderName,
            uploaderAvatarUrl = uploaderAvatarUrl
        ), errors, PagedData(itemList, null)))
    }
}