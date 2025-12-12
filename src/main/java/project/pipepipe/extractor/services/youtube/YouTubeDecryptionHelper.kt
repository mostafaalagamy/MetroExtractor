package project.pipepipe.extractor.services.youtube

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import project.pipepipe.extractor.ExtractorContext
import project.pipepipe.extractor.ExtractorContext.asJson
import project.pipepipe.extractor.services.youtube.YouTubeRequestHelper.parseQueryToMap
import project.pipepipe.extractor.utils.RequestHelper
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireInt
import project.pipepipe.shared.utils.json.requireObject
import project.pipepipe.shared.utils.json.requireString
import java.net.URLEncoder
import kotlin.collections.component1
import kotlin.collections.component2

object YouTubeDecryptionHelper {

    /**
     * Data class to hold cipher information for a format item
     */
    data class CipherInfo(
        val baseUrl: String,
        val sp: String,  // "n" or "sig"
        val encryptedValue: String  // s的值
    )

    /**
     * Fetches the latest player name and signature timestamp from PipePipe API
     * @return Pair of (player name, signatureTimestamp) or null if failed
     */
    fun getLatestPlayer(): Pair<String, Int>? {
        return runBlocking {
            try {
                val response = ExtractorContext.downloader.get(
                    "https://api.pipepipe.dev/decoder/latest-player",
                    mapOf("User-Agent" to "PipePipe/5.0.0")
                ).bodyAsText().asJson()

                val player = response.requireString("player")
                val signatureTimestamp = response.requireInt("signatureTimestamp")

                player to signatureTimestamp
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Batch decrypts ciphers using PipePipe API
     * @param nValues List of encrypted values where sp=n
     * @param sigValues List of encrypted values where sp=sig
     * @return Map from encrypted value to decrypted value
     */
    suspend fun batchDecryptCiphers(nValues: List<String>, sigValues: List<String>, player: String): Map<String, String> {
        if (nValues.isEmpty() && sigValues.isEmpty()) return emptyMap()

        val queryParams = mutableListOf<String>()

        if (nValues.isNotEmpty()) {
            queryParams.add("n=${nValues.joinToString(",") { URLEncoder.encode(it, "UTF-8") }}")
        }

        if (sigValues.isNotEmpty()) {
            queryParams.add("sig=${sigValues.joinToString(",") { URLEncoder.encode(it, "UTF-8") }}")
        }

        val apiUrl = "https://api.pipepipe.dev/decoder/decode?player=${player}&${queryParams.joinToString("&")}"
        val response = ExtractorContext.downloader.get(
            apiUrl,
            mapOf("User-Agent" to "PipePipe/5.0.0")
        ).bodyAsText().asJson()

        val results = mutableMapOf<String, String>()
        response.requireArray("responses").forEach { responseItem ->
            if (responseItem.requireString("type") == "result") {
                val data = responseItem.requireObject("data")
                data.fields().forEach { (encryptedValue, decryptedNode) ->
                    results[encryptedValue] = decryptedNode.asText()
                }
            }
        }
        return results
    }

    /**
     * Extracts cipher info from format item
     */
    fun extractCipherInfo(formatItem: JsonNode): CipherInfo? {
        // Try to get direct URL first
        runCatching { formatItem.requireString("url") }.getOrNull()?.let {
            return null  // Direct URL, no cipher needed
        }

        // No direct URL, check for cipher/signatureCipher
        val cipherField = when {
            formatItem.has("signatureCipher") -> "signatureCipher"
            formatItem.has("cipher") -> "cipher"
            else -> return null
        }

        val cipherString = runCatching { formatItem.requireString(cipherField) }.getOrNull() ?: return null
        val cipherParams = parseQueryToMap(cipherString)

        val baseUrl = cipherParams["url"] ?: return null
        val sp = cipherParams["sp"] ?: return null
        val encryptedValue = cipherParams["s"] ?: return null

        return CipherInfo(baseUrl, sp, encryptedValue)
    }

    /**
     * Process adaptive formats and decrypt URLs in batch
     * @param formats JsonNode containing array of format items
     * @return List of pairs (format JsonNode, decrypted URL string)
     */
    suspend fun processAdaptiveFormats(formats: JsonNode, player: String?): List<Pair<JsonNode, String>> {
        val formatList = formats.toList()

        // First pass: collect cipher info and separate direct URLs (with or without encrypted n parameter)
        val directUrlFormats = mutableListOf<Pair<JsonNode, String>>()
        val directUrlWithNFormats = mutableListOf<Pair<JsonNode, Pair<String, String>>>() // format to (url, encrypted n)
        val cipherFormats = mutableListOf<Pair<JsonNode, CipherInfo>>()

        formatList.forEach { format ->
            val directUrl = runCatching { format.requireString("url") }.getOrNull()
            if (directUrl != null) {
                // Check if the URL contains an encrypted n parameter using RequestHelper
                val encryptedN = RequestHelper.getQueryValue(directUrl, "n")
                if (encryptedN != null) {
                    directUrlWithNFormats.add(format to (directUrl to java.net.URLDecoder.decode(encryptedN, "UTF-8")))
                } else {
                    directUrlFormats.add(format to directUrl)
                }
            } else {
                extractCipherInfo(format)?.let { cipherInfo ->
                    cipherFormats.add(format to cipherInfo)
                }
            }
        }

        // Collect all n values that need decryption
        val nFromCipher = cipherFormats.filter { it.second.sp == "n" }.map { it.second.encryptedValue }
        val nFromDirectUrl = directUrlWithNFormats.map { it.second.second }
        val allNValues = (nFromCipher + nFromDirectUrl).distinct()

        // Collect all sig values that need decryption
        val sigValues = cipherFormats.filter { it.second.sp == "sig" }.map { it.second.encryptedValue }.distinct()

        // Batch decrypt if needed
        val decryptedMap = if (allNValues.isNotEmpty() || sigValues.isNotEmpty()) {
            batchDecryptCiphers(allNValues, sigValues, player!!)
        } else {
            emptyMap()
        }

        if ((allNValues.isNotEmpty() || sigValues.isNotEmpty()) && decryptedMap.isEmpty()) {
            error("Decode failed")
        }

        // Build final URLs for cipher formats
        val decryptedFormats = cipherFormats.mapNotNull { (format, cipherInfo) ->
            val decryptedValue = decryptedMap[cipherInfo.encryptedValue] ?: return@mapNotNull null
            val finalUrl = cipherInfo.baseUrl + "&${cipherInfo.sp}=" + URLEncoder.encode(decryptedValue, "UTF-8")
            format to finalUrl
        }

        // Build final URLs for direct URLs with encrypted n parameter using RequestHelper
        val decryptedDirectUrlFormats = directUrlWithNFormats.mapNotNull { (format, urlAndN) ->
            val (originalUrl, encryptedN) = urlAndN
            val decryptedN = decryptedMap[encryptedN] ?: return@mapNotNull null
            val finalUrl = RequestHelper.replaceQueryValue(originalUrl, "n", URLEncoder.encode(decryptedN, "UTF-8"))
                ?: return@mapNotNull null
            format to finalUrl
        }

        return directUrlFormats + decryptedFormats + decryptedDirectUrlFormats
    }
}