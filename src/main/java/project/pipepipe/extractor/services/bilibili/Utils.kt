package project.pipepipe.extractor.services.bilibili

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.encodeUtf8
import org.brotli.dec.BrotliInputStream
import org.cache2k.Cache
import org.cache2k.Cache2kBuilder
import project.pipepipe.extractor.ExtractorContext
import project.pipepipe.shared.downloader.Downloader
import project.pipepipe.extractor.utils.json.requireArray
import project.pipepipe.extractor.utils.json.requireDouble
import project.pipepipe.extractor.utils.json.requireLong
import project.pipepipe.extractor.utils.json.requireString
import java.io.*
import java.math.BigInteger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.zip.Inflater

object Utils {
    private val XOR_CODE = BigInteger("23442827791579")
    private val MASK_CODE = BigInteger("2251799813685247")
    private val MAX_AID = BigInteger.ONE.shiftLeft(51)
    private val BASE = BigInteger("58")

    private const val TABLE = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"

    private val RENDER_DATA_PATTERN = Pattern.compile(
        "<script id=\"__RENDER_DATA__\" type=\"application/json\">(.*?)</script>",
        Pattern.DOTALL
    )

    private var wbiMixinKey: String? = null
    private var wbiMixinKeyDate: LocalDate? = null

    private val webIdCache: Cache<String, String> = Cache2kBuilder.of(String::class.java, String::class.java)
        .entryCapacity(256)
        .expireAfterWrite(86400, TimeUnit.SECONDS)
        .loader(::getWebId)
        .build()


    private val charMap = mutableMapOf<Char, Int>().apply {
        TABLE.forEachIndexed { index, char ->
            put(char, index)
        }
    }

    var isClientAPIMode = false

    fun av2bv(aid: Long): String {
        val bytes = charArrayOf('B', 'V', '1', '0', '0', '0', '0', '0', '0', '0', '0', '0')
        var bvIndex = bytes.size - 1
        var tmp = (MAX_AID.or(BigInteger.valueOf(aid))).xor(XOR_CODE)

        while (tmp.compareTo(BigInteger.ZERO) > 0) {
            bytes[bvIndex] = TABLE[tmp.mod(BASE).toInt()]
            tmp = tmp.divide(BASE)
            bvIndex -= 1
        }

        // Swap characters
        bytes[3] = bytes[9].also { bytes[9] = bytes[3] }
        bytes[4] = bytes[7].also { bytes[7] = bytes[4] }

        return String(bytes)
    }

    fun bv2av(bvid: String): Long {
        val bvidArr = bvid.toCharArray()

        // Swap characters back
        bvidArr[3] = bvidArr[9].also { bvidArr[9] = bvidArr[3] }
        bvidArr[4] = bvidArr[7].also { bvidArr[7] = bvidArr[4] }

        val subString = String(bvidArr, 3, bvidArr.size - 3)
        var tmp = BigInteger.ZERO

        for (char in subString.toCharArray()) {
            tmp = tmp.multiply(BASE).add(BigInteger.valueOf(TABLE.indexOf(char).toLong()))
        }

        return tmp.and(MASK_CODE).xor(XOR_CODE).toLong()
    }

    fun isFirstP(url: String): Boolean {
        if (!url.contains("p=")) {
            return true
        }
        val p = url.split("p=")[1].split("&")[0]
        return p == "1"
    }

    fun getUrl(url: String, id: String): String {
        var p = "1"
        if (url.contains("p=")) {
            p = url.split("p=")[1].split("&")[0]
        }
        return "https://api.bilibili.com/x/web-interface/view?bvid=$id&p=$p"
    }

    fun getPureBV(id: String): String {
        return id.split("?")[0]
    }

    fun buildUserVideosUrlClientAPI(mid: String, lastVideoAid: Long): String {
        val params = linkedMapOf<String, String>()
        params["vmid"] = mid
        params["mobi_app"] = "android"
        if (lastVideoAid > 0) {
            params["aid"] = lastVideoAid.toString()
        }
        params["order"] = "pubdate"

        val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "${BiliBiliLinks.QUERY_USER_VIDEOS_CLIENT_API_URL}?$queryString"
    }

    private fun getWh(width: Int, height: Int): IntArray {
        val rnd = ThreadLocalRandom.current().nextInt(114)
        return intArrayOf(
            2 * width + 2 * height + 3 * rnd,
            4 * width - height + rnd,
            rnd
        )
    }

    private fun getOf(scrollTop: Int, scrollLeft: Int): IntArray {
        val rnd = ThreadLocalRandom.current().nextInt(514)
        return intArrayOf(
            3 * scrollTop + 2 * scrollLeft + rnd,
            4 * scrollTop - 4 * scrollLeft + 2 * rnd,
            rnd
        )
    }

    private fun getWebId(mid: String): String {
        return ""
//        val headers = BilibiliService.Companion.getHeaders(BiliBiliLinks.WWW_REFERER, null).apply {
//            put("Upgrade-Insecure-Requests", "1")
//            put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
//            put("Priority", "u=0, i")
//        }
//
//        val response = runBlocking { ExtractorContext.downloader.get("https://space.bilibili.com/$mid", headers).bodyAsText() }
//
//        val matcher = RENDER_DATA_PATTERN.matcher(response)
//        val renderData = if (matcher.find()) {
//            matcher.group(1)
//        } else {
//            throw IOException("Invalid space page response: ${response}")
//        }
//
//        val decodedRenderData = URLDecoder.decode(renderData, StandardCharsets.UTF_8.name())
//        val json = ExtractorContext.objectMapper.readTree(decodedRenderData)
//
//        return json.requireString("access_id")
    }

    fun getDmImgParams(): LinkedHashMap<String, String> {
        val params = linkedMapOf<String, String>()
        params["dm_img_list"] = "[]"
        params["dm_img_str"] = DeviceForger.requireRandomDevice().webGlVersionBase64
        params["dm_cover_img_str"] = DeviceForger.requireRandomDevice().webGLRendererInfoBase64

        val wh = getWh(
            DeviceForger.requireRandomDevice().innerWidth,
            DeviceForger.requireRandomDevice().innerHeight
        )
        val of = getOf(0, 0)

        params["dm_img_inter"] = "{\"ds\":[],\"wh\":[${wh.joinToString(",")}],\"of\":[${of.joinToString(",")}]}"

        return params
    }

    fun buildUserVideosUrlWebAPI(id: String, cookie: String, page: String = "1"): String {
        val params = linkedMapOf<String, String>()

        params["mid"] = id
        params["order"] = "pubdate"
        params["ps"] = "25"

        params["pn"] = page

        params["order_avoided"] = "true"
        params["platform"] = "web"
        params["web_location"] = "333.1387"

        params.putAll(getDmImgParams())

        return getWbiResult(BiliBiliLinks.QUERY_USER_VIDEOS_WEB_API_URL, params, cookie)
    }

    fun getWbiResult(baseUrl: String, params: LinkedHashMap<String, String>, cookie: String): String {
        val wbiResults = encWbi(params, cookie)

        params["w_rid"] = wbiResults[0]
        params["wts"] = wbiResults[1]
        return "$baseUrl?${createQueryString(params)}"
    }

    fun getRecordApiUrl(url: String): String {
        var pn = "1"
        if (url.contains("pn=")) {
            pn = url.split("pn=")[1].split("&")[0]
        }

        val mid = url.split("space.bilibili.com/")[1].split("/")[0]
        val sid = url.split("sid=")[1]

        return "https://api.bilibili.com/x/series/archives?mid=$mid&series_id=$sid&only_normal=true&sort=desc&pn=$pn&ps=30"
    }

    fun getMidFromRecordUrl(url: String): String {
        return url.split("space.bilibili.com/")[1].split("/")[0]
    }

    fun getMidFromRecordApiUrl(url: String): String {
        return url.split("mid=")[1].split("&")[0]
    }

    fun bcc2srt(bcc: JsonNode): String {
        val array = bcc.requireArray("body")
        val result = StringBuilder()

        array.forEachIndexed { index, item ->
            result.append(index + 1).append("\n")
                .append(sec2time(item.requireDouble("from")))
                .append(" --> ")
                .append(sec2time(item.requireDouble("to"))).append("\n")
                .append(item.requireString("content")).append("\n\n")
        }

        return result.toString()
    }

    fun sec2time(sec: Double): String {
        val h = (sec / 3600).toInt()
        val m = (sec / 60 % 60).toInt()
        val s = (sec % 60).toInt()
        val f = ((sec * 1000) % 1000).toInt()
        return String.format("%02d:%02d:%02d,%03d", h, m, s, f)
    }

    fun decompress(data: ByteArray): ByteArray? {
        val decompressor = Inflater(true)
        decompressor.reset()
        decompressor.setInput(data)

        return try {
            ByteArrayOutputStream(data.size).use { outputStream ->
                val buf = ByteArray(1024)
                while (!decompressor.finished()) {
                    val i = decompressor.inflate(buf)
                    outputStream.write(buf, 0, i)
                }
                outputStream.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            decompressor.end()
        }
    }

    fun decompressZlib(data: ByteArray): ByteArray {
        val decompresser = Inflater()
        decompresser.reset()
        decompresser.setInput(data)

        return try {
            ByteArrayOutputStream(data.size).use { outputStream ->
                val buf = ByteArray(1024)
                while (!decompresser.finished()) {
                    val i = decompresser.inflate(buf)
                    outputStream.write(buf, 0, i)
                }
                outputStream.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            data
        } finally {
            decompresser.end()
        }
    }

    fun decompressBrotli(body: ByteArray): String {
        return BufferedReader(InputStreamReader(BrotliInputStream(ByteArrayInputStream(body))))
            .lines()
            .collect(Collectors.joining())
    }

    fun getNextPageFromCurrentUrl(
        currentUrl: String,
        varName: String,
        addCount: Int,
        shouldTryInit: Boolean = false,
        initValue: String = "0",
        urlType: String = "&"
    ): String {
        var url = currentUrl
        val varString = "&$varName="
        val varStringVariant = "?$varName="

        if (shouldTryInit && !url.contains(varString) && !url.contains(varStringVariant)) {
            val modifiedVarString = varString.replace("&", urlType)
            url += modifiedVarString + initValue
        }

        val actualVarString = when {
            url.contains(varStringVariant) -> varStringVariant
            url.contains(varString) -> varString
            else -> error("Could not find $varName in url: $url")
        }

        val offset = url.split(Pattern.quote(actualVarString))[1].split(Pattern.quote("&"))[0]
        return url.replace(actualVarString + offset, actualVarString + (offset.toInt() + addCount))
    }

    fun getNextPageFromCurrentUrl(currentUrl: String, varName: String, addCount: Int): String {
        return getNextPageFromCurrentUrl(currentUrl, varName, addCount, false, "0", "&")
    }

    private fun getMixinKey(ae: String): String {
        val oe = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41,
            13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
        )

        val le = oe.joinToString("") { ae[it].toString() }
        return le.substring(0, 32)
    }

    fun formatParamWithPercentSpace(value: String?): String {
        return try {
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(e)
        }
    }

    fun createQueryString(params: Map<String, String>): String {
        return params.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    private fun createQueryStringWithPercentSpace(params: Map<String, String>): String {
        return params.entries.joinToString("&") {
            "${formatParamWithPercentSpace(it.key)}=${formatParamWithPercentSpace(it.value)}"
        }
    }

    private fun encWbi(params: Map<String, String>, cookie: String): Array<String> {
        return try {
            val currentDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))

            if (wbiMixinKey == null || wbiMixinKeyDate?.isBefore(currentDate) == true) {
                val responseBody = runBlocking { ExtractorContext.downloader.get(
                    BiliBiliLinks.WBI_IMG_URL,
                    BilibiliService.Companion.getHeadersWithCookie(BiliBiliLinks.WWW_REFERER, cookie)
                ).bodyAsText() }
                val imgUrl = responseBody.split("\"img_url\":\"")[1].split("\"")[0]
                val subUrl = responseBody.split("\"sub_url\":\"")[1].split("\"")[0]
                val imgValue = imgUrl.split("/").last().split(".")[0]
                val subValue = subUrl.split("/").last().split(".")[0]

                wbiMixinKey = getMixinKey(imgValue + subValue)
                wbiMixinKeyDate = currentDate
            }

            val wts = Math.round(System.currentTimeMillis() / 1000f)
            val sortedParams = TreeMap(params)
            sortedParams["wts"] = wts.toString()

            val ae = createQueryStringWithPercentSpace(sortedParams)
            val toHash = ae + wbiMixinKey

            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(toHash.toByteArray(StandardCharsets.UTF_8))
            val wRid = bytesToHex(digest)

            arrayOf(wRid, wts.toString())
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun requestUserSpaceResponse(
        downloader: Downloader,
        url: String,
        headers: Map<String, String>
    ): JsonNode {
        val maxTry = 6
        var currentTry = maxTry
        var responseBody = ""

        while (currentTry > 0) {
            responseBody =  downloader.get(url, headers).bodyAsText()
            try {
                val responseJson = ExtractorContext.objectMapper.readTree(responseBody)
                val code = responseJson.requireLong("code")

                when (code) {
                    0L -> return responseJson
                    -352L -> {
                        // blocked risk control
                        DeviceForger.regenerateRandomDevice() // try to regenerate a new one
                        currentTry -= 1
                    }
                    else -> {
                        // Other error codes, continue trying
                        currentTry -= 1
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                error("Failed parse response body: $responseBody")
            }
        }

        val device = DeviceForger.requireRandomDevice()
        val msg = """
            BiliBili blocked us, we retried $maxTry times, the last forged device is:
            ${device.info()}
            Try to refresh, or report this!
            $responseBody
        """.trimIndent()

        isClientAPIMode = !isClientAPIMode // flip API mode
        DeviceForger.regenerateRandomDevice() // try to regenerate a new one
        error(msg)
    }

    fun encodeToBase64SubString(raw: String): String {
        val byteString = raw.encodeUtf8()
        val encodedString = byteString.base64()
        return encodedString.substring(0, encodedString.length - 2)
    }
}