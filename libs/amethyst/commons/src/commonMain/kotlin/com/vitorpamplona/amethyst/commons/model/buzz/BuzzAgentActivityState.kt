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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live "an agent is currently working" state for Buzz workspaces, fed by the ephemeral NIP-AO
 * observer frames (kind-24200, `#p` = owner). Frames stream in only while an agent is actively
 * running and only reach the owner, so the mere arrival of a recent frame — no decryption needed —
 * is the liveness signal a "Working…" indicator wants.
 *
 * Shape: `agentPubKey -> the newest frame time (secs)`. The display edge decides how fresh counts
 * as "working" (a short window), since ephemeral frames are frequent while active and simply stop
 * when the turn ends. Process-wide singleton like [BuzzTypingState] / [BuzzPresenceState]; mutations
 * are lock-guarded because relay reads land on several reader threads.
 */
object BuzzAgentActivityState {
    private val lock = KmpLock()
    private val mutableActivity = MutableStateFlow<PersistentMap<HexKey, Long>>(persistentMapOf())

    /** `agentPubKey -> newest observer-frame time (secs)`; the UI collects this and checks freshness. */
    val flow: StateFlow<Map<HexKey, Long>> = mutableActivity

    /**
     * Records that [agent] emitted a frame at [atSecs]; keeps the newest so staleness is monotonic.
     * Backed by a persistent map so a record shares structure with the previous snapshot instead of
     * copying the whole map — this runs on a hot relay-consume path.
     */
    fun record(
        agent: HexKey,
        atSecs: Long,
    ) = lock.withLock {
        val prev = mutableActivity.value[agent]
        if (prev != null && atSecs <= prev) return@withLock
        mutableActivity.value = mutableActivity.value.putting(agent, atSecs)
    }

    /** Test-only: clears all activity so unit tests don't leak into each other. */
    fun clearForTesting() =
        lock.withLock {
            mutableActivity.value = persistentMapOf()
        }
}
