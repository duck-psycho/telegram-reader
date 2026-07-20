package com.duckpsycho.telegramreader.data

const val BASE_URL = "https://reader.duckpsycho.dev"

data class Account(
    val id: String,
    val createdAt: String,
)

data class ChannelCounters(
    val photos: String? = null,
    val videos: String? = null,
    val files: String? = null,
    val links: String? = null,
)

data class ChannelLastPost(
    val text: String? = null,
    val date: String,
    val mediaType: String? = null,
    val mediaCount: Int? = null,
)

data class Channel(
    val username: String,
    val title: String,
    val description: String? = null,
    val subscriberCount: String? = null,
    val counters: ChannelCounters? = null,
    val photoUrl: String? = null,
    val lastPost: ChannelLastPost? = null,
)

fun mergeChannel(base: Channel?, update: Channel?): Channel? {
    if (update == null) return base
    if (base == null) return update
    return update.copy(
        description = update.description ?: base.description,
        subscriberCount = update.subscriberCount ?: base.subscriberCount,
        counters = update.counters ?: base.counters,
        photoUrl = update.photoUrl ?: base.photoUrl,
        lastPost = update.lastPost ?: base.lastPost,
    )
}

data class PostMedia(
    val type: String,
    val url: String,
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)

data class PostMediaGroup(
    val width: Double,
    val height: Double,
    val items: List<PostMedia>,
)

data class PostReply(
    val link: String,
    val author: String? = null,
    val text: String? = null,
    val html: String? = null,
    val thumbUrl: String? = null,
)

data class PostDocument(
    val title: String,
    val extra: String? = null,
    val url: String,
)

data class PostLocation(
    val url: String,
    val mapUrl: String? = null,
)

data class PostMediaVideoAttrs(
    val muted: Boolean = false,
    val autoplay: Boolean = false,
    val loop: Boolean = false,
)

data class Post(
    val id: Long,
    val channelUsername: String,
    val text: String? = null,
    val html: String? = null,
    val date: String,
    val views: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val mediaUnavailableReason: String? = null,
    val mediaVideoAttrs: PostMediaVideoAttrs? = null,
    val mediaGroup: PostMediaGroup? = null,
    val document: PostDocument? = null,
    val location: PostLocation? = null,
    val forwardedFrom: String? = null,
    val replyTo: PostReply? = null,
)

fun Post.readerShareUrl(): String = "$BASE_URL/channel/$channelUsername/$id"

data class PostsPage(
    val posts: List<Post>,
    val channel: Channel? = null,
    val hasMore: Boolean,
    val nextBefore: Long? = null,
)

data class SubscriptionItem(
    val channel: Channel,
    val subscribedAt: String,
)

class ApiException(
    override val message: String,
    val httpStatus: Int,
    val code: String? = null,
) : Exception(message)
