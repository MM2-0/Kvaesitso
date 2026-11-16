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
package com.vitorpamplona.amethyst.commons.ui.insets

import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The keyboard-sized gap that outlives the keyboard is a *frozen* IME inset, not a wrong one: the
 * animated value and the animation target disagree and then stop moving. These pin the one thing
 * that separates that from a perfectly normal close animation, which disagrees just as much for
 * every frame it runs — the clock.
 *
 * See [SafeImeInsets] for why the raw inset freezes and why nothing recovers it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SafeImeInsetsTest {
    private val keyboardHeight = 1200

    @Test
    fun aSteadyKeyboardIsNeverStranded() =
        runTest {
            val seen = mutableListOf<Boolean>()

            watchForStrandedIme(flow { emit(ImeInsetSample(keyboardHeight, keyboardHeight)) }) { seen.add(it) }
            advanceUntilIdle()

            assertEquals(listOf(false), seen)
        }

    @Test
    fun aCloseAnimationIsNeverStranded() =
        runTest {
            // The target drops to zero on the first frame and the animated value walks down to meet
            // it. Every frame of that walk disagrees with the target — the state the watchdog must
            // not mistake for a freeze.
            val seen = mutableListOf<Boolean>()
            val samples = MutableSharedFlow<ImeInsetSample>()
            val job = launch { watchForStrandedIme(samples) { seen.add(it) } }
            runCurrent()

            for (height in keyboardHeight downTo 0 step 100) {
                samples.emit(ImeInsetSample(height, 0))
                advanceTimeBy(16)
            }
            advanceUntilIdle()

            assertEquals("only the final, settled frame reports", listOf(false), seen)
            job.cancel()
        }

    @Test
    fun anAnimationThatStopsHalfWayIsStranded() =
        runTest {
            // A cancelled IME animation leaves Compose's listener wedged: the last frame it managed
            // to deliver is the last one that will ever arrive, and it is not the target.
            val seen = mutableListOf<Boolean>()
            val samples = MutableSharedFlow<ImeInsetSample>()
            val job = launch { watchForStrandedIme(samples) { seen.add(it) } }
            runCurrent()

            samples.emit(ImeInsetSample(keyboardHeight, 0))

            advanceTimeBy(IME_STRAND_GRACE_MS - 1)
            assertEquals("still inside the grace period", emptyList<Boolean>(), seen)

            advanceUntilIdle()
            assertEquals(listOf(true), seen)
            job.cancel()
        }

    @Test
    fun aFrozenZeroUnderAVisibleKeyboardIsAlsoStranded() =
        runTest {
            // The freeze has no direction: the same wedged listener can strand the inset at zero
            // while the keyboard is back up, which loses the padding instead of leaving it behind.
            val seen = mutableListOf<Boolean>()
            val samples = MutableSharedFlow<ImeInsetSample>()
            val job = launch { watchForStrandedIme(samples) { seen.add(it) } }
            runCurrent()

            samples.emit(ImeInsetSample(0, keyboardHeight))
            advanceUntilIdle()

            assertEquals(listOf(true), seen)
            job.cancel()
        }

    @Test
    fun aStrandedInsetRecoversWhenTheWindowStartsReportingAgain() =
        runTest {
            val seen = mutableListOf<Boolean>()
            val samples = MutableSharedFlow<ImeInsetSample>()
            val job = launch { watchForStrandedIme(samples) { seen.add(it) } }
            runCurrent()

            samples.emit(ImeInsetSample(keyboardHeight, 0))
            advanceUntilIdle()
            assertEquals(listOf(true), seen)

            // Whatever later inset pass rebalances the holder, agreement is the all-clear.
            samples.emit(ImeInsetSample(0, 0))
            advanceUntilIdle()

            assertEquals(listOf(true, false), seen)
            job.cancel()
        }

    @Test
    fun aSlowFirstFrameDoesNotStrandTheInset() =
        runTest {
            // The target is published from onApplyWindowInsets before the animation delivers its
            // first onProgress. On a janky device that gap can stretch; the grace has to cover it.
            val seen = mutableListOf<Boolean>()
            val samples = MutableSharedFlow<ImeInsetSample>()
            val job = launch { watchForStrandedIme(samples) { seen.add(it) } }
            runCurrent()

            samples.emit(ImeInsetSample(keyboardHeight, 0))
            advanceTimeBy(IME_STRAND_GRACE_MS / 2)

            samples.emit(ImeInsetSample(keyboardHeight / 2, 0))
            advanceTimeBy(IME_STRAND_GRACE_MS / 2)
            samples.emit(ImeInsetSample(0, 0))
            advanceUntilIdle()

            assertEquals("the restarted wait must never have fired", listOf(false), seen)
            job.cancel()
        }

    @Test
    fun theWatchdogIsIdleWhileTheReadingsAgree() =
        runTest {
            // Every imePaddingSafe() call site parks one of these. It must not hold a timer open
            // for the 99% of the time the keyboard is not animating.
            val seen = mutableListOf<Boolean>()
            val samples = MutableSharedFlow<ImeInsetSample>()
            val job = launch { watchForStrandedIme(samples) { seen.add(it) } }
            runCurrent()

            samples.emit(ImeInsetSample(0, 0))
            advanceUntilIdle()

            assertEquals(listOf(false), seen)
            assertEquals("agreement must not park a timer", 0L, testScheduler.currentTime)
            job.cancel()
        }

    @Test
    fun aReopenWhileStrandedCorrectsImmediately() =
        runTest {
            // While wedged, the animated value never moves, so a keyboard coming back up shows up
            // only in the target. That must not wait out another grace period before the padding
            // follows it.
            val seen = mutableListOf<Boolean>()
            val samples = MutableSharedFlow<ImeInsetSample>()
            val job = launch { watchForStrandedIme(samples) { seen.add(it) } }
            runCurrent()

            samples.emit(ImeInsetSample(keyboardHeight, 0))
            advanceUntilIdle()
            assertEquals(listOf(true), seen)

            samples.emit(ImeInsetSample(keyboardHeight, keyboardHeight))
            delay(1)

            assertEquals("agreement is trusted at once", listOf(true, false), seen)
            job.cancel()
        }

    // --- what the padding actually measures against ---

    private class FixedInsets(
        val bottom: Int,
    ) : WindowInsets {
        override fun getLeft(
            density: Density,
            layoutDirection: LayoutDirection,
        ) = 0

        override fun getTop(density: Density) = 0

        override fun getRight(
            density: Density,
            layoutDirection: LayoutDirection,
        ) = 0

        override fun getBottom(density: Density) = bottom
    }

    @Test
    fun theAnimatedInsetIsUsedUntilItIsKnownToBeStale() {
        val density = Density(1f)
        val insets = SafeImeInsets(FixedInsets(keyboardHeight), FixedInsets(0))

        assertEquals("the animated value drives every ordinary frame", keyboardHeight, insets.getBottom(density))

        insets.isStranded = true
        assertEquals("the gap the keyboard left behind closes", 0, insets.getBottom(density))
    }

    @Test
    fun aStrandedZeroFallsBackToTheKeyboardHeight() {
        // The other direction of the same freeze: correcting to zero would be wrong here, which is
        // why the fallback is the target rather than a hard-coded no-padding.
        val density = Density(1f)
        val insets = SafeImeInsets(FixedInsets(0), FixedInsets(keyboardHeight))

        insets.isStranded = true
        assertEquals(keyboardHeight, insets.getBottom(density))
    }

    // --- one flag per window ---

    @Test
    fun everyCallSiteInAWindowGetsTheSameInstance() {
        // isStranded describes the window's insets listener, not a layout. Two instances would run
        // two independent grace timers and could hold different answers across a frame — which is
        // exactly the case DisappearingScaffold hits, padding with one reading and subtracting the
        // other.
        val view = mockk<View>(relaxed = true)

        val first = SafeImeInsets.forView(view, FixedInsets(0), FixedInsets(0))
        val second = SafeImeInsets.forView(view, FixedInsets(keyboardHeight), FixedInsets(0))

        assertSame("a second call site must not build its own flag", first, second)
    }

    @Test
    fun aDialogWindowGetsItsOwnInstance() {
        // A Dialog composes against its own window, so it must not inherit the host activity's
        // reading. Keying on the view is what buys that; a CompositionLocal would not.
        val activity = mockk<View>(relaxed = true)
        val dialog = mockk<View>(relaxed = true)

        assertNotSame(
            SafeImeInsets.forView(activity, FixedInsets(0), FixedInsets(0)),
            SafeImeInsets.forView(dialog, FixedInsets(0), FixedInsets(0)),
        )
    }

    @Test
    fun theStrandedFlagSurvivesACallSiteBeingRebuilt() {
        // DisappearingScaffold rebuilds its group when canHideBars toggles. Before the per-window
        // cache that dropped isStranded back to false and put the stale gap back on screen until a
        // fresh watchdog re-detected it.
        val view = mockk<View>(relaxed = true)
        val insets = SafeImeInsets.forView(view, FixedInsets(keyboardHeight), FixedInsets(0))
        insets.isStranded = true

        val afterRebuild = SafeImeInsets.forView(view, FixedInsets(keyboardHeight), FixedInsets(0))

        assertEquals("the correction must not be forgotten", true, afterRebuild.isStranded)
        assertEquals(0, afterRebuild.getBottom(Density(1f)))
    }
}
