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

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Snapshot of one relay's rolling-window latency at a point in time. Compose-safe — backed by
 * an [ImmutableMap] so strong skipping engages when nothing changed since the previous tick.
 *
 * Missing metrics simply aren't in the map (no entry vs zero-count is semantically the same;
 * the classifier treats both as "no samples").
 */
@Immutable
data class RelayLatencySnapshot(
    val samples: ImmutableMap<LatencyMetric, MetricSample> = persistentMapOf(),
) {
    /** Convenience: returns the count for [metric], or 0 if no samples exist. */
    fun countOf(metric: LatencyMetric): Int = samples[metric]?.count ?: 0

    /** Convenience: returns the p50 for [metric], or null if no samples exist. */
    fun p50Of(metric: LatencyMetric): Int? = samples[metric]?.p50Ms
}
