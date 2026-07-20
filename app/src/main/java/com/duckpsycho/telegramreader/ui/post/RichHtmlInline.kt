package com.duckpsycho.telegramreader.ui.post

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.duckpsycho.telegramreader.ui.components.openUrl
import com.duckpsycho.telegramreader.ui.theme.ReaderTheme
import com.duckpsycho.telegramreader.util.HtmlNode

@Composable
internal fun InlineHtmlText(
    nodes: List<HtmlNode>,
    style: RichHtmlStyle,
    revealedSpoilers: MutableMap<String, Boolean>,
    onToggleSpoiler: (String) -> Unit,
    keyPrefix: String,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val colors = ReaderTheme.colors
    val textColor = color ?: colors.text
    val context = LocalContext.current
    val density = LocalDensity.current
    val emojiSizeDp = with(density) { style.emojiSize.toDp() }
    val spoilerSnapshot = revealedSpoilers.toMap()
    val emojiEntries = remember(nodes, keyPrefix, spoilerSnapshot) {
        collectEmojiNodes(nodes, keyPrefix, revealedSpoilers)
    }
    val inlineContent = emojiEntries.associate { (id, entry) ->
        id to InlineTextContent(
            Placeholder(
                width = style.emojiSize,
                height = style.emojiSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            if (entry.src != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(entry.src)
                        .crossfade(false)
                        .memoryCacheKey(entry.src)
                        .diskCacheKey(entry.src)
                        .build(),
                    contentDescription = entry.alt,
                    modifier = Modifier.size(emojiSizeDp),
                )
            } else if (entry.alt.isNotEmpty()) {
                Text(
                    text = entry.alt,
                    fontSize = style.emojiSize,
                    lineHeight = style.emojiSize,
                )
            }
        }
    }
    val annotated = remember(nodes, spoilerSnapshot, textColor) {
        buildInlineAnnotatedString(
            nodes = nodes,
            linkColor = textColor,
            spoilerColor = colors.textMuted,
            revealedSpoilers = revealedSpoilers,
            onToggleSpoiler = onToggleSpoiler,
            keyPrefix = keyPrefix,
            context = context,
        )
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        color = textColor,
        style = TextStyle(
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun collectEmojiNodes(
    nodes: List<HtmlNode>,
    keyPrefix: String,
    revealedSpoilers: Map<String, Boolean>,
): List<Pair<String, TgEmojiEntry>> {
    val out = mutableListOf<Pair<String, TgEmojiEntry>>()
    collectEmojiNodesRecursive(nodes, keyPrefix, revealedSpoilers, out, SpoilerCounter(), hideMedia = false)
    return out
}

private fun collectEmojiNodesRecursive(
    nodes: List<HtmlNode>,
    keyPrefix: String,
    revealedSpoilers: Map<String, Boolean>,
    out: MutableList<Pair<String, TgEmojiEntry>>,
    spoilerCounter: SpoilerCounter,
    hideMedia: Boolean,
) {
    nodes.forEachIndexed { index, node ->
        when (node) {
            is HtmlNode.Text -> Unit

            is HtmlNode.Element -> when (node.tag) {
                "img" -> {
                    if (!hideMedia && isTgEmoji(node)) {
                        out.add(
                            "$keyPrefix-emoji-$index" to TgEmojiEntry(
                                src = node.attributes["src"]?.takeIf { it.isNotBlank() },
                                alt = node.attributes["alt"].orEmpty(),
                            ),
                        )
                    }
                }

                "span" -> if (node.attributes["class"] == "tg-spoiler") {
                    val spoilerKey = "$keyPrefix-spoiler-${spoilerCounter.next()}"
                    val revealed = revealedSpoilers[spoilerKey] == true
                    collectEmojiNodesRecursive(
                        node.children,
                        spoilerKey,
                        revealedSpoilers,
                        out,
                        spoilerCounter,
                        hideMedia = !revealed,
                    )
                } else {
                    collectEmojiNodesRecursive(
                        node.children,
                        "$keyPrefix-$index",
                        revealedSpoilers,
                        out,
                        spoilerCounter,
                        hideMedia,
                    )
                }

                else -> collectEmojiNodesRecursive(
                    node.children,
                    "$keyPrefix-$index",
                    revealedSpoilers,
                    out,
                    spoilerCounter,
                    hideMedia,
                )
            }
        }
    }
}

internal class SpoilerCounter {
    private var value = 0
    fun next(): Int = value++
}

private fun buildInlineAnnotatedString(
    nodes: List<HtmlNode>,
    linkColor: Color,
    spoilerColor: Color,
    revealedSpoilers: MutableMap<String, Boolean>,
    onToggleSpoiler: (String) -> Unit,
    keyPrefix: String,
    context: Context,
): AnnotatedString = buildAnnotatedString {
    appendInlineNodes(
        nodes = nodes,
        linkColor = linkColor,
        spoilerColor = spoilerColor,
        revealedSpoilers = revealedSpoilers,
        onToggleSpoiler = onToggleSpoiler,
        keyPrefix = keyPrefix,
        context = context,
    )
}

private fun AnnotatedString.Builder.appendInlineNodes(
    nodes: List<HtmlNode>,
    linkColor: Color,
    spoilerColor: Color,
    revealedSpoilers: Map<String, Boolean>,
    onToggleSpoiler: (String) -> Unit,
    keyPrefix: String,
    context: Context,
    spoilerCounter: SpoilerCounter = SpoilerCounter(),
    hideMedia: Boolean = false,
) {
    nodes.forEachIndexed { index, node ->
        when (node) {
            is HtmlNode.Text -> append(node.value)

            is HtmlNode.Element -> when (node.tag) {
                "br" -> append("\n")

                "b", "strong" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineNodes(
                        node.children, linkColor, spoilerColor, revealedSpoilers,
                        onToggleSpoiler, "$keyPrefix-$index", context, spoilerCounter, hideMedia,
                    )
                }

                "i", "em" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineNodes(
                        node.children, linkColor, spoilerColor, revealedSpoilers,
                        onToggleSpoiler, "$keyPrefix-$index", context, spoilerCounter, hideMedia,
                    )
                }

                "u", "ins" -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    appendInlineNodes(
                        node.children, linkColor, spoilerColor, revealedSpoilers,
                        onToggleSpoiler, "$keyPrefix-$index", context, spoilerCounter, hideMedia,
                    )
                }

                "s", "strike", "del" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInlineNodes(
                        node.children, linkColor, spoilerColor, revealedSpoilers,
                        onToggleSpoiler, "$keyPrefix-$index", context, spoilerCounter, hideMedia,
                    )
                }

                "code" -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = spoilerColor.copy(alpha = 0.15f),
                    ),
                ) {
                    appendInlineNodes(
                        node.children, linkColor, spoilerColor, revealedSpoilers,
                        onToggleSpoiler, "$keyPrefix-$index", context, spoilerCounter, hideMedia,
                    )
                }

                "a" -> {
                    val href = node.attributes["href"].orEmpty()
                    withLink(
                        LinkAnnotation.Url(
                            url = href,
                            styles = TextLinkStyles(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                            linkInteractionListener = { openUrl(context, href) },
                        ),
                    ) {
                        appendInlineNodes(
                            node.children, linkColor, spoilerColor, revealedSpoilers,
                            onToggleSpoiler, "$keyPrefix-$index", context, spoilerCounter, hideMedia,
                        )
                    }
                }

                "span" -> if (node.attributes["class"] == "tg-spoiler") {
                    val spoilerKey = "$keyPrefix-spoiler-${spoilerCounter.next()}"
                    val revealed = revealedSpoilers[spoilerKey] == true
                    if (revealed) {
                        appendInlineNodes(
                            node.children, linkColor, spoilerColor, revealedSpoilers,
                            onToggleSpoiler, spoilerKey, context, spoilerCounter, hideMedia = false,
                        )
                    } else {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = spoilerKey,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        background = spoilerColor,
                                        color = Color.Transparent,
                                    ),
                                ),
                                linkInteractionListener = { onToggleSpoiler(spoilerKey) },
                            ),
                        ) {
                            appendInlineNodes(
                                node.children, linkColor, spoilerColor, revealedSpoilers,
                                onToggleSpoiler, spoilerKey, context, spoilerCounter, hideMedia = true,
                            )
                        }
                    }
                } else {
                    appendInlineNodes(
                        node.children, linkColor, spoilerColor, revealedSpoilers,
                        onToggleSpoiler, "$keyPrefix-$index", context, spoilerCounter, hideMedia,
                    )
                }

                "img" -> if (!hideMedia && isTgEmoji(node)) {
                    val alt = node.attributes["alt"].orEmpty().ifEmpty { "\u200B" }
                    appendInlineContent("$keyPrefix-emoji-$index", alt)
                } else if (isTgEmoji(node)) {
                    val alt = node.attributes["alt"].orEmpty()
                    if (alt.isNotEmpty()) append(alt)
                }

                else -> appendInlineNodes(
                    node.children, linkColor, spoilerColor, revealedSpoilers,
                    onToggleSpoiler, "$keyPrefix-$index", context, spoilerCounter, hideMedia,
                )
            }
        }
    }
}
