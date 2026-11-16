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

/**
 * Exponential-backoff settings for the reconnect path that
 * [connectNestsListener] / [connectNestsSpeaker] consult when the
 * underlying WebTransport session drops mid-room. Mirrors the
 * `delay: { initial, multiplier, max }` shape kixelated/moq's JS
 * reference uses.
 *
 * Defaults match the JS reference (1 s → 30 s ceiling, doubling).
 * `maxAttempts` defaults to unbounded — a long-running room should
 * keep trying as long as the user hasn't left the screen; the
 * Composable's `DisposableEffect.onDispose` is the cancel signal.
 *
 * **Jitter**: when the relay restarts, every reconnecting client is
 * mid-backoff at the same step. Without jitter they all retry on the
 * same `1 s, 2 s, 4 s, …` schedule and hammer the relay during
 * recovery. [jitter] applies AWS-style "equal jitter" to spread the
 * herd: `delay ∈ [(1 - jitter) × base, base]`. 0.0 disables (used by
 * deterministic tests); the default of 0.3 is a 30 % spread.
 */
data class NestsReconnectPolicy(
    val initialDelayMs: Long = 1_000,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 30_000,
    val maxAttempts: Int = Int.MAX_VALUE,
    val jitter: Double = 0.3,
) {
    init {
        require(initialDelayMs > 0) { "initialDelayMs must be > 0, got $initialDelayMs" }
        require(multiplier > 1.0) { "multiplier must be > 1.0 to grow, got $multiplier" }
        require(maxDelayMs >= initialDelayMs) {
            "maxDelayMs ($maxDelayMs) must be >= initialDelayMs ($initialDelayMs)"
        }
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, got $maxAttempts" }
        require(jitter in 0.0..1.0) { "jitter must be in [0.0, 1.0], got $jitter" }
    }

    /**
     * Delay for the [attempt]-th retry (1-indexed). attempt=1 →
     * [initialDelayMs]; subsequent attempts multiply by [multiplier]
     * and clamp at [maxDelayMs]. attempt < 1 returns 0.
     *
     * When [jitter] > 0, returns a uniformly-random value in
     * `[(1 - jitter) × base, base]` using [random] as the source.
     * Tests pass a deterministic source; production callers use the
     * default [delayForAttempt] which seeds from [Random.Default].
     */
    fun delayForAttempt(
        attempt: Int,
        random: Random = Random.Default,
    ): Long {
        if (attempt < 1) return 0L
        // Compute attempt-1 doublings so attempt=1 returns initial.
        var d = initialDelayMs.toDouble()
        repeat(attempt - 1) { d *= multiplier }
        val base = d.coerceAtMost(maxDelayMs.toDouble())
        if (jitter <= 0.0) return base.toLong()
        val low = base * (1.0 - jitter)
        return (low + (base - low) * random.nextDouble()).toLong()
    }

    /** True when [attempt] has hit [maxAttempts] (the next retry is forbidden). */
    fun isExhausted(attempt: Int): Boolean = attempt >= maxAttempts

    companion object {
        /** Off-switch for callers that want first-shot-or-fail (tests, single-room demos). */
        val NoRetry = NestsReconnectPolicy(maxAttempts = 1, jitter = 0.0)
    }
}
