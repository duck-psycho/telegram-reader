package com.duckpsycho.telegramreader.ui.post

import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.TelegramReaderApp
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoStart: Boolean = true,
    controlsAlwaysVisible: Boolean = false,
    fullscreen: Boolean = false,
    /** When false, parent (e.g. lightbox) owns tap / swipe. */
    handleContentGestures: Boolean = true,
    onContentTapReady: ((() -> Unit) -> Unit)? = null,
) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    val mediaCache = remember {
        (context.applicationContext as TelegramReaderApp).mediaCache
    }
    val headers = remember(url) { mediaCache.playbackHeaders(url) }
    var loadFailed by remember(url) { mutableStateOf(false) }
    var started by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var ready by remember(url) { mutableStateOf(false) }
    var currentTimeMs by remember(url) { mutableLongStateOf(0L) }
    var durationMs by remember(url) { mutableLongStateOf(0L) }
    var buffered by remember(url) { mutableFloatStateOf(0f) }
    var muted by remember(url) { mutableStateOf(false) }
    var controlsVisible by remember(url) { mutableStateOf(controlsAlwaysVisible) }
    var isSeeking by remember(url) { mutableStateOf(false) }
    var seekPositionMs by remember(url) { mutableLongStateOf(0L) }
    val player = remember(url) {
        val app = context.applicationContext as TelegramReaderApp
        val source = OkHttpDataSource.Factory(app.httpClient).setDefaultRequestProperties(headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(ProgressiveMediaSource.Factory(source)).build().apply {
            setMediaItem(MediaItem.fromUri(url))
        }
    }
    var textureView by remember(url) { mutableStateOf<TextureView?>(null) }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
            playing = false
        } else {
            player.play()
            playing = true
            started = true
        }
    }

    fun onContentTap() {
        if (!started) return
        togglePlay()
        controlsVisible = true
    }

    val currentContentTap by rememberUpdatedState(newValue = { onContentTap() })
    LaunchedEffect(onContentTapReady) {
        onContentTapReady?.invoke { currentContentTap() }
    }

    fun toggleMute() {
        muted = !muted
        player.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(playing, isSeeking) {
        while (playing && !isSeeking) {
            currentTimeMs = player.currentPosition
            buffered = player.bufferedPercentage / 100f
            delay(250)
        }
    }

    LaunchedEffect(playing, controlsVisible, controlsAlwaysVisible) {
        if (!controlsAlwaysVisible && playing && controlsVisible) {
            delay(3_000)
            controlsVisible = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    ready = true
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) {
                    playing = false
                    currentTimeMs = durationMs
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                loadFailed = true
            }
        }
        player.addListener(listener)
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(autoStart, ready) {
        if (!ready) return@LaunchedEffect
        if (autoStart) {
            player.play()
            started = true
            playing = true
        } else {
            player.pause()
            playing = false
        }
    }

    Box(
        modifier = modifier
            .then(
                if (fullscreen) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(colors.radius))
                },
            )
            .background(Color.Black),
    ) {
        key(url) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        if (fullscreen) {
                            isClickable = false
                            isFocusable = false
                            setOnTouchListener { _, _ -> false }
                        }
                        player.setVideoTextureView(this)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        textureView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view !== textureView) {
                        textureView = view
                    }
                },
            )
        }

        if (!ready && !loadFailed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Transparent overlay so Compose gets taps/swipes instead of the texture view.
        if (handleContentGestures && (fullscreen || started)) {
            val swipeDismiss = LocalLightboxSwipeDismiss.current
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = started,
                    ) {
                        onContentTap()
                    }
                    .then(
                        if (swipeDismiss != null) {
                            Modifier.pointerInput(swipeDismiss) {
                                detectVerticalDragGestures(
                                    onDragStart = { swipeDismiss.onDragStart() },
                                    onDragEnd = {
                                        swipeDismiss.onDragEnd(size.height.toFloat())
                                    },
                                    onDragCancel = { swipeDismiss.onDragCancel() },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        swipeDismiss.onVerticalDrag(dragAmount)
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        if (started && (controlsAlwaysVisible || !playing || controlsVisible)) {
            VideoPlayerControls(
                playing = playing,
                currentTimeMs = if (isSeeking) seekPositionMs else currentTimeMs,
                durationMs = durationMs,
                buffered = buffered,
                muted = muted,
                onTogglePlay = {
                    togglePlay()
                    controlsVisible = true
                },
                onToggleMute = {
                    toggleMute()
                    controlsVisible = true
                },
                onSeek = { value ->
                    isSeeking = true
                    seekPositionMs = value
                },
                onSeekFinished = {
                    player.seekTo(seekPositionMs)
                    currentTimeMs = seekPositionMs
                    isSeeking = false
                    controlsVisible = true
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun VideoPlayerControls(
    playing: Boolean,
    currentTimeMs: Long,
    durationMs: Long,
    buffered: Float,
    muted: Boolean,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.player_pause else R.string.player_play,
                ),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }

        VideoProgressBar(
            currentTimeMs = currentTimeMs,
            durationMs = durationMs,
            buffered = buffered,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "${formatVideoTime(currentTimeMs)} / ${formatVideoTime(durationMs)}",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.widthIn(min = 72.dp),
        )

        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(
                    if (muted) R.string.player_unmute else R.string.player_mute,
                ),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun VideoProgressBar(
    currentTimeMs: Long,
    durationMs: Long,
    buffered: Float,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) {
        (currentTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressLabel = stringResource(R.string.player_progress)

    BoxWithConstraints(
        modifier = modifier.height(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(buffered.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.45f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxSize()
                    .background(Color.White),
            )
        }

        Slider(
            value = currentTimeMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = progressLabel },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.White,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

private fun formatVideoTime(timeMs: Long): String {
    if (timeMs < 0) return "0:00"
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
