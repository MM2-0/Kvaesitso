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
package com.vitorpamplona.amethyst.service.nests

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.NestAudioFocusBus
import com.vitorpamplona.amethyst.commons.viewmodels.NestAudioFocusState
import com.vitorpamplona.amethyst.commons.viewmodels.NestNetworkChangeBus
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.Log

/**
 * Process-anchor for an active audio-room session. Holds a partial wake-lock
 * and a foreground notification so playback continues when the screen is off
 * or the user briefly leaves the app.
 *
 * Scope decisions:
 *   - The service does NOT own the MoQ session / decoder / player. Those
 *     live in `NestViewModel`. This service exists to keep the process
 *     alive while audio is in flight; the VM still drives all wire activity.
 *   - Foreground type is `mediaPlayback`; if the user starts broadcasting,
 *     the screen calls [promoteToMicrophone] which re-`startForeground`s
 *     with `mediaPlayback|microphone` (Android 14+ requires the explicit
 *     microphone type while the mic is open).
 *   - One service per process — multiple audio-room screens are not a real
 *     scenario today (there's one room per screen and the room screen is a
 *     full-screen activity). The service uses a shared notification id so
 *     start/promote/stop is idempotent.
 */
class NestForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var promoted = false
    private var audioFocusRequest: AudioFocusRequest? = null

    /**
     * Logs route changes (Bluetooth headset attach/detach, wired
     * headset, USB audio, speakerphone) so field reports of
     * "audio cut out when I plugged in headphones" can be correlated
     * with a concrete device-add / device-remove event.
     *
     * Doesn't drive playback decisions — Android's AudioTrack +
     * AudioRecord auto-route to whichever device the OS treats as
     * active, so the brief silence on a route swap is unavoidable
     * without going through the (intrusive) `MODE_IN_COMMUNICATION` +
     * `setCommunicationDevice` flow that this service deliberately
     * avoids. v1 ships observability only; future work could pause
     * playback briefly across a route change to mask the swap.
     */
    private val deviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                addedDevices?.forEach { dev ->
                    Log.i("NestAudio") {
                        "audio device added: type=${dev.type} name='${dev.productName}' " +
                            "isSink=${dev.isSink} isSource=${dev.isSource}"
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                removedDevices?.forEach { dev ->
                    Log.i("NestAudio") {
                        "audio device removed: type=${dev.type} name='${dev.productName}' " +
                            "isSink=${dev.isSink} isSource=${dev.isSource}"
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        Amethyst.instance.nestsSession.setActive(true)
        createNotificationChannel()
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "amethyst:audio-room")
                .apply { setReferenceCounted(false) }
        requestAudioFocus()
        registerAudioDeviceCallback()
        registerNetworkCallback()
    }

    private fun registerAudioDeviceCallback() {
        runCatching {
            val mgr = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // null Handler → callback runs on the main looper, which is
            // the cheapest path for "log a line" handlers like this.
            mgr.registerAudioDeviceCallback(deviceCallback, null)
        }
    }

    private fun unregisterAudioDeviceCallback() {
        runCatching {
            val mgr = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mgr.unregisterAudioDeviceCallback(deviceCallback)
        }
    }

    /**
     * Track the current default network so the NetworkCallback can
     * distinguish "first onAvailable after register" (no-op) from "the
     * default network just changed under us" (publish). Volatile because
     * the callback fires on a binder thread; the publish path is
     * lock-free via [NestNetworkChangeBus].
     *
     * `seenInitialNetwork` is the registration-time suppression flag —
     * set true on the first onAvailable and never cleared. The earlier
     * shape used `previous != null` as the suppression check, which
     * also suppressed the WiFi-loss-then-cellular-available case
     * (`onLost` clears [currentDefaultNetwork] back to null, then the
     * follow-up `onAvailable` looks identical to the registration
     * callback). That broke the most important scenario this whole
     * code path exists for — a clean Wi-Fi ↔ cellular handover.
     */
    @Volatile private var currentDefaultNetwork: Network? = null

    @Volatile private var seenInitialNetwork: Boolean = false

    /**
     * Listens for the device's default-network changing (Wi-Fi ↔
     * cellular handover, plane mode toggle, hotspot swap) and signals
     * every active [com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel]
     * to recycle its QUIC session. Without this nudge the QUIC
     * connection sitting on the now-dead socket would have to wait
     * for its PTO (~30 s) before the wrapper notices — a long
     * audible silence on every handover.
     *
     * The callback also fires once right after registration with the
     * current default network — we suppress that emission so the VM
     * doesn't recycle on every service start.
     */
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previous = currentDefaultNetwork
                currentDefaultNetwork = network
                if (!seenInitialNetwork) {
                    // Registration-time callback — fires once with the
                    // currently-active default network. Suppress so the
                    // VM doesn't recycle on every service start.
                    seenInitialNetwork = true
                    return
                }
                if (previous != network) {
                    Log.i("NestNet") {
                        "default network changed ($previous → $network), recycling QUIC sessions"
                    }
                    NestNetworkChangeBus.publish()
                }
            }

            override fun onLost(network: Network) {
                if (currentDefaultNetwork == network) {
                    // We've lost the current default. Don't publish here —
                    // the next onAvailable (with the replacement network)
                    // will, and recycling NOW means the wrapper would try
                    // to handshake on no network at all and fail-then-
                    // backoff. Just clear so the next onAvailable is
                    // recognised as a change.
                    currentDefaultNetwork = null
                }
            }
        }

    private fun registerNetworkCallback() {
        runCatching {
            val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            mgr.registerDefaultNetworkCallback(networkCallback)
        }
    }

    private fun unregisterNetworkCallback() {
        runCatching {
            val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            mgr.unregisterNetworkCallback(networkCallback)
        }
        currentDefaultNetwork = null
        seenInitialNetwork = false
    }

    /**
     * Request audio focus for the duration of the audio-room session
     * and route the system's focus-change events into [NestAudioFocusBus]
     * so every active [com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel]
     * can react.
     *
     * Why we actually handle the focus change (vs the previous no-op
     * listener): the platform only auto-ducks streams that opted into
     * auto-ducking (CONTENT_TYPE_MUSIC, etc.) — a `CONTENT_TYPE_SPEECH`
     * stream is left alone, so without a real listener an inbound phone
     * call would mix on top of the room audio and a Maps voice prompt
     * would be inaudible against an active speaker. The bus carries
     * the translated state to the VM, which silences the listener
     * playback and the broadcast mic for the duration of the loss.
     *
     * Matches the playback `AudioAttributes` we set on `AudioTrack`
     * in `AudioTrackPlayer` (USAGE_MEDIA + CONTENT_TYPE_SPEECH) so
     * the focus request applies to the same stream the audio
     * actually renders on.
     */
    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        val mgr = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs =
            android.media.AudioAttributes
                .Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    NestAudioFocusBus.publish(translateFocusChange(focusChange))
                }.build()
        // Strict GRANTED check: anything else — FAILED (active call
        // blocking us), DELAYED (we set [setAcceptsDelayedFocusGain]
        // false so this shouldn't happen but treat it as not-granted
        // defensively), or a swallowed exception from runCatching —
        // means we don't actually own playback yet, so the VM must
        // start muted. The earlier shape used `!= FAILED` which fell
        // through to Granted on `null` (exception path) and on
        // DELAYED (= 2), playing audio over a call that the OS hadn't
        // released to us.
        val result = runCatching { mgr.requestAudioFocus(request) }.getOrNull()
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            NestAudioFocusBus.publish(NestAudioFocusState.Granted)
        } else {
            NestAudioFocusBus.publish(NestAudioFocusState.TransientLoss)
        }
        audioFocusRequest = request
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        val mgr = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { mgr.abandonAudioFocusRequest(request) }
        audioFocusRequest = null
        // Reset to Granted so a future foreground-service start that
        // happens before the next focus request lands doesn't inherit
        // a stale "muted because we lost focus" state.
        NestAudioFocusBus.publish(NestAudioFocusState.Granted)
    }

    private fun translateFocusChange(focusChange: Int): NestAudioFocusState =
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            -> NestAudioFocusState.Granted

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> NestAudioFocusState.TransientLoss

            AudioManager.AUDIOFOCUS_LOSS -> NestAudioFocusState.Loss

            // Unknown future codes: be defensive and treat as Granted
            // so a vendor-specific extension can't silently mute the
            // room forever.
            else -> NestAudioFocusState.Granted
        }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Always call startForeground first — Android's contract is that
        // every onStartCommand for a service started via
        // startForegroundService MUST call startForeground within 5 s, on
        // every invocation (audit Android #8). Even the STOP path needs a
        // foreground state before stopForeground can demote it cleanly.
        //
        // Three startForegroundService entry points reach this code:
        //   - `startListening(context)`  → intent != null, action == null
        //     The wrapper is in listener-only mode; the service must
        //     foreground with MEDIA_PLAYBACK type (no MICROPHONE).
        //   - `promoteToMicrophone(ctx)` → intent.action == ACTION_PROMOTE_TO_MIC
        //   - OS-restart of a START_STICKY service → intent == null
        //     Preserve whatever foreground type we last ran with.
        //
        // The explicit demote branch (`else -> false`) is load-bearing:
        // when the user leaves the stage, `startListening` is called to
        // demote, AudioRecord is being released, and asking the OS for
        // FOREGROUND_SERVICE_TYPE_MICROPHONE here throws SecurityException
        // on Android 14+. The runCatching below would then `stopSelf()`
        // without ever calling `startForeground`, tripping
        // ForegroundServiceDidNotStartInTimeException.
        val mic =
            when {
                intent == null -> promoted
                intent.action == ACTION_PROMOTE_TO_MIC -> true
                else -> false
            }
        runCatching { startForegroundWithType(includeMic = mic) }
            .onFailure {
                // foreground-not-allowed (mic-only restrictions, etc.) —
                // bail cleanly rather than leak a wake-lock / partial state.
                stopSelf()
                return START_NOT_STICKY
            }

        // A null intent is the OS restarting the service after the process died. The room it served
        // (its ViewModel, the composable that stops this service) died with that process, so there is
        // nothing to keep alive: running on would hold audio focus and a wake lock for a room that no
        // longer exists, with nothing left to stop it.
        if (intent?.action == ACTION_STOP || intent == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        // Not sticky for the same reason: only a live room can restart it.
        return START_NOT_STICKY
    }

    private fun startForegroundWithType(includeMic: Boolean) {
        promoted = includeMic
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type =
                if (includeMic) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, NestForegroundService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val title =
            if (promoted) {
                stringRes(this, R.string.nest_notification_broadcasting)
            } else {
                stringRes(this, R.string.nest_notification_listening)
            }

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(stringRes(this, R.string.nest_notification_text))
            .setSmallIcon(R.drawable.amethyst)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, stringRes(this, R.string.nest_notification_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    stringRes(this, R.string.nest_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = stringRes(this@NestForegroundService, R.string.nest_notification_channel_description)
                    setShowBadge(false)
                },
            )
        }
    }

    override fun onDestroy() {
        Amethyst.instance.nestsSession.setActive(false)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        abandonAudioFocus()
        unregisterAudioDeviceCallback()
        unregisterNetworkCallback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "nest_foreground"
        private const val NOTIFICATION_ID = 0xA0D10
        private const val ACTION_PROMOTE_TO_MIC = "com.vitorpamplona.amethyst.nest.PROMOTE_MIC"
        private const val ACTION_STOP = "com.vitorpamplona.amethyst.nest.STOP"

        // 4-hour hard cap — long enough for typical audio-room sessions
        // (2-3 h podcasts / panels) plus headroom, short enough to release
        // the device from a stuck connection that fails to detect a
        // network drop. Refresh on each ACTION_PROMOTE_TO_MIC arrival.
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000

        /** Start the service in listener-only foreground mode. Idempotent. */
        fun startListening(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NestForegroundService::class.java),
            )
        }

        /**
         * Re-`startForeground` with mediaPlayback + microphone type while the
         * user is broadcasting. Required by Android 14's split foreground-type
         * permission model.
         */
        fun promoteToMicrophone(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NestForegroundService::class.java).apply {
                    action = ACTION_PROMOTE_TO_MIC
                },
            )
        }

        /** Stop and remove the foreground notification. Idempotent. */
        fun stop(context: Context) {
            context.stopService(Intent(context, NestForegroundService::class.java))
        }
    }
}
