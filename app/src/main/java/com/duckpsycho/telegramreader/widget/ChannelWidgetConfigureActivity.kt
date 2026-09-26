package com.duckpsycho.telegramreader.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.duckpsycho.telegramreader.MainActivity
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.TelegramReaderApp
import com.duckpsycho.telegramreader.data.SubscriptionItem
import com.duckpsycho.telegramreader.data.UserPreferences
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelWidgetConfigureActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var loading by mutableStateOf(true)
    private var loadError by mutableStateOf(false)
    private var accountId by mutableStateOf<String?>(null)
    private var channels by mutableStateOf<List<SubscriptionItem>>(emptyList())

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(UserPreferences(newBase).locale.tag))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || widgetId !in ChannelWidgetUpdater.ids(this)) {
            finish()
            return
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(getString(R.string.widget_choose_channel), style = MaterialTheme.typography.headlineSmall)
                        when {
                            loading -> CircularProgressIndicator()

                            loadError -> {
                                Text(getString(R.string.widget_channels_error))
                                Button(onClick = ::loadChannels) { Text(getString(R.string.widget_retry)) }
                            }

                            accountId == null -> {
                                Text(getString(R.string.widget_no_account))
                                Button(onClick = ::openApp) { Text(getString(R.string.app_name)) }
                            }

                            channels.isEmpty() -> {
                                Text(getString(R.string.widget_no_channels))
                                Button(onClick = ::openApp) { Text(getString(R.string.app_name)) }
                            }

                            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(channels, key = { it.channel.username }) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { select(item) }
                                            .padding(vertical = 14.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.channel.title, style = MaterialTheme.typography.titleMedium)
                                            Text("@${item.channel.username}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
        loadChannels()
    }

    private fun loadChannels() {
        loading = true
        loadError = false
        lifecycleScope.launch {
            val app = application as TelegramReaderApp
            if (!app.cookieJar.hasAccountCookie()) {
                accountId = null
                channels = emptyList()
                loading = false
                return@launch
            }
            try {
                val (account, subscriptions) = withContext(Dispatchers.IO) {
                    val account = app.api.getAccount()
                    account to if (account == null) emptyList() else app.api.getSubscriptions()
                }
                accountId = account?.id
                channels = if (account == null) emptyList() else subscriptions.sortedBy { it.channel.title.lowercase() }
            } catch (_: Exception) {
                loadError = true
            }
            loading = false
        }
    }

    private fun select(item: SubscriptionItem) {
        val id = accountId ?: return
        if (!(application as TelegramReaderApp).cookieJar.hasAccountCookie()) {
            loadChannels()
            return
        }
        WidgetStore(this).select(widgetId, item.channel.username, item.channel.title, id)
        ChannelWidgetUpdater.render(this, widgetId)
        ChannelWidgetUpdater.requestRefresh(this, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
