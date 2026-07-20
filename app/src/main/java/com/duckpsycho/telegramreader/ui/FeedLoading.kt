package com.duckpsycho.telegramreader.ui

import com.duckpsycho.telegramreader.data.ApiClient
import com.duckpsycho.telegramreader.data.Channel
import com.duckpsycho.telegramreader.data.Post
import com.duckpsycho.telegramreader.data.mergeChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class JumpLoadResult(
    val posts: List<Post>,
    val hasMore: Boolean,
    val nextBefore: Long?,
    val channel: Channel?,
)

internal fun mergePosts(existing: List<Post>, incoming: List<Post>): List<Post> {
    val map = LinkedHashMap<Long, Post>()
    for (post in existing) map[post.id] = post
    for (post in incoming) map[post.id] = post
    return map.values.sortedBy { it.id }
}

/**
 * Loads pages toward [targetId] via `before=targetId+1` (API returns id < before).
 */
internal suspend fun loadPostsAround(
    api: ApiClient,
    username: String,
    targetId: Long,
    seedPosts: List<Post>,
    seedHasMore: Boolean,
    seedNextBefore: Long?,
    maxAttempts: Int = JUMP_ATTEMPTS,
): JumpLoadResult {
    var posts = seedPosts
    var hasMore = seedHasMore
    var nextBefore = seedNextBefore
    var channel: Channel? = null
    var before: Long? = targetId + 1
    var attempts = 0

    while (
        before != null &&
        posts.none { it.id == targetId } &&
        attempts < maxAttempts
    ) {
        attempts++
        val page = withContext(Dispatchers.IO) { api.getPosts(username, before) }
        if (page.posts.isEmpty()) break

        posts = mergePosts(posts, page.posts)
        nextBefore = page.nextBefore
        hasMore = page.hasMore
        channel = mergeChannel(channel, page.channel)

        if (posts.any { it.id == targetId }) break

        val newestLoaded = page.posts.maxOf { it.id }
        if (newestLoaded < targetId) break

        before = nextBefore
        if (!hasMore) break
    }

    return JumpLoadResult(posts, hasMore, nextBefore, channel)
}

private const val JUMP_ATTEMPTS = 5
