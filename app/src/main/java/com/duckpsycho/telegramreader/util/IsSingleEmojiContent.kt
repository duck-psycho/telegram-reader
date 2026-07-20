package com.duckpsycho.telegramreader.util

import java.text.BreakIterator

private val inlineTagPattern =
    Regex("""<\/?(?:b|strong|i|em|u|ins|s|strike|del|span|a)[^>]*>""", RegexOption.IGNORE_CASE)

private val singleCustomEmojiPattern =
    Regex("""^<img\b[^>]*\btg-emoji\b[^>]*>$""", RegexOption.IGNORE_CASE)

fun isSingleEmojiContent(text: String?, html: String?): Boolean {
    html?.trim()?.takeIf { it.isNotEmpty() }?.let { trimmed ->
        if (singleCustomEmojiPattern.matches(trimmed)) return true
        val stripped = trimmed
            .replace(inlineTagPattern, "")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (stripped.isNotEmpty() && isSingleEmoji(stripped)) return true
    }
    text?.trim()?.takeIf { it.isNotEmpty() }?.let { trimmed ->
        if (isSingleEmoji(trimmed)) return true
    }
    return false
}

private fun isSingleEmoji(value: String): Boolean {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(value)
    var count = 0
    var onlyEmoji = true
    var start = iterator.first()
    while (start != BreakIterator.DONE) {
        val end = iterator.next()
        if (start == BreakIterator.DONE) break
        val sliceEnd = if (end == BreakIterator.DONE) value.length else end
        val segment = value.substring(start, sliceEnd)
        if (segment.isNotBlank()) {
            count++
            if (!segment.codePoints().allMatch { codePoint -> isExtendedPictographic(codePoint) }) {
                onlyEmoji = false
                break
            }
        }
        start = end
    }
    return count == 1 && onlyEmoji
}

private fun isExtendedPictographic(codePoint: Int): Boolean = when (codePoint) {
    in 0x1F300..0x1FAFF -> true
    in 0x2600..0x27BF -> true
    else -> false
}
