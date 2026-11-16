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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live "who is typing" state for Buzz workspace channels, fed by the ephemeral
 * kind-20002 typing indicator (`TypingIndicatorEvent`). Buzz relays never store these
 * (20000-29999), so this is pure in-memory live state — the exact analog of
 * `ConcordCommunitySession.typing`, but keyed by the channel's `h` UUID because Buzz
 * channels are relay-authoritative NIP-29 groups rather than encrypted planes.
 *
 * Shape: `channelId -> (typistPubKey -> lastHeartbeatUnixSecs)`. A typist is "active"
 * while their last heartbeat is within [TYPING_STALE_SECS]; the UI ages them out on a
 * timer. Mutations are lock-guarded because `LocalCache` consume runs on several relay
 * reader threads and an unsynchronized check-then-act could drop a fresh heartbeat.
 *
 * Process-wide singleton like [BuzzRelayDialect] / `BuzzWorkspaceStates`.
 */
object BuzzTypingState {
    private val lock = KmpLock()
    private val typingByChannel = HashMap<String, HashMap<HexKey, Long>>()
    private val mutableTyping = MutableStateFlow<Map<String, Map<HexKey, Long>>>(emptyMap())

    /** `channelId -> (typist -> lastHeartbeatSecs)`; the UI collects this and ages entries out. */
    val flow: StateFlow<Map<String, Map<HexKey, Long>>> = mutableTyping

    /**
     * Records a typing heartbeat from [typist] in [channelId] at [atSecs] (clamped to
     * [nowSecs] so a future-dated peer can't wedge the freshness window), then prunes
     * anything already stale. No-op for a stale-on-arrival heartbeat.
     */
    fun record(
        channelId: String,
        typist: HexKey,
        atSecs: Long,
        nowSecs: Long,
    ) = lock.withLock {
        val stamp = minOf(atSecs, nowSecs)
        if (nowSecs - stamp > TYPING_STALE_SECS) return@withLock
        val perChannel = typingByChannel.getOrPut(channelId) { HashMap() }
        val prev = perChannel[typist]
        if (prev == null || stamp > prev) perChannel[typist] = stamp
        perChannel.entries.retainAll { nowSecs - it.value <= TYPING_STALE_SECS }
        if (perChannel.isEmpty()) typingByChannel.remove(channelId)
        mutableTyping.value = typingByChannel.mapValues { it.value.toMap() }
    }

    /** Test-only: clears all typing state so unit tests don't leak into each other. */
    fun clearForTesting() =
        lock.withLock {
            typingByChannel.clear()
            mutableTyping.value = emptyMap()
        }

    /** A peer must heartbeat at least this often to stay "typing"; also the UI fade window. */
    const val TYPING_STALE_SECS = 8L

    /** Client-side throttle: send at most one typing heartbeat this often while composing. */
    const val TYPING_HEARTBEAT_SECS = 4L
}
