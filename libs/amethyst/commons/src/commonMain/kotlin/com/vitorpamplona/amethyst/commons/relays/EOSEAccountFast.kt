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

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Per-key EOSE tracker keyed by an arbitrary [T] (a `User`, a pubkey, …), used
 * by the relay-subscription assemblers to remember which relays already EOSE'd
 * for a given key so the next filter assembly can add a `since` and avoid a full
 * re-download.
 *
 * KMP-pure: uses [KmpLock] instead of `synchronized(...)` so it compiles for the
 * iOS targets `commons` builds for. Moved out of `amethyst.service.relays` so the
 * shared user/event finder assemblers can live in `commonMain`. The old location
 * keeps a `typealias` for source compatibility.
 */
class EOSEAccountFast<T : Any>(
    private val cacheSize: Int = 20,
) {
    private val users: LruCache<T, EOSERelayList> = LruCache(cacheSize)
    private val lock = KmpLock()

    /**
     * Sizes the cache to the [liveKeys] the caller tracks right now plus [cacheSize] recently dropped
     * ones, growing for a feed's whole author list and shrinking back once it is gone. A fixed cap
     * below the live count evicts live keys, which are then asked for again and evict others in turn.
     */
    fun fitTo(liveKeys: Int) {
        val target = liveKeys + cacheSize
        lock.withLock {
            if (users.maxSize() != target) users.resize(target)
        }
    }

    fun addOrUpdate(
        user: T,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        lock.withLock {
            val relayList = users[user]
            if (relayList == null) {
                val newList = EOSERelayList()
                users.put(user, newList)

                newList.addOrUpdate(relayUrl, time)
            } else {
                relayList.addOrUpdate(relayUrl, time)
            }
        }
    }

    fun removeEveryoneBut(list: Set<T>) {
        lock.withLock {
            users.snapshot().forEach {
                if (it.key !in list) {
                    users.remove(it.key)
                }
            }
        }
    }

    fun removeDataFor(user: T) {
        lock.withLock {
            users.remove(user)
        }
    }

    fun since(key: T): SincePerRelayMap? =
        lock.withLock {
            users[key]?.relayList?.toMutableMap()
        }

    fun sinceRelaySet(key: T): Set<NormalizedRelayUrl>? =
        lock.withLock {
            users[key]?.relayList?.keys?.toSet()
        }

    fun newEose(
        user: T,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(user, relayUrl, time)
}
