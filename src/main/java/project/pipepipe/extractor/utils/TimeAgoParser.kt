package project.pipepipe.extractor.utils

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.regex.Pattern

object TimeAgoParser {

    private val TIME_PATTERNS = mapOf(
        ChronoUnit.SECONDS to listOf(
            "second", "seconds", "sec", "secs",
            "umzuzwana", "imizuzwana", "amasekhondi", "isekhondi", "emasekhondini", "emizuzwaneni" // Zulu
        ),
        ChronoUnit.MINUTES to listOf(
            "minute", "minutes", "min", "mins",
            "umzuzu", "imizuzu", "amaminithi", "iminithi", "emaminithini", "emizuzwini"       // Zulu
        ),
        ChronoUnit.HOURS to listOf(
            "hour", "hours", "hr", "hrs",
            "ihora", "amahora", "emahoreni"        // Zulu
        ),
        ChronoUnit.DAYS to listOf(
            "day", "days",
            "usuku", "izinsuku", "osukwini", "ezinsukwini"       // Zulu
        ),
        ChronoUnit.WEEKS to listOf(
            "week", "weeks",
            "iviki", "amaviki", "isonto", "amasonto", "emavikini", "emasontweni"        // Zulu
        ),
        ChronoUnit.MONTHS to listOf(
            "month", "months",
            "inyanga", "izinyanga", "ezinyangeni"    // Zulu
        ),
        ChronoUnit.YEARS to listOf(
            "year", "years",
            "unyaka", "iminyaka", "eminyakeni"      // Zulu
        )
    )

    private val SPECIAL_CASES = mapOf(
        // English
        "now" to (ChronoUnit.SECONDS to 0),
        "just now" to (ChronoUnit.SECONDS to 0),
        "a moment ago" to (ChronoUnit.SECONDS to 30),
        "yesterday" to (ChronoUnit.DAYS to 1),

        // Zulu
        "manje" to (ChronoUnit.SECONDS to 0), // now
        "izolo" to (ChronoUnit.DAYS to 1)     // yesterday
    )

    /**
     * Parses "X time ago" format and returns epoch timestamp in milliseconds
     * @throws IllegalStateException if the input cannot be parsed
     */
    fun parseToTimestamp(textualDate: String): Long {
        require(textualDate.isNotBlank()) { "Textual date cannot be blank" }

        val now = OffsetDateTime.now(ZoneOffset.UTC)

        // Check special cases first
        SPECIAL_CASES[textualDate.lowercase()]?.let { (unit, amount) ->
            return now.minus(amount.toLong(), unit).toInstant().toEpochMilli()
        }

        val amount = extractAmount(textualDate)
        val unit = extractTimeUnit(textualDate)

        val resultDateTime = when (unit) {
            ChronoUnit.YEARS -> now.minusYears(amount.toLong()).minusDays(1)
            else -> now.minus(amount.toLong(), unit)
        }
        return resultDateTime.toInstant().toEpochMilli()
    }

    /**
     * Parses duration text and returns duration in seconds
     * @throws IllegalStateException if the input cannot be parsed
     */
    fun parseDuration(textualDuration: String): Long {
        require(textualDuration.isNotBlank()) { "Textual duration cannot be blank" }

        val amount = extractAmount(textualDuration)
        val unit = extractTimeUnit(textualDuration)

        return amount * unit.duration.seconds
    }

    private fun extractAmount(text: String): Int {
        val numberStr = text.replace(Regex("\\D+"), "")

        if (numberStr.isEmpty()) {
            error("Failed to extract amount from: '$text'")
        }

        return try {
            numberStr.toInt().also {
                if (it <= 0) {
                    error("Amount must be positive, got: $it from '$text'")
                }
            }
        } catch (e: NumberFormatException) {
            error("Failed to parse amount from: '$text'")
        }
    }

    private fun extractTimeUnit(text: String): ChronoUnit {
        val lowerText = text.lowercase()

        return TIME_PATTERNS.entries.firstOrNull { (_, patterns) ->
            patterns.any { pattern ->
                textContainsPattern(lowerText, pattern)
            }
        }?.key ?: error("Failed to extract time unit from: '$text'")
    }

    private fun textContainsPattern(text: String, pattern: String): Boolean {
        // Simple contains check for exact matches
        if (text == pattern) return true

        // Word boundary check for partial matches
        val escapedPattern = Pattern.quote(pattern)
        val regex = "(^|\\s)$escapedPattern(\\s|$)".toRegex()
        return regex.containsMatchIn(text)
    }
}
