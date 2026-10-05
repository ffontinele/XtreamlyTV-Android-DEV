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
                    if (lines >= 6 && (sb.contains("username=") || sb.contains("/live/") || sb.contains("/movie/"))) break
                }
                sb.toString()
            }
        }

    private fun serverOf(p: java.net.URL): String = buildString {
        append(p.protocol); append("://"); append(p.host)
        if (p.port > 0) { append(':'); append(p.port) }
    }

    fun extract(text: String): Credentials? {
        val urls = Regex("https?://[^\\s\"'<>]+").findAll(text).map { it.value }.take(300).toList()

        // Regra 1: query params (get.php / player_api.php com username & password)
        for (u in urls) {
            val r = kotlin.runCatching {
                val p = URI(u).toURL()
                val q = p.query ?: return@runCatching null
                val user = Regex("username=([^&]+)").find(q)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                val pass = Regex("password=([^&]+)").find(q)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                if (user.isNullOrBlank() || pass.isNullOrBlank()) return@runCatching null
                Credentials(server = serverOf(p), username = user, password = pass, name = "M3U Import", kind = "xtream")
            }.getOrNull()
            if (r != null) return r
        }

        // Regra 2: caminho explicito /live|movie|series/user/pass/
        for (u in urls) {
            val r = kotlin.runCatching {
                val p = URI(u).toURL()
                val m = Regex("/(?:live|movie|series)/([^/]+)/([^/]+)/").find(p.path ?: "") ?: return@runCatching null
                Credentials(server = serverOf(p), username = m.groupValues[1], password = m.groupValues[2], name = "M3U Import", kind = "xtream")
            }.getOrNull()
            if (r != null) return r
        }

        // Regra 3: generica - .../user/pass/arquivo.midia
        for (u in urls) {
            val r = kotlin.runCatching {
                val p = URI(u).toURL()
                val segs = (p.path ?: "").split('/').filter { it.isNotEmpty() }
                if (segs.size < 3) return@runCatching null
                if (!Regex("(?i)\\.(ts|m3u8|mp4|mkv|avi|mov|flv|wmv)$").containsMatchIn(segs.last())) return@runCatching null
                val user = segs[segs.size - 3]
                val pass = segs[segs.size - 2]
                if (user.isBlank() || pass.isBlank()) return@runCatching null
                Credentials(server = serverOf(p), username = user, password = pass, name = "M3U Import", kind = "xtream")
            }.getOrNull()
            if (r != null) return r
        }
        return null
    }
}
