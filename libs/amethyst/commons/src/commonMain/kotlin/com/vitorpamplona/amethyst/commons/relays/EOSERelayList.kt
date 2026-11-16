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
package com.vitorpamplona.amethyst.commons.relays

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.concurrent.Volatile

typealias SincePerRelayMap = MutableMap<NormalizedRelayUrl, MutableTime>

/**
 * Tracks EOSE (End Of Stored Events) timestamps for relay subscriptions.
 * Used by UserCardsCache and similar classes to manage relay subscription state.
 */

class EOSERelayList {
    /**
     * Copy-on-write: replaced wholesale under [lock], never mutated in place after publication, so
     * readers need no synchronization at all.
     *
     * The access pattern makes this nearly free. [addOrUpdate] runs on **every live event** — hundreds
     * per second across a few hundred relays — but an event from a relay already in the map only bumps
     * the `Long` inside its own [MutableTime]. The map itself is only ever written on the *first* frame
     * from a relay, plus [remove] and [clear]: a couple of hundred writes for the lifetime of the
     * process. Locking the common path would have put every one of those events through one monitor
     * for no structural change at all.
     */
    @Volatile
    var relayList: SincePerRelayMap = mutableMapOf()
        private set

    /** Guards the rare structural writes only. Readers and the per-event bump never take it. */
    private val lock = KmpLock()

    fun addOrUpdate(
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        // Hot path: the relay is already known, so nothing about the map changes.
        //
        // The bump itself is unsynchronized. Two socket threads racing it can leave the older of two
        // timestamps, because `updateIfNewer` reads-compares-writes without a lock — which is harmless
        // here: this value is a floor for `since`, so losing a millisecond re-asks for a couple of
        // events rather than skipping any.
        val existing = relayList[relayUrl]
        if (existing != null) {
            existing.updateIfNewer(time)
            return
        }

        // Rare: first frame from this relay. Re-check inside the lock, since another thread may have
        // inserted it between the read above and here.
        lock.withLock {
            val current = relayList[relayUrl]
            if (current != null) {
                current.updateIfNewer(time)
            } else {
                relayList = relayList.toMutableMap().apply { put(relayUrl, MutableTime(time)) }
            }
        }
    }

    fun clear() =
        lock.withLock {
            relayList = mutableMapOf()
        }

    /**
     * Forgets one relay's cursor, so the next filter built for it asks from scratch.
     *
     * Needed when a subscription's *scope* widens rather than its contents changing — a merged filter
     * that starts covering another account has already-EOSE'd relays whose `since` would silence
     * exactly the history the new account still needs.
     */
    fun remove(relayUrl: NormalizedRelayUrl) =
        lock.withLock {
            if (relayList.containsKey(relayUrl)) {
                relayList = relayList.toMutableMap().apply { remove(relayUrl) }
            }
        }

    fun since() = relayList

    fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(relay, time)
}
