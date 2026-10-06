package com.xtreamlytv.androidtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

data class M3uChannel(
    val id: String,
    val name: String,
    val group: String,
    val logo: String?,
    val url: String,
)

object M3uParser {
    const val MAX_CHANNELS = 15000

    fun openConnection(url: String): HttpURLConnection {
        val parsed = URI(url).toURL()
        val userInfo = parsed.userInfo
        val conn = if (userInfo != null && userInfo.contains(':')) {
            val clean = buildString {
                append(parsed.protocol); append("://"); append(parsed.host)
                if (parsed.port != -1) { append(':'); append(parsed.port) }
                append(parsed.path)
                if (parsed.query != null) { append('?'); append(parsed.query) }
            }
            URI(clean).toURL().openConnection() as HttpURLConnection
        } else {
            parsed.openConnection() as HttpURLConnection
        }
        if (userInfo != null && userInfo.contains(':')) {
            val auth = android.util.Base64.encodeToString(userInfo.toByteArray(), android.util.Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $auth")
        }
        conn.connectTimeout = 15000
        conn.readTimeout = 120000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
        conn.setRequestProperty("Accept", "*/*")
        val code = conn.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP $code ao baixar a lista")
        return conn
    }

    fun parseReader(r: BufferedReader, limit: Int = MAX_CHANNELS): List<M3uChannel> {
        val channels = ArrayList<M3uChannel>()
        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null
        var counter = 0
        while (true) {
            val line = r.readLine() ?: break
            val t = line.trim()
            if (t.startsWith("#EXTINF:")) {
                val attrs = parseAttributes(t)
                pendingName = t.substringAfter(',', "").trim().ifBlank { null }
                pendingGroup = attrs["group-title"]
                pendingLogo = attrs["tvg-logo"]
            } else if (t.isNotEmpty() && !t.startsWith("#")) {
                channels.add(
                    M3uChannel(
                        id = "m3u_${++counter}",
                        name = pendingName ?: "Channel $counter",
                        group = pendingGroup ?: "M3U Channels",
                        logo = pendingLogo,
                        url = t,
                    )
                )
                pendingName = null; pendingGroup = null; pendingLogo = null
                if (channels.size >= limit) break
            }
        }
        return channels
    }

    suspend fun parseFromUrl(url: String, limit: Int = MAX_CHANNELS): List<M3uChannel> = withContext(Dispatchers.IO) {
        val conn = openConnection(url)
        conn.inputStream.bufferedReader().use { r -> parseReader(r, limit) }
    }

    suspend fun parseFromFile(file: File, limit: Int = MAX_CHANNELS): List<M3uChannel> = withContext(Dispatchers.IO) {
        file.bufferedReader().use { r -> parseReader(r, limit) }
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val regex = Regex("""([\w-]+)="([^"]*)"""")
        for (m in regex.findAll(line)) attrs[m.groupValues[1]] = m.groupValues[2]
        return attrs
    }
}
