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
package com.vitorpamplona.amethyst.commons.model.nip01Core

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.WeakReference
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isDialableFromOthers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.Volatile

@Stable
data class RelayInfo(
    var lastEvent: Long,
    var counter: Int,
) {
    fun countNewEvent(eventTime: Long) {
        if (eventTime > lastEvent) {
            lastEvent = eventTime
        }
        counter++
    }
}

@Stable
class Wrapper(
    val data: Map<NormalizedRelayUrl, RelayInfo>,
)

val DefaultOrder =
    compareByDescending<Map.Entry<NormalizedRelayUrl, RelayInfo>> { it.value.counter }
        .thenByDescending { it.value.lastEvent }
        .thenBy { it.hashCode() }

@Stable
class UserRelaysCache {
    var data: Map<NormalizedRelayUrl, RelayInfo> = mapOf()

    // @Volatile is required for the double-checked locking in flow() to be
    // safe on weak-memory platforms (K/N + ARM). Without it, the outer
    // fast-path read could observe a partially-published WeakReference.
    @Volatile
    private var flow: WeakReference<MutableStateFlow<Wrapper>>? = null
    private val flowLock = KmpLock()

    fun flow() =
        flow?.get() ?: flowLock.withLock {
            flow?.get() ?: MutableStateFlow(Wrapper(data)).also { flow = WeakReference(it) }
        }

    fun add(
        relay: NormalizedRelayUrl,
        eventTime: Long,
    ) {
        val here = data[relay]
        if (here == null) {
            data = data + Pair(relay, RelayInfo(eventTime, 1))
        } else {
            here.countNewEvent(eventTime)
        }

        flow?.get()?.tryEmit(Wrapper(data))
    }

    fun allOrNull() = data.keys.ifEmpty { null }

    fun mostUsed(): List<NormalizedRelayUrl> = data.entries.sortedWith(DefaultOrder).map { it.key }

    /**
     * [mostUsed] without the relays others may not dial: the relays where we saw this user, as
     * hints we hand to someone else.
     */
    fun mostUsedDialable(): List<NormalizedRelayUrl> = mostUsed().filter { it.isDialableFromOthers() }

    /** The first of [mostUsedDialable], in one pass instead of a sort. */
    fun mostUsedDialableRelay(): NormalizedRelayUrl? {
        var best: Map.Entry<NormalizedRelayUrl, RelayInfo>? = null
        for (entry in data.entries) {
            if (entry.key.isDialableFromOthers() && (best == null || DefaultOrder.compare(entry, best) < 0)) best = entry
        }
        return best?.key
    }
}
