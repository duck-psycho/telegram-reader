package com.duckpsycho.telegramreader.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.duckpsycho.telegramreader.MainActivity
import com.duckpsycho.telegramreader.util.parseReaderDeepLink

fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

fun openUrl(context: Context, url: String, withAppReferrer: Boolean = false) {
    if (parseReaderDeepLink(url) != null) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()), context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
        return
    }
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (withAppReferrer) {
                putExtra(
                    Intent.EXTRA_REFERRER,
                    Uri.parse("android-app://${context.packageName}"),
                )
            }
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
