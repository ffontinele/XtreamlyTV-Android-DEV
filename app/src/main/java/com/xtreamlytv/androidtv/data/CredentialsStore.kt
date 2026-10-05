package com.xtreamlytv.androidtv.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.xtreamlytv.androidtv.model.Credentials
import org.json.JSONArray
import org.json.JSONObject

class CredentialsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "xtreamlytv.credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun migrateIfNeeded() {
        val hasOld = preferences.contains("server")
        val hasNew = preferences.contains("providers_json")
        if (hasOld && !hasNew) {
            val server = preferences.getString("server", "").orEmpty().trim()
            val username = preferences.getString("username", "").orEmpty().trim()
            val password = preferences.getString("password", "").orEmpty()
            if (server.isNotBlank() && username.isNotBlank()) {
                val cred = Credentials(server = server, username = username, password = password, name = "Default")
                saveAll(listOf(cred))
                setActive(cred.id)
            }
        }
    }

    fun loadAll(): List<Credentials> {
        migrateIfNeeded()
        val json = preferences.getString("providers_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Credentials(
                    server = obj.getString("server"),
                    username = obj.getString("username"),
                    password = obj.getString("password"),
                    id = obj.getString("id"),
                    name = obj.optString("name", "Default"),
                    kind = obj.optString("kind", "xtream"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveAll(providers: List<Credentials>) {
        val arr = JSONArray()
        providers.forEach { cred ->
            val obj = JSONObject().apply {
                put("id", cred.id)
                put("name", cred.name)
                put("server", cred.server.trimEnd('/'))
                put("username", cred.username.trim())
                put("password", cred.password)
                put("kind", cred.kind)
            }
            arr.put(obj)
        }
        preferences.edit().putString("providers_json", arr.toString()).apply()
    }

    fun loadActive(): Credentials? {
        val activeId = preferences.getString("active_provider_id", null) ?: return loadAll().firstOrNull()
        return loadAll().find { it.id == activeId }
    }

    fun setActive(id: String) {
        preferences.edit().putString("active_provider_id", id).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
