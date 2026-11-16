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
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

const val DEFAULT_SLOW_MIN_SAMPLES: Int = 5
const val DEFAULT_SLOW_MULTIPLIER: Double = 2.0
const val DEFAULT_SLOW_MIN_COHORT_SIZE: Int = 2

/**
 * Pure classifier. Returns the relays whose median latency is more than [multiplier] × the
 * cohort median on at least one metric. Per-metric cohort = relays with ≥ [minSamples] samples
 * for that metric.
 *
 * Gates (return early with empty map):
 *  - Tor mode is on (existing gate; relay timing through Tor is lossy on purpose).
 *  - Relays whose NIP-11 [Nip11RelayInformation.limitation] advertises `auth_required` or
 *    `payment_required` AND [authStatus] reports auth incomplete are excluded from both
 *    cohort *and* targets. Otherwise paid / auth-only relays would be perpetually flagged
 *    while sending mostly CLOSED responses to anonymous queries.
 *
 * If a relay is slow on multiple metrics, the metric with the worst multiplier wins.
 */
fun classifySlowRelays(
    snapshots: ImmutableMap<NormalizedRelayUrl, RelayLatencySnapshot>,
    nip11: ImmutableMap<NormalizedRelayUrl, Nip11RelayInformation?>,
    torEnabled: Boolean = false,
    minSamples: Int = DEFAULT_SLOW_MIN_SAMPLES,
    multiplier: Double = DEFAULT_SLOW_MULTIPLIER,
    minCohortSize: Int = DEFAULT_SLOW_MIN_COHORT_SIZE,
    authStatus: (NormalizedRelayUrl) -> Boolean,
): ImmutableMap<NormalizedRelayUrl, SlowReason> {
    if (torEnabled) return persistentMapOf()
    if (snapshots.isEmpty()) return persistentMapOf()

    val eligible: Map<NormalizedRelayUrl, RelayLatencySnapshot> =
        snapshots.filter { (url, _) -> includeInClassification(url, nip11, authStatus) }
    if (eligible.size < minCohortSize) return persistentMapOf()

    val worst = mutableMapOf<NormalizedRelayUrl, SlowReason>()

    for (metric in LatencyMetric.entries) {
        // Per-metric cohort = relays whose own count ≥ minSamples for this metric.
        val cohort: List<Pair<NormalizedRelayUrl, Int>> =
            eligible.mapNotNull { (url, snap) ->
                val sample = snap.samples[metric] ?: return@mapNotNull null
                if (sample.count < minSamples) return@mapNotNull null
                url to sample.p50Ms
            }
        if (cohort.size < minCohortSize) continue

        val cohortP50 = medianOf(cohort.map { it.second })
        if (cohortP50 <= 0) continue

        for ((url, relayP50) in cohort) {
            val mult = relayP50.toDouble() / cohortP50.toDouble()
            if (mult > multiplier) {
                val existing = worst[url]
                if (existing == null || mult > existing.multiplier) {
                    worst[url] =
                        SlowReason(
                            metric = metric,
                            relayP50Ms = relayP50,
                            cohortP50Ms = cohortP50,
                            multiplier = mult,
                        )
                }
            }
        }
    }

    return worst.toPersistentMap()
}

private fun includeInClassification(
    url: NormalizedRelayUrl,
    nip11: ImmutableMap<NormalizedRelayUrl, Nip11RelayInformation?>,
    authStatus: (NormalizedRelayUrl) -> Boolean,
): Boolean {
    val info = nip11[url] ?: return true
    val limit = info.limitation ?: return true
    val requiresAuth = limit.auth_required == true || limit.payment_required == true
    if (!requiresAuth) return true
    return authStatus(url)
}

/**
 * Median of an Int list (n=1..N is fine). For even N returns the lower of the two middles
 * (`values[n / 2 - 1]`) — avoids floating-point arithmetic and keeps results stable.
 * Caller is responsible for non-empty input.
 */
internal fun medianOf(values: List<Int>): Int {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    val n = sorted.size
    return if (n % 2 == 1) sorted[n / 2] else sorted[n / 2 - 1]
}
