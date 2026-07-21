package com.duckpsycho.telegramreader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.BuildConfig
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.TelegramReaderApp
import com.duckpsycho.telegramreader.data.Account
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.data.CacheStats
import com.duckpsycho.telegramreader.ui.components.SecondaryButton
import com.duckpsycho.telegramreader.ui.components.copyToClipboard
import com.duckpsycho.telegramreader.ui.components.openUrl
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import com.duckpsycho.telegramreader.ui.theme.ThemePreference
import com.duckpsycho.telegramreader.util.formatAccountCreatedAt
import com.duckpsycho.telegramreader.util.formatCacheSize
import com.duckpsycho.telegramreader.util.maskedAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    account: Account?,
    subscriptionCount: Int,
    theme: ThemePreference,
    locale: AppLocale,
    loading: Boolean,
    onBack: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onLocaleChange: (AppLocale) -> Unit,
    onLogout: () -> Unit,
    onCopied: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReaderTheme.colors
    val context = LocalContext.current
    val mediaCache = remember {
        (context.applicationContext as TelegramReaderApp).mediaCache
    }
    var cacheStats by remember {
        mutableStateOf(CacheStats(fileCount = 0, usedBytes = 0L))
    }
    var clearingCache by remember { mutableStateOf(false) }
    var showId by remember { mutableStateOf(false) }
    var showIdInfo by remember { mutableStateOf(false) }
    var idCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        cacheStats = withContext(Dispatchers.IO) { mediaCache.stats() }
    }

    val developerLinks = listOf(
        DevLink("GitHub", "github.com/duck-psycho", "https://github.com/duck-psycho"),
        DevLink("X (Twitter)", "x.com/duckpsycho_dev", "https://x.com/duckpsycho_dev"),
        DevLink(stringResource(R.string.settings_site), "duckpsycho.dev", "https://duckpsycho.dev"),
    )
    val donations = listOf(
        Donation("Boosty", "https://boosty.to/duckpsycho", "https://boosty.to/duckpsycho"),
        Donation(stringResource(R.string.settings_boosty_donate), "https://boosty.to/duckpsycho/donate", "https://boosty.to/duckpsycho/donate"),
        Donation("BTC (Bitcoin)", "178tovsfYQ8omdEPQSHAiH4jXBbBCWNacs"),
        Donation("USDT, TRX (TRC20)", "TLD6QVdHhHNRDgRkYuqhUa2B9bkYgeRxmy"),
        Donation("TON", "EQDuRyrzSzP8dqbMVNwEBvan_LPK44uRAIuAoT_VF2BXQtS6"),
        Donation("ETH (ERC20)", "0xF7556e3969e520A677e91E2bFb90EA88bD57EaD9"),
        Donation("SOL (Solana)", "3tGbXMzqTu7av7DjriY7wkWGEEHxshEZN9SbFP4qBi8v"),
        Donation("LTC (Litecoin)", "LfmZvRPM4zb6EtwnAn9ATLo8kK4Rw7JxUK"),
        Donation("BNB", "0xF7556e3969e520A677e91E2bFb90EA88bD57EaD9"),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        OverlayHeader(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(title = stringResource(R.string.settings_account)) {
                if (account == null) {
                    Text(
                        stringResource(R.string.settings_account_empty),
                        color = colors.text,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                    )
                } else {
                    AccountSection(
                        account = account,
                        subscriptionCount = subscriptionCount,
                        locale = locale,
                        loading = loading,
                        showId = showId,
                        showIdInfo = showIdInfo,
                        idCopied = idCopied,
                        onToggleShowId = { showId = !showId },
                        onToggleShowIdInfo = { showIdInfo = !showIdInfo },
                        onCopyId = {
                            copyToClipboard(context, "account_id", account.id)
                            onCopied(context.getString(R.string.settings_id_copied))
                            idCopied = true
                            scope.launch {
                                delay(1500)
                                idCopied = false
                            }
                        },
                        onLogout = onLogout,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_web_version_hint),
                    color = colors.textMuted,
                    fontSize = 14.sp,
                )
                LinkRow(
                    label = stringResource(R.string.settings_web_version),
                    value = "reader.duckpsycho.dev",
                    onOpen = { openUrl(context, "https://reader.duckpsycho.dev/", withAppReferrer = true) },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_cache)) {
                CacheSection(
                    stats = cacheStats,
                    clearing = clearingCache,
                    onClear = {
                        if (clearingCache) return@CacheSection
                        clearingCache = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                mediaCache.clear()
                            }
                            cacheStats = withContext(Dispatchers.IO) { mediaCache.stats() }
                            clearingCache = false
                        }
                    },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_theme), compact = true) {
                ThemePreference.entries.forEach { option ->
                    val label = when (option) {
                        ThemePreference.System -> stringResource(R.string.settings_theme_system)
                        ThemePreference.Dark -> stringResource(R.string.settings_theme_dark)
                        ThemePreference.Light -> stringResource(R.string.settings_theme_light)
                    }
                    ChoiceRow(
                        label = label,
                        selected = theme == option,
                        onClick = { onThemeChange(option) },
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_language), compact = true) {
                ChoiceRow(
                    label = stringResource(R.string.settings_locale_ru),
                    selected = locale == AppLocale.Ru,
                    onClick = { onLocaleChange(AppLocale.Ru) },
                )
                ChoiceRow(
                    label = stringResource(R.string.settings_locale_en),
                    selected = locale == AppLocale.En,
                    onClick = { onLocaleChange(AppLocale.En) },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_developer)) {
                developerLinks.forEach { link ->
                    LinkRow(
                        label = link.label,
                        value = link.value,
                        onOpen = { openUrl(context, link.href, withAppReferrer = true) },
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_support)) {
                Text(
                    stringResource(R.string.settings_support_intro),
                    color = colors.text,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                donations.forEach { item ->
                    LinkRow(
                        label = item.label,
                        value = item.value,
                        onOpen = { item.href?.let { openUrl(context, it) } },
                        onCopy = {
                            copyToClipboard(context, item.label, item.value)
                            onCopied(context.getString(R.string.common_copied))
                        },
                    )
                }
            }

            Text(
                stringResource(R.string.common_version, BuildConfig.VERSION_NAME),
                color = colors.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CacheSection(
    stats: CacheStats,
    clearing: Boolean,
    onClear: () -> Unit,
) {
    val colors = ReaderTheme.colors
    val progress = if (stats.maxBytes > 0) {
        (stats.usedBytes.toFloat() / stats.maxBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Text(
        text = stringResource(
            R.string.settings_cache_usage,
            stats.fileCount,
            formatCacheSize(stats.usedBytes),
            formatCacheSize(stats.maxBytes),
        ),
        color = colors.text,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = colors.textStrong,
        trackColor = colors.border,
        drawStopIndicator = {},
    )
    Spacer(Modifier.height(8.dp))
    SecondaryButton(
        text = if (clearing) {
            stringResource(R.string.settings_cache_clearing)
        } else {
            stringResource(R.string.settings_cache_clear)
        },
        onClick = onClear,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountSection(
    account: Account,
    subscriptionCount: Int,
    locale: AppLocale,
    loading: Boolean,
    showId: Boolean,
    showIdInfo: Boolean,
    idCopied: Boolean,
    onToggleShowId: () -> Unit,
    onToggleShowIdInfo: () -> Unit,
    onCopyId: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = ReaderTheme.colors
    val accountIdStyle = TextStyle(
        color = colors.textStrong,
        fontSize = 15.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (showId) account.id else maskedAccountId(account.id),
            style = accountIdStyle,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleShowId) {
            Icon(
                if (showId) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (showId) {
                    stringResource(R.string.settings_hide_id)
                } else {
                    stringResource(R.string.settings_show_id)
                },
                modifier = Modifier.size(24.dp),
                tint = if (showId) colors.textStrong else colors.textMuted,
            )
        }
        IconButton(onClick = onToggleShowIdInfo) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.settings_id_info_label),
                modifier = Modifier.size(24.dp),
                tint = if (showIdInfo) colors.textStrong else colors.textMuted,
            )
        }
        IconButton(onClick = onCopyId) {
            Icon(
                painter = painterResource(if (idCopied) R.drawable.ic_check else R.drawable.ic_copy),
                contentDescription = if (idCopied) {
                    stringResource(R.string.settings_id_copied)
                } else {
                    stringResource(R.string.settings_copy_id)
                },
                modifier = Modifier.size(24.dp),
                tint = if (idCopied) colors.textStrong else colors.textMuted,
            )
        }
    }
    if (showIdInfo) {
        Text(
            stringResource(R.string.settings_id_info),
            color = colors.textMuted,
            fontSize = 15.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
    }
    Text(
        "${stringResource(R.string.settings_created)}: ${formatAccountCreatedAt(account.createdAt, locale)}",
        color = colors.textMuted,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    )
    Text(
        "${stringResource(R.string.settings_subscriptions)}: $subscriptionCount",
        color = colors.textMuted,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    )
    Spacer(Modifier.height(8.dp))
    SecondaryButton(
        text = if (loading) {
            stringResource(R.string.settings_logging_out)
        } else {
            stringResource(R.string.settings_logout)
        },
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth(),
        textColor = colors.error,
    )
}
