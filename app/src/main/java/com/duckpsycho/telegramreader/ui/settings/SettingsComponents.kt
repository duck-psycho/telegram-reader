package com.duckpsycho.telegramreader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme

internal data class DevLink(val label: String, val value: String, val href: String)

internal data class Donation(val label: String, val value: String, val href: String? = null)

@Composable
internal fun OverlayHeader(title: String, onBack: () -> Unit) {
    val colors = ReaderTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(24.dp),
                tint = colors.textStrong,
            )
        }
        Text(
            text = title,
            color = colors.textStrong,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = ReaderTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = colors.textStrong, fontWeight = FontWeight.SemiBold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(colors.radiusLg))
                .background(colors.bgElevated)
                .border(1.dp, colors.border, RoundedCornerShape(colors.radiusLg))
                .padding(if (compact) PaddingValues(0.dp) else PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ReaderTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.active else colors.bgElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textStrong,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.textStrong,
                )
            }
        }
    }
}

@Composable
internal fun LinkRow(
    label: String,
    value: String,
    onOpen: (() -> Unit)?,
    onCopy: (() -> Unit)? = null,
) {
    val colors = ReaderTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, color = colors.textMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                color = colors.textStrong,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
            )
            if (onCopy != null) {
                Text(
                    text = stringResource(R.string.settings_copy),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(onClick = onCopy)
                        .padding(8.dp),
                )
            }
        }
    }
}
