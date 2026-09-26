package com.duckpsycho.telegramreader.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

enum class ProxyType { SOCKS5, HTTP }

data class ProxySettings(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = false,
) {
    fun validate(): Boolean = host.isNotBlank() && !host.contains(Regex("[\\s/:@]")) &&
        port in 1..65535 && (username.isNotEmpty() || password.isEmpty())
}

class ProxySettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("telegram_reader_proxy", Context.MODE_PRIVATE)

    fun load(): ProxySettings? = try {
        val encoded = prefs.getString("config", null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        ProxySettings(
            type = ProxyType.valueOf(json.getString("type")),
            host = json.getString("host"),
            port = json.getInt("port"),
            username = json.optString("username"),
            password = json.optString("password"),
            enabled = json.optBoolean("enabled"),
        ).takeIf { it.validate() }
    } catch (_: Exception) {
        null
    }

    fun save(settings: ProxySettings) {
        require(settings.validate())
        val json = JSONObject()
            .put("type", settings.type.name)
            .put("host", settings.host.trim())
            .put("port", settings.port)
            .put("username", settings.username)
            .put("password", settings.password)
            .put("enabled", settings.enabled)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("config", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "telegram_reader_proxy_key"
    }
}
