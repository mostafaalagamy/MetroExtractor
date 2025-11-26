package project.pipepipe.extractor.utils

import project.pipepipe.shared.infoitem.helper.stream.AudioStream
import project.pipepipe.shared.infoitem.helper.stream.SubtitleStream
import project.pipepipe.shared.infoitem.helper.stream.VideoStream
import java.util.*

fun generateRandomString(
    length: Int,
    alphabet: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
    random: Random = Random()
): String = String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })

fun parseMediaType(input: String): Pair<String, String?> {
    val parts = input.split(";").map { it.trim() }
    val mimeType = parts[0]

    val codecs = parts.find { it.startsWith("codecs=") }
        ?.substringAfter("codecs=")
        ?.removeSurrounding("\"")

    return mimeType to codecs
}

fun createMultiStreamDashManifest(
    duration: Double,
    videoStreams: List<VideoStream>,
    audioStreams: List<AudioStream>,
    subtitleStreams: List<SubtitleStream> = emptyList()
): String {
    fun String.encodeUrl() = this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    val videoAdaptationSets = videoStreams.groupBy { it.mimeType }.map { (mimeType, streams) ->
        val representations = streams.joinToString("\n") { video ->
            // Add HDR color info to each representation
            val supplementalProperty = if (video.isHdr) {
                """
                  <SupplementalProperty schemeIdUri="urn:mpeg:mpegB:cicp:ColourPrimaries" value="9"/>
                  <SupplementalProperty schemeIdUri="urn:mpeg:mpegB:cicp:TransferCharacteristics" value="16"/>
                  <SupplementalProperty schemeIdUri="urn:mpeg:mpegB:cicp:MatrixCoefficients" value="9"/>
                """.trimIndent()
            } else ""

            """
                <Representation id="${video.id}" codecs="${video.codec}" width="${video.width}" height="${video.height}" bandwidth="${video.bandwidth}" frameRate="${video.frameRate}">
                  <BaseURL>${video.url.encodeUrl()}</BaseURL>
                  <SegmentBase indexRange="${video.indexRange}">
                    <Initialization range="${video.initialization}"/>
                  </SegmentBase>$supplementalProperty
                </Representation>
            """.trimIndent()
        }

        """
            <AdaptationSet contentType="video" mimeType="$mimeType" subsegmentAlignment="true">
              $representations
            </AdaptationSet>
        """.trimIndent()
    }.joinToString("\n\n")

    val audioAdaptationSets = audioStreams
        .groupBy { it.mimeType to it.language }  // Group by both mimeType and language
        .map { (mimeTypeAndLang, streams) ->
            val (mimeType, language) = mimeTypeAndLang
            val langAttr = if (!language.isNullOrEmpty()) """ lang="$language"""" else ""

            val representations = streams.joinToString("\n") { audio ->
                val samplingRate = if (!audio.samplingRate.isNullOrEmpty()) """ audioSamplingRate="${audio.samplingRate}"""" else ""
                """
                    <Representation id="${audio.id}" codecs="${audio.codec}" bandwidth="${audio.bandwidth}"$samplingRate>
                      <BaseURL>${audio.url.encodeUrl()}</BaseURL>
                      <SegmentBase indexRange="${audio.indexRange}">
                        <Initialization range="${audio.initialization}"/>
                      </SegmentBase>
                    </Representation>
                """.trimIndent()
            }

            """
                <AdaptationSet contentType="audio" mimeType="$mimeType"$langAttr subsegmentAlignment="true">
                  $representations
                </AdaptationSet>
            """.trimIndent()
        }.joinToString("\n\n")


    val subtitleAdaptationSets = subtitleStreams.joinToString("\n\n") { subtitle ->

        """
            <AdaptationSet contentType="text" mimeType="${subtitle.mimeType}" lang="${subtitle.language}">
              <Representation id="${subtitle.id}" bandwidth="0">
                <BaseURL>${subtitle.url.encodeUrl()}</BaseURL>
              </Representation>
            </AdaptationSet>
        """.trimIndent()
    }


    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT${duration}S">
          <Period>
            $videoAdaptationSets
            
            $audioAdaptationSets
            $subtitleAdaptationSets
          </Period>
        </MPD>
    """.trim()
}

fun String.incrementUrlParam(param: String): String {
    val regex = Regex("""$param=(\d+)""")
    return if (regex.containsMatchIn(this)) {
        replace(regex) {
            "$param=${it.groupValues[1].toInt() + 1}"
        }
    } else {
        val separator = if (contains("?")) "&" else "?"
        "$this$separator$param=2"
    }
}

fun parseNumberWithSuffix(input: String): Long {
    // Regular expression to match number with optional K/M/B suffix
    // This will match patterns like "67.5K", "1.2M", "3B", "100", etc.
    val regex = """([\d,]+\.?\d*)([KMB]?)(?:\s|$)""".toRegex()

    val match = regex.find(input)
        ?: throw IllegalArgumentException("No valid number found in: $input")

    val numberStr = match.groupValues[1].replace(",", "")
    val number = numberStr.toDouble()
    val suffix = match.groupValues[2].uppercase()

    val multiplier = when (suffix) {
        "K" -> 1_000
        "M" -> 1_000_000
        "B" -> 1_000_000_000
        else -> 1
    }

    return (number * multiplier).toLong()
}

fun String.extractDigitsAsLong(): Long =
    filter(Char::isDigit).toLongOrNull() ?: 0

fun getDurationFromString(duration: String): Long {
    val parts = duration.split(":")
    var result = 0L

    result += parts.last().toInt()
    if (parts.size > 1) {
        result += parts[parts.size - 2].toInt() * 60L
    }
    if (parts.size > 2) {
        result += parts[parts.size - 3].toInt() * 60L * 60L
    }

    return result
}

/**
 * Convert a mixed number word to a long.
 *
 *
 *
 * Examples:
 *
 *
 *
 *  * 123 -&gt; 123
 *  * 1.23K -&gt; 1230
 *  * 1.23M -&gt; 1230000
 *
 *
 * @param numberWord string to be converted to a long
 * @return a long
 */
fun mixedNumberWordToLong(numberWord: String?): Long {
    var multiplier = ""
    try {
        multiplier = Parser.matchGroup("[\\d]+([\\.,][\\d]+)?([KMBkmb])+", numberWord, 2)
    } catch (ignored: Exception) {
    }
    val count = Parser.matchGroup1("([\\d]+([\\.,][\\d]+)?)", numberWord).replace(",", ".").toDouble()
    when (multiplier.uppercase(Locale.getDefault())) {
        "K" -> return (count * 1e3).toLong()
        "M" -> return (count * 1e6).toLong()
        "B" -> return (count * 1e9).toLong()
        else -> return (count).toLong()
    }
}
