package com.duckpsycho.telegramreader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duckpsycho.telegramreader.TelegramReaderApp
import com.duckpsycho.telegramreader.data.ApiException
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.data.Channel
import com.duckpsycho.telegramreader.data.ProxySettings
import com.duckpsycho.telegramreader.data.SubscriptionItem
import com.duckpsycho.telegramreader.data.mergeChannel
import com.duckpsycho.telegramreader.data.resolveApiError
import com.duckpsycho.telegramreader.ui.theme.ThemePreference
import com.duckpsycho.telegramreader.util.parseChannelUsername
import com.duckpsycho.telegramreader.util.parseReaderDeepLink
import com.duckpsycho.telegramreader.widget.ChannelWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TelegramReaderApp
    private val api = app.api
    private val prefs = app.prefs
    private val cookieJar = app.cookieJar

    private val _state = MutableStateFlow(
        ReaderUiState(
            theme = prefs.theme,
            locale = prefs.locale,
            proxy = app.proxyStore.load(),
            hasAccount = cookieJar.hasAccountCookie(),
            subscriptions = if (cookieJar.hasAccountCookie()) prefs.loadCachedSubscriptions() else emptyList(),
        ),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var feedLoadJob: Job? = null
    private var pollJob: Job? = null
    private var subscriptionsPollJob: Job? = null

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            try {
                refreshAccountAndSubscriptions()
                if (_state.value.hasAccount) startSubscriptionsPolling()
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = app.resolveApiError(e)) }
            }
        }
    }

    private fun applySubscriptions(subscriptions: List<SubscriptionItem>) {
        val before = _state.value.subscriptions.map { it.channel.username.lowercase() }.toSet()
        _state.update { it.copy(subscriptions = subscriptions) }
        val after = subscriptions.map { it.channel.username.lowercase() }.toSet()
        if (before != after) ChannelWidgetUpdater.onSubscriptionsChanged(app, _state.value.account?.id, after)
    }

    /** Reloads account + subscriptions and writes them into UI state. */
    private suspend fun refreshAccountAndSubscriptions() {
        val account = withContext(Dispatchers.IO) { api.getAccount() }
        val subscriptions = withContext(Dispatchers.IO) { api.getSubscriptions() }
        applySubscriptions(subscriptions)
        _state.update {
            it.copy(
                hasAccount = account != null,
                account = account,
            )
        }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun openOverlay(overlay: Overlay) {
        _state.update { it.copy(overlay = overlay, actionError = null) }
    }

    fun closeOverlay() {
        _state.update { it.copy(overlay = Overlay.None, actionError = null, actionLoading = false) }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    fun showSnackbar(message: String) {
        _state.update { it.copy(snackbar = message) }
    }

    fun setTheme(theme: ThemePreference) {
        prefs.theme = theme
        _state.update { it.copy(theme = theme) }
    }

    fun setLocale(locale: AppLocale) {
        prefs.locale = locale
        _state.update { it.copy(locale = locale, recreateForLocale = true) }
    }

    fun saveProxy(settings: ProxySettings) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.proxyStore.save(settings)
                    app.proxyRouting.update(settings, app.httpClient)
                }
            } catch (_: Exception) {
                _state.update { it.copy(snackbar = app.getString(com.duckpsycho.telegramreader.R.string.proxy_save_failure)) }
                return@launch
            }
            _state.update { it.copy(proxy = settings) }
            bootstrap()
        }
    }

    fun deleteProxy() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                app.proxyStore.clear()
                app.proxyRouting.update(null, app.httpClient)
            }
            _state.update { it.copy(proxy = null) }
            bootstrap()
        }
    }

    fun consumeRecreate() {
        _state.update { it.copy(recreateForLocale = false) }
    }

    fun backToList() {
        cancelFeedWork()
        _state.update {
            it.copy(
                activeUsername = null,
                feed = null,
                overlay = Overlay.None,
            )
        }
    }

    fun handleDeepLink(uri: Uri) {
        val link = parseReaderDeepLink(uri) ?: return
        openChannel(link.username, link.postId)
    }

    fun openChannel(username: String, focusPostId: Long? = null) {
        cancelFeedWork()
        val knownChannel = channelFromSubscriptions(username)
        _state.update {
            it.copy(
                activeUsername = username,
                overlay = Overlay.None,
                feed = ChannelFeedState(
                    username = username,
                    channel = knownChannel,
                    loading = true,
                    focusPostId = focusPostId,
                    highlightedPostId = focusPostId,
                ),
            )
        }
        feedLoadJob = viewModelScope.launch {
            loadInitialFeed(username, focusPostId, knownChannel)
            if (_state.value.activeUsername == username) {
                startPolling(username)
            }
        }
    }

    private fun channelFromSubscriptions(username: String): Channel? = _state.value.subscriptions
        .firstOrNull { it.channel.username.equals(username, ignoreCase = true) }
        ?.channel

    private fun isCurrentFeed(username: String): Boolean = _state.value.activeUsername == username &&
        _state.value.feed?.username == username

    private suspend fun loadInitialFeed(username: String, focusPostId: Long?, knownChannel: Channel?) {
        try {
            val page = withContext(Dispatchers.IO) { api.getPosts(username) }
            if (!isCurrentFeed(username)) return

            var posts = mergePosts(emptyList(), page.posts)
            var hasMore = page.hasMore
            var nextBefore = page.nextBefore
            var channel = mergeChannel(knownChannel, page.channel)

            if (focusPostId != null && posts.none { it.id == focusPostId }) {
                val jumped = loadPostsAround(api, username, focusPostId, posts, hasMore, nextBefore)
                if (!isCurrentFeed(username)) return
                posts = jumped.posts
                hasMore = jumped.hasMore
                nextBefore = jumped.nextBefore
                channel = mergeChannel(channel, jumped.channel)
            }

            if (!isCurrentFeed(username)) return
            _state.update {
                it.copy(
                    feed = ChannelFeedState(
                        username = username,
                        channel = channel ?: Channel(username = username, title = username),
                        posts = posts,
                        hasMore = hasMore,
                        nextBefore = nextBefore,
                        loading = false,
                        focusPostId = focusPostId,
                        highlightedPostId = focusPostId,
                        notFound = channel == null && posts.isEmpty(),
                    ),
                )
            }
        } catch (e: ApiException) {
            if (!isCurrentFeed(username)) return
            _state.update {
                it.copy(
                    feed = ChannelFeedState(
                        username = username,
                        loading = false,
                        notFound = e.httpStatus == 404,
                        error = app.resolveApiError(e),
                    ),
                )
            }
        } catch (e: Exception) {
            if (!isCurrentFeed(username)) return
            _state.update {
                it.copy(
                    feed = ChannelFeedState(
                        username = username,
                        loading = false,
                        error = app.resolveApiError(e),
                    ),
                )
            }
        }
    }

    fun loadOlderPosts() {
        val feed = _state.value.feed ?: return
        if (!feed.hasMore || feed.nextBefore == null || feed.loadingMore || feed.loading) return
        val username = feed.username
        val before = feed.nextBefore
        _state.update { it.copy(feed = feed.copy(loadingMore = true)) }
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { api.getPosts(username, before) }
                _state.update { state ->
                    val current = state.feed ?: return@update state
                    if (current.username != username) return@update state
                    state.copy(
                        feed = current.copy(
                            posts = mergePosts(current.posts, page.posts),
                            hasMore = page.hasMore,
                            nextBefore = page.nextBefore,
                            loadingMore = false,
                            channel = mergeChannel(current.channel, page.channel) ?: current.channel,
                        ),
                    )
                }
            } catch (_: Exception) {
                _state.update { state ->
                    val current = state.feed ?: return@update state
                    state.copy(feed = current.copy(loadingMore = false))
                }
            }
        }
    }

    private fun startPolling(username: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val feed = _state.value.feed ?: break
                if (feed.username != username) break
                try {
                    val page = withContext(Dispatchers.IO) { api.getPosts(username) }
                    _state.update { state ->
                        val current = state.feed ?: return@update state
                        if (current.username != username) return@update state
                        state.copy(
                            feed = current.copy(
                                posts = mergePosts(current.posts, page.posts),
                                channel = mergeChannel(current.channel, page.channel) ?: current.channel,
                            ),
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun cancelFeedWork() {
        feedLoadJob?.cancel()
        feedLoadJob = null
        stopPolling()
    }

    private fun startSubscriptionsPolling() {
        if (subscriptionsPollJob?.isActive == true) return
        subscriptionsPollJob?.cancel()
        subscriptionsPollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (!_state.value.hasAccount) break
                try {
                    val subscriptions = withContext(Dispatchers.IO) { api.getSubscriptions() }
                    applySubscriptions(subscriptions)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun stopSubscriptionsPolling() {
        subscriptionsPollJob?.cancel()
        subscriptionsPollJob = null
    }

    /** Clears [ChannelFeedState.focusPostId] after scroll; keeps highlight. */
    fun consumeFocusPost() {
        _state.update { state ->
            val feed = state.feed ?: return@update state
            state.copy(feed = feed.copy(focusPostId = null))
        }
    }

    fun navigateToReply(postId: Long) {
        val feed = _state.value.feed ?: return
        _state.update {
            it.copy(feed = feed.copy(highlightedPostId = postId, focusPostId = postId))
        }
        if (feed.posts.any { it.id == postId }) return
        if (feed.loading || feed.loadingMore) return

        val username = feed.username
        _state.update { state ->
            val current = state.feed ?: return@update state
            state.copy(feed = current.copy(loadingMore = true))
        }
        viewModelScope.launch {
            try {
                val current = _state.value.feed
                if (current == null || current.username != username) {
                    _state.update { state ->
                        val latest = state.feed ?: return@update state
                        if (latest.username != username) return@update state
                        state.copy(feed = latest.copy(loadingMore = false))
                    }
                    return@launch
                }
                val jumped = loadPostsAround(
                    api = api,
                    username = username,
                    targetId = postId,
                    seedPosts = current.posts,
                    seedHasMore = current.hasMore,
                    seedNextBefore = current.nextBefore,
                )
                _state.update { state ->
                    val latest = state.feed ?: return@update state
                    if (latest.username != username) return@update state
                    state.copy(
                        feed = latest.copy(
                            posts = jumped.posts,
                            hasMore = jumped.hasMore,
                            nextBefore = jumped.nextBefore,
                            loadingMore = false,
                            channel = mergeChannel(latest.channel, jumped.channel) ?: latest.channel,
                        ),
                    )
                }
            } catch (_: Exception) {
                _state.update { state ->
                    val latest = state.feed ?: return@update state
                    if (latest.username != username) return@update state
                    state.copy(feed = latest.copy(loadingMore = false))
                }
            }
        }
    }

    fun login(accountId: String, onSuccess: () -> Unit) {
        val id = accountId.trim()
        if (id.isEmpty()) {
            _state.update { it.copy(actionError = "enter_id") }
            return
        }
        runAuthAction(
            request = {
                api.login(id)
                ChannelWidgetUpdater.onAccountChanged(app, null)
            },
            closeOverlay = true,
            onSuccess = {
                ChannelWidgetUpdater.onAccountChanged(app, _state.value.account?.id)
                onSuccess()
            },
        )
    }

    fun logout() {
        _state.update { it.copy(actionLoading = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.logout() }
            } catch (_: Exception) {
            }
            cookieJar.clear()
            prefs.clearCachedSubscriptions()
            ChannelWidgetUpdater.onAccountChanged(app, null)
            cancelFeedWork()
            stopSubscriptionsPolling()
            _state.update {
                it.copy(
                    hasAccount = false,
                    account = null,
                    subscriptions = emptyList(),
                    activeUsername = null,
                    feed = null,
                    actionLoading = false,
                    overlay = Overlay.None,
                )
            }
        }
    }

    fun subscribe(input: String, onSuccess: (String) -> Unit) {
        val username = parseChannelUsername(input)
        if (username == null) {
            _state.update { it.copy(actionError = "invalid") }
            return
        }
        runAuthAction(
            request = { api.subscribe(username) },
            closeOverlay = true,
            onSuccess = { onSuccess(username) },
        )
    }

    fun subscribeCurrent() {
        val username = _state.value.activeUsername ?: return
        runAuthAction(
            request = { api.subscribe(username) },
            closeOverlay = false,
            reportErrorAsSnackbar = true,
        )
    }

    private fun runAuthAction(
        request: suspend () -> Unit,
        closeOverlay: Boolean,
        reportErrorAsSnackbar: Boolean = false,
        onSuccess: (() -> Unit)? = null,
    ) {
        _state.update { it.copy(actionLoading = true, actionError = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { request() }
                refreshAccountAndSubscriptions()
                _state.update { state ->
                    state.copy(
                        actionLoading = false,
                        overlay = if (closeOverlay) Overlay.None else state.overlay,
                    )
                }
                if (_state.value.hasAccount) startSubscriptionsPolling()
                onSuccess?.invoke()
            } catch (e: Exception) {
                val message = app.resolveApiError(e)
                _state.update {
                    it.copy(
                        actionLoading = false,
                        actionError = if (reportErrorAsSnackbar) it.actionError else message,
                        snackbar = if (reportErrorAsSnackbar) message else it.snackbar,
                    )
                }
            }
        }
    }

    fun leaveChannel() {
        val username = _state.value.activeUsername ?: return
        _state.update { it.copy(actionLoading = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.unsubscribe(username) }
                val subscriptions = withContext(Dispatchers.IO) { api.getSubscriptions() }
                applySubscriptions(subscriptions)
                cancelFeedWork()
                _state.update {
                    it.copy(
                        activeUsername = null,
                        feed = null,
                        actionLoading = false,
                        overlay = Overlay.None,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        actionLoading = false,
                        snackbar = app.resolveApiError(e),
                        overlay = Overlay.None,
                    )
                }
            }
        }
    }

    fun isSubscribed(username: String): Boolean = _state.value.subscriptions.any { it.channel.username.equals(username, ignoreCase = true) }

    override fun onCleared() {
        cancelFeedWork()
        stopSubscriptionsPolling()
        super.onCleared()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L
    }
}
