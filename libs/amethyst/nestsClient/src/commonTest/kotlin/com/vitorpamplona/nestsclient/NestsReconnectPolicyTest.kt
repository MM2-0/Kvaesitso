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
package com.vitorpamplona.nestsclient

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NestsReconnectPolicyTest {
    @Test
    fun firstAttemptUsesInitialDelay() {
        val p = NestsReconnectPolicy(initialDelayMs = 1_000, multiplier = 2.0, maxDelayMs = 30_000, jitter = 0.0)
        assertEquals(1_000, p.delayForAttempt(1))
    }

    @Test
    fun delayDoublesPerAttemptUntilMax() {
        val p = NestsReconnectPolicy(initialDelayMs = 1_000, multiplier = 2.0, maxDelayMs = 30_000, jitter = 0.0)
        assertEquals(2_000, p.delayForAttempt(2))
        assertEquals(4_000, p.delayForAttempt(3))
        assertEquals(8_000, p.delayForAttempt(4))
        assertEquals(16_000, p.delayForAttempt(5))
        // Step 6 doubles to 32_000 — clamps at maxDelayMs.
        assertEquals(30_000, p.delayForAttempt(6))
        // Beyond ceiling: still clamped.
        assertEquals(30_000, p.delayForAttempt(20))
    }

    @Test
    fun nonStandardMultiplierStillRespectsCeiling() {
        val p = NestsReconnectPolicy(initialDelayMs = 500, multiplier = 3.0, maxDelayMs = 5_000, jitter = 0.0)
        assertEquals(500, p.delayForAttempt(1))
        assertEquals(1_500, p.delayForAttempt(2))
        assertEquals(4_500, p.delayForAttempt(3))
        // 4_500 × 3 = 13_500 → clamps at 5_000.
        assertEquals(5_000, p.delayForAttempt(4))
    }

    @Test
    fun zeroOrNegativeAttemptIsZeroDelay() {
        val p = NestsReconnectPolicy()
        assertEquals(0L, p.delayForAttempt(0))
        assertEquals(0L, p.delayForAttempt(-1))
    }

    @Test
    fun jitter_spreads_delay_into_expected_band() {
        // Equal-jitter contract: delay is uniformly distributed on
        // [(1 - jitter) * base, base]. With jitter=0.5 and base=1000,
        // values must land in [500, 1000]. Sample a few times with a
        // seeded RNG so the test is deterministic.
        val p = NestsReconnectPolicy(initialDelayMs = 1_000, jitter = 0.5)
        val rng = Random(0xC0FFEE)
        repeat(50) {
            val d = p.delayForAttempt(1, rng)
            assertTrue(d in 500..1_000, "expected jittered delay in [500, 1000], got $d")
        }
    }

    @Test
    fun jitter_zero_yields_deterministic_base() {
        val p = NestsReconnectPolicy(initialDelayMs = 1_000, jitter = 0.0)
        // Random source is consulted but ignored when jitter == 0.
        repeat(10) {
            assertEquals(1_000, p.delayForAttempt(1, Random(it.toLong())))
        }
    }

    @Test
    fun jitter_one_can_collapse_to_zero_or_full_base() {
        val p = NestsReconnectPolicy(initialDelayMs = 1_000, jitter = 1.0)
        // jitter=1.0 → low bound is 0 → delay can be anywhere in [0, 1000].
        val rng = Random(42)
        repeat(50) {
            val d = p.delayForAttempt(1, rng)
            assertTrue(d in 0..1_000, "expected delay in [0, 1000], got $d")
        }
    }

    @Test
    fun isExhaustedHonoursMaxAttempts() {
        val p = NestsReconnectPolicy(maxAttempts = 3)
        assertFalse(p.isExhausted(0))
        assertFalse(p.isExhausted(1))
        assertFalse(p.isExhausted(2))
        assertTrue(p.isExhausted(3))
        assertTrue(p.isExhausted(4))
    }

    @Test
    fun noRetryPolicyExhaustsImmediatelyAfterFirstAttempt() {
        // The "first-shot-or-fail" policy used by tests / single-shot demos.
        val p = NestsReconnectPolicy.NoRetry
        assertFalse(p.isExhausted(0))
        assertTrue(p.isExhausted(1))
    }

    @Test
    fun rejectsInvalidConstructorInputs() {
        assertFailsWith<IllegalArgumentException> { NestsReconnectPolicy(initialDelayMs = 0) }
        assertFailsWith<IllegalArgumentException> { NestsReconnectPolicy(multiplier = 1.0) }
        assertFailsWith<IllegalArgumentException> {
            NestsReconnectPolicy(initialDelayMs = 5_000, maxDelayMs = 1_000)
        }
        assertFailsWith<IllegalArgumentException> { NestsReconnectPolicy(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { NestsReconnectPolicy(jitter = -0.1) }
        assertFailsWith<IllegalArgumentException> { NestsReconnectPolicy(jitter = 1.1) }
    }
}
