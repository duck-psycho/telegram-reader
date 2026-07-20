package com.duckpsycho.telegramreader.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class CacheStats(
    val fileCount: Int,
    val usedBytes: Long,
    val maxBytes: Long = MediaFileCache.MAX_BYTES,
)

class MediaFileCache(
    context: Context,
    private val client: OkHttpClient,
    private val cookieJar: PersistentCookieJar,
) {
    private val cacheDir = File(context.cacheDir, "media_cache").also { it.mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<File>>()

    fun getIfCached(url: String): File? {
        val key = cacheKey(url)
        val file = findPayload(key)?.takeIf { !isExpired(it) } ?: return null
        // Extensionless files are unreliable for MediaPlayer.
        if (file.name == key) return null
        return file
    }

    /** Cookie header so MediaPlayer can auth the same hosts as OkHttp. */
    fun playbackHeaders(url: String): Map<String, String> {
        val httpUrl = url.toHttpUrlOrNull() ?: return emptyMap()
        val cookies = cookieJar.loadForRequest(httpUrl)
        if (cookies.isEmpty()) return emptyMap()
        return mapOf(
            "Cookie" to cookies.joinToString("; ") { "${it.name}=${it.value}" },
        )
    }

    suspend fun getOrDownload(url: String): File = withContext(Dispatchers.IO) {
        val key = cacheKey(url)
        getIfCached(url)?.let { return@withContext it }

        findPayload(key)?.takeIf { !isExpired(it) && it.name == key }?.let {
            deleteEntry(key)
        }

        val deferred = mutex.withLock {
            inFlight[key]?.let { return@withLock it }
            scope.async {
                try {
                    download(url, key)
                } finally {
                    mutex.withLock { inFlight.remove(key) }
                }
            }.also { inFlight[key] = it }
        }
        deferred.await()
    }

    fun purgeExpired() {
        val now = System.currentTimeMillis()
        listPayloadFiles().forEach { file ->
            if (now - file.lastModified() > TTL_MS) {
                deleteEntry(keyFromPayload(file.name))
            }
        }
        deleteOrphanMetaFiles()
        enforceSizeLimit()
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    fun stats(): CacheStats {
        val files = listPayloadFiles()
        return CacheStats(
            fileCount = files.size,
            usedBytes = files.sumOf { it.length() },
            maxBytes = MAX_BYTES,
        )
    }

    private fun deleteOrphanMetaFiles() {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(META_SUFFIX)) {
                val key = file.name.removeSuffix(META_SUFFIX)
                if (findPayload(key) == null) file.delete()
            }
        }
    }

    private fun download(url: String, key: String): File {
        deleteEntry(key)

        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw IOException("Empty body for $url")
            val ext = extensionFromContentType(response.header("Content-Type"))
                .ifEmpty { extensionFromUrl(url) }
            val target = File(cacheDir, key + ext)
            val temp = File(cacheDir, "$key.tmp")
            temp.outputStream().use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            metaFile(key).writeText(url)
            target.setLastModified(System.currentTimeMillis())
            enforceSizeLimit()
            return target
        }
    }

    private fun enforceSizeLimit() {
        val entries = listPayloadFiles().sortedBy { it.lastModified() }.toMutableList()
        var total = entries.sumOf { it.length() }
        while (total > MAX_BYTES && entries.isNotEmpty()) {
            val oldest = entries.removeAt(0)
            total -= oldest.length()
            deleteEntry(keyFromPayload(oldest.name))
        }
    }

    private fun deleteEntry(key: String) {
        findPayload(key)?.delete()
        metaFile(key).delete()
        File(cacheDir, "$key.tmp").delete()
    }

    private fun isExpired(file: File): Boolean = System.currentTimeMillis() - file.lastModified() > TTL_MS

    private fun listPayloadFiles(): List<File> = cacheDir.listFiles()?.filter { file ->
        file.isFile &&
            !file.name.endsWith(META_SUFFIX) &&
            !file.name.endsWith(".tmp")
    }.orEmpty()

    private fun findPayload(key: String): File? = listPayloadFiles().firstOrNull { file ->
        file.name == key || file.name.startsWith("$key.")
    }

    private fun metaFile(key: String): File = File(cacheDir, key + META_SUFFIX)

    companion object {
        const val MAX_BYTES: Long = 512L * 1024L * 1024L
        private const val TTL_MS: Long = 24L * 60L * 60L * 1000L
        private const val META_SUFFIX = ".meta"

        fun cacheKey(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun extensionFromUrl(url: String): String {
            val segment = Uri.parse(url).lastPathSegment ?: return ""
            val dot = segment.lastIndexOf('.')
            if (dot < 0 || dot == segment.lastIndex) return ""
            val ext = segment.substring(dot).lowercase()
            if (ext.length > 8) return ""
            if (!ext.all { it.isLetterOrDigit() || it == '.' }) return ""
            return ext
        }

        private fun extensionFromContentType(contentType: String?): String {
            if (contentType.isNullOrBlank()) return ""
            val mime = contentType.substringBefore(';').trim().lowercase()
            return when (mime) {
                "video/mp4", "video/avc" -> ".mp4"
                "video/webm" -> ".webm"
                "video/quicktime" -> ".mov"
                "image/gif" -> ".gif"
                "image/webp" -> ".webp"
                "image/jpeg", "image/jpg" -> ".jpg"
                "image/png" -> ".png"
                "audio/ogg", "video/ogg" -> ".ogg"
                else -> ""
            }
        }

        /** Payload file name is `<sha256>` or `<sha256>.<ext>`. */
        private fun keyFromPayload(fileName: String): String = if (fileName.length >= 64) fileName.substring(0, 64) else fileName
    }
}
