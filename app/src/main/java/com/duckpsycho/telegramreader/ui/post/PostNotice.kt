package com.duckpsycho.telegramreader.ui.post

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme

@Composable
internal fun PostDashedNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = ReaderTheme.colors
    Text(
        text = text,
        color = colors.textMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(width = 1.dp, color = colors.border, cornerRadius = colors.radius)
            .padding(12.dp),
    )
}

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
): Modifier = drawBehind {
    val strokeWidth = width.toPx()
    val inset = strokeWidth / 2f
    val corner = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(strokeWidth * 4, strokeWidth * 3),
                0f,
            ),
        ),
    )
}
