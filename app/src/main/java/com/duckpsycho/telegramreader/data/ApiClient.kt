package com.duckpsycho.telegramreader.data

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ApiClient(
    private val client: OkHttpClient,
    private val prefs: UserPreferences,
    private val baseUrl: String = BASE_URL,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun getAccount(): Account? {
        val body = request("GET", "/api/account")
        val json = JSONObject(body)
        val account = json.optJSONObject("account") ?: return null
        return parseAccount(account)
    }

    fun login(accountId: String) {
        val payload = JSONObject().put("id", accountId).toString()
        request("POST", "/api/account", payload)
    }

    fun logout() {
        request("DELETE", "/api/account")
    }

    fun getSubscriptions(): List<SubscriptionItem> {
        val body = request("GET", "/api/subscriptions")
        val subscriptions = parseSubscriptions(JSONArray(body))
        prefs.saveCachedSubscriptions(subscriptions)
        return subscriptions
    }

    fun subscribe(channelUsername: String): Channel? {
        val payload = JSONObject().put("channelUsername", channelUsername).toString()
        val body = request("POST", "/api/subscriptions", payload)
        if (body.isBlank()) return null
        return runCatching { parseChannel(JSONObject(body)) }.getOrNull()
    }

    fun unsubscribe(username: String) {
        request("DELETE", "/api/subscriptions/${encode(username)}")
    }

    fun getPosts(username: String, before: Long? = null): PostsPage {
        val path = buildString {
            append("/api/channels/")
            append(encode(username))
            append("/posts")
            if (before != null) {
                append("?before=")
                append(before)
            }
        }
        val body = request("GET", path)
        return parsePostsPage(JSONObject(body))
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun request(method: String, path: String, jsonBody: String? = null): String {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
            .header("Accept-Language", prefs.locale.tag)

        when (method) {
            "GET" -> builder.get()

            "DELETE" -> builder.delete()

            else -> {
                builder.method(
                    method,
                    (jsonBody ?: "{}").toRequestBody(jsonMedia),
                )
                builder.header("Content-Type", "application/json")
            }
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw parseApiError(body, response.code)
                }
                if (response.code == 204) return ""
                return body
            }
        } catch (e: ApiException) {
            throw e
        } catch (_: IOException) {
            throw ApiException("Network error", -1, code = "network_error")
        }
    }
}
