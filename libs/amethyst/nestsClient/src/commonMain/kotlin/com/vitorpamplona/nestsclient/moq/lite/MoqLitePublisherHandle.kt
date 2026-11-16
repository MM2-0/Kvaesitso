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
package com.vitorpamplona.nestsclient.moq.lite

/**
 * Active publisher handle returned by [MoqLiteSession.publish].
 *
 * Lifecycle:
 *   1. Call [startGroup] (or [send] which auto-starts a fresh group on
 *      first call) to begin pushing frames for one Opus group.
 *   2. Call [send] for each frame (one Opus packet = one frame).
 *   3. Call [endGroup] to FIN the current group's uni stream and start
 *      a fresh group on the next [send]. Group rollover is the
 *      publisher's call — typically every N seconds or every keyframe.
 *   4. Call [close] when the broadcast ends — sends `Announce(Ended)`
 *      on every active announce bidi and FINs every group stream.
 */
interface MoqLitePublisherHandle {
    /**
     * The broadcast suffix this publisher claimed at [MoqLiteSession.publish].
     * Always normalised per [MoqLitePath].
     */
    val suffix: String

    /**
     * The next group sequence number that will be assigned by [send] /
     * [startGroup]. Snapshot-only — read AFTER the broadcaster has
     * stopped sending into this publisher (typically just before the
     * caller closes the publisher in a hot-swap), so the value is the
     * highest-already-used sequence + 1.
     *
     * Used by [com.vitorpamplona.nestsclient.MoqLiteNestsSpeaker]'s
     * hot-swap path to seed the new session's publisher with a
     * monotonically-continuing sequence — without this, every JWT
     * refresh restarts at sequence 0 and kixelated/hang's
     * `Container.Consumer.#run` drops every group whose sequence is
     * less than its current `#active` high-water mark, killing audio
     * for the watcher until either `#active` rolls over or the
     * watcher re-subscribes.
     *
     * `@Volatile` on the implementation; safe to read from any
     * coroutine. The accept-tiny-race window between read and a
     * concurrent `send` is closed in practice because the broadcaster
     * is responsible for swapping its publisher reference BEFORE the
     * caller reads this value (so no further sends land on this
     * publisher).
     */
    val nextSequence: Long

    /**
     * Start a new group. Allocates a fresh sequence id and opens a new
     * uni stream pre-loaded with `DataType=Group + GroupHeader`. Idempotent
     * — calling [startGroup] when the previous group hasn't been ended
     * is treated as an implicit [endGroup] then a new start.
     */
    suspend fun startGroup()

    /**
     * Push one [payload] (one Opus packet) as a `varint(size) + payload`
     * frame on the current group's uni stream. Auto-starts a group if
     * none is active.
     *
     * Returns false if no inbound subscriber is currently attached.
     * Subscriber-less sends silently drop on the wire — the relay keeps
     * the publisher's announce active either way, so unmute is
     * sample-accurate.
     */
    suspend fun send(payload: ByteArray): Boolean

    /** FIN the current group's uni stream. The next [send] starts a fresh group. */
    suspend fun endGroup()

    /**
     * Register a callback that fires once each time a new inbound
     * subscriber is registered against this publisher's track (i.e.
     * each track-matching SUBSCRIBE bidi the relay opens to us). Used
     * to push a "track-latest" payload — the canonical example is the
     * broadcast catalog manifest, which a watcher needs to receive on
     * subscribe but doesn't change between subscribers — without
     * forcing the publisher to maintain a periodic re-emit loop.
     *
     * Called once per accepted SUBSCRIBE (track filter passed). Fires
     * OUTSIDE the publisher's serialisation lock, so the hook can
     * safely call [send] / [endGroup] without deadlocking.
     *
     * Caller MUST set the hook before any subscriber attaches (typically
     * immediately after [com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession.publish]
     * returns) — there's no "fire-on-set for existing subscribers"
     * replay. For the typical catalog use case the publisher is fresh
     * when the hook is set, and the relay's SUBSCRIBE bidi takes a
     * round-trip to arrive, so this is safe in practice.
     *
     * Pass `null` to clear the hook. Calling twice with non-null
     * replaces the previous hook (no de-duplication).
     */
    fun setOnNewSubscriber(hook: (suspend () -> Unit)?)

    /**
     * Stop publishing. Sends `Announce(Ended)` on every active announce
     * bidi, FINs the current group, and releases all per-publisher
     * resources. Idempotent.
     */
    suspend fun close()
}
