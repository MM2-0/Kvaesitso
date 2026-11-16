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
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation.RelayInformationLimitation
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassifySlowRelaysTest {
    private val fast1 = NormalizedRelayUrl("wss://fast1.test/")
    private val fast2 = NormalizedRelayUrl("wss://fast2.test/")
    private val fast3 = NormalizedRelayUrl("wss://fast3.test/")
    private val slow = NormalizedRelayUrl("wss://slow.test/")
    private val paid = NormalizedRelayUrl("wss://paid.test/")

    private fun snap(vararg pairs: Pair<LatencyMetric, MetricSample>): RelayLatencySnapshot = RelayLatencySnapshot(samples = mapOf(*pairs).toPersistentMap())

    private fun authAlwaysComplete(
        @Suppress("UNUSED_PARAMETER") url: NormalizedRelayUrl,
    ): Boolean = true

    @Test
    fun emptySnapshotsReturnsEmpty() {
        val result =
            classifySlowRelays(
                snapshots = persistentMapOf(),
                nip11 = persistentMapOf(),
                authStatus = ::authAlwaysComplete,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun torEnabledShortCircuitsToEmpty() {
        val result =
            classifySlowRelays(
                snapshots = persistentMapOf(slow to snap(LatencyMetric.OK_ACK to MetricSample(10_000, 10))),
                nip11 = persistentMapOf(),
                authStatus = ::authAlwaysComplete,
                torEnabled = true,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun belowMinCohortSizeReturnsEmpty() {
        // Only one relay has samples — no cohort to compare against.
        val snapshots =
            persistentMapOf(
                slow to snap(LatencyMetric.OK_ACK to MetricSample(5_000, 50)),
            )
        val result = classifySlowRelays(snapshots, persistentMapOf(), authStatus = ::authAlwaysComplete)
        assertTrue(result.isEmpty())
    }

    @Test
    fun flagsRelayWith2xCohortMedian() {
        val snapshots =
            persistentMapOf(
                fast1 to snap(LatencyMetric.OK_ACK to MetricSample(200, 10)),
                fast2 to snap(LatencyMetric.OK_ACK to MetricSample(250, 10)),
                fast3 to snap(LatencyMetric.OK_ACK to MetricSample(300, 10)),
                slow to snap(LatencyMetric.OK_ACK to MetricSample(2_000, 10)),
            )
        val result = classifySlowRelays(snapshots, persistentMapOf(), authStatus = ::authAlwaysComplete)
        val reason = result[slow]
        assertNotNull(reason)
        assertEquals(LatencyMetric.OK_ACK, reason.metric)
        assertEquals(2_000, reason.relayP50Ms)
        // Cohort medians of [200, 250, 300, 2000] sorted = lower middle = 250.
        assertEquals(250, reason.cohortP50Ms)
        assertEquals(8.0, reason.multiplier)
        assertNull(result[fast1])
        assertNull(result[fast2])
        assertNull(result[fast3])
    }

    @Test
    fun belowMinSamplesExcludesRelayFromCohort() {
        val snapshots =
            persistentMapOf(
                fast1 to snap(LatencyMetric.OK_ACK to MetricSample(200, 4)), // below min
                fast2 to snap(LatencyMetric.OK_ACK to MetricSample(250, 10)),
                slow to snap(LatencyMetric.OK_ACK to MetricSample(2_000, 10)),
            )
        val result = classifySlowRelays(snapshots, persistentMapOf(), authStatus = ::authAlwaysComplete)
        // Cohort is now [fast2, slow] — slow vs cohort median 250 → 8× → flagged.
        assertEquals(8.0, result[slow]!!.multiplier)
    }

    @Test
    fun nip11AuthRequiredExcludesRelayFromCohortAndTargets() {
        val snapshots =
            persistentMapOf(
                fast1 to snap(LatencyMetric.OK_ACK to MetricSample(200, 10)),
                fast2 to snap(LatencyMetric.OK_ACK to MetricSample(250, 10)),
                slow to snap(LatencyMetric.OK_ACK to MetricSample(2_000, 10)),
                paid to snap(LatencyMetric.OK_ACK to MetricSample(50_000, 10)),
            )
        val nip11 =
            persistentMapOf(
                paid to Nip11RelayInformation(limitation = RelayInformationLimitation(auth_required = true)),
            )
        // `paid` hasn't completed auth, so it is excluded from both cohort and targets.
        val result = classifySlowRelays(snapshots, nip11) { url -> url != paid }
        // `paid` is excluded (auth_required + auth not complete). `slow` still flagged.
        assertNotNull(result[slow])
        assertNull(result[paid])
    }

    @Test
    fun nip11AuthRequiredIncludesRelayOnceAuthComplete() {
        val snapshots =
            persistentMapOf(
                fast1 to snap(LatencyMetric.OK_ACK to MetricSample(200, 10)),
                fast2 to snap(LatencyMetric.OK_ACK to MetricSample(250, 10)),
                paid to snap(LatencyMetric.OK_ACK to MetricSample(50_000, 10)),
            )
        val nip11 =
            persistentMapOf(
                paid to Nip11RelayInformation(limitation = RelayInformationLimitation(auth_required = true)),
            )
        // Auth complete everywhere → `paid` participates as cohort and target.
        val result = classifySlowRelays(snapshots, nip11) { _ -> true }
        assertNotNull(result[paid])
        assertEquals(LatencyMetric.OK_ACK, result[paid]!!.metric)
    }

    @Test
    fun paymentRequiredExcludesLikeAuthRequired() {
        val snapshots =
            persistentMapOf(
                fast1 to snap(LatencyMetric.OK_ACK to MetricSample(200, 10)),
                fast2 to snap(LatencyMetric.OK_ACK to MetricSample(250, 10)),
                paid to snap(LatencyMetric.OK_ACK to MetricSample(50_000, 10)),
            )
        val nip11 =
            persistentMapOf(
                paid to Nip11RelayInformation(limitation = RelayInformationLimitation(payment_required = true)),
            )
        val result = classifySlowRelays(snapshots, nip11) { url -> url != paid }
        assertNull(result[paid])
    }

    @Test
    fun worstMetricMultiplierWinsWhenMultipleFlag() {
        val snapshots =
            persistentMapOf(
                fast1 to
                    snap(
                        LatencyMetric.OK_ACK to MetricSample(200, 10),
                        LatencyMetric.EOSE to MetricSample(300, 10),
                    ),
                fast2 to
                    snap(
                        LatencyMetric.OK_ACK to MetricSample(250, 10),
                        LatencyMetric.EOSE to MetricSample(350, 10),
                    ),
                slow to
                    snap(
                        LatencyMetric.OK_ACK to MetricSample(1_000, 10), // 4×
                        LatencyMetric.EOSE to MetricSample(4_000, 10), // 11.4× (cohort p50 = 350)
                    ),
            )
        val result = classifySlowRelays(snapshots, persistentMapOf(), authStatus = ::authAlwaysComplete)
        val reason = result[slow]!!
        assertEquals(LatencyMetric.EOSE, reason.metric)
        assertTrue(reason.multiplier > 10.0)
    }

    @Test
    fun rightOnThresholdDoesNotFlag() {
        // exactly 2× the cohort median — multiplier > 2.0 required to flag.
        val snapshots =
            persistentMapOf(
                fast1 to snap(LatencyMetric.OK_ACK to MetricSample(100, 10)),
                fast2 to snap(LatencyMetric.OK_ACK to MetricSample(100, 10)),
                slow to snap(LatencyMetric.OK_ACK to MetricSample(200, 10)),
            )
        val result = classifySlowRelays(snapshots, persistentMapOf(), authStatus = ::authAlwaysComplete)
        assertNull(result[slow])
    }
}
