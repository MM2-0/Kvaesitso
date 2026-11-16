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
 * Live per-user presence for Buzz workspaces, fed by the ephemeral kind-20001 presence update
 * (`PresenceUpdateEvent`). Buzz relays never store these (20000-29999): a client publishes its
 * own status over the WebSocket, and a reader gets back the relay's synthesized snapshot
 * (relay-signed, subject in a `p` tag). Either way it is pure in-memory live state.
 *
 * Unlike [BuzzTypingState], presence is **not** a heartbeat that ages out on a fixed window —
 * `online`/`away`/`offline` is a state that holds until the peer publishes a newer one — so this
 * keeps the latest status per subject (by event time) rather than pruning on a TTL. The raw wire
 * string is kept (the relay's WS path accepts arbitrary status strings); map it through
 * `PresenceStatus.fromWire` at the display edge.
 *
 * Shape: `subjectPubKey -> latest status string`. Mutations are lock-guarded because `LocalCache`
 * consume runs on several relay reader threads. Process-wide singleton like [BuzzTypingState].
 */
object BuzzPresenceState {
    private val lock = KmpLock()
    private var stampByUser: PersistentMap<HexKey, Long> = persistentMapOf()
    private val mutablePresence = MutableStateFlow<PersistentMap<HexKey, String>>(persistentMapOf())

    /** `subjectPubKey -> latest status string`; the UI collects this and maps to a badge. */
    val flow: StateFlow<Map<HexKey, String>> = mutablePresence

    /**
     * Records [status] for [subject] as of [atSecs]. Keeps the newest by timestamp so an
     * out-of-order or duplicated delivery can't clobber a fresher state — an update that is not
     * strictly newer than what we already hold is a no-op. Backed by persistent maps so a record
     * shares structure with the previous snapshot instead of copying the whole map (this runs on a
     * hot relay-consume path).
     */
    fun record(
        subject: HexKey,
        status: String,
        atSecs: Long,
    ) = lock.withLock {
        val prev = stampByUser[subject]
        if (prev != null && atSecs <= prev) return@withLock
        stampByUser = stampByUser.putting(subject, atSecs)
        mutablePresence.value = mutablePresence.value.putting(subject, status)
    }

    /** The latest known status string for [subject], or `null` if none has arrived. */
    fun statusFor(subject: HexKey): String? = mutablePresence.value[subject]

    /** Test-only: clears all presence state so unit tests don't leak into each other. */
    fun clearForTesting() =
        lock.withLock {
            stampByUser = persistentMapOf()
            mutablePresence.value = persistentMapOf()
        }
}
