package com.duckpsycho.telegramreader.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import com.duckpsycho.telegramreader.util.HtmlNode
import com.duckpsycho.telegramreader.util.HtmlParser

internal data class TgEmojiEntry(
    val src: String?,
    val alt: String,
)

internal val BlockquoteTrailingIconReserve = 34.dp

internal fun isTgEmoji(node: HtmlNode.Element): Boolean {
    val classes = node.attributes["class"].orEmpty().split(Regex("\\s+"))
    return "tg-emoji" in classes
}

internal data class RichHtmlStyle(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val emojiSize: TextUnit,
) {
    companion object {
        val Default = RichHtmlStyle(fontSize = 15.sp, lineHeight = 22.sp, emojiSize = 18.sp)
        val Reply = RichHtmlStyle(fontSize = 13.sp, lineHeight = 18.sp, emojiSize = 16.sp)
        val SingleEmoji = RichHtmlStyle(fontSize = 64.sp, lineHeight = 64.sp, emojiSize = 64.sp)
    }
}

internal sealed interface HtmlBlock {
    data class InlineRun(val nodes: List<HtmlNode>) : HtmlBlock
    data class Paragraph(val nodes: List<HtmlNode>) : HtmlBlock
    data class Quote(val node: HtmlNode.Element) : HtmlBlock
    data class Preformatted(val node: HtmlNode.Element) : HtmlBlock
    data class ListBlock(val node: HtmlNode.Element, val ordered: Boolean) : HtmlBlock
    data class Video(val src: String) : HtmlBlock
}

@Composable
internal fun RichHtml(
    html: String,
    modifier: Modifier = Modifier,
    style: RichHtmlStyle = RichHtmlStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    color: Color? = null,
) {
    val nodes = remember(html) { HtmlParser.parse(html) }
    val revealedSpoilers = remember(html) { mutableStateMapOf<String, Boolean>() }

    if (maxLines != Int.MAX_VALUE) {
        InlineHtmlText(
            nodes = nodes,
            style = style,
            revealedSpoilers = revealedSpoilers,
            onToggleSpoiler = { key -> revealedSpoilers[key] = !(revealedSpoilers[key] ?: false) },
            keyPrefix = "compact",
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
            color = color,
        )
        return
    }

    val blocks = remember(nodes) { groupHtmlBlocks(nodes) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            RenderHtmlBlock(
                block = block,
                style = style,
                revealedSpoilers = revealedSpoilers,
                onToggleSpoiler = { key -> revealedSpoilers[key] = !(revealedSpoilers[key] ?: false) },
                keyPrefix = "block-$index",
                color = color,
            )
        }
    }
}

@Composable
private fun RenderHtmlBlock(
    block: HtmlBlock,
    style: RichHtmlStyle,
    revealedSpoilers: MutableMap<String, Boolean>,
    onToggleSpoiler: (String) -> Unit,
    keyPrefix: String,
    color: Color? = null,
) {
    when (block) {
        is HtmlBlock.InlineRun -> InlineHtmlText(
            nodes = block.nodes,
            style = style,
            revealedSpoilers = revealedSpoilers,
            onToggleSpoiler = onToggleSpoiler,
            keyPrefix = keyPrefix,
            color = color,
        )

        is HtmlBlock.Paragraph -> {
            InlineHtmlText(
                nodes = block.nodes,
                style = style,
                revealedSpoilers = revealedSpoilers,
                onToggleSpoiler = onToggleSpoiler,
                keyPrefix = keyPrefix,
                color = color,
            )
            Spacer(Modifier.height(4.dp))
        }

        is HtmlBlock.Quote -> {
            HtmlBlockquote(
                node = block.node,
                style = style,
                revealedSpoilers = revealedSpoilers,
                onToggleSpoiler = onToggleSpoiler,
                keyPrefix = keyPrefix,
            )
            Spacer(Modifier.height(4.dp))
        }

        is HtmlBlock.Preformatted -> {
            HtmlPreBlock(node = block.node, style = style)
            Spacer(Modifier.height(4.dp))
        }

        is HtmlBlock.ListBlock -> {
            HtmlListBlock(
                node = block.node,
                ordered = block.ordered,
                style = style,
                revealedSpoilers = revealedSpoilers,
                onToggleSpoiler = onToggleSpoiler,
                keyPrefix = keyPrefix,
            )
            Spacer(Modifier.height(4.dp))
        }

        is HtmlBlock.Video -> {
            VideoPreview(url = block.src, onOpen = {})
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun groupHtmlBlocks(nodes: List<HtmlNode>): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    val inlineBuffer = mutableListOf<HtmlNode>()

    fun flushInline() {
        if (inlineBuffer.isEmpty()) return
        blocks.add(HtmlBlock.InlineRun(inlineBuffer.toList()))
        inlineBuffer.clear()
    }

    for (node in nodes) {
        when (node) {
            is HtmlNode.Text -> if (node.value.isNotEmpty()) inlineBuffer.add(node)

            is HtmlNode.Element -> when (node.tag) {
                "p" -> {
                    flushInline()
                    blocks.add(HtmlBlock.Paragraph(node.children))
                }

                "blockquote" -> {
                    flushInline()
                    blocks.add(HtmlBlock.Quote(node))
                }

                "pre" -> {
                    flushInline()
                    blocks.add(HtmlBlock.Preformatted(node))
                }

                "ul" -> {
                    flushInline()
                    blocks.add(HtmlBlock.ListBlock(node, ordered = false))
                }

                "ol" -> {
                    flushInline()
                    blocks.add(HtmlBlock.ListBlock(node, ordered = true))
                }

                "video" -> {
                    flushInline()
                    node.attributes["src"]?.let { blocks.add(HtmlBlock.Video(it)) }
                }

                "br" -> inlineBuffer.add(node)

                else -> inlineBuffer.add(node)
            }
        }
    }
    flushInline()
    return blocks
}

@Composable
private fun HtmlBlockquote(
    node: HtmlNode.Element,
    style: RichHtmlStyle,
    revealedSpoilers: MutableMap<String, Boolean>,
    onToggleSpoiler: (String) -> Unit,
    keyPrefix: String,
) {
    val colors = ReaderTheme.colors
    val expandable = node.attributes.containsKey("expandable")
    var expanded by remember(keyPrefix) { mutableStateOf(false) }
    val quoteStyle = style.copy(lineHeight = style.fontSize)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.blockquoteBg)
            .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(colors.blockquoteAccent),
        )
        InlineHtmlText(
            nodes = node.children,
            style = quoteStyle,
            revealedSpoilers = revealedSpoilers,
            onToggleSpoiler = onToggleSpoiler,
            keyPrefix = keyPrefix,
            maxLines = if (expandable && !expanded) 3 else Int.MAX_VALUE,
            overflow = if (expandable && !expanded) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = BlockquoteTrailingIconReserve,
                    top = 2.dp,
                    bottom = 2.dp,
                ),
        )
        if (expandable) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.blockquoteAccent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 4.dp)
                    .size(16.dp)
                    .alpha(0.9f)
                    .rotate(if (expanded) 180f else 0f),
            )
        } else {
            Icon(
                imageVector = Icons.Default.FormatQuote,
                contentDescription = null,
                tint = colors.blockquoteAccent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 6.dp)
                    .size(12.dp),
            )
        }
    }
}

@Composable
private fun HtmlPreBlock(node: HtmlNode.Element, style: RichHtmlStyle) {
    val colors = ReaderTheme.colors
    val text = remember(node) { collectPlainText(node.children) }
    Text(
        text = text,
        color = colors.text,
        fontSize = style.fontSize,
        lineHeight = style.lineHeight,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.active)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun HtmlListBlock(
    node: HtmlNode.Element,
    ordered: Boolean,
    style: RichHtmlStyle,
    revealedSpoilers: MutableMap<String, Boolean>,
    onToggleSpoiler: (String) -> Unit,
    keyPrefix: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        node.children.forEachIndexed { index, child ->
            if (child is HtmlNode.Element && child.tag == "li") {
                HtmlListItem(
                    node = child,
                    ordered = ordered,
                    index = index,
                    style = style,
                    revealedSpoilers = revealedSpoilers,
                    onToggleSpoiler = onToggleSpoiler,
                    keyPrefix = "$keyPrefix-li-$index",
                )
            }
        }
    }
}

@Composable
private fun HtmlListItem(
    node: HtmlNode.Element,
    ordered: Boolean,
    index: Int,
    style: RichHtmlStyle,
    revealedSpoilers: MutableMap<String, Boolean>,
    onToggleSpoiler: (String) -> Unit,
    keyPrefix: String,
) {
    val colors = ReaderTheme.colors
    val itemBlocks = remember(node) { groupHtmlBlocks(node.children) }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (ordered) "${index + 1}." else "•",
            color = colors.text,
            fontSize = style.fontSize,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            itemBlocks.forEachIndexed { blockIndex, block ->
                RenderHtmlBlock(
                    block = block,
                    style = style,
                    revealedSpoilers = revealedSpoilers,
                    onToggleSpoiler = onToggleSpoiler,
                    keyPrefix = "$keyPrefix-$blockIndex",
                )
            }
        }
    }
}

internal fun collectPlainText(nodes: List<HtmlNode>): String = buildString {
    nodes.forEach { node ->
        when (node) {
            is HtmlNode.Text -> append(node.value)

            is HtmlNode.Element -> when (node.tag) {
                "br" -> append('\n')
                else -> append(collectPlainText(node.children))
            }
        }
    }
}
