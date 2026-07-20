package com.duckpsycho.telegramreader.util

import android.net.Uri
import com.duckpsycho.telegramreader.data.BASE_URL
import java.net.URI

data class ReaderDeepLink(
    val username: String,
    val postId: Long? = null,
)

fun parseReaderDeepLink(uri: Uri): ReaderDeepLink? {
    val readerHost = URI(BASE_URL).host?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host != readerHost && host != "www.$readerHost") return null

    val segments = uri.path?.split('/')?.filter { it.isNotEmpty() } ?: return null
    if (segments.firstOrNull() != "channel") return null

    val username = segments.getOrNull(1)?.lowercase() ?: return null
    if (!TELEGRAM_USERNAME_RE.matches(username)) return null

    val postId = segments.getOrNull(2)?.toLongOrNull()
    return ReaderDeepLink(username = username, postId = postId)
}

fun parseReaderDeepLink(url: String): ReaderDeepLink? = runCatching { parseReaderDeepLink(Uri.parse(url.trim())) }.getOrNull()
