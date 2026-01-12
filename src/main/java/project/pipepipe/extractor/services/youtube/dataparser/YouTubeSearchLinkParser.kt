package project.pipepipe.extractor.services.youtube.dataparser

import okio.ByteString.Companion.toByteString
import project.pipepipe.extractor.ExtractorContext.objectMapper
import project.pipepipe.extractor.services.youtube.YouTubeLinks.SEARCH_MUSIC_RAW_URL
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.DESKTOP_CONTEXT
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.DESKTOP_CONTEXT_MUSIC
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.DESKTOP_CONTEXT_ZULU
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.getContinuationBody
import project.pipepipe.extractor.services.youtube.dataparser.SpGenerator.UTF_8
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.DateFilter
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.ExtraFeatures
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.Extras
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.Features
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.Filters
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.LenFilter
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.SearchRequest
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.SortOrder
import project.pipepipe.extractor.services.youtube.search.filter.protobuf.TypeFilter
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class YouTubeSearchFilters(
    val sortOrder: SortOrder? = null,
    val dateFilter: DateFilter? = null,
    val typeFilter: TypeFilter? = null,
    val lenFilter: LenFilter? = null,
    val features: List<Features> = emptyList(),
    val extraFeatures: List<ExtraFeatures> = emptyList()
)


object FilterMapper {

    fun mapFrom(queryParams: Map<String, String>): YouTubeSearchFilters {
        val features = mutableListOf<Features>()
        val extraFeatures = mutableListOf<ExtraFeatures>()

        if (queryParams["is_hd"] == "1") features.add(Features.is_hd)
        if (queryParams["subtitles"] == "1") features.add(Features.subtitles)
        if (queryParams["ccommons"] == "1") features.add(Features.ccommons)
        if (queryParams["is_3d"] == "1") features.add(Features.is_3d)
        if (queryParams["live"] == "1") features.add(Features.live)
        if (queryParams["purchased"] == "1") features.add(Features.purchased)
        if (queryParams["is_4k"] == "1") features.add(Features.is_4k)
        if (queryParams["is_360"] == "1") features.add(Features.is_360)
        if (queryParams["location"] == "1") features.add(Features.location)
        if (queryParams["is_hdr"] == "1") features.add(Features.is_hdr)

//        if (queryParams["verbatim"] == "1") extraFeatures.add(ExtraFeatures.verbatim)

        return YouTubeSearchFilters(
            sortOrder = mapToSortOrder(queryParams["sort_by"]),
            dateFilter = mapToDateFilter(queryParams["upload_date"]),
            typeFilter = mapToTypeFilter(queryParams["type"]),
            lenFilter = mapToLenFilter(queryParams["duration"]),
            features = features,
            extraFeatures = extraFeatures
        )
    }

    private fun mapToSortOrder(value: String?): SortOrder? = when (value?.lowercase()) {
        "relevance" -> SortOrder.relevance
        "rating" -> SortOrder.rating
        "date" -> SortOrder.date
        "views" -> SortOrder.views
        else -> null
    }

    private fun mapToDateFilter(value: String?): DateFilter? = when (value?.lowercase()) {
        "hour" -> DateFilter.hour
        "day" -> DateFilter.day
        "week" -> DateFilter.week
        "month" -> DateFilter.month
        "year" -> DateFilter.year
        else -> null
    }

    private fun mapToTypeFilter(value: String?): TypeFilter? = when (value?.lowercase()) {
        "video" -> TypeFilter.video
        "channel" -> TypeFilter.channel
        "playlist" -> TypeFilter.playlist
        "movie" -> TypeFilter.movie
        else -> null
    }

    private fun mapToLenFilter(value: String?): LenFilter? = when (value?.lowercase()) {
        "duration_short" -> LenFilter.duration_short
        "duration_long" -> LenFilter.duration_long
        else -> null
    }
}

object SpGenerator {
    private const val UTF_8 = "UTF-8"

    fun from(filters: YouTubeSearchFilters): String? {
        if (filters == YouTubeSearchFilters()) {
            return null
        }

        val filtersBuilder = Filters.Builder()
        val extrasBuilder = Extras.Builder()

        filters.dateFilter?.let { filtersBuilder.date(it.value.toLong()) }
        filters.typeFilter?.let { filtersBuilder.type(it.value.toLong()) }
        filters.lenFilter?.let { filtersBuilder.length(it.value.toLong()) }
        filters.features.forEach { setFeatureState(it, true, filtersBuilder) }
        filters.extraFeatures.forEach { setExtraState(it, true, extrasBuilder) }

        val searchRequestBuilder = SearchRequest.Builder().apply {
            filters.sortOrder?.let { sorted(it.value.toLong()) }
            val builtFilters = filtersBuilder.build()
            if (builtFilters != Filters.ADAPTER.decode(ByteArray(0))) {
                filter(builtFilters)
            }

            val builtExtras = extrasBuilder.build()
            if (builtExtras != Extras.ADAPTER.decode(ByteArray(0))) {
                extras(builtExtras)
            }
        }

        val searchRequest = searchRequestBuilder.build()

        val protoBufEncoded = searchRequest.encode()
        val protoBufBase64 = protoBufEncoded.toByteString().base64()
        var finalSp = URLEncoder.encode(protoBufBase64, UTF_8)

        if (finalSp == "EgA%3D") {
            finalSp = "8AEB"
        }

        return finalSp
    }

    fun hardCodeMap(filter: String): String = when (filter) {
        "songs" -> "Eg-KAQwIARAAGAAgACgAMABqChAEEAUQAxAKEAk%3D"
        "videos" -> "Eg-KAQwIABABGAAgACgAMABqChAEEAUQAxAKEAk%3D"
        "albums" -> "Eg-KAQwIABAAGAEgACgAMABqChAEEAUQAxAKEAk%3D"
        "playlists" -> "Eg-KAQwIABAAGAAgACgBMABqChAEEAUQAxAKEAk%3D"
        "artists" -> "Eg-KAQwIABAAGAAgASgAMABqChAEEAUQAxAKEAk%3D"
        else -> error("unexpected filter")
    }

    private fun setFeatureState(feature: Features, state: Boolean, builder: Filters.Builder) {
        when (feature) {
            Features.is_hd -> builder.is_hd(state)
            Features.subtitles -> builder.subtitles(state)
            Features.ccommons -> builder.ccommons(state)
            Features.is_3d -> builder.is_3d(state)
            Features.live -> builder.live(state)
            Features.purchased -> builder.purchased(state)
            Features.is_4k -> builder.is_4k(state)
            Features.is_360 -> builder.is_360(state)
            Features.location -> builder.location(state)
            Features.is_hdr -> builder.is_hdr(state)
        }
    }

    private fun setExtraState(extra: ExtraFeatures, state: Boolean, builder: Extras.Builder) {
        when (extra) {
            ExtraFeatures.verbatim -> builder.verbatim(state)
        }
    }
}

object YouTubeSearchLinkParser {
    fun getSearchBody(rawUrl: String): String = runCatching {
        val url = URI(rawUrl)
        val queryParams = parseQuery(url.query)
        val isMusicSearch = rawUrl.contains(SEARCH_MUSIC_RAW_URL)
        val isSearchingVideo = queryParams.get("type") == "video" && !isMusicSearch
        return if (isMusicSearch) {
            if (queryParams.contains("continuation")) {
                objectMapper.writeValueAsString(mapOf(
                    "context" to DESKTOP_CONTEXT_MUSIC,
                ))
            } else {
                objectMapper.writeValueAsString(mapOf(
                    "context" to DESKTOP_CONTEXT_MUSIC,
                    "query" to queryParams["query"],
                    "params" to SpGenerator.hardCodeMap(queryParams["type"]!!)
                ))
            }
        } else {
            if (queryParams.contains("continuation")) {
                getContinuationBody(queryParams["continuation"]!!, useZuluTrick = isSearchingVideo)
            } else {
                objectMapper.writeValueAsString(mapOf(
                    "context" to if (isSearchingVideo)DESKTOP_CONTEXT_ZULU else DESKTOP_CONTEXT,
                    "query" to queryParams["query"],
                    "params" to SpGenerator.from(FilterMapper.mapFrom(queryParams))
                ))
            }
        }
    }.getOrElse {
        throw IllegalArgumentException("Invalid url: ${it.message}")
    }

    private fun parseQuery(query: String?): Map<String, String> =
        query?.takeIf { it.isNotBlank() }
            ?.split('&')
            ?.mapNotNull { param -> param.toKeyValuePair() }
            ?.toMap()
            ?: emptyMap()

    private fun String.toKeyValuePair(): Pair<String, String>? =
        split('=', limit = 2)
            .takeIf { it.size == 2 && it[0].isNotBlank() }
            ?.let { (key, value) ->
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
}