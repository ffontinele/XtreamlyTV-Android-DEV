package com.xtreamlytv.androidtv.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object CloudSync {
    private const val SUPABASE_URL = "https://fyqpqqrtmgcsjnygxkqv.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFFzIiwicmVmIjoiZnlxcHFxcnRtZ2Nzam55Z3hrcXYiLCJyb2xlIjoiYW5vbiIsImlhdCI6MTc4ODEzMTc2MSwiZXhwIjoyMTAzNzA3NzYxfQ.QInVAAU7i0GNSkWRzP6HedqkP5U6HJBDRhpQyey0eh8"
    private const val POLL_INTERVAL_MS = 5000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val random = Random.Default

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = "XTV-" + randomId(6).uppercase()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    fun getDeviceKey(context: Context): String {
        val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
        var key = prefs.getString("device_key", null)
        if (key == null) {
            key = randomId(16) + randomId(16)
            prefs.edit().putString("device_key", key).apply()
        }
        return key
    }

    private fun randomId(len: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(len) { repeat(len) { append(chars[random.nextInt(chars.length)]) } }
    }

    fun getQrUrl(context: Context): String {
        val id = getDeviceId(context)
        val key = getDeviceKey(context)
        return "https://ffontinele.github.io/ZUI_IPTV_Player_portugues/painel_web/?id=" +
            java.net.URLEncoder.encode(id, "UTF-8") +
            "&key=" + java.net.URLEncoder.encode(key, "UTF-8")
    }

    fun registerDevice(context: Context) {
        val id = getDeviceId(context)
        val key = getDeviceKey(context)
        val body = JSONObject().apply {
            put("device_id", id)
            put("device_key", key)
        }
        val req = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/devices")
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_KEY")
            .addHeader("Content-Type", "application/json")
            .post(okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"), body.toString()
            ))
            .build()
        runCatching { client.newCall(req).execute().close() }
    }

    data class PendingPlaylist(val id: Int, val name: String, val server: String, val username: String, val password: String)

    fun pollPending(context: Context): List<PendingPlaylist> {
        val deviceId = getDeviceId(context)
        val deviceKey = getDeviceKey(context)
        val url = "$SUPABASE_URL/rest/v1/playlists?device_id=eq.$deviceId&loaded=eq.false&order=sent_at.desc"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_KEY")
            .addHeader("x-zui-device-id", deviceId)
            .addHeader("x-zui-device-key", deviceKey)
            .get()
            .build()
        return runCatching {
            val response = client.newCall(req).execute()
            val body = response.body()?.string() ?: "[]"
            response.close()
            val arr = org.json.JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                PendingPlaylist(
                    id = obj.optInt("id"),
                    name = obj.optString("playlist_name", "Cloud Sync"),
                    server = obj.optString("playlist_url", ""),
                    username = obj.optString("xtream_username", ""),
                    password = obj.optString("xtream_password", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun markLoaded(context: Context, playlistId: Int) {
        val deviceId = getDeviceId(context)
        val deviceKey = getDeviceKey(context)
        val body = JSONObject().apply { put("loaded", true) }
        val req = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/playlists?id=eq.$playlistId")
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_KEY")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-zui-device-id", deviceId)
            .addHeader("x-zui-device-key", deviceKey)
            .patch(okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"), body.toString()
            ))
            .build()
        runCatching { client.newCall(req).execute().close() }
    }
}
