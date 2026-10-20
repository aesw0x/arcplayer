package com.aeswox.arcplayer.metadata

import android.util.Log
import com.aeswox.arcplayer.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * The whole HTTP layer of the metadata feature.
 *
 * Handled by hand because the app deliberately carries no networking dependency: one connection, one
 * read, and a JSON object back. Every failure shape — DNS, a 404, a body that is HTML because a
 * captive portal answered — arrives as null, since the caller's job is to try the next catalogue
 * rather than to report which one broke.
 */
internal object HttpJson {

    private const val TAG = "ArcMeta"
    private const val CONNECT_MS = 8_000
    private const val READ_MS = 12_000
    private const val USER_AGENT = "ArcPlayer/1.0"

    fun get(url: String): JSONObject? = exchange(url, null)

    fun post(url: String, body: String): JSONObject? = exchange(url, body)

    /** Raw bytes for artwork. Null means the same thing as it does for JSON: nothing usable arrived. */
    fun getBytes(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "image/*")
        }
        try {
            if (connection.responseCode !in 200..299) {
                if (BuildConfig.DEBUG) Log.w(TAG, "${connection.responseCode} $url")
                return null
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.onFailure {
        if (BuildConfig.DEBUG) Log.w(TAG, "failed: ${it.javaClass.simpleName} ${url.substringBefore('?')}")
    }.getOrNull()

    /** Query parameters, because a title with an ampersand or a comma in it is ordinary. */
    fun url(baseUrl: String, vararg params: Pair<String, String>): String {
        if (params.isEmpty()) return baseUrl
        val query = StringBuilder()
        for ((key, value) in params) {
            if (query.isNotEmpty()) query.append('&')
            query.append(encode(key)).append('=').append(encode(value))
        }
        return "$baseUrl?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun exchange(address: String, body: String?): JSONObject? = runCatching {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        try {
            if (connection.responseCode !in 200..299) {
                // A catalogue that answers 403 and one that has no entry look identical to the caller,
                // and both store a refusal. Without this line a moved endpoint is invisible.
                if (BuildConfig.DEBUG) Log.w(TAG, "${connection.responseCode} $address")
                return null
            }
            val text = connection.inputStream.use { stream ->
                val opened = if ("gzip".equals(connection.contentEncoding, ignoreCase = true)) {
                    GZIPInputStream(stream)
                } else stream
                ByteArrayOutputStream().use { sink ->
                    opened.copyTo(sink)
                    sink.toString("UTF-8")
                }
            }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }.onFailure {
        // A reset mid-handshake and a body that is not JSON both arrive here as null, and both look
        // exactly like a catalogue that has never heard of the film. The status codes above are not
        // enough to find a link that drops: this is the only place that failure is ever visible.
        if (BuildConfig.DEBUG) Log.w(TAG, "failed: ${it.javaClass.simpleName} ${address.substringBefore('?')}")
    }.getOrNull()
}
