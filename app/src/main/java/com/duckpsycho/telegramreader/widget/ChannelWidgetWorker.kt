package com.duckpsycho.telegramreader.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckpsycho.telegramreader.TelegramReaderApp
import com.duckpsycho.telegramreader.data.Account
import com.duckpsycho.telegramreader.data.PostsPage
import com.duckpsycho.telegramreader.data.SubscriptionItem
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChannelWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val app = context as TelegramReaderApp
        val activeIds = ChannelWidgetUpdater.ids(context)
        val requestedId = inputData.getInt(WIDGET_ID_KEY, AppWidgetManager.INVALID_APPWIDGET_ID)
        val ids = if (requestedId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            activeIds
        } else {
            activeIds.filter { it == requestedId }.toIntArray()
        }
        if (ids.isEmpty()) return@withContext Result.success()
        val store = WidgetStore(context)

        if (!app.cookieJar.hasAccountCookie()) {
            ids.forEach { store.clearForAccount(it, null) }
            ChannelWidgetUpdater.renderAll(context)
            return@withContext Result.success()
        }

        val accountAndSubscriptions: Pair<Account?, List<SubscriptionItem>> = try {
            val account = app.api.getAccount()
            account to if (account != null) app.api.getSubscriptions() else emptyList<SubscriptionItem>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ids.forEach { id -> store.markError(id, store.read(id)) }
            ChannelWidgetUpdater.renderAll(context)
            return@withContext Result.success()
        }

        val (account, subscriptions) = accountAndSubscriptions
        if (account == null || !app.cookieJar.hasAccountCookie()) {
            ids.forEach { store.clearForAccount(it, null) }
            ChannelWidgetUpdater.renderAll(context)
            return@withContext Result.success()
        }

        val channels = subscriptions.associateBy { it.channel.username.lowercase() }
        val fetchedPosts = mutableMapOf<String, kotlin.Result<PostsPage>>()
        for (id in ids) {
            val selected = store.read(id)
            val username = selected.username ?: continue
            if (selected.accountId != account.id) {
                store.clearForAccount(id, account.id, selected)
                ChannelWidgetUpdater.render(context, id)
                continue
            }
            val channel = channels[username.lowercase()]?.channel
            if (channel == null) {
                store.markUnavailable(id, selected)
                ChannelWidgetUpdater.render(context, id)
                continue
            }
            if (selected.status == STATUS_UNAVAILABLE || selected.status == STATUS_SIGNED_OUT) {
                store.markLoading(id, selected)
            }
            try {
                val page = fetchedPosts.getOrPut(username.lowercase()) {
                    runCatching { app.api.getPosts(username) }
                }.getOrThrow()
                if (!app.cookieJar.hasAccountCookie()) continue
                val previews = try {
                    WidgetEmojiPreview.renderPosts(context, id, page.posts)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyMap()
                }
                if (!app.cookieJar.hasAccountCookie() ||
                    !store.savePosts(id, selected, page.channel?.title ?: channel.title, page.posts, previews)
                ) {
                    WidgetEmojiPreview.deleteFiles(context, previews.values)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                store.markError(id, selected)
            }
            ChannelWidgetUpdater.render(context, id)
        }
        Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "channel_widget_periodic_refresh"
        private const val IMMEDIATE_NAME = "channel_widget_immediate_refresh"
        private const val WIDGET_ID_KEY = "widget_id"

        private fun widgetWorkName(widgetId: Int) = "channel_widget_refresh_$widgetId"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ChannelWidgetWorker>(30, TimeUnit.MINUTES)
                    .setInitialDelay(30, TimeUnit.MINUTES)
                    .build(),
            )
        }

        fun refreshNow(context: Context) {
            schedule(context)
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ChannelWidgetWorker>().build(),
            )
        }

        fun refreshNow(context: Context, widgetId: Int) {
            schedule(context)
            WorkManager.getInstance(context).enqueueUniqueWork(
                widgetWorkName(widgetId),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ChannelWidgetWorker>()
                    .setInputData(Data.Builder().putInt(WIDGET_ID_KEY, widgetId).build())
                    .build(),
            )
        }

        fun cancelForWidget(context: Context, widgetId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(widgetWorkName(widgetId))
        }

        fun cancel(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(PERIODIC_NAME)
            manager.cancelUniqueWork(IMMEDIATE_NAME)
        }
    }
}
