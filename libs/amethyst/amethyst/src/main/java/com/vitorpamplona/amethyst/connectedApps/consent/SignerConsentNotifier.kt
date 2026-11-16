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
package com.vitorpamplona.amethyst.connectedApps.consent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_notif_channel_desc
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_notif_channel_name
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_notif_tap
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import org.jetbrains.compose.resources.StringResource

/**
 * Surfaces a signer consent/connect [android.app.Activity] from the **background**.
 *
 * A bare `context.startActivity(...)` from the application context only opens a window while
 * Amethyst already owns the foreground. When a signing request arrives over a relay while the app
 * is backgrounded, Android 12+ background-activity-launch (BAL) restrictions silently drop that
 * `startActivity`, so the dialog would never appear and the request would sit until it times out.
 *
 * A full-screen-intent notification on an `IMPORTANCE_HIGH` channel (with the
 * `USE_FULL_SCREEN_INTENT` permission the manifest declares) is the documented BAL exception — the
 * same mechanism [com.vitorpamplona.amethyst.service.call.notification.CallNotifier] uses for
 * incoming calls. On a locked/idle screen it launches the Activity immediately; while the user is
 * actively on another app it shows as a heads-up banner they tap to review.
 *
 * Each coordinator posts one notification keyed by the request token's hash so concurrent requests
 * don't clobber each other, and cancels it once the deferred resolves (approved, denied, or timed
 * out) so no stale prompt lingers.
 */
object SignerConsentNotifier {
    private const val CHANNEL_ID = "com.vitorpamplona.amethyst.SIGNER_CONSENT_CHANNEL"

    private suspend fun ensureChannel(context: Context): NotificationChannel {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.getNotificationChannel(CHANNEL_ID)?.let { return it }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                loadStringRes(Res.string.nip46_signer_notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = loadStringRes(Res.string.nip46_signer_notif_channel_desc)
            }
        manager.createNotificationChannel(channel)
        return channel
    }

    /**
     * Posts a full-screen-intent notification whose content/full-screen [PendingIntent] opens
     * [activityClass] carrying [token]. Returns the notification id to pass to [cancel] once the
     * request resolves.
     */
    suspend fun show(
        context: Context,
        activityClass: Class<*>,
        extraKey: String,
        token: String,
        titleRes: StringResource,
    ): Int {
        // When Amethyst already owns the foreground the direct startActivity opens the dialog, so a
        // heads-up notification would just be redundant noise on top of it. Only fall back to the
        // full-screen intent when we're backgrounded — the case where startActivity is BAL-blocked.
        if (appInForeground()) return NO_NOTIFICATION

        val channel = ensureChannel(context)
        val notificationId = token.hashCode()

        val intent =
            Intent(context, activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(extraKey, token)

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notification =
            NotificationCompat
                .Builder(context, channel.id)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(loadStringRes(titleRes))
                .setContentText(loadStringRes(Res.string.nip46_signer_notif_tap))
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setOngoing(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
        return notificationId
    }

    fun cancel(
        context: Context,
        notificationId: Int,
    ) {
        if (notificationId == NO_NOTIFICATION) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    private fun appInForeground(): Boolean =
        // Defensive: the signer consent path only runs in the main process (where Amethyst.instance
        // is set), but touching it from the keyless :napplet process would throw. Treat any failure
        // as "not foreground" so the notification fallback still fires.
        runCatching { Amethyst.instance.foregroundTracker.isForeground.value }.getOrDefault(false)

    private const val NO_NOTIFICATION = Int.MIN_VALUE
    private const val TIMEOUT_MS = 120_000L
}
