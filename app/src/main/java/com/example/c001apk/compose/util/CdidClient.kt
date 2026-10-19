package com.example.c001apk.compose.util

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.Deflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Coolapk iOS CDID 获取协议。
 *
 * 请求体为 zlib/deflate 后的 AES-128-ECB 数据，响应使用相同密钥加密 JSON。
 * 只有服务端登记过的 CDID 才能稳定通过点赞、回复等写接口的设备校验。
 */
object CdidClient {

    private const val SDK_VERSION = "4.0.0"
    private const val BUNDLE_ID = "com.coolapk.app"
    private const val CUSTOMER_ID = "qfywy_shuzilm_cn"
    private const val PLACEHOLDER_PREFIX = "D20000000000000000000000"
    private const val MAX_RESPONSE_BYTES = 1_048_576

    private val AES_KEY = byteArrayOf(
        '6'.code.toByte(), 'E'.code.toByte(), '4'.code.toByte(), '8'.code.toByte(),
        'A'.code.toByte(), '7'.code.toByte(), 'D'.code.toByte(), '2'.code.toByte(),
        0, 0, 0, 0, 0, 0, 0, 0,
    )

    private val HOSTS = listOf(
        "iuni.telecome.cn",
        "global-iuni.checktrustworthiness.com",
    )

    fun fetch(device: IosDevice, idfv: String, installId: String): String {
        var last: Exception? = null
        for (host in HOSTS) {
            try {
                return fetchFrom(host, device, idfv, installId)
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("CDID 服务不可用", last)
    }

    private fun fetchFrom(
        host: String,
        device: IosDevice,
        idfv: String,
        installId: String,
    ): String {
        val timestamp = System.currentTimeMillis() / 1000L
        val payload = buildPayload(device, idfv, installId, timestamp)
        val body = aesEncrypt(deflate(Gson().toJson(payload).toByteArray(Charsets.UTF_8)))
        val url = "https://$host/i/mdna/report" +
            "?v=1.0" +
            "&p=${md5Hex("$BUNDLE_ID.dafqw4k1.$SDK_VERSION")}" +
            "&r=0&n=0&l=200" +
            "&h=${md5Hex("0.psgS79lU")}"

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("sdkVer", SDK_VERSION)
            setRequestProperty("appname", BUNDLE_ID)
            setRequestProperty("User-Agent", "CoolMarket/${IosHeaders.APP_CODE}")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                ?: throw IOException("CDID HTTP $code")
            val raw = readLimited(stream)
            if (code !in 200..299) throw IOException("CDID HTTP $code")
            return parseResponse(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(
        device: IosDevice,
        idfv: String,
        installId: String,
        timestamp: Long,
    ): Map<String, Any> {
        val (encodedIdfv, checksum) = encodeUuid(idfv, timestamp)
        return linkedMapOf(
            "kU4" to CUSTOMER_ID,
            "Ke6" to BUNDLE_ID,
            "Gl1" to "iOS",
            "Ih2" to SDK_VERSION,
            "AAA" to "v1.0",
            "BBB" to "v1.0",
            "OW5" to IosHeaders.APP_VERSION,
            "bj9" to IosHeaders.APP_CODE,
            "NP1" to "CoolMarket",
            "Ls7" to "酷安",
            "tP9" to device.systemVersion,
            "th1" to timestamp,
            "tf8" to linkedMapOf(
                "fW2" to "",
                "sf2W" to "",
                "js2" to encodedIdfv,
                "sj2s" to checksum,
                "se1" to "",
            ),
            "Pm4" to "",
            "mT5" to 1,
            "Zy4" to installId,
            "Vv1" to 0,
            "FU2" to encodeField(device.hardwareId, timestamp),
            "TE9" to "CN",
            "Dg2" to "0.01",
        )
    }

    private fun parseResponse(raw: ByteArray): String {
        val text = String(aesDecrypt(raw), Charsets.UTF_8)
        val json = JsonParser.parseString(text)
        if (!json.isJsonObject) throw IOException("CDID 响应不是 JSON 对象: $text")
        val obj = json.asJsonObject
        val err = obj.get("err")?.takeIf { it.isJsonPrimitive }?.asString
        if (err != "0") throw IOException("CDID 返回错误 err=$err: $text")
        val rid = obj.get("rid")?.takeIf { it.isJsonPrimitive }?.asString
        if (rid == null || rid.length != 48 || !rid.startsWith("D") ||
            rid.startsWith(PLACEHOLDER_PREFIX)
        ) {
            throw IOException("服务端未下发 CDID: $text")
        }
        return rid
    }

    /** 字段变换与请求协议保持逐字节一致，供 golden 测试校验。 */
    internal fun encodeField(value: String, timestamp: Long): String {
        if (value.isEmpty()) return ""
        require(value.all { it.code in 32..126 }) { "只支持可打印 ASCII" }
        val key = String.format(Locale.US, "%03d", timestamp % 1000)
        val shift = key.toInt() % 50 + 1
        val shifted = buildString(value.length) {
            value.forEach { c -> append((((c.code - 32 + shift) % 95) + 32).toChar()) }
        }
        val mode = key[0].digitToInt() % 4
        val n = shifted.length
        return when (mode) {
            0 -> {
                val mid = n / 2
                val left = shifted.substring(0, mid).reversed()
                val right = shifted.substring(mid + n % 2).reversed()
                buildString {
                    if (n % 2 == 1) append(shifted[mid])
                    for (i in left.indices) {
                        append(right[i])
                        append(left[i])
                    }
                }
            }
            1 -> buildString {
                for (i in 0 until n / 2) {
                    append(shifted[n - 1 - i])
                    append(shifted[i])
                }
                if (n % 2 == 1) append(shifted[n / 2])
            }
            2 -> buildString {
                var i = n - 1
                while (i >= 0) {
                    append(shifted[i])
                    i -= 2
                }
                i = n - 2
                while (i >= 0) {
                    append(shifted[i])
                    i -= 2
                }
            }
            else -> buildString {
                var i = 0
                while (i < n) {
                    append(shifted[i])
                    i += 2
                }
                i = 1
                while (i < n) {
                    append(shifted[i])
                    i += 2
                }
            }
        }
    }

    internal fun encodeUuid(value: String, timestamp: Long): Pair<String, String> {
        if (value.isEmpty()) return "" to ""
        require(value.all { it.code in 32..126 }) { "IDFV 必须是 ASCII" }
        val tens = ((timestamp % 100) / 10).toInt()
        val ones = (timestamp % 10).toInt()
        val checksum = md5Hex((value.sumOf { it.code } + tens).toString()).take(7)
        var trimmed = value
        if (trimmed.length >= 10) {
            val index = trimmed.length - ones - 1
            trimmed = trimmed.removeRange(index, index + 1)
        } else if (trimmed.length > 1) {
            trimmed = trimmed.dropLast(1)
        }
        return encodeField(trimmed, timestamp) to checksum
    }

    internal fun aesEncrypt(data: ByteArray): ByteArray =
        Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(AES_KEY, "AES"))
            doFinal(data)
        }

    internal fun aesDecrypt(data: ByteArray): ByteArray {
        if (data.isEmpty() || data.size % 16 != 0) {
            throw IOException("无效的 AES 密文长度")
        }
        return Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(AES_KEY, "AES"))
            doFinal(data)
        }
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        deflater.setInput(data)
        deflater.finish()
        val output = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(4096)
        try {
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
        } finally {
            deflater.end()
        }
        return output.toByteArray()
    }

    private fun readLimited(stream: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        stream.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_RESPONSE_BYTES) {
                    throw IOException("CDID 响应超过 1 MiB")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xFF) }
}
