package com.duckpsycho.telegramreader.ui.post

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
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
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.TelegramReaderApp
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import kotlinx.coroutines.delay

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
    var videoView by remember(url) { mutableStateOf<VideoView?>(null) }
    var mediaPlayer by remember(url) { mutableStateOf<MediaPlayer?>(null) }

    fun togglePlay() {
        val view = videoView ?: return
        if (view.isPlaying) {
            view.pause()
            playing = false
        } else {
            view.start()
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
        val player = mediaPlayer ?: return
        muted = !muted
        val volume = if (muted) 0f else 1f
        player.setVolume(volume, volume)
    }

    LaunchedEffect(playing, videoView, isSeeking) {
        while (playing && videoView != null && !isSeeking) {
            currentTimeMs = videoView?.currentPosition?.toLong() ?: 0L
            delay(250)
        }
    }

    LaunchedEffect(playing, controlsVisible, controlsAlwaysVisible) {
        if (!controlsAlwaysVisible && playing && controlsVisible) {
            delay(3_000)
            controlsVisible = false
        }
    }

    DisposableEffect(url) {
        onDispose {
            videoView?.pause()
            videoView?.stopPlayback()
        }
    }

    LaunchedEffect(autoStart, ready, videoView) {
        val view = videoView ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        if (autoStart) {
            view.start()
            started = true
            playing = true
        } else {
            view.pause()
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
                    VideoView(ctx).apply {
                        if (fullscreen) {
                            isClickable = false
                            isFocusable = false
                            setOnTouchListener { _, _ -> false }
                        }
                        setVideoURI(Uri.parse(url), headers)
                        setOnPreparedListener { player ->
                            mediaPlayer = player
                            durationMs = player.duration.toLong().coerceAtLeast(0L)
                            player.setOnBufferingUpdateListener { _, percent ->
                                buffered = percent / 100f
                            }
                            ready = true
                            if (autoStart) {
                                start()
                                started = true
                                playing = true
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            loadFailed = true
                            true
                        }
                        setOnCompletionListener {
                            playing = false
                            currentTimeMs = durationMs
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        videoView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view !== videoView) {
                        videoView = view
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

        // Transparent overlay so Compose gets taps/swipes instead of VideoView.
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
                    videoView?.seekTo(seekPositionMs.toInt())
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
