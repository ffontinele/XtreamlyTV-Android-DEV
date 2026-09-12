package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.data.DownloadTask
import com.xtreamlytv.androidtv.data.OfflineFile
import com.xtreamlytv.androidtv.ui.theme.palette
import java.util.Locale
import kotlinx.coroutines.delay

private fun mb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

@Composable
fun OfflineVideosScreen(state: AppUiState, viewModel: AppViewModel) {
    val downloads by viewModel.downloads.collectAsState()
    var files by remember { mutableStateOf<List<OfflineFile>>(emptyList()) }
    val colors = palette()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshDownloads()
            files = viewModel.scanOffline()
            delay(1500)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val active = downloads.filter {
            it.status == DownloadTask.Status.PENDING ||
                it.status == DownloadTask.Status.RUNNING ||
                it.status == DownloadTask.Status.PAUSED
        }
        val failed = downloads.filter { it.status == DownloadTask.Status.FAILED }

        if (active.isNotEmpty()) {
            Text("Baixando agora", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            active.forEach { task ->
                Column(
                    Modifier.fillMaxWidth().background(colors.panel.copy(alpha = 0.86f), RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(task.title, color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(task.progress / 100f).height(6.dp).background(colors.accent, RoundedCornerShape(3.dp)))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            task.progress.toString() + "% · " + mb(task.bytesDownloaded) + " / " + mb(task.totalBytes),
                            color = colors.muted,
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TvButton("Cancelar", { viewModel.cancelDownload(task.downloadId) }, Modifier.width(110.dp), TvButtonStyle.Secondary)
                    }
                }
            }
        }

        if (failed.isNotEmpty()) {
            Text("Falhas", color = colors.danger, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            failed.forEach { task ->
                Row(
                    Modifier.fillMaxWidth().background(colors.panel.copy(alpha = 0.86f), RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(task.title + " (falhou)", color = colors.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    TvButton("Remover", { viewModel.cancelDownload(task.downloadId) }, Modifier.width(110.dp), TvButtonStyle.Secondary)
                }
            }
        }

        Text("Vídeos offline (" + files.size + ")", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (files.isEmpty() && active.isEmpty()) {
            Text(
                "Nenhum vídeo baixado ainda. Use o botão Download em um filme ou episódio para assistir offline.",
                color = colors.muted,
                fontSize = 10.sp,
            )
        }
        files.forEach { file ->
            Row(
                Modifier.fillMaxWidth().background(colors.panel.copy(alpha = 0.86f), RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(file.name, color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(mb(file.sizeBytes), color = colors.muted, fontSize = 9.sp)
                }
                Spacer(Modifier.width(8.dp))
                TvButton("Tocar", { viewModel.playOffline(file) }, Modifier.width(100.dp), leading = "▶")
                Spacer(Modifier.width(8.dp))
                TvButton("Excluir", { viewModel.deleteOffline(file) }, Modifier.width(100.dp), TvButtonStyle.Secondary, leading = "🗑")
            }
        }
    }
}
