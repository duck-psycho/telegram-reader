package com.duckpsycho.telegramreader.widget

import android.content.Context
import android.content.res.Configuration
import com.duckpsycho.telegramreader.data.Post
import com.duckpsycho.telegramreader.data.UserPreferences
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

internal data class WidgetPost(
    val id: Long,
    val text: String,
    val date: String,
    val mediaType: String?,
    val previewFile: String? = null,
)

internal data class WidgetSnapshot(
    val username: String? = null,
    val title: String? = null,
    val accountId: String? = null,
    val status: String = STATUS_LOADING,
    val posts: List<WidgetPost> = emptyList(),
)

internal const val STATUS_LOADING = "loading"
internal const val STATUS_READY = "ready"
internal const val STATUS_ERROR = "error"
internal const val STATUS_UNAVAILABLE = "unavailable"
internal const val STATUS_SIGNED_OUT = "signed_out"

internal class WidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("telegram_reader_widgets", Context.MODE_PRIVATE)
    private val previewDirectory = File(context.cacheDir, "widget_emoji_previews")

    fun read(widgetId: Int): WidgetSnapshot {
        val raw = prefs.getString(key(widgetId), null) ?: return WidgetSnapshot()
        return runCatching {
            val json = JSONObject(raw)
            val array = json.optJSONArray("posts") ?: JSONArray()
            val posts = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        WidgetPost(
                            id = item.getLong("id"),
                            text = item.optString("text"),
                            date = item.getString("date"),
                            mediaType = item.optString("mediaType").ifBlank { null },
                            previewFile = item.optString("previewFile").ifBlank { null },
                        ),
                    )
                }
            }
            WidgetSnapshot(
                username = json.optString("username").ifBlank { null },
                title = json.optString("title").ifBlank { null },
                accountId = json.optString("accountId").ifBlank { null },
                status = json.optString("status", STATUS_LOADING),
                posts = posts,
            )
        }.getOrDefault(WidgetSnapshot())
    }

    fun select(widgetId: Int, username: String, title: String, accountId: String) {
        synchronized(prefs) {
            deletePreviews(read(widgetId).posts)
            write(widgetId, WidgetSnapshot(username, title, accountId))
        }
    }

    fun savePosts(widgetId: Int, expected: WidgetSnapshot, title: String, posts: List<Post>, previews: Map<Long, String>): Boolean {
        val updated = posts.sortedByDescending(Post::id).take(20).map { post ->
            post.toWidgetPost().copy(previewFile = previews[post.id])
        }
        return updateIfSelected(widgetId, expected) {
            deletePreviews(it.posts.filter { old -> old.previewFile !in previews.values })
            it.copy(
                title = title,
                status = STATUS_READY,
                posts = updated,
            )
        }
    }

    fun markError(widgetId: Int, expected: WidgetSnapshot) {
        updateIfSelected(widgetId, expected) { it.copy(status = STATUS_ERROR) }
    }

    fun markUnavailable(widgetId: Int, expected: WidgetSnapshot? = null) = synchronized(prefs) {
        val current = read(widgetId)
        if (expected == null || sameSelection(current, expected)) {
            deletePreviews(current.posts)
            write(widgetId, current.copy(status = STATUS_UNAVAILABLE, posts = emptyList()))
        }
    }

    fun markLoading(widgetId: Int, expected: WidgetSnapshot? = null) = synchronized(prefs) {
        val current = read(widgetId)
        if (expected == null || sameSelection(current, expected)) {
            deletePreviews(current.posts)
            write(widgetId, current.copy(status = STATUS_LOADING, posts = emptyList()))
        }
    }

    fun markRefreshing(widgetId: Int) = synchronized(prefs) {
        val current = read(widgetId)
        if (current.username != null && current.status != STATUS_SIGNED_OUT && current.status != STATUS_UNAVAILABLE) {
            write(widgetId, current.copy(status = STATUS_LOADING))
        }
    }

    fun clearForAccount(widgetId: Int, accountId: String?, expected: WidgetSnapshot? = null) = synchronized(prefs) {
        val current = read(widgetId)
        if (expected == null || sameSelection(current, expected)) {
            val status = if (accountId == null) STATUS_SIGNED_OUT else STATUS_UNAVAILABLE
            deletePreviews(current.posts)
            write(widgetId, current.copy(status = status, posts = emptyList()))
        }
    }

    fun remove(widgetId: Int) {
        synchronized(prefs) {
            deletePreviews(read(widgetId).posts)
            prefs.edit().remove(key(widgetId)).apply()
        }
    }

    private fun updateIfSelected(widgetId: Int, expected: WidgetSnapshot, change: (WidgetSnapshot) -> WidgetSnapshot): Boolean = synchronized(prefs) {
        val current = read(widgetId)
        if (sameSelection(current, expected) &&
            current.status != STATUS_SIGNED_OUT && current.status != STATUS_UNAVAILABLE
        ) {
            write(widgetId, change(current))
            true
        } else {
            false
        }
    }

    private fun deletePreviews(posts: List<WidgetPost>) {
        posts.mapNotNull(WidgetPost::previewFile).forEach { File(previewDirectory, it).delete() }
    }

    private fun sameSelection(current: WidgetSnapshot, expected: WidgetSnapshot): Boolean = current.username == expected.username && current.accountId == expected.accountId

    private fun write(widgetId: Int, snapshot: WidgetSnapshot) {
        val json = JSONObject().apply {
            put("username", snapshot.username)
            put("title", snapshot.title)
            put("accountId", snapshot.accountId)
            put("status", snapshot.status)
            put(
                "posts",
                JSONArray().apply {
                    snapshot.posts.forEach { post ->
                        put(
                            JSONObject().apply {
                                put("id", post.id)
                                put("text", post.text)
                                put("date", post.date)
                                put("mediaType", post.mediaType)
                                put("previewFile", post.previewFile)
                            },
                        )
                    }
                },
            )
        }
        prefs.edit().putString(key(widgetId), json.toString()).apply()
    }

    private fun key(widgetId: Int) = "widget_$widgetId"
}

private fun Post.toWidgetPost(): WidgetPost {
    val plain = html?.takeIf { it.contains("tg-emoji", ignoreCase = true) }?.let(WidgetEmojiPreview::plainText)
        ?: text?.takeIf { it.isNotBlank() }
        ?: html?.let { Jsoup.parse(it).text() }
        ?: ""
    return WidgetPost(
        id = id,
        text = plain.trim(),
        date = date,
        mediaType = mediaType ?: when {
            mediaGroup != null -> "photo"
            document != null -> "document"
            location != null -> "location"
            else -> null
        },
    )
}

internal fun localizedWidgetContext(context: Context): Context {
    val locale = Locale.forLanguageTag(UserPreferences(context).locale.tag)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    return context.createConfigurationContext(config)
}
