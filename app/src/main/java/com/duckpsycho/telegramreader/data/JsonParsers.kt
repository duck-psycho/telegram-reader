package com.duckpsycho.telegramreader.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return value.ifEmpty { null }
}

private fun absoluteMediaUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> BASE_URL + url
        else -> url
    }
}

/** Rewrites relative `src` / `poster` in post HTML to absolute URLs for Coil. */
private fun absolutizeHtmlMediaUrls(html: String?): String? {
    if (html.isNullOrBlank()) return html
    return HTML_MEDIA_ATTR_PATTERN.replace(html) { match ->
        val attr = match.groupValues[1]
        val quote = match.groupValues[2]
        val url = match.groupValues[3]
        val absolute = absoluteMediaUrl(url) ?: url
        "$attr=$quote$absolute$quote"
    }
}

private val HTML_MEDIA_ATTR_PATTERN =
    Regex("""\b(src|poster)=(["'])([^"']+)\2""", RegexOption.IGNORE_CASE)

fun parseAccount(json: JSONObject): Account = Account(
    id = json.getString("id"),
    createdAt = json.getString("createdAt"),
)

fun parseChannelCounters(json: JSONObject?): ChannelCounters? {
    if (json == null) return null
    return ChannelCounters(
        photos = json.optStringOrNull("photos"),
        videos = json.optStringOrNull("videos"),
        files = json.optStringOrNull("files"),
        links = json.optStringOrNull("links"),
    )
}

fun parseChannelLastPost(json: JSONObject?): ChannelLastPost? {
    if (json == null) return null
    return ChannelLastPost(
        text = json.optStringOrNull("text"),
        date = json.getString("date"),
        mediaType = json.optStringOrNull("mediaType"),
        mediaCount = if (json.has("mediaCount") && !json.isNull("mediaCount")) {
            json.optInt("mediaCount")
        } else {
            null
        },
    )
}

fun parseChannel(json: JSONObject): Channel = Channel(
    username = json.getString("username"),
    title = json.getString("title"),
    description = absolutizeHtmlMediaUrls(json.optStringOrNull("description")),
    subscriberCount = json.optStringOrNull("subscriberCount"),
    counters = parseChannelCounters(json.optJSONObject("counters")),
    photoUrl = absoluteMediaUrl(json.optStringOrNull("photoUrl")),
    lastPost = parseChannelLastPost(json.optJSONObject("lastPost")),
)

fun parsePostMedia(json: JSONObject): PostMedia = PostMedia(
    type = json.getString("type"),
    url = absoluteMediaUrl(json.getString("url"))!!,
    left = json.optDouble("left"),
    top = json.optDouble("top"),
    width = json.optDouble("width"),
    height = json.optDouble("height"),
)

fun parseMediaGroup(json: JSONObject?): PostMediaGroup? {
    if (json == null) return null
    val itemsJson = json.getJSONArray("items")
    val items = buildList {
        for (i in 0 until itemsJson.length()) {
            add(parsePostMedia(itemsJson.getJSONObject(i)))
        }
    }
    return PostMediaGroup(
        width = json.optDouble("width"),
        height = json.optDouble("height"),
        items = items,
    )
}

fun parseReply(json: JSONObject?): PostReply? {
    if (json == null) return null
    return PostReply(
        link = json.getString("link"),
        author = json.optStringOrNull("author"),
        text = json.optStringOrNull("text"),
        html = absolutizeHtmlMediaUrls(json.optStringOrNull("html")),
        thumbUrl = absoluteMediaUrl(json.optStringOrNull("thumbUrl")),
    )
}

fun parseDocument(json: JSONObject?): PostDocument? {
    if (json == null) return null
    return PostDocument(
        title = json.getString("title"),
        extra = json.optStringOrNull("extra"),
        url = json.getString("url"),
    )
}

fun parseLocation(json: JSONObject?): PostLocation? {
    if (json == null) return null
    return PostLocation(
        url = json.getString("url"),
        mapUrl = json.optStringOrNull("mapUrl"),
    )
}

fun parseVideoAttrs(json: JSONObject?): PostMediaVideoAttrs? {
    if (json == null) return null
    return PostMediaVideoAttrs(
        muted = json.optBoolean("muted"),
        autoplay = json.optBoolean("autoplay"),
        loop = json.optBoolean("loop"),
    )
}

fun parsePost(json: JSONObject): Post = Post(
    id = json.getLong("id"),
    channelUsername = json.getString("channelUsername"),
    text = json.optStringOrNull("text"),
    html = absolutizeHtmlMediaUrls(json.optStringOrNull("html")),
    date = json.getString("date"),
    views = json.optStringOrNull("views"),
    mediaUrl = absoluteMediaUrl(json.optStringOrNull("mediaUrl")),
    mediaType = json.optStringOrNull("mediaType"),
    mediaUnavailableReason = json.optStringOrNull("mediaUnavailableReason"),
    mediaVideoAttrs = parseVideoAttrs(json.optJSONObject("mediaVideoAttrs")),
    mediaGroup = parseMediaGroup(json.optJSONObject("mediaGroup")),
    document = parseDocument(json.optJSONObject("document")),
    location = parseLocation(json.optJSONObject("location")),
    forwardedFrom = json.optStringOrNull("forwardedFrom"),
    replyTo = parseReply(json.optJSONObject("replyTo")),
)

fun parsePostsPage(json: JSONObject): PostsPage {
    val postsJson = json.getJSONArray("posts")
    val posts = buildList {
        for (i in 0 until postsJson.length()) {
            add(parsePost(postsJson.getJSONObject(i)))
        }
    }
    return PostsPage(
        posts = posts,
        channel = json.optJSONObject("channel")?.let { parseChannel(it) },
        hasMore = json.optBoolean("hasMore"),
        nextBefore = if (json.has("nextBefore") && !json.isNull("nextBefore")) {
            json.optLong("nextBefore")
        } else {
            null
        },
    )
}

fun parseSubscriptions(array: JSONArray): List<SubscriptionItem> = buildList {
    for (i in 0 until array.length()) {
        val item = array.getJSONObject(i)
        add(
            SubscriptionItem(
                channel = parseChannel(item.getJSONObject("channel")),
                subscribedAt = item.getString("subscribedAt"),
            ),
        )
    }
}

fun decodeSubscriptions(json: String): List<SubscriptionItem>? = runCatching { parseSubscriptions(JSONArray(json)) }.getOrNull()

private fun channelToJson(channel: Channel): JSONObject = JSONObject().apply {
    put("username", channel.username)
    put("title", channel.title)
    channel.description?.let { put("description", it) }
    channel.subscriberCount?.let { put("subscriberCount", it) }
    channel.photoUrl?.let { put("photoUrl", it) }
    channel.counters?.let { counters ->
        put(
            "counters",
            JSONObject().apply {
                counters.photos?.let { put("photos", it) }
                counters.videos?.let { put("videos", it) }
                counters.files?.let { put("files", it) }
                counters.links?.let { put("links", it) }
            },
        )
    }
    channel.lastPost?.let { lastPost ->
        put(
            "lastPost",
            JSONObject().apply {
                lastPost.text?.let { put("text", it) }
                put("date", lastPost.date)
                lastPost.mediaType?.let { put("mediaType", it) }
                lastPost.mediaCount?.let { put("mediaCount", it) }
            },
        )
    }
}

fun encodeSubscriptions(subscriptions: List<SubscriptionItem>): String {
    val array = JSONArray()
    for (item in subscriptions) {
        array.put(
            JSONObject().apply {
                put("channel", channelToJson(item.channel))
                put("subscribedAt", item.subscribedAt)
            },
        )
    }
    return array.toString()
}

fun parseApiError(body: String, status: Int): ApiException = try {
    val json = JSONObject(body)
    val code = json.optString("code").takeIf { it.isNotBlank() }
    ApiException(
        message = when (val message = json.opt("message")) {
            is JSONArray -> buildString {
                for (i in 0 until message.length()) {
                    if (i > 0) append(", ")
                    append(message.optString(i))
                }
            }.ifEmpty { body.ifEmpty { "HTTP $status" } }

            is String -> message.ifEmpty { body.ifEmpty { "HTTP $status" } }

            else -> body.ifEmpty { "HTTP $status" }
        },
        httpStatus = status,
        code = code,
    )
} catch (_: Exception) {
    ApiException(body.ifEmpty { "HTTP $status" }, status)
}
