package com.duckpsycho.telegramreader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.duckpsycho.telegramreader.MainActivity
import com.duckpsycho.telegramreader.R
import com.duckpsycho.telegramreader.data.BASE_URL

class ChannelWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId in ChannelWidgetUpdater.ids(context)) {
                val store = WidgetStore(context)
                val selected = store.read(widgetId)
                if (selected.username != null && selected.status != STATUS_SIGNED_OUT) {
                    store.markRefreshing(widgetId)
                    ChannelWidgetUpdater.render(context, widgetId)
                    ChannelWidgetUpdater.requestRefresh(context, widgetId)
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ChannelWidgetUpdater.render(context, it) }
        ChannelWidgetUpdater.requestRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        ChannelWidgetUpdater.render(context, appWidgetId)
        if (WidgetStore(context).read(appWidgetId).posts.any { it.previewFile != null }) {
            ChannelWidgetUpdater.requestRefresh(context, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetStore(context)
        appWidgetIds.forEach { id ->
            store.remove(id)
            ChannelWidgetWorker.cancelForWidget(context, id)
        }
    }

    override fun onDisabled(context: Context) {
        ChannelWidgetUpdater.cancel(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.duckpsycho.telegramreader.widget.REFRESH"
    }
}

internal object ChannelWidgetUpdater {
    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, ChannelWidgetProvider::class.java))

    fun renderAll(context: Context) = ids(context).forEach { render(context, it) }

    fun render(context: Context, widgetId: Int) {
        if (widgetId !in ids(context)) return
        val ui = localizedWidgetContext(context)
        val snapshot = WidgetStore(context).read(widgetId)
        val manager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.channel_widget)
        views.setTextViewText(R.id.widget_title, snapshot.title ?: ui.getString(R.string.widget_title))
        val status = when {
            snapshot.username == null -> ui.getString(R.string.widget_choose_channel)
            snapshot.status == STATUS_SIGNED_OUT -> ui.getString(R.string.widget_no_account)
            snapshot.status == STATUS_UNAVAILABLE -> ui.getString(R.string.widget_channel_unavailable)
            snapshot.status == STATUS_ERROR -> ui.getString(R.string.widget_update_failed)
            snapshot.status == STATUS_LOADING -> ui.getString(R.string.widget_loading_posts)
            else -> "@${snapshot.username}"
        }
        views.setTextViewText(R.id.widget_status, status)
        views.setViewVisibility(
            R.id.widget_refresh,
            if (snapshot.username == null || snapshot.status == STATUS_SIGNED_OUT) View.GONE else View.VISIBLE,
        )
        val options = manager.getAppWidgetOptions(widgetId)
        views.setViewVisibility(
            R.id.widget_status,
            if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) < 110) View.GONE else View.VISIBLE,
        )

        val serviceIntent = Intent(context, ChannelWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse("widget://posts/$widgetId")
        }
        views.setRemoteAdapter(R.id.widget_posts, serviceIntent)
        views.setEmptyView(R.id.widget_posts, R.id.widget_empty)
        views.setTextViewText(
            R.id.widget_empty,
            when {
                snapshot.username == null -> ui.getString(R.string.widget_choose_channel)
                snapshot.status == STATUS_SIGNED_OUT -> ui.getString(R.string.widget_no_account)
                snapshot.status == STATUS_UNAVAILABLE -> ui.getString(R.string.widget_channel_unavailable)
                snapshot.status == STATUS_ERROR -> ui.getString(R.string.widget_update_failed)
                snapshot.status == STATUS_LOADING -> ui.getString(R.string.widget_loading_posts)
                else -> ui.getString(R.string.widget_no_posts)
            },
        )

        val configure = Intent(context, ChannelWidgetConfigureActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse("widget://configure/$widgetId")
        }
        views.setOnClickPendingIntent(
            R.id.widget_settings,
            PendingIntent.getActivity(context, widgetId, configure, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        val refresh = Intent(context, ChannelWidgetProvider::class.java).apply {
            action = ChannelWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse("widget://refresh/$widgetId")
        }
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(context, widgetId, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        val headerIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(if (snapshot.username == null) BASE_URL else "$BASE_URL/channel/${snapshot.username}")
        }
        views.setOnClickPendingIntent(
            R.id.widget_channel_header,
            PendingIntent.getActivity(context, widgetId, headerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        val postTemplate = Intent(context, MainActivity::class.java).apply { action = Intent.ACTION_VIEW }
        views.setPendingIntentTemplate(
            R.id.widget_posts,
            PendingIntent.getActivity(context, widgetId, postTemplate, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
        )
        manager.updateAppWidget(widgetId, views)
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_posts)
    }

    fun onAccountChanged(context: Context, accountId: String?) {
        val store = WidgetStore(context)
        ids(context).forEach { id ->
            val current = store.read(id)
            if (accountId != null && current.accountId == accountId) {
                store.markLoading(id)
            } else {
                store.clearForAccount(id, accountId)
            }
            render(context, id)
        }
        if (accountId != null) requestRefresh(context)
    }

    fun onSubscriptionsChanged(context: Context, accountId: String?, usernames: Set<String>) {
        if (accountId == null) return
        val store = WidgetStore(context)
        ids(context).forEach { id ->
            val snapshot = store.read(id)
            if (snapshot.username != null && snapshot.accountId == accountId) {
                if (snapshot.username.lowercase() !in usernames) {
                    store.markUnavailable(id)
                } else if (snapshot.status == STATUS_UNAVAILABLE) {
                    store.markLoading(id)
                }
                render(context, id)
            }
        }
        requestRefresh(context)
    }

    fun schedule(context: Context) {
        if (ids(context).isNotEmpty()) ChannelWidgetWorker.schedule(context)
    }
    fun cancel(context: Context) = ChannelWidgetWorker.cancel(context)
    fun requestRefresh(context: Context) {
        if (ids(context).isNotEmpty()) ChannelWidgetWorker.refreshNow(context)
    }

    fun requestRefresh(context: Context, widgetId: Int) {
        if (widgetId in ids(context)) ChannelWidgetWorker.refreshNow(context, widgetId)
    }
}
