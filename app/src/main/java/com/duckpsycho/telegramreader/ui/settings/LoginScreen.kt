package com.duckpsycho.telegramreader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.ui.components.ErrorText
import com.duckpsycho.telegramreader.ui.components.PrimaryButton
import com.duckpsycho.telegramreader.ui.components.ReaderTextField
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme

@Composable
fun LoginScreen(
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReaderTheme.colors
    var accountId by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        OverlayHeader(title = stringResource(R.string.login_title), onBack = onBack)
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.login_account),
                color = colors.textStrong,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.login_hint), color = colors.text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(colors.radius))
                    .background(colors.bgElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(colors.radius))
                    .padding(12.dp),
            ) {
                ReaderTextField(
                    value = accountId,
                    onValueChange = { accountId = it },
                    placeholder = stringResource(R.string.login_placeholder),
                )
            }
            if (error != null) {
                ErrorText(
                    when (error) {
                        "enter_id" -> stringResource(R.string.login_enter_id)
                        else -> error.ifBlank { stringResource(R.string.login_failed) }
                    },
                )
            }
            PrimaryButton(
                text = if (loading) {
                    stringResource(R.string.login_submitting)
                } else {
                    stringResource(R.string.login_submit)
                },
                onClick = { onSubmit(accountId) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
