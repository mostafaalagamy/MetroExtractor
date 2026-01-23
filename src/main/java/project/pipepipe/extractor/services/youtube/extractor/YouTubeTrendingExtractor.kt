package project.pipepipe.extractor.services.youtube.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.youtube.YouTubeLinks.BROWSE_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.WEB_HEADER
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getTrendingInfoBody
import project.pipepipe.extractor.services.youtube.dataparser.YouTubeStreamInfoDataParser.parseFromVideoRenderer
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.*
import project.pipepipe.shared.state.PlainState
import project.pipepipe.shared.state.State
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireObject

class YouTubeTrendingExtractor(
    url: String,
) : Extractor<PlaylistInfo, StreamInfo>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?,
        cookie: String?
    ): JobStepResult {
        val type = getQueryValue(url, "name")
        var country = getQueryValue(url, "country")!!

        if (currentState == null) {
            val supportedCountries = listOf(
                "DZ", "AR", "AU", "AT", "AZ", "BH", "BD", "BY", "BE", "BO", "BA", "BR", "BG", "CA",
                "CL", "CO", "CR", "HR", "CY", "CZ", "DK", "DO", "EC", "EG", "SV", "EE", "FI", "FR",
                "GE", "DE", "GH", "GR", "GT", "HN", "HK", "HU", "IS", "IN", "ID", "IQ", "IE", "IL",
                "IT", "JM", "JP", "JO", "KZ", "KE", "KW", "LV", "LB", "LY", "LI", "LT", "LU", "MY",
                "MT", "MX", "ME", "MA", "NP", "NL", "NZ", "NI", "NG", "MK", "NO", "OM", "PK", "PA",
                "PG", "PY", "PE", "PH", "PL", "PT", "PR", "QA", "RO", "RU", "SA", "SN", "RS", "SG",
                "SK", "SI", "ZA", "KR", "ES", "LK", "SE", "CH", "TW", "TZ", "TH", "TN", "TR", "UG",
                "UA", "AE", "GB", "US", "UY", "VE", "VN", "YE", "ZW"
            )
            if (!supportedCountries.contains(country)) country = "US"
            return JobStepResult.ContinueWith(
                listOf(
                    ClientTask(
                        payload = Payload(
                            RequestMethod.POST,
                            BROWSE_URL,
                            WEB_HEADER,
                            getTrendingInfoBody("UC4R8DWoMoI7CAwX8_LjQHig", country)
                        )
                    )
                ), PlainState(1)
            )
        } else {
            val result = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson()
            when (type) {
                "recommended_lives" ->  result
                    .requireArray("/contents/twoColumnBrowseResultsRenderer/tabs/0/tabRenderer/content/richGridRenderer/contents")
                    .flatMap { runCatching{ it.requireArray("/richSectionRenderer/content/richShelfRenderer/contents") }.getOrDefault(emptyList()) }
                    .forEach {
                        commit { parseFromVideoRenderer(it.requireObject("/richItemRenderer/content")) }
                    }
            }


            return JobStepResult.CompleteWith(
                ExtractResult(
                    errors = errors,
                    pagedData = PagedData(itemList.distinctBy { it.url }, null)
                )
            )
        }
    }
}