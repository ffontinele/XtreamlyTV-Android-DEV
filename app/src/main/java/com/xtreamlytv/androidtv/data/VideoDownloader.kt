package com.xtreamlytv.androidtv.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class DownloadTask(
    val downloadId: Long,
    val itemId: String,
    val title: String,
    val url: String,
    val filePath: String,
    val status: Status = Status.PENDING,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
) {
    enum class Status { PENDING, RUNNING, PAUSED, SUCCESSFUL, FAILED, CANCELED }
}

class VideoDownloader(private val context: Context) {

    private val dm: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _active = MutableStateFlow<List<DownloadTask>>(emptyList())
    val active: StateFlow<List<DownloadTask>> = _active.asStateFlow()

    private val downloadDir: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "XtreamlyTV/Downloads"
        ).also { it.mkdirs() }

    fun enqueue(itemId: String, title: String, url: String, fileName: String): Long {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(downloadDir, safeName)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(safeName.substringAfterLast('.', "mp4")) ?: "video/mp4"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Baixando via XtreamlyTV")
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(targetFile))
            .setVisibleInDownloadsUi(false)

        val id = dm.enqueue(request)
        val task = DownloadTask(
            downloadId = id,
            itemId = itemId,
            title = title,
            url = url,
            filePath = targetFile.absolutePath,
            status = DownloadTask.Status.PENDING,
        )
        _active.value = _active.value + task
        return id
    }

    fun queryProgress() {
        val current = _active.value
        if (current.isEmpty()) return
        val updated = current.map { task ->
            val q = DownloadManager.Query().setFilterById(task.downloadId)
            dm.query(q)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val status = when (cursor.getInt(statusIdx)) {
                        DownloadManager.STATUS_PENDING -> DownloadTask.Status.PENDING
                        DownloadManager.STATUS_RUNNING -> DownloadTask.Status.RUNNING
                        DownloadManager.STATUS_PAUSED -> DownloadTask.Status.PAUSED
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadTask.Status.SUCCESSFUL
                        DownloadManager.STATUS_FAILED -> DownloadTask.Status.FAILED
                        else -> task.status
                    }
                    val bytes = cursor.getLong(bytesIdx)
                    val total = cursor.getLong(totalIdx)
                    val progress = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 99) else 0
                    task.copy(status = status, bytesDownloaded = bytes, totalBytes = total, progress = progress)
                } else task
            } ?: task
        }
        _active.value = updated
    }

    fun cancel(downloadId: Long) {
        dm.remove(downloadId)
        _active.value = _active.value.filterNot { it.downloadId == downloadId }
    }

    fun deleteFile(filePath: String) {
        runCatching { File(filePath).takeIf { it.exists() }?.delete() }
    }

    fun dropByPath(filePath: String) {
        _active.value = _active.value.filterNot { it.filePath == filePath }
    }

    fun listFiles(): List<OfflineFile> =
        downloadDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.map {
                OfflineFile(
                    name = it.nameWithoutExtension.replace('_', ' '),
                    path = it.absolutePath,
                    sizeBytes = it.length(),
                    lastModified = it.lastModified(),
                )
            }
            ?.sortedByDescending { f -> f.lastModified }
            ?: emptyList()

    fun isDownloaded(itemId: String): String? =
        _active.value.firstOrNull { it.itemId == itemId && it.status == DownloadTask.Status.SUCCESSFUL }?.filePath

    fun listDownloaded(): List<DownloadTask> =
        _active.value.filter { it.status == DownloadTask.Status.SUCCESSFUL }
}

data class OfflineFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
)
