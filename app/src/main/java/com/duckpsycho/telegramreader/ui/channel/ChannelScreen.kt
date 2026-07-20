package com.duckpsycho.telegramreader.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.data.BASE_URL
import com.duckpsycho.telegramreader.data.Channel
import com.duckpsycho.telegramreader.ui.ChannelFeedState
import com.duckpsycho.telegramreader.ui.components.ErrorText
import com.duckpsycho.telegramreader.ui.components.ReaderFab
import com.duckpsycho.telegramreader.ui.components.copyToClipboard
import com.duckpsycho.telegramreader.ui.post.PostCard
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * reverseLayout feed: index 0 is the visual bottom (newest posts).
 * LazyListState defaults there, so the channel opens at the latest post.
 */
@Composable
fun ChannelScreen(
    feed: ChannelFeedState,
    isSubscribed: Boolean,
    locale: AppLocale,
    actionLoading: Boolean,
    onBack: () -> Unit,
    onLoadOlder: () -> Unit,
    onReplyClick: (Long) -> Unit,
    onFocusConsumed: () -> Unit,
    onSubscribe: () -> Unit,
    onOpenInfo: () -> Unit,
    onLeave: () -> Unit,
    onCopied: (String) -> Unit,
) {
    val colors = ReaderTheme.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val displayPosts = remember(feed.posts) { feed.posts.asReversed() }
    var pinnedToBottom by remember(feed.username) {
        mutableStateOf(feed.focusPostId == null)
    }
    var previousPostCount by remember(feed.username) { mutableIntStateOf(0) }

    val showScrollDown by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        }
    }

    LaunchedEffect(listState, feed.username) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                pinnedToBottom = index == 0 && offset == 0
            }
    }

    LaunchedEffect(feed.posts.size, feed.username) {
        if (
            feed.posts.size > previousPostCount &&
            pinnedToBottom &&
            feed.focusPostId == null &&
            feed.posts.isNotEmpty()
        ) {
            listState.scrollToItem(0)
        }
        previousPostCount = feed.posts.size
    }

    LaunchedEffect(feed.posts.size, feed.hasMore, feed.loadingMore, feed.focusPostId) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItems) ->
                if (
                    feed.focusPostId != null ||
                    lastVisibleIndex == null ||
                    totalItems == 0 ||
                    !feed.hasMore ||
                    feed.loadingMore
                ) {
                    return@collect
                }
                val remainingAfterVisible = totalItems - 1 - lastVisibleIndex
                if (remainingAfterVisible <= 2) {
                    onLoadOlder()
                }
            }
    }

    LaunchedEffect(feed.focusPostId, displayPosts.map { it.id }) {
        val target = feed.focusPostId ?: return@LaunchedEffect
        val index = displayPosts.indexOfFirst { it.id == target }
        if (index >= 0) {
            pinnedToBottom = false
            listState.scrollToItem(index)
            onFocusConsumed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        ChannelHeader(
            channel = feed.channel ?: Channel(username = feed.username, title = feed.username),
            locale = locale,
            isSubscribed = isSubscribed,
            onBack = onBack,
            onOpenInfo = onOpenInfo,
            onCopyLink = {
                copyToClipboard(context, "channel", "$BASE_URL/channel/${feed.username}")
                onCopied(context.getString(R.string.channel_link_copied))
            },
            onLeave = onLeave,
        )

        when {
            feed.loading -> ChannelLoadingState()

            feed.notFound -> ChannelNotFoundState(username = feed.username)

            feed.error != null -> ChannelErrorState(message = feed.error)

            else -> {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = displayPosts,
                            key = { it.id },
                            contentType = { post ->
                                buildString {
                                    append(post.mediaType ?: "text")
                                    if (post.mediaVideoAttrs?.autoplay == true) append("-gif")
                                    if (post.mediaGroup != null) append("-group")
                                }
                            },
                        ) { post ->
                            PostCard(
                                post = post,
                                locale = locale,
                                highlighted = post.id == feed.highlightedPostId,
                                onReplyClick = onReplyClick,
                            )
                        }
                    }

                    if (feed.loadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .size(24.dp),
                            color = colors.textMuted,
                            strokeWidth = 2.dp,
                        )
                    }

                    if (showScrollDown) {
                        ReaderFab(
                            onClick = {
                                scope.launch {
                                    pinnedToBottom = true
                                    listState.animateScrollToItem(0)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.post_scroll_down),
                                tint = colors.fabText,
                            )
                        }
                    }
                }

                if (!isSubscribed) {
                    SubscribeBar(
                        loading = actionLoading,
                        onSubscribe = onSubscribe,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ReaderTheme.colors.textStrong)
    }
}

@Composable
private fun ChannelNotFoundState(username: String) {
    val colors = ReaderTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.channel_not_found_title),
            color = colors.textStrong,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.channel_not_found_body, username),
            color = colors.text,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChannelErrorState(message: String) {
    val colors = ReaderTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ErrorText(message.ifBlank { stringResource(R.string.channel_subscribe_failed) })
    }
}
