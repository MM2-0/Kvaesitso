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
package com.vitorpamplona.amethyst.service.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.ui.call.CallActivity
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "CallForegroundService"

class CallForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "amethyst_call_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.vitorpamplona.amethyst.CALL_START"
        const val ACTION_STOP = "com.vitorpamplona.amethyst.CALL_STOP"
        const val ACTION_UPDATE = "com.vitorpamplona.amethyst.CALL_UPDATE"
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_IS_VIDEO = "is_video"
        const val EXTRA_IS_SCREEN_SHARING = "is_screen_sharing"
        const val EXTRA_STATUS_TEXT = "status_text"
        const val EXTRA_IS_RINGING = "is_ringing"
        private const val HANGUP_REQUEST_CODE = 0x70001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Amethyst.instance.callSession.setActive(true)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Unknown"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                val isScreenSharing = intent.getBooleanExtra(EXTRA_IS_SCREEN_SHARING, false)
                val isRinging = intent.getBooleanExtra(EXTRA_IS_RINGING, false)
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT)
                val notification = buildNotification(peerName, statusText)

                try {
                    val fgsType =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                            if (!isRinging) {
                                val hasAudioPermission =
                                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                val hasCameraPermission =
                                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                                        PackageManager.PERMISSION_GRANTED

                                if (hasAudioPermission) {
                                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                                }
                                if (isVideo && hasCameraPermission) {
                                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                                }
                                if (isScreenSharing) {
                                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                                }
                            }
                            type
                        } else {
                            0
                        }
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot start foreground service, falling back", e)
                    try {
                        val fallbackType =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                                if (isScreenSharing) {
                                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                                }
                                type
                            } else {
                                0
                            }
                        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fallbackType)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Foreground service start failed entirely", e2)
                        if (intent.action == ACTION_START) {
                            stopSelf()
                        }
                    }
                }
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Called when the user swipes the app's task away from Recents. The
     * manifest declares `stopWithTask="false"` so this callback fires
     * BEFORE the service is killed, giving us a window to publish the
     * hangup/reject signaling event synchronously.
     *
     * `runBlocking` is safe here because `onTaskRemoved` runs on the
     * main thread with a 5-second ANR window and the network publish
     * (via Nostr relay WebSocket) completes well within that. We cap
     * it at 3 seconds as a safety net.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val removed = rootIntent?.component?.className
        Log.d(TAG) { "onTaskRemoved root=$removed" }

        // Only the call's own task going away means the user dismissed the call. This callback
        // fires for *every* task of the app, and MainActivity lives in a separate one (it is
        // `singleInstance`, and CallActivity is launched with FLAG_ACTIVITY_NEW_TASK). Android
        // reclaims that backgrounded MainActivity task on its own while a call is running —
        // notably a few hundred ms after CallActivity enters picture-in-picture on HOME — and
        // hanging up on it ended calls the user never touched.
        //
        // A null root intent leaves the source unknown; treat it as a dismissal so a genuinely
        // swiped-away app can't leave a call running with no UI to end it.
        if (removed != null && removed != CallActivity::class.java.name) {
            super.onTaskRemoved(rootIntent)
            return
        }

        publishHangupBlocking()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // If onTaskRemoved already published the hangup, this is a no-op
        // because CallManager.hangup() transitions to Ended and a second
        // hangup() from Ended state returns immediately.
        publishHangupBlocking()
        Amethyst.instance.callSession.setActive(false)
        super.onDestroy()
    }

    /**
     * Synchronously publishes a hangup or reject event if a call is
     * active. Uses [runBlocking] with a 3-second timeout so we never
     * block the main thread past the ANR window.
     */
    private fun publishHangupBlocking() {
        val manager = CallSessionBridge.callManager ?: return
        val state = manager.state.value
        try {
            runBlocking {
                withTimeoutOrNull(3_000L) {
                    when (state) {
                        is CallState.IncomingCall -> {
                            manager.rejectCall()
                        }

                        is CallState.Offering,
                        is CallState.Connecting,
                        is CallState.Connected,
                        -> {
                            manager.hangup()
                        }

                        else -> { /* nothing to do */ }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "publishHangupBlocking failed", e)
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.call_ongoing),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.call_ongoing_description)
            }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        peerName: String,
        statusText: String? = null,
    ): Notification {
        val openCallIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val hangupIntent =
            PendingIntent.getBroadcast(
                this,
                HANGUP_REQUEST_CODE,
                Intent(this, CallNotificationReceiver::class.java).apply {
                    action = CallNotificationReceiver.ACTION_HANGUP_CALL
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val contentText = statusText ?: getString(R.string.call_with, peerName)

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.amethyst)
            .setOngoing(true)
            .setContentIntent(openCallIntent)
            .addAction(R.drawable.ic_call_end, getString(R.string.call_hangup), hangupIntent)
            .build()
    }
}
