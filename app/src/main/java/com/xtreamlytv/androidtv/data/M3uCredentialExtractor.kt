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
        val generic = linkedMapOf<Pair<String, String>, Int>()

        for (u in urls) {
            val p = kotlin.runCatching { URI(u).toURL() }.getOrNull() ?: continue
            val server = serverOf(p)
            val path = p.path ?: ""
            // Regra 1: API Xtream explicita (imediata, como no original)
            val q = p.query
            if (q != null && (path.contains("get.php") || path.contains("player_api.php"))) {
                val user = Regex("username=([^&]+)").find(q)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                val pass = Regex("password=([^&]+)").find(q)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                    return Credentials(server = server, username = user, password = pass, name = "M3U Import", kind = "xtream")
                }
            }
            // Regra 2: /live|movie|series/user/pass/ (imediata, como no original)
            Regex("/(?:live|movie|series)/([^/]+)/([^/]+)/").find(path)?.let { m ->
                return Credentials(server = server, username = m.groupValues[1], password = m.groupValues[2], name = "M3U Import", kind = "xtream")
            }
            // Regra 3: generica SOMENTE com ID numerico + corroboracao 2+ (anti-fantasma)
            val segs = path.split('/').filter { it.isNotEmpty() }
            if (segs.size >= 3) {
                val last = segs.last()
                if (Regex("^\\d+\\.(ts|m3u8|mp4|mkv|avi|mov|flv|wmv)$", RegexOption.IGNORE_CASE).containsMatchIn(last)) {
                    val key = segs[segs.size - 3] to segs[segs.size - 2]
                    generic[server to key.first + "/" + key.second] = (generic[server to key.first + "/" + key.second] ?: 0) + 1
                }
            }
        }
        val safe = generic.entries.firstOrNull { it.value >= 2 } ?: return null
        val (srv, pair) = safe.key
        val (user, pass) = pair.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return Credentials(server = srv, username = user, password = pass, name = "M3U Import", kind = "xtream")
    }
}
