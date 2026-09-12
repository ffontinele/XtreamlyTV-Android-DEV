package com.xtreamlytv.androidtv.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context) {
    
    private val backupDir: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "XtreamlyTV"
        ).also { it.mkdirs() }
    
    private val credentialsStore = CredentialsStore(context)
    
    fun getBackupInfo(): BackupInfo? {
        val file = File(backupDir, "backup.xtreamly")
        if (!file.exists()) return null
        return BackupInfo(
            filePath = file.absolutePath,
            lastModified = Date(file.lastModified()),
            sizeBytes = file.length()
        )
    }
    
    fun export(): ExportResult {
        return try {
            val creds = credentialsStore.loadAll()
            val activeId = credentialsStore.loadActive()?.id
            
            if (creds.isEmpty()) {
                return ExportResult.Error("Nenhuma conta configurada para exportar")
            }
            
            val data = mapOf(
                "version" to 1,
                "exported_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
                "providers" to creds.map { cred ->
                    mapOf(
                        "id" to cred.id,
                        "name" to cred.name,
                        "server" to cred.server,
                        "username" to cred.username,
                        "password" to cred.password
                    )
                },
                "active_provider_id" to activeId
            )
            
            val json = org.json.JSONObject(data as Map<*, *>).toString(2)
            val file = File(backupDir, "backup.xtreamly")
            file.writeText(json)
            
            ExportResult.Success(
                file = file,
                providerCount = creds.size,
                message = "Backup exportado com sucesso: ${creds.size} conta(s) salvas em Documents/XtreamlyTV/backup.xtreamly"
            )
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao exportar", e)
            ExportResult.Error("Erro ao exportar: ${e.message}")
        }
    }
    
    fun import(): ImportResult {
        return try {
            val file = File(backupDir, "backup.xtreamly")
            if (!file.exists()) {
                return ImportResult.Error("Arquivo de backup nao encontrado em Documents/XtreamlyTV/backup.xtreamly")
            }
            
            val json = org.json.JSONObject(file.readText())
            val version = json.optInt("version", 1)
            
            if (version != 1) {
                return ImportResult.Error("Versao do backup incompativel: $version")
            }
            
            val providersArr = json.getJSONArray("providers")
            val creds = (0 until providersArr.length()).map { i ->
                val obj = providersArr.getJSONObject(i)
                Credentials(
                    id = obj.getString("id"),
                    name = obj.optString("name", "Default"),
                    server = obj.getString("server"),
                    username = obj.getString("username"),
                    password = obj.getString("password")
                )
            }
            
            val activeId = json.optString("active_provider_id", "").takeIf { it.isNotBlank() }
            
            credentialsStore.saveAll(creds)
            if (activeId != null) {
                credentialsStore.setActive(activeId)
            }
            
            ImportResult.Success(
                providerCount = creds.size,
                message = "Backup restaurado com sucesso: ${creds.size} conta(s) importadas"
            )
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao importar", e)
            ImportResult.Error("Erro ao importar: ${e.message}")
        }
    }
}

data class BackupInfo(
    val filePath: String,
    val lastModified: Date,
    val sizeBytes: Long
)

sealed class ExportResult {
    data class Success(val file: File, val providerCount: Int, val message: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

sealed class ImportResult {
    data class Success(val providerCount: Int, val message: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
