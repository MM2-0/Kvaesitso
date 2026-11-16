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
package com.vitorpamplona.amethyst.service.call.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_calls_channel_description
import com.vitorpamplona.amethyst.commons.resources.app_notification_calls_channel_name
import com.vitorpamplona.amethyst.commons.resources.call_accept
import com.vitorpamplona.amethyst.commons.resources.call_incoming
import com.vitorpamplona.amethyst.commons.resources.call_reject
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.service.call.CallNotificationReceiver
import com.vitorpamplona.amethyst.ui.call.CallActivity
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Focused helper for incoming-call notification posting and lifecycle.
 *
 * Owns the "CALL_CHANNEL" NotificationChannel and the single
 * incoming-call system notification. Intentionally decoupled from the
 * generic notification helper (`NotificationUtils`) so that call UI
 * lifecycle concerns stay in one place and callers do not have to pull
 * in the whole DM/zap/reaction notification subsystem just to post a
 * ring notification.
 *
 * Invoked from:
 *  - [CallActivity]'s owning `CallSession` to cancel the incoming-call
 *    notification once the call transitions out of the ringing state.
 *  - `EventNotificationConsumer` to post the full-screen incoming-call
 *    notification when a CallOffer event arrives while the app is in
 *    the background.
 */
object CallNotifier {
    private var callChannel: NotificationChannel? = null
    const val CALL_CHANNEL_ID = "com.vitorpamplona.amethyst.CALL_CHANNEL"
    private const val CALL_NOTIFICATION_ID = 0x50000

    suspend fun getOrCreateCallChannel(applicationContext: Context): NotificationChannel {
        callChannel?.let { return it }

        val channel =
            NotificationChannel(
                CALL_CHANNEL_ID,
                loadStringRes(Res.string.app_notification_calls_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = loadStringRes(Res.string.app_notification_calls_channel_description)
                // Silence the notification sound — CallAudioManager plays the ringtone
                setSound(null, null)
                enableVibration(false)
            }

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
        callChannel = channel
        return channel
    }

    /**
     * Posts the system incoming-call notification. The notification is a
     * full-screen intent that opens [CallActivity] when the device is
     * unlocked, and provides Accept / Reject actions.
     */
    suspend fun send(
        callerName: String,
        callerBitmap: Bitmap?,
        applicationContext: Context,
    ) {
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = getOrCreateCallChannel(applicationContext)

        // Tapping the notification opens the CallActivity
        val contentIntent =
            Intent(applicationContext, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val contentPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                CALL_NOTIFICATION_ID,
                contentIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        // Accept launches CallActivity directly (not via BroadcastReceiver)
        // to comply with Android 12+ notification trampoline restrictions.
        val acceptIntent =
            Intent(applicationContext, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(CallActivity.EXTRA_ACCEPT_CALL, true)
            }

        val acceptPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                CALL_NOTIFICATION_ID + 1,
                acceptIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val rejectIntent =
            Intent(applicationContext, CallNotificationReceiver::class.java).apply {
                action = CallNotificationReceiver.ACTION_REJECT_CALL
            }

        val rejectPendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                CALL_NOTIFICATION_ID + 2,
                rejectIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val builder =
            NotificationCompat
                .Builder(applicationContext, channel.id)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(loadStringRes(Res.string.call_incoming))
                .setContentText(callerName)
                .setLargeIcon(callerBitmap)
                .setContentIntent(contentPendingIntent)
                .setFullScreenIntent(contentPendingIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setOngoing(true)
                .setTimeoutAfter(60_000)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.amethyst, loadStringRes(Res.string.call_reject), rejectPendingIntent)
                .addAction(R.drawable.amethyst, loadStringRes(Res.string.call_accept), acceptPendingIntent)

        notificationManager.notify("call", CALL_NOTIFICATION_ID, builder.build())
    }

    /**
     * Loads the caller's display name + profile picture and posts the
     * incoming-call notification. Invoked from the `CallSession` state
     * collector when the call manager transitions to `IncomingCall`.
     */
    suspend fun showIncomingCall(
        callerPubKey: String,
        context: Context,
    ) {
        val callerUser = LocalCache.getOrCreateUser(callerPubKey)
        val callerName = callerUser.toBestDisplayName()

        @Suppress("UNUSED_VARIABLE")
        val uri = "nostr:${callerPubKey.hexToByteArray().toNpub()}"

        val callerBitmap =
            callerUser.profilePicture()?.let { pictureUrl ->
                withContext(Dispatchers.IO) {
                    try {
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(pictureUrl)
                                .allowHardware(false)
                                .build()
                        val result = coil3.ImageLoader(context).execute(request)
                        (result.image?.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        null
                    }
                }
            }

        send(
            callerName = callerName,
            callerBitmap = callerBitmap,
            applicationContext = context,
        )
    }

    /** Cancels the incoming-call notification, if shown. */
    fun cancelIncomingCall(applicationContext: Context) {
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel("call", CALL_NOTIFICATION_ID)
    }
}
