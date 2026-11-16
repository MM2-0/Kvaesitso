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
package com.vitorpamplona.amethyst.commons.model.nip90DVMs

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Strong, process-wide record of the latest heartbeat per DVM announcement address
 * (`Address(11998, dvmPubKey, dTag) -> createdAt`).
 *
 * Beat Notes themselves live in `LocalCache.addressables`, a WeakReference store with no strong
 * holder on the Discover screen — every GC sweep cleared them all at once and the freshness gate
 * collapsed for every DVM simultaneously (the list emptied and rebuilt one beat at a time). This
 * registry is the freshness source the gate and the liveness composables read: strong references,
 * fed by every beat-arrival path (global REQ, outbox batches, per-surface fetches — all beat
 * consumption routes through [record]).
 *
 * One entry per DVM address ever seen; timestamps only, so it stays tiny. `0` means "no beat".
 */
object DvmHeartbeatRegistry {
    private val latestBeatCreatedAt = ConcurrentMap<Address, MutableStateFlow<Long>>()

    private fun flowFor(address: Address): MutableStateFlow<Long> = latestBeatCreatedAt.getOrPut(address) { MutableStateFlow(0L) }

    /** Observable latest-beat timestamp for this address; `0` means "no beat seen yet". */
    fun flowForPublic(address: Address): StateFlow<Long> = flowFor(address)

    /** Records a beat's createdAt; older beats never move the entry backwards. */
    fun record(
        address: Address,
        createdAt: Long,
    ) = flowFor(address).update { current -> if (createdAt > current) createdAt else current }

    /** The latest recorded beat's createdAt for this address, or null when no beat was ever seen. */
    fun latestAt(address: Address): Long? = flowFor(address).value.takeIf { it > 0L }
}
