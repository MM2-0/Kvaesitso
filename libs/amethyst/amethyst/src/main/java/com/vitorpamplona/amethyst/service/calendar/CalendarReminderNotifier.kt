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
package com.vitorpamplona.amethyst.service.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_channel_description
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_channel_id
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_channel_name
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.ui.MainActivity

/**
 * Posts user-visible "starting soon" notifications for NIP-52 appointments the user has RSVP'd
 * to as ACCEPTED. Mirrors the shape of [com.vitorpamplona.amethyst.service.scheduledposts.AndroidScheduledPostNotifier]
 * so the two notification surfaces stay consistent.
 */
object CalendarReminderNotifier {
    @Volatile
    private var channel: NotificationChannel? = null
    private const val REMINDER_NOT_ID_BASE = 0x80000

    /**
     * @param eventId   the appointment's event id — used to derive a stable notification id so a
     *                  second reminder for the same event collapses rather than stacking.
     * @param title     the appointment title (or a fallback string).
     * @param body      a short pre-formatted body, e.g. "Starts in 15 minutes".
     * @param deepLink  a `nostr:naddr…` URI for the calendar event. Tapping the notification
     *                  hands this to MainActivity, which routes it via `uriToRoute` → the
     *                  calendar detail screen (CalendarTimeSlotEvent / CalendarDateSlotEvent
     *                  branches in RouteMaker resolve to Route.CalendarEventDetail).
     */
    suspend fun notifyReminder(
        context: Context,
        eventId: String,
        title: String,
        body: String,
        deepLink: String,
    ) {
        ensureChannel(context)
        val notId = idFor(eventId)
        val channelId = loadStringRes(Res.string.calendar_reminder_channel_id)
        val tapIntent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = deepLink.toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val tapPendingIntent =
            PendingIntent.getActivity(
                context,
                notId,
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val nm = NotificationManagerCompat.from(context)
        // POST_NOTIFICATIONS is runtime-granted on Android 13+; bail when denied so the lint
        // call below doesn't flag and we don't log a misleading no-op.
        if (!nm.areNotificationsEnabled()) return

        val notification =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tapPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .build()
        try {
            nm.notify(notId, notification)
        } catch (_: SecurityException) {
            // Race: permission revoked between the check above and notify().
        }
    }

    suspend fun ensureChannel(context: Context) {
        if (channel != null) return
        channel =
            NotificationChannel(
                loadStringRes(Res.string.calendar_reminder_channel_id),
                loadStringRes(Res.string.calendar_reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = loadStringRes(Res.string.calendar_reminder_channel_description)
            }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel!!)
    }

    // Distinct id per event so two reminders for the same event collapse onto one row while
    // separate events render side by side.
    private fun idFor(eventId: String): Int = REMINDER_NOT_ID_BASE xor eventId.hashCode()
}
