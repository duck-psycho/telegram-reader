package com.duckpsycho.telegramreader.data

import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.videoFrameMicros

/**
 * Loads http(s) images through [MediaFileCache].
 * Skips video-frame requests: Coil needs the network MIME type for VideoFrameDecoder.
 */
class CachedMediaInterceptor(
    private val mediaCache: MediaFileCache,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data
        val isVideoFrame = request.parameters.videoFrameMicros() != null
        if (
            !isVideoFrame &&
            data is String &&
            (data.startsWith("http://") || data.startsWith("https://"))
        ) {
            val cached = mediaCache.getIfCached(data)
            val file = cached ?: mediaCache.getOrDownload(data)
            return chain.proceed(
                request.newBuilder()
                    .data(file)
                    .memoryCacheKey(data)
                    .diskCacheKey(data)
                    .build(),
            )
        }
        return chain.proceed(request)
    }
}
