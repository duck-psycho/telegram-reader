package com.duckpsycho.telegramreader.data

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object HttpClientFactory {
    fun create(cookieJar: PersistentCookieJar): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
