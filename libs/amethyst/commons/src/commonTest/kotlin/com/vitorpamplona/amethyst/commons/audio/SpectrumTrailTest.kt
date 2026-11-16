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
package com.vitorpamplona.amethyst.commons.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pacing, starvation, backlog and decay rules of [SpectrumTrail]; see its KDoc for why each exists. */
class SpectrumTrailTest {
    private val ms = 1_000_000L

    private fun frame(
        value: Float,
        durationMs: Long = 20,
    ) = Spectrum(floatArrayOf(value), durationNanos = durationMs * ms)

    @Test
    fun yieldsNothingWhenStarvedSoTheDrawnFrameIsHeld() {
        val trail = SpectrumTrail(decay = 0.5f)
        assertNull(trail.nextOrNull(0))
    }

    @Test
    fun theFirstFrameIsDrawnOnArrivalWithNothingToDecayFrom() {
        val trail = SpectrumTrail(decay = 0.5f)
        trail.offer(frame(1f))

        assertContentEquals(floatArrayOf(1f), trail.nextOrNull(0))
    }

    @Test
    fun aClusterIsSpreadOverTheAudioTimeItCoversNotTheScreenRefresh() {
        val trail = SpectrumTrail(decay = 0f)
        trail.offer(frame(1f))
        trail.offer(frame(2f))
        trail.offer(frame(3f))

        // 120 Hz refreshes (~8 ms) must not drain a frame that covers 20 ms of audio.
        assertContentEquals(floatArrayOf(1f), trail.nextOrNull(0))
        assertNull(trail.nextOrNull(8 * ms))
        assertNull(trail.nextOrNull(16 * ms))
        assertContentEquals(floatArrayOf(2f), trail.nextOrNull(20 * ms))
        assertNull(trail.nextOrNull(33 * ms))
        assertContentEquals(floatArrayOf(3f), trail.nextOrNull(40 * ms))
        assertNull(trail.nextOrNull(48 * ms))
    }

    @Test
    fun afterStarvingTheNextClusterStartsWhenItArrivesInsteadOfBeingDumpedToCatchUp() {
        val trail = SpectrumTrail(decay = 0f)
        trail.offer(frame(1f))
        assertContentEquals(floatArrayOf(1f), trail.nextOrNull(0))
        assertNull(trail.nextOrNull(100 * ms)) // starved for a while

        trail.offer(frame(2f))
        trail.offer(frame(3f))

        // The idle time is not owed: frame 2 shows now and frame 3 a full frame later.
        assertContentEquals(floatArrayOf(2f), trail.nextOrNull(300 * ms))
        assertNull(trail.nextOrNull(308 * ms))
        assertContentEquals(floatArrayOf(3f), trail.nextOrNull(320 * ms))
    }

    @Test
    fun aFrameArrivingBeforeThePreviousOneElapsedStillWaitsItsTurn() {
        val trail = SpectrumTrail(decay = 0f)
        trail.offer(frame(1f))
        assertContentEquals(floatArrayOf(1f), trail.nextOrNull(0))
        assertNull(trail.nextOrNull(8 * ms)) // queue momentarily empty

        trail.offer(frame(2f))

        assertNull(trail.nextOrNull(16 * ms)) // frame 1 still covers until 20 ms
        assertContentEquals(floatArrayOf(2f), trail.nextOrNull(24 * ms))
    }

    @Test
    fun aSlowFrameClockJumpsToTheLatestDueFrame() {
        val trail = SpectrumTrail(decay = 0f)
        trail.offer(frame(1f))
        trail.offer(frame(2f))
        trail.offer(frame(3f))

        assertContentEquals(floatArrayOf(1f), trail.nextOrNull(0))
        // A janky 45 ms frame: frames 2 (due 20) and 3 (due 40) are both due; show the newest.
        assertContentEquals(floatArrayOf(3f), trail.nextOrNull(45 * ms))
    }

    @Test
    fun aFrameWithNoDurationIsShownOnArrival() {
        // Producers that do not declare a duration (the synthetic preview emits one per display
        // frame) keep the old behaviour: whatever is queued is current.
        val trail = SpectrumTrail(decay = 0f)
        trail.offer(Spectrum(floatArrayOf(1f)))
        trail.offer(Spectrum(floatArrayOf(2f)))

        assertContentEquals(floatArrayOf(2f), trail.nextOrNull(0))
    }

    @Test
    fun aBacklogIsCappedByDroppingTheStalestFrame() {
        // Faster-than-real-time playback (2x speed) produces frames faster than they come due;
        // the cap bounds how far the picture can lag the sound.
        val trail = SpectrumTrail(decay = 0f, capacity = 2)
        trail.offer(frame(1f))
        trail.offer(frame(2f))
        trail.offer(frame(3f)) // evicts frame 1

        assertContentEquals(floatArrayOf(2f), trail.nextOrNull(0))
        assertContentEquals(floatArrayOf(3f), trail.nextOrNull(20 * ms))
        assertNull(trail.nextOrNull(40 * ms))
    }

    @Test
    fun aRisingBinTakesItsNewValueImmediately() {
        val trail = SpectrumTrail(decay = 0.5f)
        trail.offer(frame(0.2f))
        trail.nextOrNull(0)
        trail.offer(frame(0.9f))

        assertContentEquals(floatArrayOf(0.9f), trail.nextOrNull(20 * ms))
    }

    @Test
    fun aFallingBinDecaysFromWhatWasDrawnRatherThanSnapping() {
        val trail = SpectrumTrail(decay = 0.5f)
        trail.offer(frame(1f))
        trail.nextOrNull(0)
        trail.offer(frame(0f))

        assertContentEquals(floatArrayOf(0.5f), trail.nextOrNull(20 * ms))
    }

    @Test
    fun eachDrawGetsAFreshArraySoComposeSeesTheChange() {
        val trail = SpectrumTrail(decay = 0.5f)
        trail.offer(frame(1f))
        trail.offer(frame(1f))

        val first = trail.nextOrNull(0)
        val second = trail.nextOrNull(20 * ms)

        // mutableStateOf compares by reference; reusing one array would never trigger a redraw.
        assertNotSame(first, second)
    }

    @Test
    fun reportsEmptyOnlyOnceEveryQueuedFrameIsReleasedSoTheCallerKnowsWhenToPark() {
        val trail = SpectrumTrail(decay = 0f)
        assertTrue(trail.isEmpty())

        trail.offer(frame(1f))
        trail.offer(frame(2f))
        assertFalse(trail.isEmpty())

        trail.nextOrNull(0)
        assertFalse(trail.isEmpty()) // frame 2 is queued but not yet due
        trail.nextOrNull(20 * ms)
        assertTrue(trail.isEmpty())
    }
}
