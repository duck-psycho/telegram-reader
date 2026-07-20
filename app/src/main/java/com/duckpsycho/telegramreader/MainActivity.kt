package com.duckpsycho.telegramreader

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.duckpsycho.telegramreader.data.AppLocale
import com.duckpsycho.telegramreader.ui.shell.ReaderApp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var deepLinkUri by mutableStateOf<Uri?>(null)

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("telegram_reader_prefs", MODE_PRIVATE)
        val tag = prefs.getString("locale", null) ?: Locale.getDefault().language
        val locale = Locale.forLanguageTag(AppLocale.fromTag(tag).tag)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkUri = intent?.data
        enableEdgeToEdge()
        setContent {
            ReaderApp(
                deepLinkUri = deepLinkUri,
                onDeepLinkHandled = { deepLinkUri = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUri = intent.data
    }
}
