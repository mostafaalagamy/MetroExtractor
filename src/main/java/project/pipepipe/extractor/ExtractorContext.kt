package project.pipepipe.extractor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import project.pipepipe.extractor.services.bilibili.BilibiliService
import project.pipepipe.extractor.services.niconico.NicoNicoService
import project.pipepipe.extractor.services.youtube.YouTubeService
import project.pipepipe.shared.downloader.Downloader

object ExtractorContext {
    val downloader: Downloader = Downloader(HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
    })
    val objectMapper = ObjectMapper()
    fun String.asJson(): JsonNode = objectMapper.readTree(this)



    object ServiceList {
        val YouTube = YouTubeService(0)
//        val Bandcamp = BandcampService(4)
        val BiliBili = BilibiliService(5)
        val NicoNico = NicoNicoService(6)
        val all = listOf(YouTube,BiliBili, NicoNico)
    }
}
