package com.duckpsycho.telegramreader.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.data.SubscriptionItem
import com.duckpsycho.telegramreader.platform.update.ReleaseUpdate
import com.duckpsycho.telegramreader.ui.components.ReaderFab
import com.duckpsycho.telegramreader.ui.components.ReaderTextField
import com.duckpsycho.telegramreader.ui.components.openUrl
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SidebarScreen(
    hasAccount: Boolean,
    subscriptions: List<SubscriptionItem>,
    searchQuery: String,
    activeUsername: String?,
    locale: AppLocale,
    availableUpdate: ReleaseUpdate? = null,
    onSearchChange: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    onAddChannel: () -> Unit,
    onOpenSettingsFromSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReaderTheme.colors
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var wasImeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (wasImeVisible && !imeVisible) {
            focusManager.clearFocus()
        }
        wasImeVisible = imeVisible
    }
    val filtered = subscriptions.filter {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            true
        } else {
            it.channel.title.lowercase().contains(q) ||
                it.channel.username.lowercase().contains(q)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SidebarTopBar(
                hasAccount = hasAccount,
                onOpenLogin = onOpenLogin,
                onOpenSettings = onOpenSettings,
            )

            if (subscriptions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(colors.radius))
                        .background(colors.bgElevated)
                        .border(1.dp, colors.border, RoundedCornerShape(colors.radius))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    ReaderTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = stringResource(R.string.common_search),
                        fontSize = 15.sp,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() },
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isEmpty()) {
                SidebarEmptyState(
                    modifier = Modifier.weight(1f),
                    hasAccount = hasAccount,
                    onOpenSettingsFromSupport = onOpenSettingsFromSupport,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(filtered, key = { it.channel.username }) { item ->
                        ChannelListItem(
                            item = item,
                            selected = item.channel.username.equals(activeUsername, true),
                            locale = locale,
                            onClick = { onOpenChannel(item.channel.username) },
                        )
                    }
                }
            }
        }

        val fabPadding = 20.dp
        if (availableUpdate != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(fabPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UpdateButton(
                    update = availableUpdate,
                    modifier = Modifier.weight(1f),
                )
                ReaderFab(onClick = onAddChannel) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.nav_add_channel),
                        tint = colors.fabText,
                    )
                }
            }
        } else {
            ReaderFab(
                onClick = onAddChannel,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(fabPadding),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.nav_add_channel),
                    tint = colors.fabText,
                )
            }
        }
    }
}

@Composable
private fun UpdateButton(
    update: ReleaseUpdate,
    modifier: Modifier = Modifier,
) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.fabBg)
            .clickable { openUrl(context, update.pageUrl) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.update_available, update.versionLabel),
            color = colors.fabText,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SidebarTopBar(
    hasAccount: Boolean,
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = ReaderTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = colors.textStrong,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
        if (!hasAccount) {
            IconButton(onClick = onOpenLogin) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(R.string.nav_login),
                    modifier = Modifier.size(24.dp),
                    tint = colors.textStrong,
                )
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.nav_settings),
                modifier = Modifier.size(24.dp),
                tint = colors.textStrong,
            )
        }
    }
}

@Composable
private fun SidebarEmptyState(
    hasAccount: Boolean,
    onOpenSettingsFromSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReaderTheme.colors
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 360.dp),
            ) {
                if (hasAccount) {
                    Text(
                        text = stringResource(R.string.welcome_sidebar_guest),
                        color = colors.textMuted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.empty_intro),
                        color = colors.textMuted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.empty_intro_how_to),
                        color = colors.textMuted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SupportHint(
                    onOpenSettings = onOpenSettingsFromSupport,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
