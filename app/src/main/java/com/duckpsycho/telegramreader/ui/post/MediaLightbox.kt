package com.duckpsycho.telegramreader.ui.post

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun PostLightboxes(
    lightboxUrls: List<String>?,
    lightboxIndex: Int,
    videoLightboxUrls: List<String>?,
    videoLightboxIndex: Int,
    onLightboxIndexChange: (Int) -> Unit,
    onVideoLightboxIndexChange: (Int) -> Unit,
    onDismissPhoto: () -> Unit,
    onDismissVideo: () -> Unit,
) {
    lightboxUrls?.let { urls ->
        PhotoLightbox(
            urls = urls,
            index = lightboxIndex,
            onIndexChange = onLightboxIndexChange,
            onDismiss = onDismissPhoto,
        )
    }
    videoLightboxUrls?.let { urls ->
        VideoLightbox(
            urls = urls,
            index = videoLightboxIndex,
            onIndexChange = onVideoLightboxIndexChange,
            onDismiss = onDismissVideo,
        )
    }
}

@Composable
private fun PhotoLightbox(
    urls: List<String>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MediaPagerLightbox(
        urls = urls,
        index = index,
        onIndexChange = onIndexChange,
        onDismiss = onDismiss,
    ) { url, isActive ->
        ZoomableLightboxContent(isActive = isActive) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun VideoLightbox(
    urls: List<String>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MediaPagerLightbox(
        urls = urls,
        index = index,
        onIndexChange = onIndexChange,
        onDismiss = onDismiss,
    ) { url, isActive ->
        var onContentTap by remember(url) { mutableStateOf<(() -> Unit)?>(null) }
        ZoomableLightboxContent(
            isActive = isActive,
            onSingleTap = { onContentTap?.invoke() },
        ) {
            VideoPlayer(
                url = url,
                modifier = Modifier.fillMaxSize(),
                autoStart = isActive,
                controlsAlwaysVisible = false,
                fullscreen = true,
                handleContentGestures = false,
                onContentTapReady = { onContentTap = it },
            )
        }
    }
}

private const val LIGHTBOX_MIN_ZOOM = 1f
private const val LIGHTBOX_MAX_ZOOM = 5f
private const val LIGHTBOX_ZOOM_EPSILON = 0.01f

internal data class LightboxZoomState(
    val isZoomed: Boolean,
    val onZoomedChange: (Boolean) -> Unit,
)

internal val LocalLightboxZoomState = staticCompositionLocalOf<LightboxZoomState?> { null }

/** Pinch-zoom + pan; shares a gesture pipeline with swipe-dismiss so pinch isn't mistaken for dismiss. */
@Composable
private fun ZoomableLightboxContent(
    isActive: Boolean,
    onSingleTap: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var scale by remember { mutableFloatStateOf(LIGHTBOX_MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomState = LocalLightboxZoomState.current
    val dismiss = LocalLightboxSwipeDismiss.current
    val isZoomed = scale > LIGHTBOX_MIN_ZOOM + LIGHTBOX_ZOOM_EPSILON
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)

    LaunchedEffect(isZoomed) {
        zoomState?.onZoomedChange(isZoomed)
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            scale = LIGHTBOX_MIN_ZOOM
            offset = Offset.Zero
            zoomState?.onZoomedChange(false)
        }
    }

    fun clampOffset(raw: Offset, atScale: Float): Offset {
        if (atScale <= LIGHTBOX_MIN_ZOOM + LIGHTBOX_ZOOM_EPSILON || layoutSize == IntSize.Zero) {
            return Offset.Zero
        }
        val maxX = layoutSize.width * (atScale - 1f) / 2f
        val maxY = layoutSize.height * (atScale - 1f) / 2f
        return Offset(
            x = raw.x.coerceIn(-maxX, maxX),
            y = raw.y.coerceIn(-maxY, maxY),
        )
    }

    fun applyZoomPan(zoomChange: Float, panChange: Offset) {
        val newScale = (scale * zoomChange).coerceIn(LIGHTBOX_MIN_ZOOM, LIGHTBOX_MAX_ZOOM)
        // Scale pan by zoom so finger tracking stays 1:1 when zoomed in.
        val acceleratedPan = panChange * scale
        scale = newScale
        offset = if (newScale <= LIGHTBOX_MIN_ZOOM + LIGHTBOX_ZOOM_EPSILON) {
            Offset.Zero
        } else {
            clampOffset(offset + acceleratedPan, newScale)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { layoutSize = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(dismiss) {
                val touchSlop = viewConfiguration.touchSlop
                val dismissSlop = touchSlop * 2.5f
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mode = LightboxGestureMode.Undecided
                    var totalPan = Offset.Zero

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pressedCount >= 2 || (zoomChange != 1f && pressedCount > 0)) {
                            if (mode == LightboxGestureMode.Dismiss) {
                                dismiss?.onDragCancel()
                            }
                            mode = LightboxGestureMode.Transform
                            applyZoomPan(zoomChange, panChange)
                            event.changes.fastForEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        } else {
                            when (mode) {
                                LightboxGestureMode.Transform -> {
                                    if (scale > LIGHTBOX_MIN_ZOOM + LIGHTBOX_ZOOM_EPSILON) {
                                        applyZoomPan(1f, panChange)
                                        event.changes.fastForEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                }

                                LightboxGestureMode.Dismiss -> {
                                    dismiss?.onVerticalDrag(panChange.y)
                                    event.changes.fastForEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                }

                                LightboxGestureMode.Undecided -> {
                                    totalPan += panChange
                                    val atBaseZoom = scale <= LIGHTBOX_MIN_ZOOM + LIGHTBOX_ZOOM_EPSILON
                                    when {
                                        !atBaseZoom -> {
                                            mode = LightboxGestureMode.Transform
                                            applyZoomPan(1f, panChange)
                                            event.changes.fastForEach { change ->
                                                if (change.positionChanged()) change.consume()
                                            }
                                        }

                                        abs(totalPan.y) > dismissSlop &&
                                            abs(totalPan.y) > abs(totalPan.x) * 1.75f -> {
                                            mode = LightboxGestureMode.Dismiss
                                            dismiss?.onDragStart()
                                            dismiss?.onVerticalDrag(totalPan.y)
                                            event.changes.fastForEach { change ->
                                                if (change.positionChanged()) change.consume()
                                            }
                                        }

                                        abs(totalPan.x) > dismissSlop &&
                                            abs(totalPan.x) > abs(totalPan.y) -> {
                                            return@awaitEachGesture
                                        }
                                    }
                                }
                            }
                        }

                        if (!event.changes.fastAny { it.pressed }) {
                            when (mode) {
                                LightboxGestureMode.Dismiss ->
                                    dismiss?.onDragEnd(size.height.toFloat())

                                LightboxGestureMode.Transform -> {
                                    if (scale < LIGHTBOX_MIN_ZOOM + LIGHTBOX_ZOOM_EPSILON) {
                                        scale = LIGHTBOX_MIN_ZOOM
                                        offset = Offset.Zero
                                    }
                                }

                                LightboxGestureMode.Undecided -> Unit
                            }
                            break
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnSingleTap?.invoke() },
                    onDoubleTap = { tapOffset ->
                        if (scale > LIGHTBOX_MIN_ZOOM + LIGHTBOX_ZOOM_EPSILON) {
                            scale = LIGHTBOX_MIN_ZOOM
                            offset = Offset.Zero
                        } else {
                            val target = 2.5f
                            scale = target
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clampOffset((center - tapOffset) * (target - 1f), target)
                        }
                    },
                )
            },
        content = content,
    )
}

private enum class LightboxGestureMode {
    Undecided,
    Dismiss,
    Transform,
}

internal data class LightboxSwipeDismiss(
    val onVerticalDrag: (Float) -> Unit,
    val onDragStart: () -> Unit,
    val onDragEnd: (layoutHeightPx: Float) -> Unit,
    val onDragCancel: () -> Unit,
)

internal val LocalLightboxSwipeDismiss = staticCompositionLocalOf<LightboxSwipeDismiss?> { null }

@Composable
private fun MediaPagerLightbox(
    urls: List<String>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    pageContent: @Composable (url: String, isActive: Boolean) -> Unit,
) {
    if (urls.isEmpty()) return
    val pageCount = urls.size
    val cyclic = pageCount > 1
    val initialPage = remember(urls, index) {
        val safeIndex = index.coerceIn(0, pageCount - 1)
        if (!cyclic) {
            safeIndex
        } else {
            val mid = Int.MAX_VALUE / 2
            mid - (mid % pageCount) + safeIndex
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { if (cyclic) Int.MAX_VALUE else pageCount },
    )
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    val dismissThresholdPx = with(LocalDensity.current) { 112.dp.toPx() }
    val progress = (abs(offsetY) / dismissThresholdPx).coerceIn(0f, 1f)

    val zoomState = remember {
        LightboxZoomState(
            isZoomed = false,
            onZoomedChange = { zoomed -> isZoomed = zoomed },
        )
    }

    val swipeDismiss = remember(dismissThresholdPx, scope) {
        LightboxSwipeDismiss(
            onVerticalDrag = { dragAmount ->
                if (!isZoomed) offsetY += dragAmount
            },
            onDragStart = {
                if (!isZoomed) isDragging = true
            },
            onDragCancel = {
                isDragging = false
                scope.launch {
                    val anim = Animatable(offsetY)
                    anim.animateTo(0f, animationSpec = spring()) {
                        offsetY = value
                    }
                }
            },
            onDragEnd = { layoutHeightPx ->
                isDragging = false
                if (isZoomed) {
                    scope.launch {
                        val anim = Animatable(offsetY)
                        anim.animateTo(0f, animationSpec = spring()) {
                            offsetY = value
                        }
                    }
                    return@LightboxSwipeDismiss
                }
                scope.launch {
                    if (abs(offsetY) >= dismissThresholdPx) {
                        val direction = if (offsetY > 0f) 1f else -1f
                        val anim = Animatable(offsetY)
                        anim.animateTo(
                            targetValue = direction * layoutHeightPx,
                            animationSpec = tween(160),
                        ) {
                            offsetY = value
                        }
                        currentOnDismiss()
                    } else {
                        val anim = Animatable(offsetY)
                        anim.animateTo(0f, animationSpec = spring()) {
                            offsetY = value
                        }
                    }
                }
            },
        )
    }

    LaunchedEffect(pagerState, pageCount) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                onIndexChange(page % pageCount)
                offsetY = 0f
                isDragging = false
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(
            LocalLightboxSwipeDismiss provides swipeDismiss,
            LocalLightboxZoomState provides zoomState,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 1f - progress * 0.55f)),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = cyclic && !isDragging && !isZoomed,
                    beyondViewportPageCount = if (cyclic) 1 else 0,
                ) { page ->
                    val pageIndex = page % pageCount
                    val isActive = pagerState.settledPage % pageCount == pageIndex
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = offsetY
                                val dismissScale = 1f - 0.06f * progress
                                scaleX = dismissScale
                                scaleY = dismissScale
                            },
                    ) {
                        pageContent(urls[pageIndex], isActive)
                    }
                }
                LightboxBackHeader(onBack = onDismiss)
                if (pageCount > 1) {
                    Text(
                        text = "${(pagerState.currentPage % pageCount) + 1}/$pageCount",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LightboxBackHeader(onBack: () -> Unit) {
    val colors = ReaderTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgElevated.copy(alpha = 0.82f))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = colors.textStrong,
            )
        }
    }
}
