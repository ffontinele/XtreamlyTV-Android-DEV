package com.xtreamlytv.androidtv.data

import com.xtreamlytv.androidtv.model.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

object M3uCredentialExtractor {

    suspend fun fetchFirstLines(url: String, maxLines: Int = 200, maxBytes: Long = 256 * 1024): String =
        withContext(Dispatchers.IO) {
            val conn = M3uParser.openConnection(url)
            conn.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder()
                var lines = 0
                var bytes = 0L
                while (lines < maxLines && bytes < maxBytes) {
                    val line = r.readLine() ?: break
                    sb.appendLine(line)
                    bytes += line.length + 1
                    lines++
                    if (lines >= 5 && (sb.contains("username=") || sb.contains("/live/") || sb.contains("/movie/") || sb.contains("/series/"))) {
                        break
                    }
                }
                sb.toString()
            }
        }

    fun extract(text: String): Credentials? {
        val urlPattern = Regex("https?://[^\\s\"'<>]+")
        val urls = urlPattern.findAll(text).map { it.value }.take(15).toList()
        // 1) Query params: get.php/player_api.php com username/password
        for (u in urls) {
            val r = kotlin.runCatching {
                val p = URI(u).toURL()
                val q = p.query ?: return@runCatching null
                val user = Regex("username=([^&]+)").find(q)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                val pass = Regex("password=([^&]+)").find(q)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                if (user.isNullOrBlank() || pass.isNullOrBlank()) return@runCatching null
                val server = buildString {
                    append(p.protocol); append("://"); append(p.host)
                    if (p.port > 0) { append(':'); append(p.port) }
                }
                Credentials(server = server, username = user, password = pass, name = "M3U Import", kind = "xtream")
            }.getOrNull()
            if (r != null) return r
        }
        // 2) Path /live/user/pass/  /movie/  /series/
        for (u in urls) {
            val r = kotlin.runCatching {
                val p = URI(u).toURL()
                val path = p.path ?: return@runCatching null
                val m = Regex("/(?:live|movie|series)/([^/]+)/([^/]+)/").find(path) ?: return@runCatching null
                val server = buildString {
                    append(p.protocol); append("://"); append(p.host)
                    if (p.port > 0) { append(':'); append(p.port) }
                }
                Credentials(server = server, username = m.groupValues[1], password = m.groupValues[2], name = "M3U Import", kind = "xtream")
            }.getOrNull()
            if (r != null) return r
        }
        return null
    }
}
