package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.xtreamlytv.androidtv.data.CloudSync
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xtreamlytv.androidtv.model.Credentials
import com.xtreamlytv.androidtv.ui.theme.palette

@Composable
fun ProviderManagerCard(state: AppUiState, viewModel: AppViewModel) {
    val colors = palette()
    var addingProvider by remember { mutableStateOf(false) }
    var editingProviderId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(colors.panel.copy(alpha = 0.84f), colors.panel.copy(alpha = 0.74f))),
                RoundedCornerShape(18.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("Providers", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Your saved Xtream lists. Use, edit or add new ones.", color = colors.muted, fontSize = 11.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(4.dp))
        val providers = viewModel.getProviders()
        val activeId = state.credentials?.id
        if (providers.isEmpty()) {
            Text("No providers saved yet. Add one below.", color = colors.muted, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { cred ->
                    var showPassword by remember(cred.id) { mutableStateOf(false) }
                    ProviderRow(
                        cred = cred,
                        active = cred.id == activeId,
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                        onUse = { viewModel.useProvider(cred.id) },
                        onEdit = { editingProviderId = cred.id },
                        onDelete = { deleteConfirmId = cred.id },
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TvButton("+ Add provider", { addingProvider = true }, Modifier.width(150.dp))
            TvButton("QR Code", { showQr = true }, Modifier.width(110.dp), TvButtonStyle.Secondary)
            TvButton("Disconnect", viewModel::disconnect, Modifier.width(140.dp), TvButtonStyle.Danger)
        }
        if (showQr) {
            QrDialog(url = CloudSync.getQrUrl(context), onDismiss = { showQr = false })
        }
        if (addingProvider) {
            ProviderEditDialog(
                title = "Add provider",
                initial = Credentials(server = "", username = "", password = "", name = ""),
                onDismiss = { addingProvider = false },
                onConfirm = { name, cred -> viewModel.addProvider(name, cred); addingProvider = false },
            )
        }
        editingProviderId?.let { pid ->
            val existing = providers.find { it.id == pid }
            if (existing != null) {
                ProviderEditDialog(
                    title = "Edit provider",
                    initial = existing,
                    onDismiss = { editingProviderId = null },
                    onConfirm = { name, cred -> viewModel.updateProviderById(pid, name, cred); editingProviderId = null },
                )
            }
        }
        deleteConfirmId?.let { pid ->
            Dialog(onDismissRequest = { deleteConfirmId = null }) {
                Column(
                    Modifier
                        .width(460.dp)
                        .background(colors.panelStrong.copy(alpha = 0.97f), RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Delete provider", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Remove this provider from the list?", color = colors.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        TvButton("Delete", { viewModel.deleteProvider(pid); deleteConfirmId = null }, Modifier.width(110.dp), TvButtonStyle.Danger)
                        TvButton("Cancel", { deleteConfirmId = null }, Modifier.width(110.dp), TvButtonStyle.Secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    cred: Credentials,
    active: Boolean,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = palette()
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (active) colors.accent.copy(alpha = 0.10f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .border(1.dp, if (active) colors.accent.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (active) colors.accent else colors.muted, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(cred.name, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (active) Text("ACTIVE", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Server", color = colors.muted, fontSize = 11.sp, modifier = Modifier.width(108.dp))
            Text(cred.server, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Username", color = colors.muted, fontSize = 11.sp, modifier = Modifier.width(108.dp))
            Text(cred.username, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Password", color = colors.muted, fontSize = 11.sp, modifier = Modifier.width(108.dp))
            Text(if (showPassword) cred.password else "••••••••", color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TvButton("Use", onUse, Modifier.width(64.dp), TvButtonStyle.Primary)
            TvButton("Edit", onEdit, Modifier.width(64.dp), TvButtonStyle.Secondary)
            TvButton(if (showPassword) "Hide" else "Show", onTogglePassword, Modifier.width(84.dp), TvButtonStyle.Secondary)
            TvButton("Delete", onDelete, Modifier.width(88.dp), TvButtonStyle.Danger)
        }
    }
}

@Composable
private fun ProviderEditDialog(
    title: String,
    initial: Credentials,
    onDismiss: () -> Unit,
    onConfirm: (name: String, credentials: Credentials) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var server by remember { mutableStateOf(initial.server) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var showPassword by remember { mutableStateOf(false) }
    val colors = palette()
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
            Text(title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Fill in the provider details below.", color = colors.muted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            TvTextField("Name", name, { name = it }, placeholder = "My list")
            TvTextField("Server URL", server, { server = it }, placeholder = "http://provider.example:port")
            TvTextField("Username", username, { username = it }, placeholder = "Provider username")
            TvTextField("Password", password, { password = it }, placeholder = "Provider password", password = !showPassword)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TvButton(if (showPassword) "Hide password" else "Show password", { showPassword = !showPassword }, Modifier.width(180.dp), TvButtonStyle.Secondary)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TvButton("Save", { onConfirm(name, Credentials(server = server, username = username, password = password)) }, Modifier.width(110.dp))
                TvButton("Cancel", onDismiss, Modifier.width(110.dp), TvButtonStyle.Secondary)
            }
        }
    }
}
