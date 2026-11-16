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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricPayload
import com.vitorpamplona.quartz.buzz.amTurnMetrics.TokenCounts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentFleetAggregatorTest {
    private val agentA = "a".repeat(64)
    private val agentB = "b".repeat(64)

    private fun turn(
        agent: String,
        session: String?,
        seq: Long,
        turn: TokenCounts? = null,
        cumulative: TokenCounts? = null,
        deltaReliable: Boolean = true,
        ts: String = "2026-07-0${seq}T00:00:00Z",
        harness: String = "goose",
        model: String? = "claude",
    ) = agent to
        AgentTurnMetricPayload(
            harness = harness,
            timestamp = ts,
            model = model,
            sessionId = session,
            turnId = "turn-$seq",
            turnSeq = seq,
            turn = turn,
            cumulative = cumulative,
            deltaReliable = deltaReliable,
        )

    private fun counts(
        input: Long? = null,
        output: Long? = null,
        total: Long? = null,
        cost: Double? = null,
    ) = TokenCounts(inputTokens = input, outputTokens = output, totalTokens = total, costUsd = cost)

    @Test
    fun emptyYieldsEmpty() {
        assertEquals(AgentFleetMetrics.EMPTY, AgentFleetAggregator.aggregate(emptyList()))
    }

    @Test
    fun cumulativeIsTheSessionTotalNotTheSumOfCumulatives() {
        // Three turns of one session; cumulative is the running total. Summing the
        // cumulatives (100+250+400) would triple-count — the total is the LAST one, 400.
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, cumulative = counts(total = 100, cost = 0.10)),
                    turn(agentA, "s1", 2, cumulative = counts(total = 250, cost = 0.25)),
                    turn(agentA, "s1", 3, cumulative = counts(total = 400, cost = 0.40)),
                ),
            )

        assertEquals(400L, metrics.totals.totalTokens)
        assertEquals(0.40, metrics.totals.costUsd, 1e-9)
        assertEquals(3, metrics.totalTurns)
        assertEquals(1, metrics.totalSessions)
        assertFalse(metrics.hasUnreliableEstimates)
    }

    @Test
    fun aMissingMiddleTurnStillGivesTheRightSessionTotal() {
        // Turn 2 never arrived; cumulative-max still recovers the true session total.
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, cumulative = counts(total = 100)),
                    turn(agentA, "s1", 3, cumulative = counts(total = 400)),
                ),
            )
        assertEquals(400L, metrics.totals.totalTokens)
    }

    @Test
    fun withoutCumulativeItSumsTheTurnDeltas() {
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, turn = counts(input = 10, output = 5, total = 15, cost = 0.01)),
                    turn(agentA, "s1", 2, turn = counts(input = 20, output = 8, total = 28, cost = 0.02)),
                ),
            )
        assertEquals(30L, metrics.totals.inputTokens)
        assertEquals(13L, metrics.totals.outputTokens)
        assertEquals(43L, metrics.totals.totalTokens)
        assertEquals(0.03, metrics.totals.costUsd, 1e-9)
    }

    @Test
    fun perFieldFallbackTakesCostFromDeltasWhenCumulativeOmitsIt() {
        // cumulative reports tokens but not cost; cost must still come from the deltas.
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, turn = counts(cost = 0.05), cumulative = counts(total = 100)),
                    turn(agentA, "s1", 2, turn = counts(cost = 0.07), cumulative = counts(total = 200)),
                ),
            )
        assertEquals(200L, metrics.totals.totalTokens) // from cumulative max
        assertEquals(0.12, metrics.totals.costUsd, 1e-9) // from summed deltas
    }

    @Test
    fun distinctSessionsSumTogether() {
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, cumulative = counts(total = 100, cost = 0.10)),
                    turn(agentA, "s2", 1, cumulative = counts(total = 300, cost = 0.30)),
                ),
            )
        assertEquals(400L, metrics.totals.totalTokens)
        assertEquals(0.40, metrics.totals.costUsd, 1e-9)
        assertEquals(2, metrics.totalSessions)
    }

    @Test
    fun turnsWithNoSessionIdAreSeparateSingletons() {
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, null, 1, cumulative = counts(total = 100)),
                    turn(agentA, null, 2, cumulative = counts(total = 100)),
                ),
            )
        // Two sessionless turns must NOT collapse into one session (that would drop one).
        assertEquals(2, metrics.totalSessions)
        assertEquals(200L, metrics.totals.totalTokens)
    }

    @Test
    fun perAgentBreakdownSortedByCostDescending() {
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, cumulative = counts(total = 100, cost = 0.10), model = "haiku"),
                    turn(agentB, "s2", 1, cumulative = counts(total = 900, cost = 0.90), model = "opus"),
                ),
            )
        assertEquals(2, metrics.agents.size)
        assertEquals(agentB, metrics.agents[0].agentPubKey) // pricier agent first
        assertEquals(0.90, metrics.agents[0].totals.costUsd, 1e-9)
        assertEquals(setOf("opus"), metrics.agents[0].models)
        assertEquals("2026-07-01T00:00:00Z", metrics.agents[1].lastActivity)
    }

    @Test
    fun unreliableDeltaWithoutCumulativeFlagsEstimate() {
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, turn = counts(total = 50, cost = 0.05), deltaReliable = false),
                ),
            )
        assertEquals(50L, metrics.totals.totalTokens)
        assertTrue(metrics.hasUnreliableEstimates)
    }

    @Test
    fun cumulativePresenceMakesUnreliableDeltaIrrelevant() {
        // deltaReliable=false but cumulative is present → we use cumulative, no estimate flag.
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, turn = counts(total = 999), cumulative = counts(total = 50), deltaReliable = false),
                ),
            )
        assertEquals(50L, metrics.totals.totalTokens)
        assertFalse(metrics.hasUnreliableEstimates)
    }

    private fun cache(
        read: Long? = null,
        write: Long? = null,
    ) = TokenCounts(inputTokens = 100, cacheReadTokens = read, cacheWriteTokens = write)

    /** NIP-AM: an omitted cache component is unknown — the total must not become a fabricated 0. */
    @Test
    fun unreportedCacheStaysUnknown() {
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    turn(agentA, "s1", 1, turn = counts(input = 100)),
                    turn(agentA, "s1", 2, turn = counts(input = 100)),
                ),
            )
        assertNull(metrics.totals.cacheReadTokens)
        assertNull(metrics.totals.cacheWriteTokens)
        assertEquals(200L, metrics.totals.inputTokens)
    }

    @Test
    fun explicitZeroCacheIsKnownZero() {
        val metrics = AgentFleetAggregator.aggregate(listOf(turn(agentA, "s1", 1, turn = cache(read = 0, write = 0))))
        assertEquals(0L, metrics.totals.cacheReadTokens)
        assertEquals(0L, metrics.totals.cacheWriteTokens)
    }

    @Test
    fun cacheUsesMaxCumulativeThenReportedDeltas() {
        val metrics =
            AgentFleetAggregator.aggregate(
                listOf(
                    // Session s1 reports cumulatively; s2 only per turn, and only for reads.
                    turn(agentA, "s1", 1, cumulative = cache(read = 40, write = 5)),
                    turn(agentA, "s1", 2, cumulative = cache(read = 90, write = 5)),
                    turn(agentA, "s2", 3, turn = cache(read = 7)),
                    turn(agentA, "s2", 4, turn = cache(read = 3)),
                    // Another agent that never reports cache at all.
                    turn(agentB, "s3", 5, turn = counts(input = 1)),
                ),
            )
        val a = metrics.agents.single { it.agentPubKey == agentA }
        assertEquals(100L, a.totals.cacheReadTokens)
        assertEquals(5L, a.totals.cacheWriteTokens)

        val b = metrics.agents.single { it.agentPubKey == agentB }
        assertNull(b.totals.cacheReadTokens)

        // Unknown sessions add nothing to the fleet total, but don't erase the known ones.
        assertEquals(100L, metrics.totals.cacheReadTokens)
        assertEquals(5L, metrics.totals.cacheWriteTokens)
    }

    @Test
    fun tokenTotalsAddUnknownAsIdentity() {
        val known = TokenTotals(cacheReadTokens = 3)
        val unknown = TokenTotals()
        assertEquals(3L, (known + unknown).cacheReadTokens)
        assertEquals(3L, (unknown + known).cacheReadTokens)
        assertNull((unknown + unknown).cacheReadTokens)
        assertEquals(6L, (known + known).cacheReadTokens)
    }
}
