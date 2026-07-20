package com.duckpsycho.telegramreader.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.data.Channel
import com.duckpsycho.telegramreader.ui.components.ChannelAvatar
import com.duckpsycho.telegramreader.ui.components.PrimaryButton
import com.duckpsycho.telegramreader.ui.components.SecondaryButton
import com.duckpsycho.telegramreader.ui.post.RichHtml
import com.duckpsycho.telegramreader.ui.post.RichHtmlStyle
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import com.duckpsycho.telegramreader.util.CountWordForms
import com.duckpsycho.telegramreader.util.formatCount

@Composable
internal fun ChannelHeader(
    channel: Channel,
    locale: AppLocale,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onCopyLink: () -> Unit,
    onLeave: () -> Unit,
) {
    val colors = ReaderTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val subscriberForms = CountWordForms(
        one = stringResource(R.string.post_subscriber_one),
        few = stringResource(R.string.post_subscriber_few),
        many = stringResource(R.string.post_subscriber_many),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgElevated)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .clickable(onClick = onOpenInfo),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.channel_back_to_chats),
                tint = colors.textStrong,
            )
        }
        ChannelAvatar(
            title = channel.title,
            photoUrl = channel.photoUrl,
            size = 40.dp,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.title,
                color = colors.textStrong,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            channel.subscriberCount?.let {
                Text(
                    formatCount(it, locale, subscriberForms),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.channel_menu),
                    tint = colors.textStrong,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.channel_info)) },
                    onClick = {
                        menuOpen = false
                        onOpenInfo()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.channel_copy_link)) },
                    onClick = {
                        menuOpen = false
                        onCopyLink()
                    },
                )
                if (isSubscribed) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.channel_leave)) },
                        onClick = {
                            menuOpen = false
                            onLeave()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SubscribeBar(loading: Boolean, onSubscribe: () -> Unit) {
    val colors = ReaderTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgElevated)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        PrimaryButton(
            text = if (loading) {
                stringResource(R.string.channel_subscribing)
            } else {
                stringResource(R.string.channel_subscribe)
            },
            onClick = onSubscribe,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ChannelInfoDialog(
    channel: Channel,
    onDismiss: () -> Unit,
) {
    val colors = ReaderTheme.colors
    val counterItems = buildList {
        channel.subscriberCount?.let {
            add(ChannelCounterItem(it, stringResource(R.string.channel_counters_subscribers)))
        }
        channel.counters?.photos?.let {
            add(ChannelCounterItem(it, stringResource(R.string.channel_counters_photos)))
        }
        channel.counters?.videos?.let {
            add(ChannelCounterItem(it, stringResource(R.string.channel_counters_videos)))
        }
        channel.counters?.files?.let {
            add(ChannelCounterItem(it, stringResource(R.string.channel_counters_files)))
        }
        channel.counters?.links?.let {
            add(ChannelCounterItem(it, stringResource(R.string.channel_counters_links)))
        }
    }
    val descriptionScrollState = rememberScrollState()
    val maxDescriptionHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlay)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(colors.radiusLg))
                .background(colors.bg)
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ChannelAvatar(
                    title = channel.title,
                    photoUrl = channel.photoUrl,
                    size = 64.dp,
                    modifier = Modifier.size(64.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        channel.title,
                        color = colors.textStrong,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        "@${channel.username}",
                        color = colors.textMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (counterItems.isNotEmpty()) {
                ChannelInfoCounters(counterItems)
            }

            val description = channel.description?.takeIf { it.isNotBlank() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDescriptionHeight)
                    .verticalScroll(descriptionScrollState),
            ) {
                if (description != null) {
                    RichHtml(
                        html = description,
                        modifier = Modifier.fillMaxWidth(),
                        style = RichHtmlStyle.Default,
                    )
                } else {
                    Text(
                        stringResource(R.string.channel_no_description),
                        color = colors.textMuted,
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SecondaryButton(
                text = stringResource(R.string.common_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class ChannelCounterItem(
    val value: String,
    val label: String,
)

@Composable
private fun ChannelInfoCounters(items: List<ChannelCounterItem>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item ->
                    ChannelInfoCounter(item)
                }
            }
        }
    }
}

@Composable
private fun ChannelInfoCounter(item: ChannelCounterItem) {
    val colors = ReaderTheme.colors
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(colors.radius))
            .background(colors.bgElevated)
            .border(1.dp, colors.border, RoundedCornerShape(colors.radius))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            item.value,
            color = colors.textStrong,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 19.sp,
        )
        Text(
            item.label,
            color = colors.textMuted,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun LeaveChannelDialog(
    title: String,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = ReaderTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlay)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(colors.radiusLg))
                .background(colors.bgElevated)
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.channel_leave_title),
                color = colors.textStrong,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Text(
                stringResource(R.string.channel_leave_confirm, title),
                color = colors.text,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SecondaryButton(stringResource(R.string.common_cancel), onClick = onDismiss)
                PrimaryButton(
                    text = if (loading) {
                        stringResource(R.string.channel_leaving)
                    } else {
                        stringResource(R.string.channel_leave_action)
                    },
                    onClick = onConfirm,
                    enabled = !loading,
                )
            }
        }
    }
}
