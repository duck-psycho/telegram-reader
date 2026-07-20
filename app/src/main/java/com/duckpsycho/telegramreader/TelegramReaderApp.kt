package com.duckpsycho.telegramreader

import android.app.Application
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.duckpsycho.telegramreader.data.ApiClient
import com.duckpsycho.telegramreader.data.CachedMediaInterceptor
import com.duckpsycho.telegramreader.data.HttpClientFactory
import com.duckpsycho.telegramreader.data.MediaFileCache
import com.duckpsycho.telegramreader.data.PersistentCookieJar
import com.duckpsycho.telegramreader.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class TelegramReaderApp : Application() {
    lateinit var cookieJar: PersistentCookieJar
        private set
    lateinit var httpClient: OkHttpClient
        private set
    lateinit var api: ApiClient
        private set
    lateinit var prefs: UserPreferences
        private set
    lateinit var mediaCache: MediaFileCache
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        cookieJar = PersistentCookieJar(this)
        prefs = UserPreferences(this)
        httpClient = HttpClientFactory.create(cookieJar)
        api = ApiClient(httpClient, prefs)
        mediaCache = MediaFileCache(this, httpClient, cookieJar)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                    add(VideoFrameDecoder.Factory())
                    add(CachedMediaInterceptor(mediaCache))
                }
                .build(),
        )
        appScope.launch(Dispatchers.IO) {
            mediaCache.purgeExpired()
        }
    }
}
