/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_calls_channel_name
import com.vitorpamplona.amethyst.commons.resources.app_notification_scheduled_posts_channel_id
import com.vitorpamplona.amethyst.commons.resources.app_notification_scheduled_posts_channel_name
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.service.call.notification.CallNotifier
import com.vitorpamplona.amethyst.service.scheduledposts.AndroidScheduledPostNotifier
import com.vitorpamplona.quartz.utils.Log
import org.jetbrains.compose.resources.StringResource

/**
 * Registry of user-facing notification channels and helpers to read their
 * current importance / open the system settings page for them.
 *
 * Android (post-Oreo) owns channel state — the app cannot toggle channel
 * importance directly. The Notifications settings screen surfaces the
 * channels here and routes the user to the system per-channel page.
 *
 * Foreground-service channels (relay-connection, nests audio) are
 * intentionally omitted: they're functional indicators, not content
 * notifications, and disabling them breaks the foreground service contract.
 */
object NotificationChannels {
    private const val TAG = "NotificationChannels"

    enum class ChannelStatus { ON, SILENT, OFF }

    /**
     * A single content-bearing notification channel exposed in the settings UI.
     * [ensure] creates the channel if missing — needed so the system per-channel
     * settings page has something to open even before the first notification fires.
     */
    data class Entry(
        val nameRes: StringResource,
        val icon: MaterialSymbol,
        val channelId: suspend (Context) -> String,
        val ensure: suspend (Context) -> Unit,
    )

    /** The per-kind content channels, derived from [NotificationCategory], in a
     * sensible settings order, plus the two non-event channels (scheduled posts,
     * calls) that don't map to a Nostr event kind. */
    val contentChannels: List<Entry> =
        NotificationCategory.entries
            // Chess (NIP-64) is a debug-only feature for now — don't surface (or
            // create) its channel in release settings.
            .filter { it != NotificationCategory.CHESS || isDebug }
            .map { category ->
                Entry(
                    nameRes = category.channelNameRes,
                    icon = category.settingsIcon,
                    channelId = { category.channelId(it) },
                    ensure = { category.ensureChannel(it) },
                )
            } +
            listOf(
                Entry(
                    nameRes = Res.string.app_notification_scheduled_posts_channel_name,
                    icon = MaterialSymbols.Schedule,
                    channelId = { loadStringRes(Res.string.app_notification_scheduled_posts_channel_id) },
                    ensure = { AndroidScheduledPostNotifier.ensureChannel(it) },
                ),
                Entry(
                    nameRes = Res.string.app_notification_calls_channel_name,
                    icon = MaterialSymbols.Call,
                    channelId = { CallNotifier.CALL_CHANNEL_ID },
                    ensure = { CallNotifier.getOrCreateCallChannel(it) },
                ),
            )

    fun statusOf(
        context: Context,
        channelId: String,
    ): ChannelStatus {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return ChannelStatus.OFF
        val nm = context.getSystemService(NotificationManager::class.java) ?: return ChannelStatus.OFF
        val channel = nm.getNotificationChannel(channelId) ?: return ChannelStatus.ON
        return when (channel.importance) {
            NotificationManager.IMPORTANCE_NONE -> ChannelStatus.OFF
            NotificationManager.IMPORTANCE_MIN, NotificationManager.IMPORTANCE_LOW -> ChannelStatus.SILENT
            else -> ChannelStatus.ON
        }
    }

    /**
     * Opens the system per-channel notification settings page. Falls back to
     * the app-level notification settings if the per-channel intent isn't
     * supported (e.g. the channel was never created, or on stripped-down ROMs).
     */
    fun openChannelSettings(
        context: Context,
        channelId: String,
    ) {
        try {
            val intent =
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Per-channel intent failed, falling back to app notification settings", e)
            openAppNotificationSettings(context)
        }
    }

    fun openAppNotificationSettings(context: Context) {
        try {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app notification settings", e)
        }
    }
}
