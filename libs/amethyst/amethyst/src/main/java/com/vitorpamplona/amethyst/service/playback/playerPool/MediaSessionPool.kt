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
@file:OptIn(UnstableApi::class)

package com.vitorpamplona.amethyst.service.playback.playerPool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemCache
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SessionListener(
    val session: MediaSession,
    val playerListener: Player.Listener,
) {
    // Set once, by the retire funnel, making retireSession idempotent no matter how many
    // paths reach the same entry.
    val retired = AtomicBoolean(false)

    fun removeListeners() {
        session.player.removeListener(playerListener)
    }
}

/**
 * The goal for this class is to make sure all sessions and exoplayers are closed correctly.
 */
class MediaSessionPool(
    val exoPlayerPool: ExoPlayerPool,
    val dataSourceFactory: DataSource.Factory,
    val appContext: Context,
    // Ceiling on cached sessions. Each one holds a checked-out ExoPlayer, so on a device whose
    // decoder ceiling is lower than [MAX_CACHED_SESSIONS] this is what keeps the session cache
    // from pinning more MediaCodec instances than the hardware will grant.
    maxSessions: Int = MAX_CACHED_SESSIONS,
    val reset: (MediaSession, Boolean) -> Unit,
) {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("MediaSessionPool", "Caught exception: ${throwable.message}", throwable)
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    val globalCallback = MediaSessionCallback(this, appContext)

    // Last cleanup timestamp in nanos, guarded by CAS so concurrent releaseSession() calls
    // can't all win the time check and each launch a redundant scope.launch sweep.
    private val lastCleanupNs = AtomicLong(System.nanoTime())

    // The bitmap loader is stateless w.r.t. the session; a fresh allocation per session was
    // pure noise. ExoPlayer's DEFAULT_EXECUTOR_SERVICE is a process-wide singleton, the
    // dataSourceFactory is owned by the pool, and the appContext is already retained.
    // The init is in a separate function so the @OptIn lands on a real declaration —
    // applying it to a `by lazy` property doesn't propagate into the lambda body.
    private val sharedBitmapLoader by lazy { buildSharedBitmapLoader() }

    @OptIn(UnstableApi::class)
    private fun buildSharedBitmapLoader(): BitmapLoader {
        val decoder =
            DataSourceBitmapLoader
                .Builder(appContext)
                .setExecutorService(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get())
                .setDataSourceFactory(dataSourceFactory)
                // Subsampling only halves, so decoding straight to the ceiling can land at half the
                // size the session would have accepted (a 1080px image under a 900px ceiling decodes
                // to 540px). Decoding to just under 2x and letting MetadataArtworkBitmapLoader scale
                // precisely is the same recipe media3 uses for its own default loader.
                .setMaximumOutputDimension((mediaMetadataBitmapMaxSize() * 2 - 1).coerceAtLeast(1))
                .build()

        // Artwork comes from arbitrary nostr imeta URLs, so it routinely arrives far larger than the
        // platform's metadata bitmap ceiling, and android.media.session.MediaSession.setMetadata()
        // then re-scales it inside MediaMetadata.Builder.build(). media3 stores the *same* Bitmap
        // instance under both METADATA_KEY_DISPLAY_ICON and METADATA_KEY_ALBUM_ART
        // (LegacyConversions.convertToMediaMetadataCompat), so build() scales that one instance
        // twice. AOSP leaves the source alone, but on ROMs that recycle it while scaling the second
        // pass throws "cannot use a recycled source in createBitmap" on the main thread, inside a
        // Guava callback the app cannot intercept.
        //
        // media3 does size-limit artwork (SizeLimitedBitmapLoader), but it reads the ceiling from
        // Resources.getSystem() and falls back to the full display width when
        // config_mediaMetadataBitmapMaxSize can't be resolved by name, while the framework itself
        // reads it from the context that created the session. Capping it here, the same way the
        // framework measures it, keeps build() from scaling at all.
        return MetadataArtworkBitmapLoader(decoder, ::mediaMetadataBitmapMaxSize)
    }

    // getIdentifier() is a by-name lookup, so it is resolved once; the dimension itself is re-read
    // per load because it is a dp value and the app survives display-size changes without a restart.
    // The dimension is internal to the framework, so there is no R id to use instead.
    @Suppress("DiscouragedApi")
    private val metadataBitmapMaxSizeResId by lazy {
        appContext.resources.getIdentifier("config_mediaMetadataBitmapMaxSize", "dimen", "android")
    }

    /**
     * Mirrors how android.media.session.MediaSession derives its metadata bitmap ceiling: the
     * framework dimension config_mediaMetadataBitmapMaxSize, resolved from the app context so it
     * matches the value the platform compares against. Falls back to AOSP's 320dp default when the
     * (hidden, framework-internal) resource can't be resolved by name.
     */
    private fun mediaMetadataBitmapMaxSize(): Int {
        val resources = appContext.resources
        val resolved =
            if (metadataBitmapMaxSizeResId != 0) {
                try {
                    resources.getDimensionPixelSize(metadataBitmapMaxSizeResId)
                } catch (e: Resources.NotFoundException) {
                    Log.w("MediaSessionPool", "config_mediaMetadataBitmapMaxSize could not be read", e)
                    0
                }
            } else {
                0
            }
        return if (resolved > 0) resolved else (DEFAULT_METADATA_BITMAP_DP * resources.displayMetrics.density).toInt()
    }

    private val registry =
        SessionRegistry<SessionListener>(maxSessions.coerceIn(1, MAX_CACHED_SESSIONS)) { entry ->
            retireSession(entry)
        }

    /**
     * The one place a session's player goes back to the pool — the registry's onDropped and
     * newSession's failure unwind both land here.
     *
     * [SessionRegistry] removes an entry from both of its maps before signaling, so its paths
     * already deliver each entry at most once; the [SessionListener.retired] CAS keeps the funnel
     * idempotent regardless, because a double retire would double-return the player — an
     * under-count, the harmful direction (toward MediaCodec NO_MEMORY).
     *
     * Both orderings below are load-bearing, not tidiness:
     * - removeListeners() before release(), because releasing a session can fire
     *   onIsPlayingChanged, which would re-enter setPlaying while we are still inside
     *   the registry's entryRemoved callback.
     * - releasePlayerAsync() before release(), so a throw from session teardown cannot
     *   strand the player. releasePlayerAsync only queues the return, so the listener and
     *   session are detached before the queued release ever runs.
     */
    private fun retireSession(entry: SessionListener) {
        if (!entry.retired.compareAndSet(false, true)) return
        entry.removeListeners()
        exoPlayerPool.releasePlayerAsync(entry.session.player as ExoPlayer)
        entry.session.release()
    }

    internal fun setPlaying(
        id: String,
        isPlaying: Boolean,
    ) {
        registry.setPlaying(id, isPlaying)
    }

    @OptIn(UnstableApi::class)
    fun newSession(
        id: String,
        keepPlaying: Boolean,
        context: Context,
        // Best-effort affinity hint: when the pool still has a paused player carrying this
        // exact mediaId (matches MediaItem.mediaId, which is the videoUri), the warm player
        // is reused so the populated buffer survives. Null falls back to a cold acquire.
        preferredMediaId: String?,
    ): MediaSession {
        val player = exoPlayerPool.acquirePlayer(context, preferredMediaId)

        // newSession owns the player until registry.register() hands ownership over. Anything
        // that throws in between — reset(), or the PendingIntent in bindSessionActivity() —
        // would otherwise strand a counted player with no owner. The unwind routes through
        // retireSession so there is exactly one teardown sequence; removing a never-added
        // listener there is a no-op.
        var entry: SessionListener? = null
        var handedOff = false

        try {
            val mediaSession =
                MediaSession
                    // appContext, not the caller's: the platform MediaSession reads its metadata
                    // bitmap ceiling off the resources of whatever context builds it, and
                    // mediaMetadataBitmapMaxSize() below caps artwork using the app context's. A
                    // context on a different configuration (a resized window, a secondary display)
                    // would set a smaller ceiling than the cap, putting the framework back into
                    // scaling artwork it was handed. It also keeps a pooled, long-lived session
                    // from holding on to an Activity.
                    .Builder(appContext, player)
                    .apply {
                        setBitmapLoader(sharedBitmapLoader)
                        setId(id)
                        setCallback(globalCallback)
                    }.build()

            val listener = MediaSessionExoPlayerConnector(mediaSession, this)
            entry = SessionListener(mediaSession, listener)

            mediaSession.player.addListener(listener)

            reset(mediaSession, keepPlaying)

            // Warm-pool fast path acquires a player that still holds its MediaItem, so the
            // client side skips setMediaItem (see GetVideoController) and onAddMediaItems
            // never fires for this fresh session — leaving the notification's tap target
            // unset. Re-bind it from the player's current item so tapping the playback
            // notification opens the originating nostr URI.
            bindSessionActivity(mediaSession, mediaSession.player.currentMediaItem)

            // Past this point the registry owns the session. register() inserts the entry before
            // it can fire an eviction's onDropped, so even if register() itself throws (a
            // displaced entry's retire failing), the new entry is already registered and a later
            // sweep retires it — the catch must not also return this player, which would
            // double-return it.
            handedOff = true
            registry.register(mediaSession.id, entry)

            return mediaSession
        } catch (e: Throwable) {
            if (!handedOff) {
                entry?.let { retireSession(it) } ?: exoPlayerPool.releasePlayerAsync(player)
            }
            throw e
        }
    }

    fun bindSessionActivity(
        session: MediaSession,
        mediaItem: MediaItem?,
    ) {
        val callbackUri = mediaItem?.mediaMetadata?.extras?.getString(MediaItemCache.EXTRA_CALLBACK_URI) ?: return
        session.setSessionActivity(
            PendingIntent.getActivity(
                appContext,
                0,
                Intent(Intent.ACTION_VIEW, callbackUri.toUri(), appContext, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    fun releaseSession(session: MediaSession) {
        // The registry removes the entry and then signals; retireSession does the teardown —
        // explicitly, whether the session is idle or playing, never by relying on a cache
        // removal firing a callback (a no-op once the entry was evicted while playing).
        //
        // An unknown id drops nothing: not in the registry means already retired (retireSession
        // released it) or never owned (newSession's failure path), and releasing such a session
        // again would be a double-release.
        registry.drop(session.id)
        cleanupUnused()
    }

    fun cleanupUnused() {
        val now = System.nanoTime()
        val previous = lastCleanupNs.get()
        if (now - previous < CLEANUP_INTERVAL_NS) return
        // CAS so only one caller actually launches the sweep when many releases fire at once.
        if (!lastCleanupNs.compareAndSet(previous, now)) return
        scope.launch {
            registry.idleSnapshot().forEach {
                if (it.session.connectedControllers.isEmpty()) {
                    releaseSession(it.session)
                }
            }
        }
    }

    fun destroy() {
        // Synchronous on purpose: retireSession uses the non-suspending releasePlayerAsync, and
        // releasePlayerAsync's contract guarantees returns queued here run before the pool's own
        // teardown — so every player is back before exoPlayerPool.destroy() sweeps.
        try {
            registry.dropAll()
        } finally {
            // The player pool must tear down even if a retire throws mid-sweep; skipping it
            // would leak every pooled player, not just the session that failed.
            exoPlayerPool.destroy()
            scope.cancel()
        }
    }

    fun getSession(
        id: String,
        keepPlaying: Boolean,
        context: Context,
        preferredMediaId: String? = null,
    ): MediaSession {
        registry.get(id)?.let { return it.session }

        return newSession(id, keepPlaying, context, preferredMediaId)
    }

    fun playingContent() = registry.playingEntries()

    // Resolves the session whether it is idle or playing.
    fun getSession(id: String) = registry.get(id)?.session

    class MediaSessionCallback(
        val pool: MediaSessionPool,
        val appContext: Context,
    ) : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            // set up return call when clicking on the Notification bar
            pool.bindSessionActivity(mediaSession, mediaItems.firstOrNull())

            return Futures.immediateFuture(mediaItems)
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            pool.releaseSession(session)
        }
    }

    class MediaSessionExoPlayerConnector(
        val mediaSession: MediaSession,
        val pool: MediaSessionPool,
    ) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Moves the one existing entry between tiers. A session must never have more than
            // one live wrapper: dropping one wrapper would hand the player back while another
            // still pointed at the live session.
            pool.setPlaying(mediaSession.id, isPlaying)
        }
    }

    companion object {
        private val CLEANUP_INTERVAL_NS = TimeUnit.MINUTES.toNanos(1)

        // Roughly how many videos can share a screen at once. Acts as the upper bound only —
        // a device that advertises fewer concurrent decoders than this caps lower.
        const val MAX_CACHED_SESSIONS = 10

        // AOSP default for config_mediaMetadataBitmapMaxSize, used when the framework resource
        // can't be resolved by name on a given ROM.
        private const val DEFAULT_METADATA_BITMAP_DP = 320
    }
}
