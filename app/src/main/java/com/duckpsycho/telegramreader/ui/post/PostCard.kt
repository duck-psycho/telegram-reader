package com.duckpsycho.telegramreader.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.data.Post
import com.duckpsycho.telegramreader.data.PostLocation
import com.duckpsycho.telegramreader.data.readerShareUrl
import com.duckpsycho.telegramreader.ui.components.openUrl
import com.duckpsycho.telegramreader.ui.components.shareText
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import com.duckpsycho.telegramreader.util.CountWordForms
import com.duckpsycho.telegramreader.util.formatCount
import com.duckpsycho.telegramreader.util.formatPostDate
import com.duckpsycho.telegramreader.util.isSingleEmojiContent

@Composable
fun PostCard(
    post: Post,
    locale: AppLocale,
    highlighted: Boolean,
    onReplyClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    var lightboxUrls by remember { mutableStateOf<List<String>?>(null) }
    var lightboxIndex by remember { mutableIntStateOf(0) }
    var videoLightboxUrls by remember { mutableStateOf<List<String>?>(null) }
    var videoLightboxIndex by remember { mutableIntStateOf(0) }
    val cardShape = RoundedCornerShape(colors.radiusLg)
    val highlightRing = colors.textStrong.copy(alpha = 0.2f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier
                        .border(2.dp, highlightRing, cardShape)
                        .padding(2.dp)
                } else {
                    Modifier
                },
            )
            .background(color = colors.bgElevated, shape = cardShape)
            .border(
                width = 1.dp,
                color = if (highlighted) colors.textStrong else colors.border,
                shape = cardShape,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        post.forwardedFrom?.let {
            Text(
                stringResource(R.string.post_forwarded_from, it),
                color = colors.textMuted,
                fontSize = 13.sp,
            )
        }

        post.replyTo?.let { reply ->
            PostReplyPreview(reply = reply, onReplyClick = onReplyClick)
        }

        post.mediaGroup?.let { group ->
            val photoItems = group.items.mapIndexedNotNull { index, item ->
                if (item.type == "photo") index to item.url else null
            }
            val videoItems = group.items.mapIndexedNotNull { index, item ->
                if (item.type == "video") index to item.url else null
            }
            MediaGroupView(
                group = group,
                onOpenPhoto = { index ->
                    val photoIndex = photoItems.indexOfFirst { it.first == index }.coerceAtLeast(0)
                    lightboxUrls = photoItems.map { it.second }
                    lightboxIndex = photoIndex
                },
                onOpenVideo = { index ->
                    val videoIndex = videoItems.indexOfFirst { it.first == index }.coerceAtLeast(0)
                    videoLightboxUrls = videoItems.map { it.second }
                    videoLightboxIndex = videoIndex
                },
            )
        }

        if (post.mediaGroup == null && !post.mediaUrl.isNullOrBlank()) {
            PostSingleMedia(
                post = post,
                onOpenPhoto = {
                    lightboxUrls = listOf(post.mediaUrl)
                    lightboxIndex = 0
                },
                onOpenVideo = {
                    videoLightboxUrls = listOf(post.mediaUrl)
                    videoLightboxIndex = 0
                },
            )
        }

        post.document?.takeIf { post.mediaType == "document" }?.let { doc ->
            val documentTitleStyle = TextStyle(
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            )
            val documentMetaStyle = TextStyle(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(colors.radius))
                    .background(colors.bgElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(colors.radius))
                    .clickable { openUrl(context, doc.url) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.blockquoteAccent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_document),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = doc.title,
                        color = colors.textStrong,
                        style = documentTitleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    doc.extra?.let {
                        Text(it, color = colors.textMuted, style = documentMetaStyle)
                    }
                    Text(
                        text = stringResource(R.string.post_document_note),
                        color = colors.textMuted,
                        style = documentMetaStyle,
                    )
                }
            }
        }

        post.location?.takeIf { post.mediaType == "location" }?.let { loc ->
            PostLocationCard(location = loc)
        }

        val unavailableReason = post.mediaUnavailableReason?.takeUnless { it.isBlank() }
        val hasRenderableMedia = post.mediaGroup != null ||
            !post.mediaUrl.isNullOrBlank() ||
            post.document != null ||
            post.location != null
        val showUnavailable = !post.mediaType.isNullOrBlank() &&
            post.mediaType != "none" &&
            !hasRenderableMedia &&
            unavailableReason != null
        val channelCreated = isChannelCreatedServiceText(post.text) ||
            (unavailableReason != null && normalizeMediaReason(unavailableReason) == "channel_created")
        when {
            showUnavailable -> PostDashedNotice(mediaReasonText(unavailableReason))
            channelCreated -> PostDashedNotice(stringResource(R.string.media_channel_created))
        }

        val skipBody = channelCreated && (
            isChannelCreatedServiceText(post.text) ||
                (post.text.isNullOrBlank() && post.html.isNullOrBlank())
            )
        if (!skipBody) {
            if (!post.html.isNullOrBlank()) {
                val singleEmoji = isSingleEmojiContent(post.text, post.html)
                RichHtml(
                    html = post.html,
                    style = if (singleEmoji) RichHtmlStyle.SingleEmoji else RichHtmlStyle.Default,
                )
            } else if (!post.text.isNullOrBlank()) {
                Text(
                    text = localizedServicePostText(post.text),
                    color = colors.text,
                    style = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }

        PostFooter(
            post = post,
            locale = locale,
        )
    }

    PostLightboxes(
        lightboxUrls = lightboxUrls,
        lightboxIndex = lightboxIndex,
        videoLightboxUrls = videoLightboxUrls,
        videoLightboxIndex = videoLightboxIndex,
        onLightboxIndexChange = { lightboxIndex = it },
        onVideoLightboxIndexChange = { videoLightboxIndex = it },
        onDismissPhoto = { lightboxUrls = null },
        onDismissVideo = { videoLightboxUrls = null },
    )
}

@Composable
private fun PostReplyPreview(
    reply: com.duckpsycho.telegramreader.data.PostReply,
    onReplyClick: (Long) -> Unit,
) {
    val colors = ReaderTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(colors.radius))
            .background(colors.blockquoteBg)
            .clickable {
                val id = reply.link.substringAfterLast('/').toLongOrNull()
                if (id != null) onReplyClick(id)
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .background(colors.blockquoteAccent),
        )
        Spacer(Modifier.width(8.dp))
        reply.thumbUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            reply.author?.let {
                Text(
                    it,
                    color = colors.blockquoteAccent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!reply.html.isNullOrBlank()) {
                RichHtml(
                    html = reply.html,
                    style = RichHtmlStyle.Reply,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (!reply.text.isNullOrBlank()) {
                Text(
                    reply.text,
                    color = colors.text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PostLocationCard(location: PostLocation) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    val shape = RoundedCornerShape(colors.radius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .background(colors.bgElevated)
            .clickable { openUrl(context, location.url) },
    ) {
        val mapUrl = location.mapUrl
        if (!mapUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(mapUrl)
                    .crossfade(false)
                    .memoryCacheKey(mapUrl)
                    .diskCacheKey(mapUrl)
                    .build(),
                contentDescription = stringResource(R.string.post_location),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .background(colors.hover),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .background(colors.hover)
                    .padding(horizontal = 12.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.post_location),
                    color = colors.textStrong,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = stringResource(R.string.post_open_map),
            color = colors.blockquoteAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PostSingleMedia(
    post: Post,
    onOpenPhoto: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    val attrs = post.mediaVideoAttrs
    when (post.mediaType) {
        "photo" -> {
            ReservedMediaImage(
                url = post.mediaUrl!!,
                contentDescription = stringResource(R.string.post_open_photo),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPhoto),
                contentScale = ContentScale.Crop,
                reserveSquare = false,
                matchIntrinsicAspect = true,
            )
        }

        "video" -> {
            val url = post.mediaUrl!!
            val isGifLike = attrs?.autoplay == true
            if (isGifLike) {
                InlineLoopVideo(
                    url = url,
                    modifier = Modifier.fillMaxWidth(),
                    muted = true,
                    loop = true,
                    autoplay = true,
                    fixedAspectRatio = 1f,
                )
            } else {
                VideoPreview(url = url, onOpen = onOpenVideo)
            }
        }

        "sticker" -> {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stickerWidth = maxWidth.coerceAtMost(256.dp)
                ReservedMediaImage(
                    url = post.mediaUrl!!,
                    contentDescription = null,
                    modifier = Modifier.width(stickerWidth),
                    contentScale = ContentScale.Fit,
                    clearBackgroundWhenLoaded = true,
                )
            }
        }

        "videosticker" -> {
            InlineLoopVideo(
                url = post.mediaUrl!!,
                modifier = Modifier.fillMaxWidth(),
                muted = attrs?.muted != false,
                loop = attrs?.loop != false,
                autoplay = attrs?.autoplay != false,
                fixedAspectRatio = 1f,
            )
        }
    }
}

@Composable
private fun PostFooter(
    post: Post,
    locale: AppLocale,
) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    val viewForms = CountWordForms(
        one = stringResource(R.string.post_view_one),
        few = stringResource(R.string.post_view_few),
        many = stringResource(R.string.post_view_many),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        HorizontalDivider(color = colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            post.views?.let { views ->
                Text(
                    formatCount(views, locale, viewForms),
                    color = colors.textMuted,
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(colors.radius))
                    .clickable { shareText(context, post.readerShareUrl()) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.post_share),
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                formatPostDate(
                    post.date,
                    locale,
                    stringResource(R.string.post_yesterday),
                ),
                color = colors.textMuted,
                fontSize = 13.sp,
            )
        }
    }
}
