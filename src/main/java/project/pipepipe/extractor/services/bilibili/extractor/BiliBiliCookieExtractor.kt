package project.pipepipe.extractor.services.bilibili.extractor

import project.pipepipe.extractor.base.CookieExtractor
import project.pipepipe.extractor.services.bilibili.BiliBiliLinks
import project.pipepipe.extractor.services.bilibili.BilibiliService.Companion.getUserAgentHeaders
import project.pipepipe.extractor.services.bilibili.BilibiliService.Companion.mapToCookieHeader
import project.pipepipe.extractor.services.bilibili.Utils.bytesToHex
import project.pipepipe.shared.state.CookieState
import project.pipepipe.shared.state.State
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.utils.json.requireLong
import project.pipepipe.extractor.utils.json.requireObject
import project.pipepipe.extractor.utils.json.requireString
import project.pipepipe.shared.infoitem.CookieInfo
import project.pipepipe.shared.job.*
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BiliBiliCookieExtractor: CookieExtractor(){
    override suspend fun refreshCookie(
        sessionId: String,
        currentState: State?,
        clientResults: List<TaskResult>?
    ): JobStepResult {
        if (currentState == null) {
            return JobStepResult.ContinueWith(listOf(
                ClientTask(payload = Payload(
                    RequestMethod.GET, BiliBiliLinks.FETCH_COOKIE_URL, getUserAgentHeaders(
                        BiliBiliLinks.WWW_REFERER
                    )
                ))
            ), state = CookieState(1, CookieInfo(null, -1)))
        } else if (currentState.step == 1){
            val cookies = linkedMapOf<String, String>()
            val result  = clientResults!!.first { it.taskId.isDefaultTask() }
            val data = result.result!!.asJson().requireObject("data")
            cookies["buvid3"] = data.requireString("b_3")
            cookies["b_nut"] = ZonedDateTime.parse(
                result.responseHeader!!["date"]?.get(0) ?: throw IOException("Missing date header"),
                DateTimeFormatter.RFC_1123_DATE_TIME
            ).toEpochSecond().toString()

            val randomLsidBytes = ByteArray(32)
            ThreadLocalRandom.current().nextBytes(randomLsidBytes)
            val randomLsid = String.format(
                "%s_%X",
                bytesToHex(randomLsidBytes).uppercase(Locale.ROOT),
                System.currentTimeMillis()
            )
            cookies["b_lsid"] = randomLsid
            cookies["_uuid"] = getFpUuid()
            cookies["buvid4"] = data.requireString("b_4")

            val ts = Instant.now().epochSecond
            val hexSign = hmacSha256("XgwSnGZ1p", "ts$ts")
            val url = "${BiliBiliLinks.FETCH_TICKET_URL}?" +
                    "key_id=ec02&" +
                    "hexsign=$hexSign&" +
                    "context[ts]=$ts&" +
                    "csrf=${""}"
            val headers = getUserAgentHeaders(BiliBiliLinks.WWW_REFERER)
            headers["Cookie"] = mapToCookieHeader(cookies)

            return JobStepResult.ContinueWith(listOf(ClientTask(payload = Payload(
                RequestMethod.POST, url, headers
            ))), state = CookieState(2, CookieInfo(mapToCookieHeader(cookies), -1)))
        } else if (currentState.step == 2) {
            val cookieInfo = (currentState as CookieState).cookieInfo
            var cookie = cookieInfo.cookie
            val data = clientResults!!.first { it.taskId.isDefaultTask() }.result!!.asJson().requireObject("data")
            val ticket = data.requireString("ticket")
            val createdAt = data.requireLong("created_at")
            require(createdAt > 0) { "created_at: $createdAt" }
            val ttl = data.requireLong("ttl")
            require(ttl > 0) { "ttl: $ttl" }
            val expires = createdAt + ttl
            cookie += ";bili_ticket=$ticket;bili_ticket_expires=$expires"
            val randomBuvidFp = ByteArray(16)
            ThreadLocalRandom.current().nextBytes(randomBuvidFp)
            cookie += ";buvid_fp=${bytesToHex(randomBuvidFp)}"
            return JobStepResult.CompleteWith(ExtractResult(CookieInfo(cookie, expires)))
        }else throw IllegalArgumentException()
    }
    /**
     * @see [_uuid](https://github.com/SocialSisterYi/bilibili-API-collect/issues/933)
     */
    private fun getFpUuid(): String {
        val digitMap = arrayOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "10"
        )

        // Get the last 5 digits of the current timestamp
        val t = System.currentTimeMillis() % 100_000

        // Generate 32 random bytes
        val index = ByteArray(32)
        ThreadLocalRandom.current().nextBytes(index)

        // Build the main part of the UUID string
        val result = StringBuilder(64)
        val hyphenIndices = listOf(9, 13, 17, 21)

        for (ii in index.indices) {
            if (ii in hyphenIndices) {
                result.append('-')
            }
            // Use the lower 4 bits of the random byte as an index into digitMap
            result.append(digitMap[index[ii].toInt() and 0x0f])
        }

        // Append the formatted timestamp and the suffix
        result.append(String.format("%05d", t))
        result.append("infoc")

        return result.toString()
    }
    /**
     * Generate a HMAC-SHA256 hash of the given message string using the given key string.
     *
     * @param key The key string to use for the HMAC-SHA256 hash.
     * @param message The message string to hash.
     * @return The HMAC-SHA256 hash of the given message string using the given key string.
     */
    private fun hmacSha256(key: String, message: String): String {
        val mac = try {
            Mac.getInstance("HmacSHA256")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
        val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        try {
            mac.init(secretKeySpec)
        } catch (e: InvalidKeyException) {
            throw RuntimeException(e)
        }
        val hash = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(hash)
    }
}