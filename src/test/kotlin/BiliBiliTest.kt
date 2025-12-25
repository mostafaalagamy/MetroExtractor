//import com.fasterxml.jackson.annotation.JsonProperty
//import kotlinx.coroutines.runBlocking
//import project.pipepipe.extractor.Router
//import project.pipepipe.shared.SharedContext
//import project.pipepipe.shared.job.SupportedJobType
//import project.pipepipe.shared.job.executeJobFlow
//import kotlin.test.Test
//
//data class StreamTestConfig(
//    @JsonProperty("url") val url: String,
//    @JsonProperty("ignoreSponsorBlockMetaInfo") val ignoreSponsorBlockMetaInfo: Boolean = false,
//    @JsonProperty("ignoreRelatedItemsMetaInfo") val ignoreRelatedItemsMetaInfo: Boolean = false,
//    @JsonProperty("ignoreCommentMetaInfo") val ignoreCommentMetaInfo: Boolean = false,
//    @JsonProperty("ignoreDanmakuMetaInfo") val ignoreDanmakuMetaInfo: Boolean = false,
//    @JsonProperty("hasPartitions") val hasPartitions: Boolean = false
//)
//
//data class TestConfigFile(
//    @JsonProperty("testConfigs") val testConfigs: List<StreamTestConfig>
//)
//
//val excludedStreamFields = setOf(
//    "dislikeCount",           // BiliBili doesn't provide dislikes
//    "uploaderVerified",       // Not extracted from BiliBili
//    "uploaderSubscriberCount", // TODO
//    "dashMpdUrl",             // DASH not used by BiliBili
//    "hlsUrl",                 // no hls here
//    "subtitles",              // no-login
//    "host",                   // Not relevant for BiliBili
//    "privacy",                // Privacy status not extracted
//    "category",               // Category not extracted
//    "licence",                // Licence not extracted
//    "streamSegments",         // Stream segments not extracted
//    "metaInfo",               // General metaInfo not extracted
//    "previewFrames",          // TODO
//)
//
//class BiliBiliTest {
//
////    @Test
////    fun `test Comment`() = runBlocking {
////        var result = executeJobFlow(
////            SupportedJobType.FETCH_INFO, "https://www.bilibili.com/video/BV1bgacz7E4J?p=1",
////            Router::execute
////        )
//////        print((result.info as StreamInfo).commentEndPoint)
////        result = executeJobFlow(
////            SupportedJobType.FETCH_FIRST_PAGE, (result.info as StreamInfo).commentInfo!!.url!!,
////            Router::execute
////        )
////        println(result.pagedData)
////        println((result.pagedData!!.itemList[0] as CommentInfo).replyEndPoint)
////        val next = result.pagedData!!.nextPageUrl!!
////        result = executeJobFlow(
////            SupportedJobType.FETCH_FIRST_PAGE, (result.pagedData!!.itemList[0] as CommentInfo).replyEndPoint!!.url,
////            Router::execute
////        )
////        println(result.pagedData)
////        result = executeJobFlow(
////            SupportedJobType.FETCH_GIVEN_PAGE, next,
////            Router::execute
////        )
////        println(result.pagedData)
////    }
//    @Test
//    fun `test Related`() = runBlocking {
//        SharedContext.serverRequestHandler = Router::execute
//        var result = executeJobFlow(
//            SupportedJobType.FETCH_INFO, "https://www.youtube.com/@ShirakamiFubuki/featured",
//            0
//        )
//        println(result)
//    }
//
//}