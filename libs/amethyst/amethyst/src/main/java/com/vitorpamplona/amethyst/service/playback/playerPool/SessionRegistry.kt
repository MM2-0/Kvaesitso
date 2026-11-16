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

import androidx.collection.LruCache

/**
 * Owns which sessions are reachable, and is the only place that decides one has been dropped.
 *
 * Two tiers: an LRU of idle entries and a map of ids that are currently playing. A playing entry
 * stays in [idle] as well — [playing] only makes an eviction non-dropping. That is deliberate: it
 * matches today's behaviour exactly (a playing session keeps consuming a cache slot until it ages
 * out), and it is what keeps the replacement guard in [entryRemoved] load-bearing rather than dead
 * code.
 *
 * Generic over the entry type so the bookkeeping — the part that has actually been wrong — is unit
 * testable on the JVM with no Android or media3 objects involved.
 *
 * Uses `androidx.collection.LruCache` rather than `android.util.LruCache`: the latter is stubbed
 * out under `unitTests.isReturnDefaultValues = true`, which would make every test here vacuous.
 * The contract is otherwise identical, including `entryRemoved` firing outside the lock.
 *
 * Not thread safe. Every caller runs on the main thread (see MediaSessionPool).
 */
internal class SessionRegistry<T : Any>(
    maxIdle: Int,
    private val onDropped: (T) -> Unit,
) {
    private val playing = mutableMapOf<String, T>()

    // Set while an explicit drop is tearing an entry out of [idle], so the resulting entryRemoved
    // callback doesn't signal a second time for the same drop.
    //
    // Resetting to false in `finally` (rather than restoring a saved value) is sound because the
    // guarded region cannot nest: onDropped is never invoked while the flag is set, so nothing
    // inside it can re-enter drop()/dropAll(). That non-nesting property is the invariant — not
    // single-threadedness — and a future edit that called onDropped inside the guarded region
    // would break it.
    private var suppressDropSignal = false

    private val idle =
        object : LruCache<String, T>(maxIdle) {
            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: T,
                newValue: T?,
            ) {
                // A replacement is not a drop: re-putting the same entry (pause -> idle) fires this
                // with newValue === oldValue, and retiring then would kill the session we are
                // keeping. `newValue == null` needs no separate test — V is non-null by type, so a
                // null newValue can never be identical to oldValue.
                if (suppressDropSignal) return
                if (newValue !== oldValue && !playing.containsKey(key)) onDropped(oldValue)
            }
        }

    fun register(
        id: String,
        entry: T,
    ) {
        idle.put(id, entry)
    }

    fun setPlaying(
        id: String,
        isPlaying: Boolean,
    ) {
        val entry = get(id) ?: return
        if (isPlaying) {
            playing[id] = entry
        } else {
            // Re-inserts if it was evicted from idle while playing. Must happen before the playing
            // entry is cleared, so the eviction this put may trigger still sees the pin.
            idle.put(id, entry)
            playing.remove(id)
        }
    }

    fun get(id: String): T? = playing[id] ?: idle.get(id)

    // snapshot() already materializes a detached map, so its values need no second copy.
    fun idleSnapshot(): Collection<T> = idle.snapshot().values

    // Detached, like idleSnapshot(). Handing out playing.values would be a live view, and a caller
    // that iterated it while a drop fired would get a ConcurrentModificationException. A type whose
    // job is owning reachability should not ship that footgun.
    fun playingEntries(): List<T> = playing.values.toList()

    /**
     * Explicit release. Drops whether or not the session is playing, and does not rely on
     * [idle]'s removal firing the callback — that reliance is the leak, because it silently does
     * nothing when the entry was already evicted while playing.
     */
    fun drop(id: String): T? {
        val playingEntry = playing.remove(id)
        suppressDropSignal = true
        val idleEntry =
            try {
                idle.remove(id)
            } finally {
                suppressDropSignal = false
            }
        val entry = playingEntry ?: idleEntry ?: return null
        onDropped(entry)
        return entry
    }

    /** Teardown sweep: every reachable entry is dropped exactly once, playing or idle. */
    fun dropAll() {
        val all = ArrayList<T>()
        playing.values.forEach { entry -> if (all.none { it === entry }) all.add(entry) }
        idle.snapshot().values.forEach { entry -> if (all.none { it === entry }) all.add(entry) }

        playing.clear()
        suppressDropSignal = true
        try {
            idle.evictAll()
        } finally {
            suppressDropSignal = false
        }

        all.forEach(onDropped)
    }
}
