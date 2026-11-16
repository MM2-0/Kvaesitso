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

/**
 * Generic EOSE cache keyed by any type K.
 * KMP-compatible version (no LruCache dependency).
 *
 * For memory-constrained environments, consider using platform-specific
 * LRU implementations in androidMain/jvmMain.
 */
open class EOSECache<K : Any>(
    private val maxSize: Int = 200,
) {
    private val cache = linkedMapOf<K, EOSERelayList>()
    private val lock = KmpLock()

    fun addOrUpdate(
        key: K,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        lock.withLock {
            val relayList = cache[key]
            if (relayList == null) {
                // Evict oldest if at capacity
                if (cache.size >= maxSize) {
                    cache.remove(cache.keys.first())
                }
                val newList = EOSERelayList()
                newList.addOrUpdate(relayUrl, time)
                cache[key] = newList
            } else {
                relayList.addOrUpdate(relayUrl, time)
            }
        }
    }

    fun since(key: K): SincePerRelayMap? =
        lock.withLock {
            cache[key]?.relayList?.toMutableMap()
        }

    fun newEose(
        key: K,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(key, relayUrl, time)

    fun remove(key: K) {
        lock.withLock {
            cache.remove(key)
        }
    }

    fun clear() {
        lock.withLock {
            cache.clear()
        }
    }

    fun size(): Int = lock.withLock { cache.size }
}

/**
 * Two-level EOSE cache: outer key -> inner key -> relay list.
 * Useful for user + list combinations.
 */
open class EOSETwoLevelCache<K1 : Any, K2 : Any>(
    private val outerMaxSize: Int = 20,
    private val innerMaxSize: Int = 200,
) {
    private val cache = linkedMapOf<K1, EOSECache<K2>>()
    private val lock = KmpLock()

    fun addOrUpdate(
        outerKey: K1,
        innerKey: K2,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        lock.withLock {
            val innerCache = cache[outerKey]
            if (innerCache == null) {
                // Evict oldest if at capacity
                if (cache.size >= outerMaxSize) {
                    cache.remove(cache.keys.first())
                }
                val newCache = EOSECache<K2>(innerMaxSize)
                newCache.addOrUpdate(innerKey, relayUrl, time)
                cache[outerKey] = newCache
            } else {
                innerCache.addOrUpdate(innerKey, relayUrl, time)
            }
        }
    }

    fun since(
        outerKey: K1,
        innerKey: K2,
    ): SincePerRelayMap? =
        lock.withLock {
            cache[outerKey]?.since(innerKey)
        }

    fun newEose(
        outerKey: K1,
        innerKey: K2,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(outerKey, innerKey, relayUrl, time)

    fun removeOuter(key: K1) {
        lock.withLock {
            cache.remove(key)
        }
    }

    fun clear() {
        lock.withLock {
            cache.clear()
        }
    }
}
