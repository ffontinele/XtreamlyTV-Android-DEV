package com.xtreamlytv.androidtv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xtreamlytv.androidtv.ui.theme.palette

@Composable
fun M3uImportDialog(
    onDismiss: () -> Unit,
    onImportUrl: (String) -> Unit,
    onImportFile: (Uri) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val colors = palette()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) onImportFile(uri)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(560.dp)
                .verticalScroll(rememberScrollState())
                .background(colors.panelStrong.copy(alpha = 0.97f), RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(18.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Adicionar lista M3U", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Cole a URL da lista ou escolha o arquivo baixado. O app extrairá automaticamente as credenciais (se houver) e conectará como Xtream; caso contrário, carregará como lista pública.",
                color = colors.muted, fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            TvTextField("URL da lista M3U", url, { url = it }, placeholder = "http://servidor:8080/get.php?username=...")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TvButton(
                    "Importar URL",
                    {
                        if (url.isBlank()) {
                            message = "Informe uma URL."
                        } else {
                            message = "Processando..."
                            onImportUrl(url)
                        }
                    },
                    Modifier.width(150.dp),
                )
                TvButton(
                    "📂 Escolher arquivo",
                    { filePicker.launch("*/*") },
                    Modifier.width(180.dp),
                    TvButtonStyle.Secondary,
                )
            }
            message?.let {
                Text(it, color = colors.accent, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TvButton("Cancelar", onDismiss, Modifier.width(110.dp), TvButtonStyle.Secondary)
            }
        }
    }
}
