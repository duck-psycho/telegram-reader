package com.duckpsycho.telegramreader.ui.shell

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duckpsycho.telegramreader.platform.update.GitHubReleaseChecker
import com.duckpsycho.telegramreader.platform.update.ReleaseUpdate
import com.duckpsycho.telegramreader.ui.Overlay
import com.duckpsycho.telegramreader.ui.ReaderViewModel
import com.duckpsycho.telegramreader.ui.channel.ChannelInfoDialog
import com.duckpsycho.telegramreader.ui.channel.ChannelScreen
import com.duckpsycho.telegramreader.ui.channel.LeaveChannelDialog
import com.duckpsycho.telegramreader.ui.settings.AddChannelScreen
import com.duckpsycho.telegramreader.ui.settings.LoginScreen
import com.duckpsycho.telegramreader.ui.settings.SettingsScreen
import com.duckpsycho.telegramreader.ui.sidebar.SidebarScreen
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import com.duckpsycho.telegramreader.ui.theme.TelegramReaderTheme

@Composable
fun ReaderApp(
    deepLinkUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {},
    viewModel: ReaderViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var availableUpdate by remember { mutableStateOf<ReleaseUpdate?>(null) }

    LaunchedEffect(Unit) {
        availableUpdate = GitHubReleaseChecker.check()
    }

    LaunchedEffect(deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        viewModel.handleDeepLink(uri)
        onDeepLinkHandled()
    }

    LaunchedEffect(state.recreateForLocale) {
        if (state.recreateForLocale) {
            viewModel.consumeRecreate()
            (context as? Activity)?.recreate()
        }
    }

    LaunchedEffect(state.snackbar) {
        val message = state.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearSnackbar()
    }

    TelegramReaderTheme(preference = state.theme) {
        val colors = ReaderTheme.colors
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = colors.bg,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(colors.bg),
            ) {
                val showChannel = state.activeUsername != null && state.feed != null

                if (showChannel) {
                    BackHandler { viewModel.backToList() }
                    ChannelScreen(
                        feed = state.feed!!,
                        isSubscribed = viewModel.isSubscribed(state.activeUsername!!),
                        locale = state.locale,
                        actionLoading = state.actionLoading,
                        onBack = viewModel::backToList,
                        onLoadOlder = viewModel::loadOlderPosts,
                        onReplyClick = viewModel::navigateToReply,
                        onFocusConsumed = viewModel::consumeFocusPost,
                        onSubscribe = viewModel::subscribeCurrent,
                        onOpenInfo = { viewModel.openOverlay(Overlay.ChannelInfo) },
                        onLeave = { viewModel.openOverlay(Overlay.LeaveChannel) },
                        onCopied = viewModel::showSnackbar,
                    )
                } else {
                    SidebarScreen(
                        hasAccount = state.hasAccount,
                        subscriptions = state.subscriptions,
                        searchQuery = state.searchQuery,
                        activeUsername = state.activeUsername,
                        locale = state.locale,
                        availableUpdate = availableUpdate,
                        onSearchChange = viewModel::setSearch,
                        onOpenChannel = { viewModel.openChannel(it) },
                        onOpenSettings = { viewModel.openOverlay(Overlay.Settings) },
                        onOpenLogin = { viewModel.openOverlay(Overlay.Login) },
                        onAddChannel = { viewModel.openOverlay(Overlay.AddChannel) },
                        onOpenSettingsFromSupport = { viewModel.openOverlay(Overlay.Settings) },
                    )
                }

                when (state.overlay) {
                    Overlay.None -> Unit

                    Overlay.Settings -> {
                        BackHandler { viewModel.closeOverlay() }
                        SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            account = state.account,
                            subscriptionCount = state.subscriptions.size,
                            theme = state.theme,
                            locale = state.locale,
                            loading = state.actionLoading,
                            onBack = viewModel::closeOverlay,
                            onThemeChange = viewModel::setTheme,
                            onLocaleChange = viewModel::setLocale,
                            onLogout = viewModel::logout,
                            onCopied = viewModel::showSnackbar,
                        )
                    }

                    Overlay.Login -> {
                        BackHandler { viewModel.closeOverlay() }
                        LoginScreen(
                            modifier = Modifier.fillMaxSize(),
                            loading = state.actionLoading,
                            error = state.actionError,
                            onBack = viewModel::closeOverlay,
                            onSubmit = { viewModel.login(it) {} },
                        )
                    }

                    Overlay.AddChannel -> {
                        AddChannelScreen(
                            loading = state.actionLoading,
                            error = state.actionError,
                            onDismiss = viewModel::closeOverlay,
                            onSubmit = { input ->
                                viewModel.subscribe(input) { username ->
                                    viewModel.openChannel(username)
                                }
                            },
                        )
                    }

                    Overlay.ChannelInfo -> {
                        val channel = state.feed?.channel
                        if (channel != null) {
                            ChannelInfoDialog(
                                channel = channel,
                                onDismiss = viewModel::closeOverlay,
                            )
                        }
                    }

                    Overlay.LeaveChannel -> {
                        LeaveChannelDialog(
                            title = state.feed?.channel?.title ?: state.activeUsername.orEmpty(),
                            loading = state.actionLoading,
                            onDismiss = viewModel::closeOverlay,
                            onConfirm = viewModel::leaveChannel,
                        )
                    }
                }
            }
        }
    }
}
