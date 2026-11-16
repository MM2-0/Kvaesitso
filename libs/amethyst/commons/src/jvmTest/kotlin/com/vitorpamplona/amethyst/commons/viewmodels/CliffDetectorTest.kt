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
package com.vitorpamplona.amethyst.commons.viewmodels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/**
 * Unit tests for the [computeStalledSpeakers] predicate that drives the
 * listener-side cliff detector in [NestViewModel]. The predicate is
 * extracted as a pure function specifically so we can drive it with a
 * [TestTimeSource] — neither `runTest`'s virtual scheduler nor a real
 * monotonic clock would let us deterministically place events relative
 * to the 4 s `ROOM_AUDIO_CLIFF_TIMEOUT_MS` threshold.
 *
 * The detector defends against the moq-rs production-relay forward-
 * queue cliff documented in
 * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`:
 * the relay opens fewer (or zero) new uni streams to a slow
 * subscriber while the announce stream still says the broadcast is
 * Active and the subscribe bidi is still alive at QUIC level — no
 * other layer notices. These tests pin the recycle gate so a future
 * refactor doesn't accidentally widen or narrow when we recycle.
 */
@OptIn(ExperimentalTime::class)
class CliffDetectorTest {
    @Test
    fun returnsEmptyWhenNoSpeakersActive() {
        val ts = TestTimeSource()
        val result =
            computeStalledSpeakers(
                activeSpeakers = emptySet(),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to ts.markNow()),
                lastRecycleAt = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresSpeakerNotInAnnounced() {
        // A speaker we're subscribed to but who isn't in the relay's
        // announce-stream output is most likely either (a) already
        // off stage and the relay just hasn't propagated the Ended
        // yet, or (b) in the brief gap between an Ended and the
        // wrapper re-issuing. Recycling the whole transport here
        // would be wasteful — we only act on confirmed cliffs.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 10_000.milliseconds // way past threshold

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = emptySet(),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresSpeakerWithNoFrameYet() {
        // Brand-new subscription that hasn't ramped up: lastFrameAt
        // has no entry. Don't recycle — the wrapper's per-speaker
        // re-issue + opener-throws backoff already retries on its
        // own; the cliff detector only acts once we've proven the
        // subscription was working.
        val ts = TestTimeSource()
        ts += 10_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = emptyMap(),
                lastRecycleAt = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyJustBeforeThreshold() {
        // Boundary case: 1 ms under the 2.5 s threshold. The detector
        // should still consider this healthy. Relevant because audio
        // groups arrive at ~1 s cadence (framesPerGroup=50), so a
        // false positive at ≈ threshold would recycle on every
        // group-rollover hiccup.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 2_499.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsStalledSpeakerAtThresholdInclusive() {
        // Exactly at threshold: include. The check is `>=`, not `>`.
        // Tested explicitly because boundary semantics here are
        // user-visible (this is the "audio went silent" detection).
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 2_500.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun returnsStalledSpeakerWellPastThreshold() {
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 30_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun returnsAllStalledSpeakersAtOnceMixedWithFreshOnes() {
        // Multi-speaker stage: alice and bob have stalled, charlie's
        // subscription is fresh (just received a frame). Recycle
        // should fire for the stalled set and leave charlie alone —
        // the `recycleSession` itself takes the whole listener down,
        // but the test here is the predicate's classification.
        // Threshold = 2.5 s default. Spacings: alice/bob 4 s old (past),
        // charlie 1.5 s old (under).
        val ts = TestTimeSource()
        val aliceFrame = ts.markNow()
        val bobFrame = ts.markNow()
        ts += 2_500.milliseconds
        val charlieFrame = ts.markNow()
        ts += 1_500.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE, BOB, CHARLIE),
                announcedSpeakers = setOf(ALICE, BOB, CHARLIE),
                lastFrameAt =
                    mapOf(
                        ALICE to aliceFrame,
                        BOB to bobFrame,
                        CHARLIE to charlieFrame,
                    ),
                lastRecycleAt = null,
            )
        assertEquals(setOf(ALICE, BOB), result.toSet())
    }

    @Test
    fun postRecycleGraceSuppressesEvenAtAttemptZero() {
        // Inside the 3 s post-recycle handshake window, never recycle
        // — the wrapper is still tearing down + reopening QUIC, so
        // "no frames since recycle" is just the handshake, not a
        // relay-side cliff.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds
        val recycleMark = ts.markNow()
        ts += 2_000.milliseconds // inside 3 s grace

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 0,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun attemptZeroFiresImmediatelyOnceGracePasses() {
        // After a recovered-then-restall pattern: counter has been
        // reset by `onSpeakerActivity`, so even though there's a
        // prior recycleMark, the next cliff fires as soon as the
        // 3 s grace passes — no extended backoff.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds
        val recycleMark = ts.markNow()
        ts += 3_500.milliseconds // past 3 s grace, attempt=0 schedule = 0 ms

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 0,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun attemptOneBackoffSuppressesUntilFiveSeconds() {
        // First failed recycle: schedule says wait 5 s before next.
        // 4 s in: still suppressed.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds
        val recycleMark = ts.markNow()
        ts += 4_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 1,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun attemptOneBackoffReleasesAtFiveSeconds() {
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds
        val recycleMark = ts.markNow()
        ts += 5_000.milliseconds // exactly at attempt-1 boundary

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 1,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun attemptTwoBackoffSuppressesUntilTwelveSeconds() {
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds
        val recycleMark = ts.markNow()
        ts += 11_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 2,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun attemptTwoBackoffReleasesPastTwelveSeconds() {
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds
        val recycleMark = ts.markNow()
        ts += 12_500.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 2,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun attemptFourCapsAtThirtySecondMax() {
        // Fourth and beyond consecutive failed recycle: backoff
        // saturates at the 30 s cap (matching the original flat
        // cooldown — by this point we ARE the moq-rs-protection
        // case the old constant existed for).
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 4_500.milliseconds
        val recycleMark = ts.markNow()
        ts += 25_000.milliseconds // past attempt-3 (24 s), inside attempt-4 cap (30 s)

        val resultStillSuppressed =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 4,
            )
        assertTrue(resultStillSuppressed.isEmpty())

        ts += 6_000.milliseconds // now past 30 s cap
        val resultReleased =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 4,
            )
        assertEquals(listOf(ALICE), resultReleased)
    }

    @Test
    fun customBackoffFunctionIsHonored() {
        // A test can override the schedule (e.g. shorter intervals
        // for unit tests that don't want to march through 30 s of
        // virtual time) without mutating the production constants.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 3_000.milliseconds
        val recycleMark = ts.markNow()
        ts += 1_000.milliseconds // past grace=500, past tightBackoff(1)=750

        val tightBackoff = { attempt: Int -> if (attempt <= 0) 0L else 750L }
        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = recycleMark,
                consecutiveFailedRecycles = 1,
                postRecycleGraceMs = 500L,
                backoffForAttempt = tightBackoff,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun defaultBackoffSchedulePinsValues() {
        // Pin the production schedule so a future tweak is visible
        // in code review. attempt 0 → immediate, 1 → 5 s, 2 → 12 s,
        // 3 → 24 s, 4+ → 30 s cap. Cumulative wall-clock to the
        // Nth recycle: 0, 5, 17, 41, 71 s — slower than the
        // 4-recycles-in-30 s pattern that wedged moq-rs in
        // commit ea08c43.
        assertEquals(0L, defaultCliffBackoffMs(0))
        assertEquals(5_000L, defaultCliffBackoffMs(1))
        assertEquals(12_000L, defaultCliffBackoffMs(2))
        assertEquals(24_000L, defaultCliffBackoffMs(3))
        assertEquals(30_000L, defaultCliffBackoffMs(4))
        assertEquals(30_000L, defaultCliffBackoffMs(10))
    }

    @Test
    fun customTimeoutsAreHonored() {
        // Defaults are wired into the production VM, but the function
        // accepts overrides so a test for tighter / looser tolerances
        // can drive it without mutating the constants.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 1_000.milliseconds

        val withDefault =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertTrue(withDefault.isEmpty(), "1 s elapsed shouldn't trip 2.5 s default")

        val withTightTimeout =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
                cliffTimeoutMs = 500L,
            )
        assertEquals(listOf(ALICE), withTightTimeout, "1 s elapsed should trip a 500 ms threshold")
    }

    @Test
    fun activeSpeakerNotInLastFrameAtIsIgnoredEvenWithStalledPeers() {
        // Mixed: alice is announced + active + stalled, bob is
        // announced + active but has no frame yet. Detector should
        // return alice only — bob hasn't proven the subscription
        // works, so it's not "stalled" yet, just slow to start.
        val ts = TestTimeSource()
        val aliceFrame = ts.markNow()
        ts += 5_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE, BOB),
                announcedSpeakers = setOf(ALICE, BOB),
                lastFrameAt = mapOf(ALICE to aliceFrame),
                lastRecycleAt = null,
            )
        assertEquals(listOf(ALICE), result)
    }

    @Test
    fun nullLastRecycleNeverSuppresses() {
        // Null `lastRecycleAt` means "we've never recycled" — the
        // very first cliff event of a session must fire. The
        // cooldown check is gated on non-null.
        val ts = TestTimeSource()
        val frameMark = ts.markNow()
        ts += 5_000.milliseconds

        val result =
            computeStalledSpeakers(
                activeSpeakers = setOf(ALICE),
                announcedSpeakers = setOf(ALICE),
                lastFrameAt = mapOf(ALICE to frameMark),
                lastRecycleAt = null,
            )
        assertEquals(listOf(ALICE), result)
    }

    companion object {
        private val ALICE = "a".repeat(64)
        private val BOB = "b".repeat(64)
        private val CHARLIE = "c".repeat(64)
    }
}
