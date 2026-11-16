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

import com.vitorpamplona.amethyst.commons.relayClient.speedLogger.RelaySpeedLogger.Companion.TAG
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class FrameStat {
    var eventCount = AtomicInt(0)
    var kinds = LargeCache<Int, KindGroup>()

    fun increment(
        kind: Int,
        subId: String,
        relayUrl: NormalizedRelayUrl,
        memory: Int,
    ) {
        eventCount.addAndFetch(1)

        val kindGroup = kinds.get(kind)
        if (kindGroup != null) {
            kindGroup.increment(memory, subId, relayUrl)
        } else {
            val group = KindGroup()
            group.increment(memory, subId, relayUrl)
            kinds.put(kind, group)
        }
    }

    fun hasAnything() = eventCount.load() > 0

    fun reset() {
        eventCount.store(0)
        kinds.forEach { _, value -> value.reset() }
    }

    fun log() {
        Log.d(TAG) { "Events Per Second: ${eventCount.load()}" }
        kinds.forEach { key, value ->
            if (value.count.load() > 0) {
                Log.d(TAG) { "-- Kind $key $value" }
            }
        }
    }
}
