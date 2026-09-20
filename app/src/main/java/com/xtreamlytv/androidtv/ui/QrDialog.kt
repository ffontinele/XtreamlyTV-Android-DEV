package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Hashtable

@Composable
fun QrDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val matrix = remember(url) {
        runCatching {
            val hints = Hashtable<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            hints[EncodeHintType.MARGIN] = 2
            QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 600, 600, hints)
        }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(Color(0xFF181410), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Adicionar provedor via QR Code",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Escaneie com o celular para enviar uma lista pra TV",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                if (matrix != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val count = matrix.width
                        val rawCell = size.minDimension / count
                        val cell = rawCell.coerceAtLeast(1f)
                        val qrSize = cell * count
                        val offsetX = (size.width - qrSize) / 2f
                        val offsetY = (size.height - qrSize) / 2f
                        for (r in 0 until count) {
                            for (c in 0 until count) {
                                if (matrix.get(c, r)) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = androidx.compose.ui.geometry.Offset(offsetX + c * cell, offsetY + r * cell),
                                        size = androidx.compose.ui.geometry.Size(cell, cell),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "Erro ao gerar QR",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Ou digite este endereco no celular:",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                url,
                color = Color(0xFFE8B567),
                fontSize = 11.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(10.dp),
            )
            Spacer(Modifier.height(18.dp))
            TvButton(
                label = "Fechar",
                onClick = onDismiss,
                modifier = Modifier.width(180.dp),
                focusRequester = remember { FocusRequester() },
            )
        }
    }
}
