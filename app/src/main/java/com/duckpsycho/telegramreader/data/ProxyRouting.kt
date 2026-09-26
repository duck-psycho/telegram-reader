package com.duckpsycho.telegramreader.data

import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator as OkHttpAuthenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

class ProxyRouting(initial: ProxySettings?) {
    @Volatile private var current: ProxySettings? = initial
    private val testSettings = ThreadLocal<ProxySettings?>()

    init {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val settings = testSettings.get() ?: current?.takeIf { it.enabled } ?: return null
                if (settings.type != ProxyType.SOCKS5 || settings.username.isEmpty()) return null
                if (requestingProtocol != "SOCKS5" || requestingPort != settings.port ||
                    !requestingHost.equals(settings.host, ignoreCase = true)
                ) {
                    return null
                }
                return PasswordAuthentication(settings.username, settings.password.toCharArray())
            }
        })
    }

    fun update(settings: ProxySettings?, client: OkHttpClient) {
        current = settings
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    fun selector(settings: () -> ProxySettings?): ProxySelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            val config = settings()?.takeIf { it.enabled && uri.host.equals(READER_HOST, true) }
                ?: return listOf(Proxy.NO_PROXY)
            val type = if (config.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP
            return listOf(Proxy(type, InetSocketAddress.createUnresolved(config.host, config.port)))
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) = Unit
    }

    fun proxyAuthenticator(settings: () -> ProxySettings?): OkHttpAuthenticator = OkHttpAuthenticator { route, response ->
        val config = settings()?.takeIf { it.enabled && it.type == ProxyType.HTTP && it.username.isNotEmpty() }
            ?: return@OkHttpAuthenticator null
        val proxyAddress = route?.proxy?.address() as? InetSocketAddress
        if (route?.proxy?.type() != Proxy.Type.HTTP || proxyAddress?.port != config.port ||
            !proxyAddress.hostString.equals(config.host, true) ||
            response.request.url.host != READER_HOST || response.request.header("Proxy-Authorization") != null
        ) {
            return@OkHttpAuthenticator null
        }
        response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
            .build()
    }

    fun active() = current

    fun test(settings: ProxySettings): Result<Int> = runCatching {
        val trial = settings.copy(enabled = true)
        val client = OkHttpClient.Builder()
            .proxySelector(selector { trial })
            .proxyAuthenticator(proxyAuthenticator { trial })
            .followRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        testSettings.set(trial)
        try {
            client.newCall(Request.Builder().url("$BASE_URL/").get().build()).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                response.code
            }
        } finally {
            testSettings.remove()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    companion object {
        private const val READER_HOST = "reader.duckpsycho.dev"
    }
}
