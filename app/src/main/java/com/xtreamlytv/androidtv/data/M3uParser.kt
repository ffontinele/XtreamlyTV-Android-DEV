package com.xtreamlytv.androidtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

data class M3uChannel(
    val id: String,
    val name: String,
    val group: String,
    val logo: String?,
    val url: String,
)

object M3uParser {
    suspend fun parseFromUrl(url: String): List<M3uChannel> = withContext(Dispatchers.IO) {
        val text = fetchWithAuth(url)
        parse(text)
    }
    
    private fun fetchWithAuth(url: String): String {
        val parsed = java.net.URI(url).toURL()
        val userInfo = parsed.userInfo
        if (userInfo != null && userInfo.contains(':')) {
            // URL com credenciais embutidas (http://user:pass@...)
            val (user, pass) = userInfo.split(':', limit = 2)
            val cleanUrl = "${parsed.protocol}://${parsed.host}:${parsed.port}${parsed.path}${if (parsed.query != null) "?${parsed.query}" else ""}"
            val conn = java.net.URI(cleanUrl).toURL().openConnection() as java.net.HttpURLConnection
            val auth = android.util.Base64.encodeToString("$user:$pass".toByteArray(), android.util.Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $auth")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            return conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            // URL normal (sem auth ou com params GET)
            return parsed.readText()
        }
    }
    
    fun parse(text: String): List<M3uChannel> {
        val lines = text.lines()
        val channels = mutableListOf<M3uChannel>()
        var i = 0
        var counter = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                val attrs = parseAttributes(line)
                val name = line.substringAfter(',', "").trim().ifBlank { "Channel ${counter + 1}" }
                var url = ""
                var j = i + 1
                while (j < lines.size) {
                    val next = lines[j].trim()
                    if (next.isNotEmpty() && !next.startsWith("#")) {
                        url = next
                        break
                    }
                    j++
                }
                if (url.isNotEmpty()) {
                    channels.add(M3uChannel(
                        id = "m3u_${++counter}",
                        name = name,
                        group = attrs["group-title"] ?: "M3U Channels",
                        logo = attrs["tvg-logo"],
                        url = url,
                    ))
                }
                i = j
            } else {
                i++
            }
        }
        return channels
    }
    
    private fun parseAttributes(line: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val regex = Regex("""(\w[\w-]*)="([^"]*)"""")
        regex.findAll(line).forEach { match ->
            attrs[match.groupValues[1]] = match.groupValues[2]
        }
        return attrs
    }
}
