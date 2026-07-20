package com.duckpsycho.telegramreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme

fun readerInputTextStyle(
    color: Color,
    fontSize: TextUnit = 16.sp,
): TextStyle = TextStyle(
    color = color,
    fontSize = fontSize,
    lineHeight = fontSize,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
fun ReaderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    fontSize: TextUnit = 16.sp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = ReaderTheme.colors
    val textStyle = readerInputTextStyle(colors.textStrong, fontSize)
    val placeholderStyle = readerInputTextStyle(colors.textMuted, fontSize)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.textStrong),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = placeholderStyle)
                }
                inner()
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ChannelAvatar(
    title: String,
    photoUrl: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val colors = ReaderTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.active)
            .border(1.dp, colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = title,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = title.take(1).uppercase(),
                color = colors.textStrong,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}
