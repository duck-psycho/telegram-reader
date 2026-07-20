package com.duckpsycho.telegramreader.util

import com.duckpsycho.telegramreader.data.AppLocale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun parseIsoDate(value: String): Date? {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
    )
    for (pattern in patterns) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.parse(value)
        } catch (_: Exception) {
        }
    }
    return null
}

fun formatChatListDate(iso: String, locale: AppLocale, yesterdayLabel: String): String {
    val date = parseIsoDate(iso) ?: return iso
    val cal = Calendar.getInstance().apply { time = date }
    val now = Calendar.getInstance()
    val javaLocale = if (locale == AppLocale.En) Locale.ENGLISH else Locale("ru")

    val timeFmt = SimpleDateFormat("HH:mm", javaLocale)
    val time = timeFmt.format(date)

    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfDate = Calendar.getInstance().apply {
        this.time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffDays = ((startOfToday.timeInMillis - startOfDate.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()

    return when {
        diffDays == 0 -> time

        diffDays == 1 -> yesterdayLabel

        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
            val dayFmt = SimpleDateFormat("d MMM", javaLocale)
            dayFmt.format(date)
        }

        else -> {
            val dayFmt = SimpleDateFormat(
                if (locale == AppLocale.En) "M/d/yy" else "d.M.yy",
                javaLocale,
            )
            dayFmt.format(date)
        }
    }
}

fun formatPostDate(iso: String, locale: AppLocale, yesterdayLabel: String): String {
    val date = parseIsoDate(iso) ?: return iso
    val cal = Calendar.getInstance().apply { time = date }
    val now = Calendar.getInstance()
    val javaLocale = if (locale == AppLocale.En) Locale.ENGLISH else Locale("ru")

    val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

    val timeFmt = SimpleDateFormat("HH:mm", javaLocale)
    val time = timeFmt.format(date)

    return when {
        sameDay -> time

        isYesterday -> "$yesterdayLabel, $time"

        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
            val dayFmt = SimpleDateFormat("d MMM", javaLocale)
            "${dayFmt.format(date)}, $time"
        }

        else -> {
            val dayFmt = SimpleDateFormat("d MMM yyyy", javaLocale)
            "${dayFmt.format(date)}, $time"
        }
    }
}

fun formatAccountCreatedAt(iso: String, locale: AppLocale): String {
    val date = parseIsoDate(iso) ?: return iso
    val javaLocale = if (locale == AppLocale.En) Locale.ENGLISH else Locale("ru")
    val fmt = SimpleDateFormat("d MMMM yyyy, HH:mm", javaLocale)
    return fmt.format(date)
}

data class CountWordForms(
    val one: String,
    val few: String,
    val many: String,
)

private fun parseCount(value: String): Int? {
    val normalized = value.trim().replace("\\s".toRegex(), "")
    if (normalized.isEmpty() || !normalized.all { it.isDigit() }) return null
    return normalized.toIntOrNull()
}

private fun pluralize(count: Int, locale: AppLocale, forms: CountWordForms): String {
    if (locale == AppLocale.En) {
        return if (kotlin.math.abs(count) == 1) forms.one else forms.many
    }
    val abs = kotlin.math.abs(count)
    val mod10 = abs % 10
    val mod100 = abs % 100
    return when {
        mod10 == 1 && mod100 != 11 -> forms.one
        mod10 in 2..4 && (mod100 < 10 || mod100 >= 20) -> forms.few
        else -> forms.many
    }
}

fun formatCount(value: String, locale: AppLocale, forms: CountWordForms): String {
    val count = parseCount(value)
    val word = if (count != null) pluralize(count, locale, forms) else forms.many
    return "$value $word"
}

fun maskedAccountId(id: String): String = "*".repeat(id.length)

fun formatCacheSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
