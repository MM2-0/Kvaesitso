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
package com.vitorpamplona.amethyst.commons.relayClient.speedLogger

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class KindGroup(
    var count: AtomicInt = AtomicInt(0),
    var memory: AtomicInt = AtomicInt(0),
    val subs: LargeCache<String, AtomicInt> = LargeCache(),
    val relays: LargeCache<NormalizedRelayUrl, AtomicInt> = LargeCache(),
) {
    companion object {
        const val MB: Int = 1024
    }

    fun increment(
        mem: Int,
        subId: String,
        relayUrl: NormalizedRelayUrl,
    ) {
        count.addAndFetch(1)
        memory.addAndFetch(mem)

        val subStats = subs.get(subId)
        if (subStats != null) {
            subStats.addAndFetch(1)
        } else {
            subs.put(subId, AtomicInt(1))
        }

        val relayStats = relays.get(relayUrl)
        if (relayStats != null) {
            relayStats.addAndFetch(1)
        } else {
            relays.put(relayUrl, AtomicInt(1))
        }
    }

    fun reset() {
        count.store(0)
        memory.store(0)
        subs.forEach { _, value -> value.store(0) }
        relays.forEach { _, value -> value.store(0) }
    }

    fun printSubs() = subs.joinToString(", ") { key, value -> if (value.load() > 0) "$key ($value)" else "" }

    fun printRelays() = relays.joinToString(", ") { key, value -> if (value.load() > 0) "${key.displayUrl()} ($value)" else "" }

    override fun toString() = "(${count.load()} - ${memory.load().div(MB)}kb); ${printSubs()}; ${printRelays()}"
}
