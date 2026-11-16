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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which Buzz huddle sessions are live right now, per channel, fed by the relay-synthesized
 * kind-48104 liveness events (`HuddleLivenessEvent`). The relay builds those on demand from its
 * live huddle table, answers only for sessions that are live, and never stores them, so a
 * session's liveness is "seen in a recent answer": Buzz's desktop re-asks every 10 seconds and
 * treats a session that stops appearing as ended. [LIVE_WINDOW_SECS] leaves room for two missed
 * polls before a huddle drops off.
 *
 * Shape: `channelId -> sessionId -> last seen (seconds)`. Lock-guarded because `LocalCache`
 * consume runs on several relay reader threads. Process-wide singleton like [BuzzPresenceState].
 */
object BuzzHuddleLivenessState {
    const val LIVE_WINDOW_SECS = 30L

    private val lock = KmpLock()
    private val mutableSeen = MutableStateFlow<PersistentMap<String, PersistentMap<String, Long>>>(persistentMapOf())

    /** `channelId -> sessionId -> last seen`; collect it and filter with [liveSessions]. */
    val flow: StateFlow<Map<String, Map<String, Long>>> = mutableSeen

    /** Records that the relay reported [sessionId] in [channelId] live as of [seenAtSecs]. */
    fun record(
        channelId: String,
        sessionId: String,
        seenAtSecs: Long,
    ) = lock.withLock {
        val sessions = mutableSeen.value[channelId] ?: persistentMapOf()
        val prev = sessions[sessionId]
        if (prev != null && seenAtSecs <= prev) return@withLock
        mutableSeen.value = mutableSeen.value.putting(channelId, sessions.putting(sessionId, seenAtSecs))
    }

    /** The sessions in [channelId] reported live within [LIVE_WINDOW_SECS] of [nowSecs]. */
    fun liveSessions(
        channelId: String,
        nowSecs: Long,
        snapshot: Map<String, Map<String, Long>> = mutableSeen.value,
    ): Set<String> =
        snapshot[channelId]
            ?.filterValues { nowSecs - it <= LIVE_WINDOW_SECS }
            ?.keys
            .orEmpty()

    /** Test-only: clears all state so unit tests don't leak into each other. */
    fun clearForTesting() =
        lock.withLock {
            mutableSeen.value = persistentMapOf()
        }
}
