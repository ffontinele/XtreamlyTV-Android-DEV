package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xtreamlytv.androidtv.ui.theme.palette

@Composable
fun QrDialog(url: String, onDismiss: () -> Unit) {
    val colors = palette()
    val uriHandler = LocalUriHandler.current
    val matrix = remember(url) {
        runCatching {
            val hints = mapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
            )
            QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 600, 600, hints)
        }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(colors.panelStrong.copy(alpha = 0.97f), RoundedCornerShape(18.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Adicionar provedor via QR Code", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Escaneie o QR ou toque no link abaixo para abrir o painel direto no navegador, ja pareado com este dispositivo.",
                color = colors.muted, fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            if (matrix != null) {
                val n = matrix.width
                Canvas(Modifier.size(280.dp).background(Color.White, RoundedCornerShape(8.dp))) {
                    val cell = size.minDimension / n.toFloat()
                    val qrSize = cell * n
                    val offX = (size.width - qrSize) / 2f
                    val offY = (size.height - qrSize) / 2f
                    for (x in 0 until n) {
                        for (y in 0 until n) {
                            if (matrix.get(x, y)) {
                                drawRect(
                                    Color.Black,
                                    topLeft = Offset(offX + x * cell, offY + y * cell),
                                    size = Size(cell, cell),
                                )
                            }
                        }
                    }
                }
            } else {
                Text("Erro ao gerar QR", color = colors.muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                url,
                color = colors.accent,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { uriHandler.openUri(url) }.padding(vertical = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TvButton("Abrir no navegador", { uriHandler.openUri(url) }, Modifier.weight(1f))
                TvButton("Fechar", onDismiss, Modifier.weight(1f), TvButtonStyle.Secondary)
            }
        }
    }
}
