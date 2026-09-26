package com.duckpsycho.telegramreader.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.data.BASE_URL
import com.duckpsycho.telegramreader.data.UserPreferences
import com.duckpsycho.telegramreader.util.formatPostDate

class ChannelWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = WidgetPostsFactory(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
    )
}

private class WidgetPostsFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var snapshot = WidgetSnapshot()

    override fun onCreate() = onDataSetChanged()

    override fun onDataSetChanged() {
        snapshot = WidgetStore(context).read(widgetId)
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = when (snapshot.status) {
        STATUS_UNAVAILABLE, STATUS_SIGNED_OUT -> 0
        else -> snapshot.posts.size
    }

    override fun getViewAt(position: Int): RemoteViews? {
        val post = snapshot.posts.getOrNull(position) ?: return null
        val ui = localizedWidgetContext(context)
        val typeLabel = when (post.mediaType?.lowercase()) {
            "photo", "image" -> ui.getString(R.string.widget_photo)
            "video", "gif" -> ui.getString(R.string.widget_video)
            "document", "file" -> ui.getString(R.string.widget_file)
            null -> null
            else -> ui.getString(R.string.widget_media)
        }
        val preview = when {
            post.text.isBlank() -> typeLabel?.let { "[$it]" } ?: ui.getString(R.string.widget_media)
            typeLabel != null -> "${post.text}  ·  [$typeLabel]"
            else -> post.text
        }
        val locale = UserPreferences(context).locale
        val emojiPreview = post.previewFile?.let { name ->
            BitmapFactory.decodeFile(
                WidgetEmojiPreview.file(context, name).path,
                BitmapFactory.Options().apply { inScaled = false },
            )?.apply { density = context.resources.displayMetrics.densityDpi }
        }
        return RemoteViews(context.packageName, R.layout.channel_widget_post).apply {
            setTextViewText(R.id.widget_post_text, preview)
            if (emojiPreview != null) {
                setImageViewBitmap(R.id.widget_post_emoji_preview, emojiPreview)
                setContentDescription(R.id.widget_post_emoji_preview, preview)
                setViewVisibility(R.id.widget_post_text, View.GONE)
                setViewVisibility(R.id.widget_post_emoji_preview, View.VISIBLE)
            }
            setTextViewText(
                R.id.widget_post_date,
                formatPostDate(post.date, locale, ui.getString(R.string.post_yesterday)),
            )
            setOnClickFillInIntent(
                R.id.widget_post_row,
                Intent().apply { data = Uri.parse("$BASE_URL/channel/${snapshot.username}/${post.id}") },
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = snapshot.posts.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
