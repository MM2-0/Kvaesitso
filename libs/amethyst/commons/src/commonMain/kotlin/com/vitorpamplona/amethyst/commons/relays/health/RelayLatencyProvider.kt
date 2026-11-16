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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableMap

/**
 * commonMain-facing view of a relay latency tracker. The [RelayHealthStore] holds a
 * reference to this interface and drives `sweep` + `snapshot` from its existing 60 s
 * reclassify tick. The concrete implementation lives in `jvmAndroidMain` because it
 * needs `ConcurrentHashMap` for pending-correlation maps; iOS picks up later via an
 * `expect class` once that target is wired.
 */
interface RelayLatencyProvider {
    /** Expire pending entries past their TTL, recording the TTL value as the sample. */
    fun sweep(nowMs: Long = TimeUtils.nowMillis())

    /** Immutable snapshot of all tracked relays' rolling-window medians. */
    fun snapshot(): ImmutableMap<NormalizedRelayUrl, RelayLatencySnapshot>

    /** Raw per-relay per-metric sample arrays for persistence (chronological order). */
    fun samplesForPersistence(): Map<NormalizedRelayUrl, Map<LatencyMetric, IntArray>>

    /** Restore samples from a previous session — typically called once at app start. */
    fun restoreSamples(saved: Map<NormalizedRelayUrl, Map<LatencyMetric, IntArray>>)
}
