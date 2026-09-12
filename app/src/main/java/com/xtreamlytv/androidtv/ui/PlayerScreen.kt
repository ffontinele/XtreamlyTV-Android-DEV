package com.xtreamlytv.androidtv.ui
import android.app.Activity
import android.content.Context
import android.media.AudioManager

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
            if (request.item.type == ContentType.LIVE) {
                Text("↑/↓ Change channel", color = colors.muted, fontSize = 9.sp)
            } else {
                Text("${formatDuration(position)} / ${formatDuration(duration)}", color = colors.muted, fontSize = 9.sp)
            }
        }
    }
}
