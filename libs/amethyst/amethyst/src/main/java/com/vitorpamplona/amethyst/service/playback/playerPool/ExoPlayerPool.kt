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
package com.vitorpamplona.amethyst.service.playback.playerPool

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.vitorpamplona.amethyst.service.nowPlaying.InAppPlaybackRegistry
import com.vitorpamplona.amethyst.service.playback.PLAYBACK_DIAG_TAG
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@OptIn(UnstableApi::class)
class ExoPlayerPool(
    val builder: ExoPlayerBuilder,
    private val poolSize: Int,
    // Requested ceiling on paused-with-buffer players retained across releases. Each retained
    // player keeps its decoder and LoadControl buffer alive, which costs both memory and a
    // MediaCodec instance, so warm slots count against the same [poolSize] codec budget as
    // cold players (see [warmSlotsCap]). [warmSlotsFor] sizes it for the device.
    requestedWarmSlots: Int = MIN_WARM_SLOTS,
    // How short of memory the device is right now. Asked on the main thread each time a player
    // would go warm, so it must not block (see MemoryPressure). The system only trims a backgrounded
    // app (API 34+ delivers no foreground trim level), so this is what keeps a scrolling feed from
    // holding warm decoders while the device runs out.
    private val memoryLevel: () -> MemoryPressure.Level = { MemoryPressure.Level.NORMAL },
) {
    // Cap warm slots at poolSize-1 so there's always at least one slot available for a cold
    // (cleared) player; otherwise a feed full of unique URIs would starve the cold pool and
    // every new URI would force a fresh ExoPlayer build.
    private val warmSlotsCap = requestedWarmSlots.coerceAtMost((poolSize - 1).coerceAtLeast(0))

    // Idle players that have been stop()'d and clearMediaItems()'d — ready to be re-prepared
    // with any URI. Maintained as a FIFO so the oldest cleared instance is reused first.
    private val coldPool = ConcurrentLinkedQueue<ExoPlayer>()
    private val poolStartingSize = 3

    // Most-recent paused players, indexed by the mediaId of the MediaItem they still hold.
    // ArrayDeque is used as an LRU: head = oldest, tail = newest. Access is guarded by
    // [warmPoolLock] (a plain monitor, since both acquire and release callers run on the
    // service's main thread but we don't want to require the suspending [mutex] in acquire).
    private data class WarmPlayer(
        val mediaId: String,
        val player: ExoPlayer,
        // When it went warm, across every pool, so the oldest of both pools can be found.
        val order: Long = warmOrder.incrementAndGet(),
    )

    private val warmPool = ArrayDeque<WarmPlayer>(warmSlotsCap.coerceAtLeast(1))
    private val warmPoolLock = Any()

    init {
        livePools.add(this)
    }

    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("PlaybackService", "Caught exception: ${throwable.message}", throwable)
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    private val mutex = Mutex()

    // Guards against firing the warmup more than once if create() is called repeatedly
    // (e.g. on reconfiguration or when both pools share a startup hook).
    private val warmupStarted = AtomicBoolean(false)

    /**
     * Pre-warms the pool with [poolStartingSize] ExoPlayer instances on the main looper, yielding
     * between each build so the warmup is spread across frames instead of stalling the UI in one
     * burst. ExoPlayer must be constructed on the same thread that will operate it (the main
     * thread for this pool), so we cannot fan out across IO threads here. Idempotent — additional
     * calls are no-ops.
     */
    fun create(context: Context) {
        if (!warmupStarted.compareAndSet(false, true)) return
        scope.launch {
            while (coldPool.size < poolStartingSize) {
                coldPool.offer(builder.build(context))
                // Hand the frame back so an in-flight onGetSession / acquirePlayer / layout
                // pass isn't blocked behind the next build.
                yield()
            }
        }
    }

    /**
     * Acquire a player. When [preferredMediaId] matches a warm entry, returns that player intact
     * — it still holds its MediaItem and any populated LoadControl buffer, so the caller can
     * skip [androidx.media3.common.Player.setMediaItem] / [androidx.media3.common.Player.prepare]
     * and resume immediately. Falls back to a cold (cleared) player or a freshly built one.
     */
    fun acquirePlayer(
        context: Context,
        preferredMediaId: String? = null,
    ): ExoPlayer {
        if (preferredMediaId != null) {
            val warm = takeWarm(preferredMediaId)
            if (warm != null) {
                // A warm player can error *after* it was pooled clean — its decoder dies
                // asynchronously while paused (emulator surface reclaim, codec loss). releasePlayer
                // can't catch that (the error appears post-release), so it's caught here at acquire:
                // never hand a stale PlaybackException to a controller. Release the dead player and
                // fall through to a clean cold/fresh one — a guaranteed setMediaItem+prepare ahead.
                val error = warm.playerError
                if (error != null) {
                    Log.d("PlaybackService") { "ExoPlayerPool discarding errored warm player: $preferredMediaId (${error.errorCodeName})" }
                    PcmTapRegistry.unregisterPlayer(warm)
                    InAppPlaybackRegistry.forget(warm)
                    warm.release()
                    releaseDecoder()
                } else {
                    Log.d("PlaybackService") { "ExoPlayerPool warm hit: $preferredMediaId" }
                    // Already counted against the decoder budget for as long as it sat warm.
                    Log.d(PLAYBACK_DIAG_TAG) { "DECODERS warm-hit -> ${liveDecoders.get()} / budget $poolSize (unchanged)" }
                    return warm
                }
            }
        }
        ensureDecoderHeadroom()
        // Count only once the player exists — incrementing first leaves a phantom decoder on the
        // books if builder.build() throws.
        val player = coldPool.poll() ?: builder.build(context)
        acquireDecoder()
        return player
    }

    /**
     * Frees decoder headroom before a cold or freshly built player is handed out.
     *
     * Every player that still holds a prepared MediaItem — checked out or merely warm — owns a
     * MediaCodec instance, and devices advertise a hard ceiling on those (the emulator's
     * c2.goldfish.h264.decoder declares `concurrent-instances max="4"`). Past that ceiling
     * MediaCodec.start() fails with NO_MEMORY and the video surfaces as "can't load", so the
     * budget has to be enforced at acquisition rather than only at retention.
     *
     * Warm players are a scroll-back cache, so they are what gives way: demoting one to cold
     * stop()s it and releases its codec. This pool's own entries go first, then any other pool's
     * — [PlaybackService] keeps a separate pool for direct and for Tor-proxied traffic, and both
     * draw on the one per-process pile of decoders.
     */
    private fun ensureDecoderHeadroom() {
        while (liveDecoders.get() >= poolSize) {
            if (!evictOldestWarm() && !evictOldestWarmElsewhere()) return
        }
    }

    // acquireDecoder/releaseDecoder are the only two places the shared counter moves, so the
    // DECODERS trace always logs the value the mutation itself produced (a re-read could show a
    // sibling pool's concurrent move instead).
    private fun acquireDecoder() {
        val now = liveDecoders.incrementAndGet()
        Log.d(PLAYBACK_DIAG_TAG) { "DECODERS acquire -> $now / budget $poolSize" }
    }

    // Floored at zero. An under-count is the harmful direction: it lets ensureDecoderHeadroom
    // hand out more concurrent decoders than the device grants, which surfaces as MediaCodec
    // NO_MEMORY and "can't play this video". An over-count only costs the warm cache.
    private fun releaseDecoder() {
        val now = liveDecoders.updateAndGet { (it - 1).coerceAtLeast(0) }
        Log.d(PLAYBACK_DIAG_TAG) { "DECODERS release -> $now / budget $poolSize" }
    }

    private fun evictOldestWarm(): Boolean {
        val oldest = synchronized(warmPoolLock) { warmPool.removeFirstOrNull() } ?: return false
        Log.d("PlaybackService") { "ExoPlayerPool decoder-budget evict: ${oldest.mediaId}" }
        demoteToCold(oldest.player)
        return true
    }

    private fun evictOldestWarmElsewhere(): Boolean {
        livePools.forEach { pool ->
            if (pool !== this && pool.evictOldestWarm()) return true
        }
        return false
    }

    private fun takeWarm(mediaId: String): ExoPlayer? =
        synchronized(warmPoolLock) {
            // Iterate from the newest end so a duplicated URI returns the freshest player.
            val it = warmPool.listIterator(warmPool.size)
            while (it.hasPrevious()) {
                val entry = it.previous()
                if (entry.mediaId == mediaId) {
                    it.remove()
                    return@synchronized entry.player
                }
            }
            null
        }

    /**
     * Queues the return on this pool's single main-thread scope. Contract: returns queued before
     * [destroy] is called run before destroy's teardown — both serialize on the same scope and
     * [mutex] — so a caller may tear its sessions down synchronously and then destroy the pool
     * (see MediaSessionPool.destroy).
     */
    fun releasePlayerAsync(player: ExoPlayer) {
        scope.launch {
            releasePlayer(player)
        }
    }

    suspend fun releasePlayer(player: ExoPlayer) {
        mutex.withLock {
            if (player.isReleased) return@withLock

            // A player that errored out (decoder-init failure, decode error) must never be
            // returned to either pool. Kept warm, it hands the stale PlaybackException straight
            // back to the next acquire of the same URI — the "Can't play this video" flash traced
            // to warm-pool reuse. Its failed MediaCodec instance is also suspect. Drop it so the
            // pool builds a clean replacement on the next miss.
            val error = player.playerError
            if (error != null) {
                Log.d("PlaybackService") { "ExoPlayerPool dropping errored player: ${player.currentMediaItem?.mediaId} (${error.errorCodeName})" }
                PcmTapRegistry.unregisterPlayer(player)
                InAppPlaybackRegistry.forget(player)
                player.release()
                releaseDecoder()
                return@withLock
            }

            // Each warm player holds a decoder and its frame buffers (~30 MB measured), so the cache
            // shrinks with free memory: the old three slots when it is getting tight, none when the
            // system says it is low, counted across both pools. Above it the oldest warm player goes cold, one per
            // release: stop() releases a hardware decoder on this (main) thread, and dropping the
            // whole cache at once would stall it just when the device is struggling.
            val cap = warmCapFor(memoryLevel(), warmSlotsCap)

            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != null && cap > 0) {
                // Warm path: keep the player paused but loaded so a quick scroll-back to the
                // same video resumes from the existing buffer instead of re-fetching from disk
                // cache and re-priming the decoder.
                player.pause()
                val evicted = pushWarm(mediaId, player, cap)
                if (evicted != null) {
                    Log.d("PlaybackService") { "ExoPlayerPool warm evict: ${evicted.mediaId}" }
                    demoteToCold(evicted.player)
                }
                shedOneWarmAbove(cap)
                return@withLock
            }

            shedOneWarmAbove(cap)

            demoteToCold(player)
        }
    }

    private fun pushWarm(
        mediaId: String,
        player: ExoPlayer,
        cap: Int,
    ): WarmPlayer? =
        synchronized(warmPoolLock) {
            // If the same URI is already warm (rare — duplicate VideoView in another scroller),
            // drop the older entry so it can be demoted; the freshest copy wins.
            val duplicate = warmPool.indexOfFirst { it.mediaId == mediaId }
            val displaced =
                if (duplicate >= 0) {
                    warmPool.removeAt(duplicate)
                } else if (warmPool.size >= cap) {
                    warmPool.removeFirst()
                } else {
                    null
                }
            warmPool.addLast(WarmPlayer(mediaId, player))
            displaced
        }

    /**
     * Sends the oldest warm player of any pool cold when all pools together hold more than [cap];
     * at most one per call. The cap is for the whole process: the direct and Tor pools each keep a
     * warm cache, and a feed that mixes both routes would otherwise hold twice what RAM allows.
     */
    private fun shedOneWarmAbove(cap: Int) {
        if (livePools.sumOf { it.warmCount() } <= cap) return
        val pool = livePools.minByOrNull { it.oldestWarmOrder() ?: Long.MAX_VALUE } ?: return
        val oldest = synchronized(pool.warmPoolLock) { pool.warmPool.removeFirstOrNull() } ?: return
        Log.d("PlaybackService") { "ExoPlayerPool warm budget evict: ${oldest.mediaId} (cap $cap)" }
        pool.demoteToCold(oldest.player)
    }

    private fun warmCount() = synchronized(warmPoolLock) { warmPool.size }

    private fun oldestWarmOrder() = synchronized(warmPoolLock) { warmPool.firstOrNull()?.order }

    private fun demoteToCold(player: ExoPlayer) {
        if (player.isReleased) return
        player.pause()
        // stop() tears the renderers down, which is what actually hands the MediaCodec instance
        // back to the system — so this is the point where the player stops costing budget.
        player.stop()
        releaseDecoder()
        player.clearVideoSurface()
        player.clearMediaItems()

        // Clear any video quality overrides so the next video starts with Auto
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()

        // Total idle (cold + warm) must respect the device-derived poolSize cap so we don't
        // exceed the MediaCodec instance budget. Warm slots get first dibs; cold gets the rest.
        val warmSize = synchronized(warmPoolLock) { warmPool.size }
        val coldCap = (poolSize - warmSize).coerceAtLeast(0)
        if (coldPool.size < coldCap) {
            if (!coldPool.contains(player)) {
                coldPool.add(player)
            }
        } else {
            PcmTapRegistry.unregisterPlayer(player)
            InAppPlaybackRegistry.forget(player)
            player.release() // Release if pool is full.
        }
    }

    /**
     * Evicts all warm (paused-with-buffer) players back to the cold pool without touching active
     * players. Safe to call under memory pressure: warm players are idle; active players are
     * checked out via [acquirePlayer] and are not held in either pool.
     */
    fun releaseWarmPool() {
        val evicted =
            synchronized(warmPoolLock) {
                val copy = warmPool.toList()
                warmPool.clear()
                copy
            }
        if (evicted.isEmpty()) return
        scope.launch {
            mutex.withLock {
                evicted.forEach { demoteToCold(it.player) }
            }
        }
    }

    fun destroy() {
        scope
            .launch {
                mutex.withLock {
                    val warmSnapshot =
                        synchronized(warmPoolLock) {
                            val copy = warmPool.toList()
                            warmPool.clear()
                            copy
                        }
                    warmSnapshot.forEach {
                        PcmTapRegistry.unregisterPlayer(it.player)
                        InAppPlaybackRegistry.forget(it.player)
                        it.player.release()
                        releaseDecoder()
                    }
                    coldPool.forEach {
                        PcmTapRegistry.unregisterPlayer(it)
                        InAppPlaybackRegistry.forget(it)
                        it.release()
                    }
                    coldPool.clear()

                    poolFinishedTeardown(this@ExoPlayerPool)
                }
            }.invokeOnCompletion {
                scope.cancel()
            }
    }

    companion object {
        // What the pool kept before it was sized by the device, and its floor.
        private const val MIN_WARM_SLOTS = 3

        // Decoders kept out of the warm cache for the players in use: the videos on screen plus
        // the card the feed prepares ahead of the viewport.
        private const val DECODERS_IN_USE = 4

        private const val GIB = 1024L * 1024 * 1024

        /**
         * How many released players to keep paused with their buffer, for a device with [poolSize]
         * decoders and [totalRamBytes] of RAM.
         *
         * A feed hands a player back as soon as its card scrolls off, and with three slots a video
         * was dropped a few cards later: scrolling back, or a card prepared ahead that left and came
         * back, loaded it from nothing again. Every slot more saves one of those, but a warm player
         * keeps its decoder and that decoder's frame buffers: about 30 MB each on an SM-T220
         * (3 GB), measured as the Graphics line of `dumpsys meminfo` climbing from 221 MB at 8
         * decoders to ~470 MB at 16, while the device's MemAvailable fell from ~650 MB to ~260 MB.
         * So RAM, not the decoder count, sets the size; the decoder budget only caps it. Only the
         * 3 GB tier was measured, so the larger ones stay modest, and `memoryLevel` shrinks the
         * cache when the device runs short.
         */
        fun warmSlotsFor(
            poolSize: Int,
            totalRamBytes: Long,
        ): Int {
            val byRam =
                when {
                    totalRamBytes <= 0L -> MIN_WARM_SLOTS
                    totalRamBytes < 4 * GIB -> 4
                    totalRamBytes < 8 * GIB -> 5
                    else -> 6
                }
            return byRam.coerceAtMost(poolSize - DECODERS_IN_USE).coerceAtLeast(MIN_WARM_SLOTS)
        }

        /**
         * The warm players to keep across all pools at memory [level], for a pool sized to
         * [warmSlots]: all of them while memory is fine, the old three when it is getting tight,
         * none when the system says it is low.
         */
        fun warmCapFor(
            level: MemoryPressure.Level,
            warmSlots: Int,
        ): Int =
            when (level) {
                MemoryPressure.Level.NORMAL -> warmSlots
                MemoryPressure.Level.TIGHT -> warmSlots.coerceAtMost(MIN_WARM_SLOTS)
                MemoryPressure.Level.LOW -> 0
            }

        // Orders warm players across pools (see WarmPlayer.order).
        private val warmOrder = AtomicLong(0)

        // MediaCodec instances are a per-process resource, but PlaybackService builds one pool for
        // direct traffic and another for Tor-proxied traffic, so a per-pool budget would let the
        // app hold twice the device's decoder ceiling. Both counters below are therefore global.

        // Players currently holding a decoder: checked out, or warm (paused but still prepared).
        // Cold players have been stop()'d and own none.
        private val liveDecoders = AtomicInteger(0)

        // Every pool that hasn't been destroy()'d, so a pool starved of headroom can reclaim a
        // warm player from a sibling instead of overshooting the shared ceiling.
        private val livePools = ConcurrentLinkedQueue<ExoPlayerPool>()

        // A pool calls this only after decrementing its own players, so livePools.isEmpty()
        // means EVERY pool has finished tearing down — deregistering earlier would let the first
        // pool out see a sibling's still-counted decoders as drift. The last pool out resets the
        // shared counter; a non-zero value at that point is a genuine accounting leak (a player
        // acquired and never returned), worth seeing.
        private fun poolFinishedTeardown(pool: ExoPlayerPool) {
            livePools.remove(pool)
            if (livePools.isEmpty()) {
                val stranded = liveDecoders.getAndSet(0)
                if (stranded != 0) {
                    Log.w(PLAYBACK_DIAG_TAG) { "decoder accounting drift at teardown: $stranded" }
                }
            }
        }
    }
}
