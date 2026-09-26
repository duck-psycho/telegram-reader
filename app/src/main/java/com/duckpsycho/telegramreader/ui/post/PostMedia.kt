package com.duckpsycho.telegramreader.ui.post

import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.TelegramReaderApp
import com.duckpsycho.telegramreader.data.PostMediaGroup
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme

@Composable
internal fun MediaGroupView(
    group: PostMediaGroup,
    onOpenPhoto: (Int) -> Unit,
    onOpenVideo: (Int) -> Unit,
) {
    val colors = ReaderTheme.colors
    val ratio = if (group.height > 0) (group.width / group.height).toFloat() else 1f
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (ratio > 0f) ratio else 1f)
            .clip(RoundedCornerShape(colors.radius)),
    ) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        group.items.forEachIndexed { index, item ->
            val left = (item.left / group.width * boxW).toFloat()
            val top = (item.top / group.height * boxH).toFloat()
            val w = (item.width / group.width * boxW).toFloat()
            val h = (item.height / group.height * boxH).toFloat()
            val itemModifier = Modifier
                .offset(
                    x = with(density) { left.toDp() },
                    y = with(density) { top.toDp() },
                )
                .size(
                    width = with(density) { w.toDp() },
                    height = with(density) { h.toDp() },
                )
            if (item.type == "video") {
                VideoPreview(
                    url = item.url,
                    modifier = itemModifier,
                    fillCell = true,
                    onOpen = { onOpenVideo(index) },
                )
            } else {
                ReservedMediaImage(
                    url = item.url,
                    contentDescription = null,
                    modifier = itemModifier.clickable { onOpenPhoto(index) },
                    contentScale = ContentScale.Crop,
                    reserveSquare = false,
                    clipCorners = false,
                )
            }
        }
    }
}

/**
 * Media slot with a loader until the image is ready.
 * [reserveSquare] locks 1:1; [matchIntrinsicAspect] starts 1:1 then settles to decoded ratio.
 */
@Composable
internal fun ReservedMediaImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    reserveSquare: Boolean = true,
    matchIntrinsicAspect: Boolean = false,
    clipCorners: Boolean = true,
    videoFrame: Boolean = false,
    /** Drop placeholder fill once the image is visible. */
    clearBackgroundWhenLoaded: Boolean = false,
    memoryCacheKey: String? = null,
    diskCacheKey: String? = null,
    overlay: @Composable BoxScope.(loaded: Boolean) -> Unit = {},
) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var aspectRatio by remember(url) { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .then(
                when {
                    reserveSquare -> Modifier.aspectRatio(1f)
                    matchIntrinsicAspect -> Modifier.aspectRatio(aspectRatio.coerceIn(0.4f, 2.5f))
                    else -> Modifier
                },
            )
            .then(
                if (clipCorners) {
                    Modifier.clip(RoundedCornerShape(colors.radius))
                } else {
                    Modifier
                },
            )
            .then(
                if (!loaded || !clearBackgroundWhenLoaded) {
                    Modifier.background(colors.active)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .memoryCacheKey(memoryCacheKey ?: if (videoFrame) "frame:$url" else url)
            .diskCacheKey(diskCacheKey ?: if (videoFrame) "frame:$url" else url)
            .apply { if (videoFrame) videoFrameMillis(0) }
            .build()
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        if (matchIntrinsicAspect) {
                            val drawable = state.result.drawable
                            val w = drawable.intrinsicWidth
                            val h = drawable.intrinsicHeight
                            if (w > 0 && h > 0) {
                                aspectRatio = w.toFloat() / h.toFloat()
                            }
                        }
                        loaded = true
                        failed = false
                    }

                    is AsyncImagePainter.State.Error -> {
                        failed = true
                        loaded = false
                    }

                    else -> Unit
                }
            },
        )
        if (!loaded && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.textMuted,
                strokeWidth = 2.dp,
            )
        }
        overlay(loaded)
    }
}

/** Inline muted looping video (GIF / videosticker). Uses TextureView so LazyColumn scrolls smoothly. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun InlineLoopVideo(
    url: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    loop: Boolean = true,
    autoplay: Boolean = true,
    /** Fixed layout size (e.g. 1:1 stickers) so it doesn't jump on decode. */
    fixedAspectRatio: Float? = null,
) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    val mediaCache = remember {
        (context.applicationContext as TelegramReaderApp).mediaCache
    }
    val headers = remember(url) { mediaCache.playbackHeaders(url) }
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var frameReady by remember(url) { mutableStateOf(false) }
    var aspectRatio by remember(url) {
        mutableFloatStateOf(fixedAspectRatio ?: 1f)
    }
    val player = remember(url) {
        val app = context.applicationContext as TelegramReaderApp
        val source = OkHttpDataSource.Factory(app.httpClient).setDefaultRequestProperties(headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(ProgressiveMediaSource.Factory(source)).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
        }
    }

    fun updateAspectRatio(width: Int, height: Int) {
        if (fixedAspectRatio != null || width <= 0 || height <= 0) return
        aspectRatio = (width.toFloat() / height.toFloat()).coerceIn(0.4f, 2.5f)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val size = player.videoSize
                    updateAspectRatio(size.width, size.height)
                    ready = true
                    if (autoplay) player.play()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                updateAspectRatio(videoSize.width, videoSize.height)
            }
        }
        player.addListener(listener)
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
            ready = false
            frameReady = false
        }
    }

    Box(
        modifier = modifier
            .aspectRatio((fixedAspectRatio ?: aspectRatio).coerceIn(0.4f, 2.5f))
            .clip(RoundedCornerShape(colors.radius))
            .background(colors.active),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .videoFrameMillis(0)
                .crossfade(false)
                .memoryCacheKey("frame:$url")
                .diskCacheKey("frame:$url")
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    frameReady = true
                    val drawable = state.result.drawable
                    updateAspectRatio(drawable.intrinsicWidth, drawable.intrinsicHeight)
                }
            },
        )

        key(url) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        player.setVideoTextureView(this)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (ready) {
                            Modifier
                        } else {
                            Modifier.graphicsLayer { alpha = 0f }
                        },
                    ),
            )
        }

        if (!ready && !failed && !frameReady) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.textMuted,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
internal fun VideoPreview(
    url: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    sticker: Boolean = false,
    fillCell: Boolean = false,
    onOpen: () -> Unit,
) {
    val contentScale = when {
        sticker -> ContentScale.Fit
        else -> ContentScale.Crop
    }
    ReservedMediaImage(
        url = url,
        contentDescription = null,
        modifier = modifier
            .then(if (fillCell) Modifier else Modifier.fillMaxWidth())
            .clickable(onClick = onOpen),
        contentScale = contentScale,
        reserveSquare = !fillCell,
        clipCorners = !fillCell,
        videoFrame = true,
    ) { loaded ->
        if (loaded && !sticker) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                VideoPlayButton()
            }
        }
    }
}

@Composable
private fun VideoPlayButton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = VideoPlayTriangleIcon,
            contentDescription = stringResource(R.string.post_play),
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
    }
}

// Play triangle shifted so its visual centroid sits on the circle center.
private val VideoPlayTriangleIcon: ImageVector by lazy {
    val opticalShift = 12f - (8f + 8f + 19f) / 3f
    ImageVector.Builder(
        name = "VideoPlayTriangle",
        defaultWidth = 34.dp,
        defaultHeight = 34.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(8f + opticalShift, 5f)
            verticalLineTo(19f)
            lineTo(19f + opticalShift, 12f)
            close()
        }
    }.build()
}
