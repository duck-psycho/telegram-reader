package com.duckpsycho.telegramreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ReaderTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(colors.radius))
            .background(if (enabled) colors.fabBg else colors.active)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) colors.fabText else colors.textMuted,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
) {
    val colors = ReaderTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(colors.radius))
            .border(1.dp, colors.border, RoundedCornerShape(colors.radius))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = textColor ?: colors.textStrong)
    }
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ReaderTheme.colors.error,
        modifier = modifier,
        fontSize = 14.sp,
    )
}

@Composable
fun ReaderFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = ReaderTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.fabBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
