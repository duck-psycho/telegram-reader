package com.duckpsycho.telegramreader.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.data.SubscriptionItem
import com.duckpsycho.telegramreader.ui.components.ChannelAvatar
import com.duckpsycho.telegramreader.ui.post.localizedServicePostText
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import com.duckpsycho.telegramreader.util.formatChatListDate

@Composable
internal fun ChannelListItem(
    item: SubscriptionItem,
    selected: Boolean,
    locale: AppLocale,
    onClick: () -> Unit,
) {
    val colors = ReaderTheme.colors
    val channel = item.channel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.active else colors.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(
            title = channel.title,
            photoUrl = channel.photoUrl,
            size = 48.dp,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val lastPostDate = channel.lastPost?.date?.let {
                formatChatListDate(it, locale, stringResource(R.string.post_yesterday))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.title,
                    color = colors.textStrong,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (lastPostDate != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = lastPostDate,
                        color = colors.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            val lastPost = channel.lastPost
            val preview = lastPost?.text?.takeIf { it.isNotBlank() }?.let { text ->
                localizedServicePostText(text)
            } ?: lastPostPreviewLabel(lastPost?.mediaType, lastPost?.mediaCount)
            if (!preview.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    lastPostMediaEmoji(lastPost?.mediaType)?.let { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(
                        text = preview,
                        color = colors.textMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun lastPostPreviewLabel(mediaType: String?, mediaCount: Int?): String? = when (mediaType) {
    "photo" -> if ((mediaCount ?: 1) > 1) {
        stringResource(R.string.preview_photos, mediaCount ?: 1)
    } else {
        stringResource(R.string.preview_photo)
    }

    "video" -> stringResource(R.string.preview_video)

    "sticker", "videosticker" -> stringResource(R.string.preview_sticker)

    "document" -> stringResource(R.string.preview_file)

    "location" -> stringResource(R.string.preview_location)

    "unsupported" -> stringResource(R.string.preview_unsupported)

    else -> null
}

internal fun lastPostMediaEmoji(mediaType: String?): String? = when (mediaType) {
    "photo" -> "📷"
    "video" -> "🎬"
    "sticker", "videosticker" -> "🎭"
    "document" -> "📎"
    "location" -> "📍"
    else -> null
}

@Composable
internal fun SupportHint(
    onOpenSettings: () -> Unit,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: TextUnit = 13.sp,
) {
    val colors = ReaderTheme.colors
    val linkStyle = TextLinkStyles(
        style = androidx.compose.ui.text.SpanStyle(
            color = colors.textStrong,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
        ),
    )
    val mutedStyle = androidx.compose.ui.text.SpanStyle(
        color = colors.textMuted,
        fontSize = fontSize,
    )
    val text = buildAnnotatedString {
        withStyle(mutedStyle) {
            append(stringResource(R.string.welcome_support_prefix).trimEnd())
            append(" ")
        }
        withLink(LinkAnnotation.Clickable(tag = "support", linkStyle) { onOpenSettings() }) {
            append(stringResource(R.string.welcome_support_project))
        }
        withStyle(mutedStyle) {
            append(stringResource(R.string.welcome_support_middle).trimEnd())
            append(" ")
        }
        withLink(LinkAnnotation.Clickable(tag = "settings", linkStyle) { onOpenSettings() }) {
            append(stringResource(R.string.welcome_support_settings))
        }
        withStyle(mutedStyle) {
            append(stringResource(R.string.welcome_support_suffix))
        }
    }
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.45f).sp,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
    )
}
