package com.duckpsycho.telegramreader.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ImageSpan
import android.util.TypedValue
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.data.Post
import com.duckpsycho.telegramreader.util.HtmlNode
import com.duckpsycho.telegramreader.util.HtmlParser
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal object WidgetEmojiPreview {
    private const val MAX_BITMAP_PIXELS = 750_000L
    private const val MAX_EMOJI_IMAGES = 24
    private val whitespace = Regex("\\s+")

    private sealed interface Piece {
        data class Text(val value: String) : Piece
        data class Emoji(val src: String?, val alt: String) : Piece
    }

    fun plainText(html: String): String = pieces(html)
        .joinToString("") { piece ->
            when (piece) {
                is Piece.Text -> piece.value
                is Piece.Emoji -> piece.alt
            }
        }
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    suspend fun renderPosts(context: Context, widgetId: Int, posts: List<Post>): Map<Long, String> {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val density = context.resources.displayMetrics.density
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val width = ((widthDp - 32).coerceAtLeast(100) * density).toInt().coerceAtMost(900)
        val emojiSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, context.resources.displayMetrics)
            .toInt().coerceAtLeast(18)
        val bitmaps = mutableMapOf<String, Bitmap?>()
        val result = mutableMapOf<Long, String>()
        posts.sortedByDescending(Post::id).take(20).forEach { post ->
            val html = post.html?.takeIf { it.contains("tg-emoji", ignoreCase = true) } ?: return@forEach
            val preview = try {
                renderPost(context, widgetId, post, html, width, emojiSize, bitmaps)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            preview?.let { result[post.id] = it }
        }
        return result
    }

    fun file(context: Context, name: String): File = File(File(context.cacheDir, "widget_emoji_previews"), name)

    fun deleteFiles(context: Context, names: Collection<String>) {
        names.forEach { file(context, it).delete() }
    }

    private suspend fun renderPost(
        context: Context,
        widgetId: Int,
        post: Post,
        html: String,
        width: Int,
        emojiSize: Int,
        bitmaps: MutableMap<String, Bitmap?>,
    ): String? {
        val parts = pieces(html)
        if (parts.none { it is Piece.Emoji }) return null
        val text = SpannableStringBuilder()
        var drewEmoji = false
        for (part in parts) {
            when (part) {
                is Piece.Text -> text.append(part.value)

                is Piece.Emoji -> {
                    val bitmap = part.src?.let { src ->
                        if (src !in bitmaps && bitmaps.size < MAX_EMOJI_IMAGES) {
                            bitmaps[src] = loadEmoji(context, src, emojiSize)
                        }
                        bitmaps[src]
                    }
                    if (bitmap == null) {
                        text.append(part.alt)
                    } else {
                        val start = text.length
                        text.append('\uFFFC')
                        val drawable = BitmapDrawable(context.resources, bitmap).apply {
                            setBounds(0, 0, emojiSize, emojiSize)
                        }
                        text.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        drewEmoji = true
                    }
                }
            }
        }
        val label = mediaLabel(context, post)
        if (label != null) text.append("  ·  [$label]")
        if (!drewEmoji || text.isEmpty()) return null

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, context.resources.displayMetrics)
            color = context.getColor(R.color.widget_text)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        val height = layout.height.coerceAtLeast(emojiSize)
        if (width.toLong() * height > MAX_BITMAP_PIXELS) return null
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(image))
        val name = "emoji_${widgetId}_${post.id}_${UUID.randomUUID()}.png"
        val target = file(context, name)
        target.parentFile?.mkdirs()
        val saved = runCatching {
            target.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.getOrDefault(false)
        image.recycle()
        if (!saved) {
            target.delete()
            return null
        }
        return name
    }

    private suspend fun loadEmoji(context: Context, src: String, size: Int): Bitmap? = try {
        withTimeoutOrNull(4_000) {
            val request = ImageRequest.Builder(context)
                .data(src)
                .size(size, size)
                .allowHardware(false)
                .build()
            val drawable = (Coil.imageLoader(context).execute(request) as? SuccessResult)?.drawable
                ?: return@withTimeoutOrNull null
            val image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(image))
            image
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun mediaLabel(context: Context, post: Post): String? {
        val ui = localizedWidgetContext(context)
        val type = post.mediaType ?: when {
            post.mediaGroup != null -> "photo"
            post.document != null -> "document"
            post.location != null -> "location"
            else -> null
        }
        return when (type?.lowercase()) {
            "photo", "image" -> ui.getString(R.string.widget_photo)
            "video", "gif" -> ui.getString(R.string.widget_video)
            "document", "file" -> ui.getString(R.string.widget_file)
            null -> null
            else -> ui.getString(R.string.widget_media)
        }
    }

    private fun pieces(html: String): List<Piece> = buildList {
        fun appendNode(node: HtmlNode) {
            when (node) {
                is HtmlNode.Text -> add(Piece.Text(node.value))

                is HtmlNode.Element -> when (node.tag) {
                    "img" -> {
                        val classes = node.attributes["class"].orEmpty().split(whitespace)
                        if ("tg-emoji" in classes) {
                            add(Piece.Emoji(node.attributes["src"], node.attributes["alt"].orEmpty()))
                        }
                    }

                    "br" -> add(Piece.Text("\n"))

                    else -> {
                        if (node.tag in setOf("p", "div", "blockquote", "li") && isNotEmpty()) add(Piece.Text("\n"))
                        node.children.forEach(::appendNode)
                    }
                }
            }
        }
        HtmlParser.parse(html).forEach(::appendNode)
    }
}
