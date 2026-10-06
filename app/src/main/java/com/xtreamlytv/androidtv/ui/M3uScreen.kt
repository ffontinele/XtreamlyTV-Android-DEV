package com.xtreamlytv.androidtv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.ui.theme.palette

@Composable
fun M3uScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    var urlInput by remember { mutableStateOf("https://iptv-org.github.io/iptv/countries/br.m3u") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importM3uFromFile(it) }
    }
    val colors = palette()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "DBG items=${state.items.size} | loading=${state.loading} | err=${state.error?.take(40)}",
            color = Color.Yellow,
            fontSize = 12.sp,
        )
        Text("Load M3U Playlist", color = colors.text, fontSize = 24.sp)
        TextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("M3U URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Load",
                color = colors.accent,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent.copy(alpha = 0.2f))
                    .clickable { viewModel.loadM3uFromUrl(urlInput) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
            Text(
                "📂 Ler arquivo .m3u",
                color = colors.text,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable { filePicker.launch("*/*") }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (state.loading) {
            Text("Carregando lista... (pode levar alguns segundos)", color = colors.accent, fontSize = 14.sp)
        }
        state.error?.let { err ->
            Text(
                err,
                color = if (err.startsWith("OK")) colors.accent else Color(0xFFE87968),
                fontSize = 13.sp,
            )
        }
        if (state.items.isNotEmpty()) {
            Text("Loaded ${state.items.size} channels", color = colors.muted, fontSize = 14.sp)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.items.forEach { item ->
                    Text(
                        item.name,
                        color = colors.text,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                            .clickable { viewModel.play(item) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
