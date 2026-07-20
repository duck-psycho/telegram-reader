package com.duckpsycho.telegramreader.ui

import com.duckpsycho.telegramreader.data.Account
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.data.Channel
import com.duckpsycho.telegramreader.data.Post
import com.duckpsycho.telegramreader.data.SubscriptionItem
import com.duckpsycho.telegramreader.ui.theme.ThemePreference

enum class Overlay {
    None,
    Settings,
    Login,
    AddChannel,
    ChannelInfo,
    LeaveChannel,
}

data class ChannelFeedState(
    val username: String,
    val channel: Channel? = null,
    val posts: List<Post> = emptyList(),
    val hasMore: Boolean = false,
    val nextBefore: Long? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val notFound: Boolean = false,
    val focusPostId: Long? = null,
    val highlightedPostId: Long? = null,
    val error: String? = null,
)

data class ReaderUiState(
    val hasAccount: Boolean = false,
    val account: Account? = null,
    val subscriptions: List<SubscriptionItem> = emptyList(),
    val searchQuery: String = "",
    val activeUsername: String? = null,
    val feed: ChannelFeedState? = null,
    val overlay: Overlay = Overlay.None,
    val theme: ThemePreference = ThemePreference.System,
    val locale: AppLocale = AppLocale.Ru,
    val snackbar: String? = null,
    val actionLoading: Boolean = false,
    val actionError: String? = null,
    val recreateForLocale: Boolean = false,
)
