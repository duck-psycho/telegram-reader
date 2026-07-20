package com.duckpsycho.telegramreader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.ui.components.ErrorText
import com.duckpsycho.telegramreader.ui.components.PrimaryButton
import com.duckpsycho.telegramreader.ui.components.ReaderTextField
import com.duckpsycho.telegramreader.ui.components.SecondaryButton
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme

@Composable
fun AddChannelScreen(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val colors = ReaderTheme.colors
    var username by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlay)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(colors.radiusLg))
                .background(colors.bgElevated)
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.channel_add_title),
                color = colors.textStrong,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(colors.radius))
                    .border(1.dp, colors.border, RoundedCornerShape(colors.radius))
                    .padding(12.dp),
            ) {
                ReaderTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = stringResource(R.string.channel_add_placeholder),
                )
            }
            if (error != null) {
                ErrorText(
                    when (error) {
                        "invalid" -> stringResource(R.string.channel_invalid_input)
                        else -> error.ifBlank { stringResource(R.string.channel_subscribe_failed) }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SecondaryButton(stringResource(R.string.common_cancel), onClick = onDismiss)
                PrimaryButton(
                    text = if (loading) {
                        stringResource(R.string.channel_adding)
                    } else {
                        stringResource(R.string.channel_add)
                    },
                    onClick = { onSubmit(username) },
                    enabled = !loading,
                )
            }
        }
    }
}
