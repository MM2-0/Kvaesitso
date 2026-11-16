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
package com.vitorpamplona.amethyst.service.nowPlaying

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSource
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What another app on this device is playing, as reported by its media session. */
object OtherAppsPlaybackRegistry {
    private val playing = MutableStateFlow<NowPlaying?>(null)

    val flow: StateFlow<NowPlaying?> = playing

    fun update(track: NowPlaying?) {
        // Many players report their position every second; only a real change goes downstream.
        if (track != null && track.isSameMoment(playing.value)) return
        playing.value = track
    }

    private val wantedState = MutableStateFlow<Boolean?>(null)

    /** Whether the logged-in account shares other apps; null until it and its settings load. */
    val wanted: StateFlow<Boolean?> = wantedState

    fun setWanted(value: Boolean) {
        wantedState.value = value
    }
}

/**
 * Reads the media sessions of other apps (Spotify, YouTube Music, podcast players, ...) to share
 * what the user is listening to as a NIP-38 music status.
 *
 * Android only hands out other apps' sessions to an enabled notification listener, which is why
 * this is one: it never reads or posts notifications. The user grants it in the system's
 * "Notification access" screen ([openAccessSettings]); until then the system never binds it.
 * Whether anything is published is still up to the account's now-playing settings.
 *
 * Once granted, the system keeps the listener bound for good, which holds the app's process out of
 * the cache and restarts it after a reboot or a force stop. So it unbinds itself ([requestUnbind])
 * whenever the logged-in account does not share other apps' playback, and [setWanted] asks for it
 * back when one does. Disabling the component would not do: the system does not bind a granted
 * listener again when it is re-enabled (seen on Android 17).
 *
 * Runs in the main process (it is not declared in `:napplet`), where the account lives. The
 * system may start that process just to bind this listener; AppModules then logs the saved
 * account in on its own, as it does on every start, so the publisher has it.
 */
class OtherAppsNowPlayingService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())

    // A failure here (the system service dying, a player's session throwing) must not crash the app.
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, e -> Log.w("OtherAppsNowPlaying", "Listener failed", e) },
        )
    private var sessionManager: MediaSessionManager? = null
    private val watched = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    // App labels come from PackageManager, an IPC; players report state up to once a second.
    private val labels = mutableMapOf<String, String>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            watch(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.coroutineContext.cancelChildren()
        scope.launch {
            // The system may have bound this right after a reboot or update, before the account
            // loaded: only watch sessions once sharing is known to be on.
            val wanted = OtherAppsPlaybackRegistry.wanted
            if (wanted.first { it != null } == true) {
                watchSessions()
                wanted.first { it == false }
            }
            // Let go, so the process can be cached.
            Log.i("OtherAppsNowPlaying", "Not sharing other apps' playback: unbinding")
            try {
                requestUnbind()
            } catch (e: RuntimeException) {
                // SecurityException, or the system's RemoteException rethrown.
                Log.w("OtherAppsNowPlaying", "Could not unbind", e)
            }
        }
    }

    private fun watchSessions() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = manager

        try {
            val component = ComponentName(this, OtherAppsNowPlayingService::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            watch(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Access was revoked between the bind and this call.
            Log.w("OtherAppsNowPlaying", "Notification access is not granted", e)
        }
    }

    override fun onListenerDisconnected() {
        scope.coroutineContext.cancelChildren()
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        unwatchAll()
        OtherAppsPlaybackRegistry.update(null)
        super.onListenerDisconnected()
        // Sharing came back on while the unbind was still in flight: that rebind request reached a
        // listener that was still bound, so ask again.
        if (OtherAppsPlaybackRegistry.wanted.value == true) rebind(this)
    }

    override fun onDestroy() {
        scope.cancel()
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        unwatchAll()
        OtherAppsPlaybackRegistry.update(null)
        super.onDestroy()
    }

    private fun watch(controllers: List<MediaController>) {
        val others = controllers.filter { it.packageName != packageName }
        val keys = others.mapTo(mutableSetOf()) { it.sessionToken }

        watched.keys.filter { it !in keys }.forEach { key ->
            watched.remove(key)?.let { (controller, callback) -> controller.unregisterCallback(callback) }
        }

        others.forEach { controller ->
            val key = controller.sessionToken
            if (key !in watched) {
                val callback =
                    object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()

                        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()

                        override fun onSessionDestroyed() {
                            watched.remove(key)?.let { (it, cb) -> it.unregisterCallback(cb) }
                            refresh()
                        }
                    }
                controller.registerCallback(callback, handler)
                watched[key] = controller to callback
            }
        }

        refresh()
    }

    private fun unwatchAll() {
        watched.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        watched.clear()
    }

    /** The first session that is playing, in the system's priority order. */
    private fun refresh() {
        val track = watched.values.firstNotNullOfOrNull { (controller, _) -> toNowPlaying(controller) }
        OtherAppsPlaybackRegistry.update(track)
    }

    private fun toNowPlaying(controller: MediaController): NowPlaying? {
        val state = controller.playbackState ?: return null
        if (state.state != PlaybackState.STATE_PLAYING) return null

        val metadata = controller.metadata ?: return null
        val title =
            metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.ifBlank { null }
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.ifBlank { null }
                ?: return null
        val artist =
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.ifBlank { null }
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.ifBlank { null }
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.ifBlank { null }

        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
        val speed = state.playbackSpeed.takeIf { it > 0f } ?: 1f
        val position =
            if (state.lastPositionUpdateTime > 0) {
                state.position + ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * speed).toLong()
            } else {
                state.position
            }

        return NowPlaying(
            title = title,
            artist = artist,
            source = NowPlayingSource.OtherApp(controller.packageName, appLabel(controller.packageName)),
            endsAt = NowPlaying.endsAt(TimeUtils.now(), duration, position, speed),
        )
    }

    private fun appLabel(packageName: String): String =
        labels.getOrPut(packageName) {
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        }

    companion object {
        fun hasAccess(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /**
         * Records whether the logged-in account shares other apps. A bound listener unbinds itself
         * when this turns false; turning it true undoes that [requestUnbind], so the system binds
         * the listener again, now if access is granted, else as soon as it is.
         */
        fun setWanted(
            context: Context,
            wanted: Boolean,
        ) {
            OtherAppsPlaybackRegistry.setWanted(wanted)
            if (wanted) rebind(context)
        }

        /** Undoes a [requestUnbind]. Does nothing while the listener is bound. */
        private fun rebind(context: Context) {
            try {
                NotificationListenerService.requestRebind(ComponentName(context, OtherAppsNowPlayingService::class.java))
            } catch (e: RuntimeException) {
                Log.w("OtherAppsNowPlaying", "Could not rebind", e)
            }
        }

        /** Opens the system screen where the user grants or revokes access to this listener. */
        fun openAccessSettings(context: Context) {
            val component = ComponentName(context, OtherAppsNowPlayingService::class.java).flattenToString()
            // Straight to this app's toggle where the system supports it, else the list of listeners.
            val intents =
                listOfNotNull(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
                    } else {
                        null
                    },
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                )

            for (intent in intents) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (e: ActivityNotFoundException) {
                    Log.w("OtherAppsNowPlaying", "No activity for ${intent.action}", e)
                }
            }
        }
    }
}
