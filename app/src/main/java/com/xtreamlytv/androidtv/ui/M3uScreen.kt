package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.ui.theme.palette

@Composable
fun M3uScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    var urlInput by remember { mutableStateOf("https://github.com/iptv-com/iptv/raw/refs/heads/main/lists/brazil.m3u") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { viewModel.importM3uFromFile(it) } }
    val colors = palette()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Load M3U Playlist",
            color = colors.text,
            fontSize = 24.sp,
        )
        
        TextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("M3U URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )

        
        if (state.items.isNotEmpty() && state.items.first().id.startsWith("m3u_")) {
            Text(
                "Loaded ${state.items.size} channels",
                color = colors.muted,
                fontSize = 14.sp,
            )
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items) { item ->
                    M3uChannelCard(
                        item = item,
                        onClick = { viewModel.play(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun M3uChannelCard(
    item: CatalogItem,
    onClick: () -> Unit,
) {
    val colors = palette()
    
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Text(
            item.name,
            color = colors.text,
            fontSize = 14.sp,
            maxLines = 2,
        )
    }
}
