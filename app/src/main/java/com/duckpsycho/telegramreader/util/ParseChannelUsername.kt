package com.duckpsycho.telegramreader.util

internal val TELEGRAM_USERNAME_RE = Regex("^[a-zA-Z][a-zA-Z0-9_]{4,31}$")

private val TELEGRAM_HOSTS = setOf(
    "t.me",
    "telegram.me",
    "telegram.dog",
    "www.t.me",
    "www.telegram.me",
    "www.telegram.dog",
)
private val RESERVED_PATHS = setOf(
    "c", "addstickers", "share", "proxy", "socks", "setlanguage",
    "joinchat", "addlist", "boost", "iv", "login", "confirmphone",
)

private fun isValidUsername(value: String): Boolean = TELEGRAM_USERNAME_RE.matches(value)

private fun usernameFromPath(pathname: String): String? {
    val parts = pathname.split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    if (parts[0].startsWith('+') || parts[0].equals("joinchat", ignoreCase = true)) return null
    if (parts[0].equals("s", ignoreCase = true)) {
        val candidate = parts.getOrNull(1) ?: return null
        return if (isValidUsername(candidate)) candidate.lowercase() else null
    }
    if (RESERVED_PATHS.contains(parts[0].lowercase())) return null
    val candidate = parts[0]
    return if (isValidUsername(candidate)) candidate.lowercase() else null
}

private fun parseHttpTelegramLink(raw: String): String? {
    val url = try {
        java.net.URI(if (raw.contains("://")) raw else "https://$raw").toURL()
    } catch (_: Exception) {
        return null
    }
    if (!TELEGRAM_HOSTS.contains(url.host.lowercase())) return null
    return usernameFromPath(url.path)
}

private fun parseTgProtocol(raw: String): String? {
    val normalized = raw.replace(Regex("^tg:", RegexOption.IGNORE_CASE), "tg://")
    val uri = try {
        java.net.URI(normalized)
    } catch (_: Exception) {
        return null
    }
    if (uri.scheme != "tg") return null
    val host = (uri.host ?: "").lowercase()
    val path = uri.path?.removePrefix("/")?.lowercase().orEmpty()
    if (host == "resolve" || path == "resolve" || host.isEmpty()) {
        val query = uri.rawQuery ?: return null
        val domain = query.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.firstOrNull() == "domain" }
            ?.getOrNull(1)
        if (domain != null && isValidUsername(domain)) return domain.lowercase()
    }
    return null
}

fun parseChannelUsername(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith('@')) {
        val candidate = trimmed.drop(1)
        return if (isValidUsername(candidate)) candidate.lowercase() else null
    }
    if (trimmed.startsWith("tg:", ignoreCase = true)) {
        return parseTgProtocol(trimmed)
    }
    if (Regex("""^(https?://)?((www\.)?(t\.me|telegram\.me|telegram\.dog))\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(trimmed)
    ) {
        return parseHttpTelegramLink(trimmed)
    }
    return if (isValidUsername(trimmed)) trimmed.lowercase() else null
}
