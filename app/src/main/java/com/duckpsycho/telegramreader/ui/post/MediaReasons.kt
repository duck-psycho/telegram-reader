package com.duckpsycho.telegramreader.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.duckpsycho.telegramreader.R

@Composable
internal fun mediaReasonText(reason: String): String = when (normalizeMediaReason(reason)) {
    "photo_load_failed" -> stringResource(R.string.media_photo_load_failed)
    "video_load_failed" -> stringResource(R.string.media_video_load_failed)
    "sticker_load_failed" -> stringResource(R.string.media_sticker_load_failed)
    "videosticker_load_failed" -> stringResource(R.string.media_videosticker_load_failed)
    "document_load_failed" -> stringResource(R.string.media_document_load_failed)
    "location_load_failed" -> stringResource(R.string.media_location_load_failed)
    "unsupported_post_type" -> stringResource(R.string.media_unsupported_post_type)
    "media_unavailable" -> stringResource(R.string.media_unavailable)
    "media_not_supported_browser" -> stringResource(R.string.media_not_supported_browser)
    "open_in_telegram" -> stringResource(R.string.media_open_in_telegram)
    "media_too_big" -> stringResource(R.string.media_too_big)
    "channel_created" -> stringResource(R.string.media_channel_created)
    else -> reason
}

/** Maps API reason keys and raw t.me labels to stable keys. */
internal fun normalizeMediaReason(reason: String): String = when (reason.trim()) {
    "channel_created", "Channel created", "Канал создан" -> "channel_created"

    "media_not_supported_browser",
    "This media is not supported in your browser",
    "This media is not supported in the browser",
    "This media is not supported",
    "Это медиа не поддерживается в браузере",
    "Это медиа не поддерживается",
    -> "media_not_supported_browser"

    "open_in_telegram",
    "Please open Telegram to view this post",
    -> "open_in_telegram"

    "media_too_big", "Media is too big" -> "media_too_big"

    else -> reason.trim()
}

@Composable
internal fun localizedServicePostText(text: String): String = when (normalizeMediaReason(text)) {
    "channel_created" -> stringResource(R.string.media_channel_created)

    "media_not_supported_browser" -> stringResource(R.string.media_not_supported_browser)

    "open_in_telegram" -> stringResource(R.string.media_open_in_telegram)

    "media_too_big" -> stringResource(R.string.media_too_big)

    else -> when (text.trim()) {
        "Геолокация", "Location" -> stringResource(R.string.post_location)
        "Файл", "File" -> stringResource(R.string.preview_file)
        "Фото", "Photo" -> stringResource(R.string.preview_photo)
        "Видео", "Video" -> stringResource(R.string.preview_video)
        "Стикер", "Sticker" -> stringResource(R.string.preview_sticker)
        else -> text
    }
}

internal fun isChannelCreatedServiceText(text: String?): Boolean = text != null && normalizeMediaReason(text) == "channel_created"
