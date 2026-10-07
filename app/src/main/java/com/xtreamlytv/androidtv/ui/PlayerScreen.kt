package com.xtreamlytv.androidtv.ui
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.widget.Toast

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.core.view.isVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.TextStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.PlayerRequest
import com.xtreamlytv.androidtv.ui.theme.palette
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    request: PlayerRequest,
    favorite: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val focusRequester = remember { FocusRequester() }
    var candidateIndex by remember(request.item.id) { mutableIntStateOf(0) }
    var controlsVisible by remember(request.item.id) { mutableStateOf(true) }
    var playing by remember(request.item.id) { mutableStateOf(true) }
    var position by remember(request.item.id) { mutableLongStateOf(request.startPositionMs) }
    var duration by remember(request.item.id) { mutableLongStateOf(0L) }
    var errorMessage by remember(request.item.id) { mutableStateOf<String?>(null) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var gestureIndicator by remember { mutableStateOf<String?>(null) }
    var volumeFraction by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()) }
    var lastVolumeInt by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var trackGroups by remember(request.item.id) { mutableStateOf<Tracks?>(null) }
    var showAudioPanel by remember { mutableStateOf(false) }
    var showSubPanel by remember { mutableStateOf(false) }
    var autoApplied by remember(request.item.id) { mutableStateOf(false) }
    var subtitleCues by remember(request.item.id) { mutableStateOf<List<Cue>>(emptyList()) }
    var showSubSettings by remember { mutableStateOf(false) }
    var subStyle by remember { mutableStateOf(loadSubStyle(context)) }
    val allGroups = trackGroups?.groups ?: emptyList<Tracks.Group>()
    val audioGroups = allGroups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val subGroups = allGroups.filter { it.type == C.TRACK_TYPE_TEXT }

    val player = remember(request.item.id, candidateIndex) {
        ExoPlayer.Builder(context).build().apply {
            val candidate = request.urlCandidates.getOrElse(candidateIndex) { request.urlCandidates.first() }
            setMediaItem(MediaItem.fromUri(candidate))
            if (request.startPositionMs > 0L && request.item.type != ContentType.LIVE) seekTo(request.startPositionMs)
            playWhenReady = true
            prepare()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(900L)
            seekFeedback = null
        }
    }
    LaunchedEffect(gestureIndicator) {
        if (gestureIndicator != null) {
            delay(900L)
            gestureIndicator = null
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(900L)
            seekFeedback = null
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(900L)
            seekFeedback = null
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) errorMessage = null
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onTracksChanged(tracks: Tracks) {
                trackGroups = tracks
                if (!autoApplied) {
                    autoApplied = true
                    applyPreferredTracks(player, tracks, context)
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                subtitleCues = cueGroup.cues
            }

            override fun onPlayerError(error: PlaybackException) {
                if (candidateIndex + 1 < request.urlCandidates.size) candidateIndex += 1
                else errorMessage = error.message ?: "Unable to play this stream."
            }
        }
        player.addListener(listener)
        onDispose {
            if (request.item.type != ContentType.LIVE) {
                onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.takeIf { it > 0L } ?: 0L)
            }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        var persistTicks = 0
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            persistTicks += 1
            if (persistTicks >= 10 && request.item.type != ContentType.LIVE) {
                onProgress(position, duration)
                persistTicks = 0
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(4000)
            controlsVisible = false
        }
    }

    fun showControls() { controlsVisible = true }

    fun adjustVolume(delta: Float) {
        if (maxVolume <= 0) return
        volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
        val newVolumeInt = (volumeFraction * maxVolume).toInt().coerceIn(0, maxVolume)
        if (newVolumeInt != lastVolumeInt) {
            lastVolumeInt = newVolumeInt
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolumeInt, 0)
            }
        }
        val pct = (volumeFraction * 100).toInt()
        gestureIndicator = "🔊 $pct%"
    }

    fun adjustBrightness(delta: Float) {
        activity?.let { act ->
            val params = act.window.attributes
            val current = params.screenBrightness.takeIf { it >= 0f } ?: 0.5f
            val next = (current + delta).coerceIn(0.01f, 1f)
            params.screenBrightness = next
            act.window.attributes = params
            val pct = (next * 100).toInt()
            gestureIndicator = "☀️ $pct%"
        }
    }
    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
        showControls()
    }

    BackHandler { onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (showAudioPanel || showSubPanel || showSubSettings) {
                    if (event.key == Key.Back) {
                        showAudioPanel = false
                        showSubPanel = false
                        showSubSettings = false
                    }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { togglePlayback(); true }
                    Key.DirectionLeft -> { player.seekTo((player.currentPosition - 30_000L).coerceAtLeast(0L)); showControls(); true }
                    Key.DirectionRight -> { player.seekTo(player.currentPosition + 30_000L); showControls(); true }
                    Key.DirectionUp -> { onPrevious(); true }
                    Key.DirectionDown -> { onNext(); true }
                    Key.Menu -> { onToggleFavorite(); showControls(); true }
                    Key.Back -> { onBack(); true }
                    else -> { showControls(); false }
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    runCatching { subtitleView?.isVisible = false }
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        // GestureOverlay: toques + arrastos verticais (volume direita / brilho esquerda)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player, maxVolume) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (offset.x > size.width / 2f) {
                                player.seekTo(player.currentPosition + 30_000L)
                                seekFeedback = "⏩ +30s"
                            } else {
                                player.seekTo((player.currentPosition - 30_000L).coerceAtLeast(0L))
                                seekFeedback = "⏪ -30s"
                            }
                            showControls()
                        },
                        onTap = { if (controlsVisible) togglePlayback() else showControls() },
                    )
                }
                .pointerInput(maxVolume) {
                    detectVerticalDragGestures { change, dragAmount ->
                        val delta = -dragAmount / size.height.toFloat()
                        if (change.position.x > size.width / 2f) {
                            adjustVolume(delta * 1.5f)
                        } else {
                            adjustBrightness(delta * 1.5f)
                        }
                    }
                },
        )

        if (controlsVisible) {
            PlayerControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                request = request,
                favorite = favorite,
                playing = playing,
                position = position,
                duration = duration,
                onSeekBack = { player.seekTo((player.currentPosition - 30_000L).coerceAtLeast(0L)); showControls() },
                onTogglePlay = { togglePlayback() },
                onSeekForward = { player.seekTo(player.currentPosition + 30_000L); showControls() },
                onToggleFavorite = { onToggleFavorite(); showControls() },
                onSeekFraction = { frac -> player.seekTo((frac.coerceIn(0f, 1f) * duration).toLong()); showControls() },
                onPreviousEpisode = { onPrevious(); showControls() },
                onNextEpisode = { onNext(); showControls() },
                hasAudio = audioGroups.isNotEmpty(),
                hasSubs = subGroups.isNotEmpty(),
                onOpenAudio = { showAudioPanel = true; controlsVisible = true },
                onOpenSubs = { showSubPanel = true; controlsVisible = true },
            )
        }

        val subtitleText = runCatching { subtitleCues.joinToString("\n") { it.text?.toString().orEmpty() }.trim() }.getOrDefault("")
        if (SUB_RENDERER_ON && subtitleText.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (subStyle.positionTop) Alignment.TopCenter else Alignment.BottomCenter,
            ) {
                Text(
                    subtitleText,
                    color = SUB_COLORS[subStyle.colorIdx].copy(alpha = SUB_OPACITY[subStyle.opacityIdx]),
                    fontSize = SUB_SIZES[subStyle.sizeIdx].sp,
                    fontWeight = if (subStyle.bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = if (subStyle.shadow) Shadow(Color.Black, Offset(2f, 2f), 8f) else Shadow(Color.Transparent, Offset.Zero, 0f),
                    ),
                    modifier = Modifier
                        .padding(
                            top = if (subStyle.positionTop) 24.dp else 0.dp,
                            bottom = if (subStyle.positionTop) 0.dp else 110.dp,
                        )
                        .background(subBgColorOf(subStyle))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        if (showAudioPanel || showSubPanel) {
            val forSubs = showSubPanel
            val groups = if (forSubs) subGroups else audioGroups
            val type = if (forSubs) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
            val data = buildTrackRows(player, groups, type)
            TrackPanelOverlay(
                title = if (forSubs) "Faixas de Legendas" else "Faixas de Áudio",
                rows = data.rows,
                onDismiss = { showAudioPanel = false; showSubPanel = false },
                onOpenSettings = if (forSubs) {
                    { showSubPanel = false; showSubSettings = true }
                } else {
                    null
                },
                onRowClick = { i ->
                    handleTrackRowClick(i, data, player, groups, type, context)
                    showAudioPanel = false
                    showSubPanel = false
                },
            )
        }

        if (showSubSettings) {
            SubtitleSettingsOverlay(
                style = subStyle,
                onChange = { novo -> subStyle = novo; saveSubStyle(context, novo) },
                onDismiss = { showSubSettings = false },
            )
        }

        errorMessage?.let { message ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE6071014), RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                Text(message, color = Color.White, fontSize = 14.sp)
            }
        }

        seekFeedback?.let { label ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xB3071014), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        gestureIndicator?.let { label ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xB3071014), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        seekFeedback?.let { label ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xB3071014), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        seekFeedback?.let { label ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xB3071014), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlayerControls(
    modifier: Modifier = Modifier,
    request: PlayerRequest,
    favorite: Boolean,
    playing: Boolean,
    position: Long,
    duration: Long,
    onSeekBack: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onSeekFraction: (Float) -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    hasAudio: Boolean = false,
    hasSubs: Boolean = false,
    onOpenAudio: () -> Unit = {},
    onOpenSubs: () -> Unit = {},
) {
    val colors = palette()
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xDC071014))
            .padding(horizontal = 34.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandLockup(iconSize = 28.dp, wordmarkSize = 14)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(request.item.name, color = colors.text, fontSize = 15.sp, maxLines = 1)
                Text(
                    request.item.channelNumber?.let { "Channel $it" } ?: request.item.type.title(),
                    color = colors.muted,
                    fontSize = 9.sp,
                )
            }
            if (favorite) Text("♥", color = colors.danger, fontSize = 14.sp)
            Spacer(Modifier.width(14.dp))
            Text(if (request.item.type == ContentType.LIVE) "LIVE" else formatDuration(position), color = colors.accent, fontSize = 11.sp)
        }
        if (request.item.type != ContentType.LIVE && duration > 0L) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .pointerInput(duration) {
                        detectTapGestures { offset -> onSeekFraction(offset.x / size.width.toFloat()) }
                    }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> onSeekFraction(offset.x / size.width.toFloat()) },
                            onHorizontalDrag = { change, _ -> onSeekFraction(change.position.x / size.width.toFloat()) },
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)))
                Box(
                    Modifier
                        .fillMaxWidth((position.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(colors.accent, RoundedCornerShape(50)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("← 30s", color = colors.muted, fontSize = 9.sp, modifier = Modifier.clickable { onSeekBack() }.padding(6.dp))
            Text(if (playing) "OK Pause" else "OK Play", color = colors.text, fontSize = 10.sp, modifier = Modifier.clickable { onTogglePlay() }.padding(6.dp))
            Text("30s →", color = colors.muted, fontSize = 9.sp, modifier = Modifier.clickable { onSeekForward() }.padding(6.dp))
            Text("MENU ${if (favorite) "Unfavorite" else "Favorite"}", color = colors.muted, fontSize = 9.sp, modifier = Modifier.clickable { onToggleFavorite() }.padding(6.dp))
            if (hasAudio) Text("Aa", color = colors.accent, fontSize = 10.sp, modifier = Modifier.clickable { onOpenAudio() }.padding(6.dp))
            if (hasSubs) Text("CC", color = colors.accent, fontSize = 10.sp, modifier = Modifier.clickable { onOpenSubs() }.padding(6.dp))
            if (request.item.type == ContentType.LIVE) {
                Text("↑/↓ Change channel", color = colors.muted, fontSize = 9.sp)
            } else {
                Text("${formatDuration(position)} / ${formatDuration(duration)}", color = colors.muted, fontSize = 9.sp)
            }
            if (request.queue.size > 1) {
                if (request.item.type == ContentType.LIVE) {
                    Text("◀ Canal ant.", color = colors.accent, fontSize = 9.sp, modifier = Modifier.clickable { onPreviousEpisode() }.padding(6.dp))
                    Text("Canal próx. ▶", color = colors.accent, fontSize = 9.sp, modifier = Modifier.clickable { onNextEpisode() }.padding(6.dp))
                } else {
                    Text("◀ Ep ant.", color = colors.accent, fontSize = 9.sp, modifier = Modifier.clickable { onPreviousEpisode() }.padding(6.dp))
                    Text("Ep próx. ▶", color = colors.accent, fontSize = 9.sp, modifier = Modifier.clickable { onNextEpisode() }.padding(6.dp))
                }
            }
        }
    }
}

// ================= SELETOR DE TRILHAS (Fase 1) =================

private data class TrackRows(
    val rows: List<Pair<String, Boolean>>,
    val map: List<Pair<Int, Int>>,
)

private val LANG_NAMES = mapOf(
    "en" to "English", "en-us" to "English (US)", "en-gb" to "English (UK)",
    "ja" to "Japanese", "pt" to "Portuguese",
    "pt-br" to "Portuguese (Brazil)", "pt-pt" to "Portuguese (Portugal)",
    "es" to "Spanish", "es-419" to "Spanish (Latin America)", "es-mx" to "Spanish (Mexico)",
    "fr" to "French", "fr-ca" to "French (Canada)", "de" to "German", "it" to "Italian",
    "ru" to "Russian", "ko" to "Korean", "zh" to "Chinese",
    "zh-cn" to "Chinese (Simplified)", "zh-tw" to "Chinese (Traditional)",
    "ar" to "Arabic", "tr" to "Turkish", "pl" to "Polish", "ms" to "Malay",
    "id" to "Indonesian", "th" to "Thai", "vi" to "Vietnamese", "hi" to "Hindi",
    "nl" to "Dutch", "sv" to "Swedish", "no" to "Norwegian", "da" to "Danish",
    "fi" to "Finnish", "el" to "Greek", "he" to "Hebrew", "fa" to "Persian",
    "uk" to "Ukrainian", "cs" to "Czech", "hu" to "Hungarian", "ro" to "Romanian",
    "bg" to "Bulgarian", "hr" to "Croatian", "sr" to "Serbian", "sk" to "Slovak",
    "ca" to "Catalan", "fil" to "Filipino", "bn" to "Bengali", "ta" to "Tamil",
    "te" to "Telugu", "ur" to "Urdu", "sw" to "Swahili", "af" to "Afrikaans",
    "sq" to "Albanian", "et" to "Estonian", "lv" to "Latvian", "lt" to "Lithuanian",
    "sl" to "Slovenian", "mk" to "Macedonian", "is" to "Icelandic"
)

private val ISO3 = mapOf(
    "por" to "pt", "eng" to "en", "spa" to "es", "fre" to "fr", "fra" to "fr",
    "ger" to "de", "deu" to "de", "jpn" to "ja", "kor" to "ko", "rus" to "ru",
    "ita" to "it", "chi" to "zh", "zho" to "zh", "pol" to "pl", "may" to "ms",
    "msa" to "ms", "ind" to "id", "tha" to "th", "vie" to "vi", "hin" to "hi",
    "tur" to "tr", "ara" to "ar", "ukr" to "uk", "cze" to "cs", "ces" to "cs",
    "hun" to "hu", "ron" to "ro", "rum" to "ro", "bul" to "bg", "hrv" to "hr",
    "srp" to "sr", "slk" to "sk", "slo" to "sl", "cat" to "ca", "nld" to "nl",
    "dut" to "nl", "swe" to "sv", "nor" to "no", "dan" to "da", "fin" to "fi",
    "ell" to "el", "gre" to "el", "heb" to "he", "fas" to "fa", "per" to "fa",
    "tgl" to "fil", "fil" to "fil"
)

private fun langKey(lang: String?): String? {
    val l = lang?.lowercase()?.replace("_", "-") ?: return null
    val base = l.substringBefore('-')
    val two = ISO3[base] ?: base
    val region = l.substringAfter('-', "")
    return if (region.isEmpty()) two else "$two-$region"
}

private fun langName(code: String): String? {
    LANG_NAMES[code]?.let { return it }
    val base = code.substringBefore('-')
    val region = code.substringAfter('-', "").uppercase()
    val baseName = LANG_NAMES[base] ?: return null
    return if (region.isEmpty()) baseName else "$baseName ($region)"
}

private fun score(key: String?, wanted: String): Int {
    if (key == null) return -1
    return if (wanted == "pt") when {
        key == "pt-br" -> 3
        key == "pt" -> 2
        key.startsWith("pt-") -> 1
        else -> -1
    } else when {
        key == wanted -> 2
        key.startsWith("$wanted-") -> 1
        else -> -1
    }
}

private fun keyFor(type: Int) = if (type == C.TRACK_TYPE_AUDIO) "audio_lang" else "sub_lang"

private fun setDisabled(player: Player, type: Int, disabled: Boolean) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(type, disabled)
        .build()
}

private fun selectTrackAt(player: Player, groups: List<Tracks.Group>, gi: Int, ti: Int, type: Int) {
    val g = groups[gi]
    val override = TrackSelectionOverride(g.mediaTrackGroup, listOf(ti))
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(type, false)
        .clearOverridesOfType(type)
        .addOverride(override)
        .build()
}

private fun selectPreferred(player: Player, groups: List<Tracks.Group>, type: Int, wanted: String): Boolean {
    var best = 0
    var bg = -1
    var bt = -1
    groups.forEachIndexed { gi, g ->
        for (ti in 0 until g.length) {
            val sc = score(langKey(g.getTrackFormat(ti).language), wanted)
            if (sc > best) { best = sc; bg = gi; bt = ti }
        }
    }
    if (bg < 0) return false
    selectTrackAt(player, groups, bg, bt, type)
    return true
}

private fun applyPreferredTracks(player: Player, tracks: Tracks, context: Context) {
    val prefs = context.getSharedPreferences("xui_track_prefs", 0)
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val subGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    when (val a = prefs.getString("audio_lang", "pt")) {
        "none" -> setDisabled(player, C.TRACK_TYPE_AUDIO, true)
        else -> selectPreferred(player, audioGroups, C.TRACK_TYPE_AUDIO, a!!)
    }
    when (val s = prefs.getString("sub_lang", "pt")) {
        "none" -> setDisabled(player, C.TRACK_TYPE_TEXT, true)
        else -> if (!selectPreferred(player, subGroups, C.TRACK_TYPE_TEXT, s!!)) {
            setDisabled(player, C.TRACK_TYPE_TEXT, true)
        }
    }
}

private fun buildTrackRows(player: Player, groups: List<Tracks.Group>, type: Int): TrackRows {
    val rows = mutableListOf<Pair<String, Boolean>>()
    val map = mutableListOf<Pair<Int, Int>>()
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(type)
    var ptSelected = false
    groups.forEach { g ->
        for (ti in 0 until g.length) {
            if (g.isTrackSelected(ti) && score(langKey(g.getTrackFormat(ti).language), "pt") > 0) ptSelected = true
        }
    }
    rows.add("★  Padrão (Português)" to ptSelected)
    rows.add("Disable" to disabled)
    val counts = mutableMapOf<String, Int>()
    groups.forEachIndexed { gi, g ->
        for (ti in 0 until g.length) {
            val f = g.getTrackFormat(ti)
            val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.lowercase()
            val ln = lang?.let { langName(it) }
            val fallback = if (type == C.TRACK_TYPE_TEXT) "Legenda" else "Áudio"
            var base = f.label?.takeIf { it.isNotBlank() } ?: ln ?: fallback
            val key = base.lowercase()
            val n = (counts[key] ?: 0) + 1
            counts[key] = n
            if (n > 1) base = "$base $n"
            rows.add((base + (lang?.let { " [$it]" } ?: "")) to g.isTrackSelected(ti))
            map.add(gi to ti)
        }
    }
    return TrackRows(rows, map)
}

private fun handleTrackRowClick(
    i: Int,
    data: TrackRows,
    player: Player,
    groups: List<Tracks.Group>,
    type: Int,
    context: Context,
) {
    val prefs = context.getSharedPreferences("xui_track_prefs", 0)
    when {
        i == 0 -> {
            if (selectPreferred(player, groups, type, "pt")) {
                prefs.edit().putString(keyFor(type), "pt").apply()
            } else {
                Toast.makeText(context, "Português não encontrado neste vídeo", Toast.LENGTH_SHORT).show()
            }
        }
        i == 1 -> {
            setDisabled(player, type, true)
            prefs.edit().putString(keyFor(type), "none").apply()
        }
        else -> {
            val (gi, ti) = data.map[i - 2]
            selectTrackAt(player, groups, gi, ti, type)
            prefs.edit().putString(keyFor(type), langKey(groups[gi].getTrackFormat(ti).language) ?: "und").apply()
        }
    }
}

@Composable
private fun TrackPanelOverlay(
    title: String,
    rows: List<Pair<String, Boolean>>,
    onDismiss: () -> Unit,
    onRowClick: (Int) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.7f)
                .background(Color(0xE61A1A1A), RoundedCornerShape(14.dp))
                .padding(24.dp)
                .clickable { },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                if (onOpenSettings != null) {
                    Text("⚙", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { onOpenSettings() }.padding(8.dp))
                }
                Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { i, row ->
                    Text(
                        (if (row.second) "●  " else "○  ") + row.first,
                        color = Color.White,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRowClick(i) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

// ================= CONFIGURACOES DE LEGENDAS (Fase 2) =================

private data class SubStyle(
    val positionTop: Boolean = false,
    val colorIdx: Int = 0,
    val bold: Boolean = false,
    val sizeIdx: Int = 1,
    val opacityIdx: Int = 3,
    val bgIdx: Int = 1,
    val shadow: Boolean = true,
)

private const val SUB_RENDERER_ON = true

private val SUB_COLORS = listOf(Color.White, Color.Black, Color.Red, Color(0xFF4CD964), Color.Yellow, Color.Cyan)
private val SUB_SIZES = floatArrayOf(14f, 18f, 22f, 26f)
private val SUB_SIZE_NAMES = listOf("Pequeno", "Médio", "Grande", "Enorme")
private val SUB_OPACITY = floatArrayOf(0.25f, 0.5f, 0.75f, 1f)
private val SUB_OPACITY_NAMES = listOf("25%", "50%", "75%", "100%")
private val SUB_BG_NAMES = listOf("Sem fundo", "Translúcido", "Sólido")

private fun subBgColorOf(s: SubStyle): Color = when (s.bgIdx) {
    0 -> Color.Transparent
    1 -> Color(0x66000000)
    else -> Color.Black
}

private fun loadSubStyle(ctx: Context): SubStyle {
    val p = ctx.getSharedPreferences("xui_subtitle_style", 0)
    return SubStyle(
        positionTop = p.getBoolean("pos_top", false),
        colorIdx = p.getInt("color", 0),
        bold = p.getBoolean("bold", false),
        sizeIdx = p.getInt("size", 1),
        opacityIdx = p.getInt("opacity", 3),
        bgIdx = p.getInt("bg", 1),
        shadow = p.getBoolean("shadow", true),
    )
}

private fun saveSubStyle(ctx: Context, s: SubStyle) {
    ctx.getSharedPreferences("xui_subtitle_style", 0).edit()
        .putBoolean("pos_top", s.positionTop)
        .putInt("color", s.colorIdx)
        .putBoolean("bold", s.bold)
        .putInt("size", s.sizeIdx)
        .putInt("opacity", s.opacityIdx)
        .putInt("bg", s.bgIdx)
        .putBoolean("shadow", s.shadow)
        .apply()
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
    )
}

@Composable
private fun SubtitleSettingsOverlay(
    style: SubStyle,
    onChange: (SubStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.8f)
                .background(Color(0xE61A1A1A), RoundedCornerShape(14.dp))
                .padding(24.dp)
                .clickable { },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Configurações de Legendas", color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            }
            Text(
                "Texto de exemplo da legenda",
                color = SUB_COLORS[style.colorIdx].copy(alpha = SUB_OPACITY[style.opacityIdx]),
                fontSize = SUB_SIZES[style.sizeIdx].sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = if (style.shadow) Shadow(Color.Black, Offset(2f, 2f), 8f) else Shadow(Color.Transparent, Offset.Zero, 0f),
                ),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .background(subBgColorOf(style))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SettingRow("Posição: " + if (style.positionTop) "Superior" else "Inferior") { onChange(style.copy(positionTop = !style.positionTop)) }
                SettingRow("Negrito: " + if (style.bold) "Ativado" else "Desativado") { onChange(style.copy(bold = !style.bold)) }
                SettingRow("Tamanho: " + SUB_SIZE_NAMES[style.sizeIdx]) { onChange(style.copy(sizeIdx = (style.sizeIdx + 1) % SUB_SIZES.size)) }
                SettingRow("Opacidade: " + SUB_OPACITY_NAMES[style.opacityIdx]) { onChange(style.copy(opacityIdx = (style.opacityIdx + 1) % SUB_OPACITY.size)) }
                SettingRow("Fundo: " + SUB_BG_NAMES[style.bgIdx]) { onChange(style.copy(bgIdx = (style.bgIdx + 1) % SUB_BG_NAMES.size)) }
                SettingRow("Sombra: " + if (style.shadow) "Ativada" else "Desativada") { onChange(style.copy(shadow = !style.shadow)) }
                Text("Cor do texto:", color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SUB_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(c, RoundedCornerShape(6.dp))
                                .clickable { onChange(style.copy(colorIdx = i)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == style.colorIdx) {
                                Text("✓", color = if (i == 0 || i == 4 || i == 5) Color.Black else Color.White, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
