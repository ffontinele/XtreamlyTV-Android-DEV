package com.xtreamlytv.androidtv.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.xtreamlytv.androidtv.BuildConfig
import com.xtreamlytv.androidtv.model.AppSettings
import com.xtreamlytv.androidtv.model.AppTheme
import com.xtreamlytv.androidtv.model.Credentials
import com.xtreamlytv.androidtv.model.StreamFormat
import com.xtreamlytv.androidtv.ui.theme.palette
import com.xtreamlytv.androidtv.ui.theme.paletteFor

@Composable
fun SettingsScreen(state: AppUiState, viewModel: AppViewModel) {
    var addingProvider by remember { mutableStateOf(false) }
    var editingProviderId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SettingsCard("Providers", "Your saved Xtream lists. Use, edit or add new ones.") {
                val providers = viewModel.getProviders()
                val activeId = state.credentials?.id
                if (providers.isEmpty()) {
                    Text("No providers saved yet. Add one below.", color = palette().muted, fontSize = 12.sp)
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
                    TvButton("Disconnect", viewModel::disconnect, Modifier.width(120.dp), TvButtonStyle.Danger)
                }
                if (addingProvider) {
                    ProviderEditDialog(
                        title = "Add provider",
                        initial = Credentials(server = "", username = "", password = "", name = ""),
                        onDismiss = { addingProvider = false },
                        onConfirm = { name, cred ->
                            viewModel.addProvider(name, cred)
                            addingProvider = false
                        },
                    )
                }
                editingProviderId?.let { pid ->
                    val existing = providers.find { it.id == pid }
                    if (existing != null) {
                        ProviderEditDialog(
                            title = "Edit provider",
                            initial = existing,
                            onDismiss = { editingProviderId = null },
                            onConfirm = { name, cred ->
                                viewModel.updateProviderById(pid, name, cred)
                                editingProviderId = null
                            },
                        )
                    }
                }
                deleteConfirmId?.let { pid ->
                    AlertDialog(
                        onDismissRequest = { deleteConfirmId = null },
                        title = { Text("Delete provider", fontWeight = FontWeight.Bold) },
                        text = { Text("Remove this provider from the list?") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.deleteProvider(pid); deleteConfirmId = null }) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") }
                        },
                    )
                }
            }
        }

        item {
            SettingsCard("Appearance", "Visual preferences for the living-room surface.") {
                val colors = palette()
                Text("Theme", color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    themeOptions().forEach { (label, theme) ->
                        TvChip(
                            label = label,
                            selected = state.settings.theme == theme,
                            onClick = { viewModel.updateSettings(state.settings.copy(theme = theme)) },
                        )
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text("Preferred stream format", color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    streamFormatOptions().forEach { (label, format) ->
                        TvChip(
                            label = label,
                            selected = state.settings.preferredStreamFormat == format,
                            onClick = { viewModel.updateSettings(state.settings.copy(preferredStreamFormat = format)) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Provider catalogs remain category-scoped so the full library is never loaded into TV memory at once.",
                    color = colors.muted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }

        item {
            SettingsCard("Catalog", "Cached metadata for faster browsing.") {
                val catsTotal = state.categories.values.sumOf { it.size }
                val itemsTotal = state.loadedItems.size
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TvButton(
                        "Clear catalog cache",
                        viewModel::clearCatalogCache,
                        Modifier.width(178.dp),
                        TvButtonStyle.Secondary,
                    )
                    Text(
                        "$catsTotal categories · $itemsTotal items cached",
                        color = palette().muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        item {
            SettingsCard("History", "Resume positions for in-progress programs.") {
                val recent = state.progress.entries
                    .sortedByDescending { it.value.updatedAt }
                    .take(4)
                if (recent.isEmpty()) {
                    Text("No recent playback history yet.", color = palette().muted, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { entry ->
                            HistoryRow(entry.key, entry.value)
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                TvButton(
                    "Clear history",
                    viewModel::clearHistory,
                    Modifier.width(124.dp),
                    TvButtonStyle.Danger,
                )
            }
        }

        item {
            SettingsCard("About", "Build & device information.") {
                ProviderSummaryRow("Version", BuildConfig.VERSION_NAME)
                ProviderSummaryRow("Application ID", "com.github.xtreamlytv.androidtv")
                ProviderSummaryRow("Platform", "Android TV ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                ProviderSummaryRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                ProviderSummaryRow("Playback", "AndroidX Media3 ExoPlayer")
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
            if (active) Text("ATIVA", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        ProviderSummaryRow("Server", cred.server)
        ProviderSummaryRow("Username", cred.username)
        ProviderSummaryRow("Password", if (showPassword) cred.password else "••••••••")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TvButton("Use", onUse, Modifier.width(72.dp), TvButtonStyle.Primary)
            TvButton("Edit", onEdit, Modifier.width(72.dp), TvButtonStyle.Secondary)
            IconButton(onClick = onTogglePassword, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle password",
                    tint = colors.muted,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFF8090))
            }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, Credentials(server = server, username = username, password = password)) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun HistoryRow(key: String, progress: com.xtreamlytv.androidtv.model.PlaybackProgress) {
    val colors = palette()
    val percent = if (progress.durationMs > 0) (progress.positionMs * 100 / progress.durationMs).toInt().coerceIn(0, 100) else 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(key, color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                "${formatDuration(progress.positionMs)} / ${formatDuration(progress.durationMs)} · ${percent}%",
                color = colors.muted,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = palette()
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(colors.panel.copy(alpha = 0.84f), colors.panel.copy(alpha = 0.74f)),
                ),
                RoundedCornerShape(18.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(description, color = colors.muted, fontSize = 11.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun ProviderSummaryRow(label: String, value: String) {
    val colors = palette()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.muted, fontSize = 11.sp, modifier = Modifier.width(108.dp))
        Text(value, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

private fun themeOptions() = listOf(
    "Dusk" to AppTheme.Dusk,
    "Sapphire" to AppTheme.Sapphire,
    "Forest" to AppTheme.Forest,
    "Ember" to AppTheme.Ember,
)

private fun streamFormatOptions() = listOf(
    "HLS" to StreamFormat.HLS,
    "TS" to StreamFormat.MpegTs,
)
