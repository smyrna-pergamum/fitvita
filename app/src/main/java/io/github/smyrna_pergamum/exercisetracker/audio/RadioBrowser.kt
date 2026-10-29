package io.github.smyrna_pergamum.exercisetracker.audio

import io.github.smyrna_pergamum.exercisetracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Radio Browser (radio-browser.info): a free, community-run directory of ~50,000 stations with an
 * open API and no key. Only the search (name, tag, country) is sent; we ask for HTTPS streams only
 * so playback keeps cleartext traffic blocked. Several public mirrors are tried in turn.
 */
class RadioBrowser(private val mirrors: List<String> = MIRRORS) {

    suspend fun top(countryCode: String?, limit: Int = 60): List<Station> =
        query("stations/search", buildMap {
            countryCode?.let { put("countrycode", it) }
            put("order", "clickcount"); put("reverse", "true")
        }, limit)

    suspend fun search(text: String, tag: String?, countryCode: String?, limit: Int = 60): List<Station> =
        query("stations/search", buildMap {
            if (text.isNotBlank()) put("name", text.trim())
            tag?.let { put("tag", it) }
            countryCode?.let { put("countrycode", it) }
            put("order", "clickcount"); put("reverse", "true")
        }, limit)

    private suspend fun query(path: String, params: Map<String, String>, limit: Int): List<Station> = withContext(Dispatchers.IO) {
        val q = (params + mapOf("hidebroken" to "true", "is_https" to "true", "limit" to limit.toString()))
            .entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        var last: Exception? = null
        for (host in mirrors) {
            try {
                return@withContext parse(get("https://$host/json/$path?$q"))
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no mirror")
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "FitVita/${BuildConfig.VERSION_NAME} (+https://github.com/smyrna-pergamum/fitvita)")
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("http ${c.responseCode}")
            return c.inputStream.use { s ->
                val bytes = s.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) throw IllegalStateException("too large")
                bytes.toString(Charsets.UTF_8)
            }
        } finally {
            c.disconnect()
        }
    }

    @Serializable
    private data class Raw(
        val stationuuid: String = "", val name: String = "", val url_resolved: String = "", val country: String = "",
        val tags: String = "", val codec: String = "", val bitrate: Int = 0,
    )

    companion object {
        val MIRRORS = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info", "at1.api.radio-browser.info")
        private const val MAX_BYTES = 4 * 1024 * 1024
        private val json = Json { ignoreUnknownKeys = true }

        /** Keeps HTTPS streams with a name, removes duplicates. */
        fun parse(body: String): List<Station> = json.decodeFromString<List<Raw>>(body)
            .filter { it.url_resolved.startsWith("https://") && it.name.isNotBlank() && it.stationuuid.isNotBlank() }
            .distinctBy { it.url_resolved }
            .map { Station(it.stationuuid, it.name.trim().take(80), it.url_resolved, it.country, it.tags, it.codec, it.bitrate) }
    }
}

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
