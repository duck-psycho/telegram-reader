package com.duckpsycho.telegramreader.data

import android.content.Context
import com.duckpsycho.telegramreader.R

fun Context.resolveApiError(error: Throwable): String {
    if (error is ApiException) {
        resolveErrorCode(error.code)?.let { return getString(it) }
        if (error.httpStatus == -1) return getString(R.string.common_network_error)
        return error.message
    }
    return error.message?.takeIf { it.isNotBlank() }
        ?: getString(R.string.common_network_error)
}

private fun resolveErrorCode(code: String?): Int? = when (code) {
    "account_load_failed" -> R.string.error_account_load_failed
    "bad_request" -> R.string.error_bad_request
    "enter_account_id" -> R.string.error_enter_account_id
    "account_not_found" -> R.string.error_account_not_found
    "account_not_found_refresh" -> R.string.error_account_not_found_refresh
    "account_create_failed" -> R.string.error_account_create_failed
    "rate_limited" -> R.string.error_rate_limited
    "service_unavailable" -> R.string.error_service_unavailable
    "network_error" -> R.string.common_network_error
    else -> null
}
