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
package com.vitorpamplona.geode.fixtures

import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Generators for cheap, deterministic, structurally valid Nostr events.
 *
 * The signatures and ids produced here are *not* cryptographically valid —
 * the in-process relay's default [com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy]
 * doesn't verify them, and neither does the underlying SQLite event store.
 * Use these when a test only needs to exercise relay logic (filter matching,
 * limits, EOSE, live updates) and not the cryptographic layer.
 */
object SyntheticEvents {
    /** A pubkey/sig pair that's syntactically valid (64/128 hex chars) but signs nothing. */
    private val DEFAULT_PUBKEY = "0".repeat(64)
    private val FAKE_SIG = "0".repeat(128)

    /** Hex padding to 64 chars so deterministic ids look like real event ids. */
    fun hexId(seed: Int): String = seed.toString().padStart(64, '0')

    fun fakeEvent(
        idSeed: Int,
        kind: Int = 1,
        pubKey: String = DEFAULT_PUBKEY,
        createdAt: Long = idSeed.toLong(),
        content: String = "",
        tags: Array<Array<String>> = emptyArray(),
    ): Event = Event(hexId(idSeed), pubKey, createdAt, kind, tags, content, FAKE_SIG)

    /**
     * Returns [count] events of [kind] with monotonic [createdAt] starting at 1
     * and a *distinct* pubkey per event. Distinct pubkeys are essential for
     * replaceable kinds (0, 3, 10000-19999): without them, the relay collapses
     * the whole batch to one row per (kind, pubkey).
     */
    fun batch(
        count: Int,
        kind: Int = 1,
        pubKeyOf: (Int) -> String = { hexId(1_000_000 + it) },
    ): List<Event> =
        List(count) { i ->
            val seed = i + 1
            fakeEvent(idSeed = seed, kind = kind, pubKey = pubKeyOf(seed), createdAt = seed.toLong())
        }
}
