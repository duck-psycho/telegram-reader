package com.duckpsycho.telegramreader.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("telegram_reader_cookies", Context.MODE_PRIVATE)
    private val lock = Any()
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    init {
        load()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val host = url.host
            val existing = store.getOrPut(host) { mutableListOf() }
            for (cookie in cookies) {
                existing.removeAll { it.name == cookie.name }
                if (cookie.expiresAt > System.currentTimeMillis() || cookie.persistent.not()) {
                    if (cookie.value.isNotEmpty()) {
                        existing.add(cookie)
                    }
                }
            }
            existing.removeAll { it.expiresAt <= System.currentTimeMillis() && it.persistent }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val host = url.host
            val cookies = store[host] ?: return emptyList()
            val removed = cookies.removeAll {
                it.expiresAt <= System.currentTimeMillis() && it.persistent
            }
            if (removed) persist()
            return cookies.filter { it.matches(url) }
        }
    }

    fun clear() {
        synchronized(lock) {
            store.clear()
            prefs.edit().clear().apply()
        }
    }

    fun hasAccountCookie(): Boolean {
        synchronized(lock) {
            return store.values.flatten().any { it.name == "account_id" && it.value.isNotEmpty() }
        }
    }

    private fun persist() {
        val array = JSONArray()
        for ((host, cookies) in store) {
            for (cookie in cookies) {
                array.put(
                    JSONObject()
                        .put("host", host)
                        .put("name", cookie.name)
                        .put("value", cookie.value)
                        .put("domain", cookie.domain)
                        .put("path", cookie.path)
                        .put("expiresAt", cookie.expiresAt)
                        .put("secure", cookie.secure)
                        .put("httpOnly", cookie.httpOnly)
                        .put("hostOnly", cookie.hostOnly)
                        .put("persistent", cookie.persistent),
                )
            }
        }
        prefs.edit().putString("cookies", array.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString("cookies", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val expiresAt = obj.getLong("expiresAt")
                if (obj.optBoolean("persistent", true) && expiresAt <= System.currentTimeMillis()) {
                    continue
                }
                val host = obj.getString("host")
                val builder = Cookie.Builder()
                    .name(obj.getString("name"))
                    .value(obj.getString("value"))
                    .path(obj.getString("path"))
                    .expiresAt(expiresAt)
                if (obj.optBoolean("hostOnly")) {
                    builder.hostOnlyDomain(obj.getString("domain"))
                } else {
                    builder.domain(obj.getString("domain"))
                }
                if (obj.optBoolean("secure")) builder.secure()
                if (obj.optBoolean("httpOnly")) builder.httpOnly()
                store.getOrPut(host) { mutableListOf() }.add(builder.build())
            }
        } catch (_: Exception) {
            prefs.edit().clear().apply()
        }
    }
}
