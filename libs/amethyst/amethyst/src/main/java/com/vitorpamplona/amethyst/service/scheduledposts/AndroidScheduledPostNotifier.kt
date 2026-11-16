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
package com.vitorpamplona.amethyst.service.scheduledposts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_scheduled_posts_channel_description
import com.vitorpamplona.amethyst.commons.resources.app_notification_scheduled_posts_channel_id
import com.vitorpamplona.amethyst.commons.resources.app_notification_scheduled_posts_channel_name
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_error_prefix
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_notification_failed_title
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_notification_sent_title
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostNotifier
import com.vitorpamplona.amethyst.commons.scheduledposts.extractContentPreview
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.ui.MainActivity

/**
 * Android [ScheduledPostNotifier]: posts a user-visible system notification when a
 * scheduled post completes (sent or failed). Lives in the app module because it
 * depends on app resources (strings, drawables) and [MainActivity], which are not
 * available to `commons`.
 */
class AndroidScheduledPostNotifier(
    private val context: Context,
) : ScheduledPostNotifier {
    override suspend fun notifySent(post: ScheduledPost) {
        ensureChannel(context)
        post(
            context = context,
            notId = idFor(post.id),
            title = loadStringRes(Res.string.scheduled_posts_notification_sent_title),
            body = extractContentPreview(post, 120),
        )
    }

    override suspend fun notifyFailed(
        post: ScheduledPost,
        error: String?,
    ) {
        ensureChannel(context)
        val snippet = extractContentPreview(post, 120)
        val body =
            if (error.isNullOrBlank()) {
                snippet
            } else {
                "$snippet\n${loadStringRes(Res.string.scheduled_posts_error_prefix, error)}"
            }
        post(
            context = context,
            notId = idFor(post.id),
            title = loadStringRes(Res.string.scheduled_posts_notification_failed_title),
            body = body,
        )
    }

    private suspend fun post(
        context: Context,
        notId: Int,
        title: String,
        body: String,
    ) {
        val channelId = loadStringRes(Res.string.app_notification_scheduled_posts_channel_id)
        val tapIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val tapPendingIntent =
            PendingIntent.getActivity(
                context,
                notId,
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notificationManager = NotificationManagerCompat.from(context)
        // POST_NOTIFICATIONS is runtime-granted on Android 13+; bail out
        // explicitly so lint doesn't flag the notify() call and so a denied
        // user doesn't see a misleading no-op log.
        if (!notificationManager.areNotificationsEnabled()) return
        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tapPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
        try {
            notificationManager.notify(notId, builder.build())
        } catch (_: SecurityException) {
            // Race: permission revoked between the check above and notify().
        }
    }

    // Distinct id per post so multiple completions don't collapse onto one row.
    private fun idFor(postId: String): Int = SCHEDULED_POST_NOT_ID_BASE xor postId.hashCode()

    companion object {
        // @Volatile so the channel reference is visible across the WorkManager IO
        // thread pool — two workers firing in the same window can race on
        // ensureChannel. createNotificationChannel itself is idempotent.
        @Volatile
        private var channel: NotificationChannel? = null
        private const val SCHEDULED_POST_NOT_ID_BASE = 0x70000

        suspend fun ensureChannel(context: Context) {
            if (channel != null) return
            channel =
                NotificationChannel(
                    loadStringRes(Res.string.app_notification_scheduled_posts_channel_id),
                    loadStringRes(Res.string.app_notification_scheduled_posts_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = loadStringRes(Res.string.app_notification_scheduled_posts_channel_description)
                }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel!!)
        }
    }
}
