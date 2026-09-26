package com.duckpsycho.telegramreader.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.TelegramReaderApp
import com.duckpsycho.telegramreader.data.ProxySettings
import com.duckpsycho.telegramreader.data.ProxyType
import com.duckpsycho.telegramreader.ui.components.PrimaryButton
import com.duckpsycho.telegramreader.ui.components.ReaderTextField
import com.duckpsycho.telegramreader.ui.components.SecondaryButton
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProxySection(
    proxy: ProxySettings?,
    onSave: (ProxySettings) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ReaderTheme.colors
    val routing = (LocalContext.current.applicationContext as TelegramReaderApp).proxyRouting
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var type by remember(proxy, editing) { mutableStateOf(proxy?.type ?: ProxyType.SOCKS5) }
    var host by remember(proxy, editing) { mutableStateOf(proxy?.host.orEmpty()) }
    var port by remember(proxy, editing) { mutableStateOf(proxy?.port?.toString().orEmpty()) }
    var username by remember(proxy, editing) { mutableStateOf(proxy?.username.orEmpty()) }
    var password by remember(proxy, editing) { mutableStateOf(proxy?.password.orEmpty()) }
    var checking by remember(editing) { mutableStateOf(false) }
    var status by remember(editing) { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<ProxySettings?>(null) }
    val invalid = stringResource(R.string.proxy_invalid)
    val success = stringResource(R.string.proxy_test_success)
    val failure = stringResource(R.string.proxy_test_failure)

    fun draft() = ProxySettings(type, host.trim(), port.toIntOrNull() ?: 0, username, password, proxy?.enabled ?: false)

    LaunchedEffect(proxy, pendingSave) {
        if (pendingSave != null && proxy == pendingSave) {
            pendingSave = null
            editing = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!editing) {
            if (proxy != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(proxy.type.name, color = colors.textStrong)
                        Text(
                            stringResource(if (proxy.enabled) R.string.proxy_enabled else R.string.proxy_disabled),
                            color = colors.textMuted,
                        )
                    }
                    Switch(checked = proxy.enabled, onCheckedChange = { onSave(proxy.copy(enabled = it)) })
                }
            }
            SecondaryButton(
                text = stringResource(if (proxy == null) R.string.proxy_add else R.string.proxy_edit),
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        Text(stringResource(R.string.proxy_type), color = colors.text)
        ProxyType.entries.forEach { option ->
            ChoiceRow(option.name, type == option) {
                type = option
                status = null
                pendingSave = null
            }
        }
        ProxyField(stringResource(R.string.proxy_host), host, {
            host = it
            status = null
            pendingSave = null
        })
        ProxyField(
            stringResource(R.string.proxy_port),
            port,
            {
                port = it
                status = null
                pendingSave = null
            },
            keyboardType = KeyboardType.Number,
        )
        ProxyField(stringResource(R.string.proxy_username), username, {
            username = it
            status = null
            pendingSave = null
        })
        ProxyField(
            stringResource(R.string.proxy_password),
            password,
            {
                password = it
                status = null
                pendingSave = null
            },
            password = true,
        )
        status?.let { Text(it, color = colors.text) }
        SecondaryButton(
            text = if (checking) stringResource(R.string.proxy_testing) else stringResource(R.string.proxy_test),
            onClick = {
                val candidate = draft()
                if (!candidate.validate()) {
                    status = invalid
                } else if (!checking) {
                    checking = true
                    status = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { routing.test(candidate) }
                        status = if (result.isSuccess) success else "$failure: ${result.exceptionOrNull()?.localizedMessage.orEmpty()}"
                        checking = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = stringResource(R.string.proxy_save),
            onClick = {
                val candidate = draft()
                if (candidate.validate()) {
                    if (candidate == proxy) {
                        editing = false
                    } else {
                        pendingSave = candidate
                        onSave(candidate)
                    }
                } else {
                    status = invalid
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.proxy_cancel),
            onClick = {
                pendingSave = null
                editing = false
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (proxy != null) {
            SecondaryButton(
                text = stringResource(R.string.proxy_delete),
                onClick = {
                    pendingSave = null
                    onDelete()
                    editing = false
                },
                modifier = Modifier.fillMaxWidth(),
                textColor = colors.error,
            )
        }
    }
}

@Composable
private fun ProxyField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    val colors = ReaderTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.text)
        ReaderTextField(
            value = value,
            onValueChange = onChange,
            placeholder = label,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, RoundedCornerShape(colors.radius))
                .padding(12.dp),
        )
    }
}
