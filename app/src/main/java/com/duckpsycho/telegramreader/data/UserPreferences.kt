package com.duckpsycho.telegramreader.data

import android.content.Context
import android.content.SharedPreferences
import com.duckpsycho.telegramreader.ui.theme.ThemePreference
import java.util.Locale

enum class AppLocale(val tag: String) {
    Ru("ru"),
    En("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLocale = when (tag?.lowercase()) {
            "en" -> En
            else -> Ru
        }
    }
}

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("telegram_reader_prefs", Context.MODE_PRIVATE)

    var theme: ThemePreference
        get() = when (prefs.getString(KEY_THEME, "system")) {
            "light" -> ThemePreference.Light
            "dark" -> ThemePreference.Dark
            else -> ThemePreference.System
        }
        set(value) {
            val raw = when (value) {
                ThemePreference.System -> "system"
                ThemePreference.Light -> "light"
                ThemePreference.Dark -> "dark"
            }
            prefs.edit().putString(KEY_THEME, raw).apply()
        }

    var locale: AppLocale
        get() = AppLocale.fromTag(prefs.getString(KEY_LOCALE, null) ?: Locale.getDefault().language)
        set(value) {
            prefs.edit().putString(KEY_LOCALE, value.tag).apply()
        }

    fun loadCachedSubscriptions(): List<SubscriptionItem> {
        val json = prefs.getString(KEY_SUBSCRIPTIONS, null) ?: return emptyList()
        return decodeSubscriptions(json) ?: emptyList()
    }

    fun saveCachedSubscriptions(subscriptions: List<SubscriptionItem>) {
        prefs.edit().putString(KEY_SUBSCRIPTIONS, encodeSubscriptions(subscriptions)).apply()
    }

    fun clearCachedSubscriptions() {
        prefs.edit().remove(KEY_SUBSCRIPTIONS).apply()
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_LOCALE = "locale"
        private const val KEY_SUBSCRIPTIONS = "subscriptions_cache"
    }
}
