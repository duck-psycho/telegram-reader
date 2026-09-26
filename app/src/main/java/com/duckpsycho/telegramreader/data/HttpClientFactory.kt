package com.duckpsycho.telegramreader.data

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object HttpClientFactory {
    fun create(cookieJar: PersistentCookieJar, routing: ProxyRouting): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .proxySelector(routing.selector { routing.active() })
        .proxyAuthenticator(routing.proxyAuthenticator { routing.active() })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
