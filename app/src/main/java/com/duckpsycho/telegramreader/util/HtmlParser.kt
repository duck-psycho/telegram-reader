package com.duckpsycho.telegramreader.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

sealed interface HtmlNode {
    data class Text(val value: String) : HtmlNode
    data class Element(
        val tag: String,
        val attributes: Map<String, String> = emptyMap(),
        val children: List<HtmlNode> = emptyList(),
    ) : HtmlNode
}

object HtmlParser {
    fun parse(html: String): List<HtmlNode> {
        if (html.isBlank()) return emptyList()
        val body = Jsoup.parseBodyFragment(html.trim()).body()
        return body.childNodes().mapNotNull { it.toHtmlNode() }
    }
}

private fun Node.toHtmlNode(): HtmlNode? = when (this) {
    is TextNode -> {
        val text = wholeText
        if (text.isEmpty()) null else HtmlNode.Text(text)
    }

    is Element -> HtmlNode.Element(
        tag = tagName().lowercase(),
        attributes = attributes().associate { it.key.lowercase() to it.value },
        children = childNodes().mapNotNull { it.toHtmlNode() },
    )

    else -> null
}
