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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import com.vitorpamplona.amethyst.commons.ui.playback.controls.MuteAction
import com.vitorpamplona.amethyst.commons.ui.playback.controls.computeLevel
import com.vitorpamplona.amethyst.commons.ui.playback.controls.levelToVolumeIndex
import com.vitorpamplona.amethyst.commons.ui.playback.controls.muteActionFor
import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenSwipeMathTest {
    @Test
    fun dragUpIncreasesLevel() {
        // Drag up 500px (negative) on a 1000px screen from 0.2 -> +0.5 = 0.7
        assertEquals(0.7f, computeLevel(0.2f, -500f, 1000f), 0.0001f)
    }

    @Test
    fun dragDownDecreasesLevel() {
        assertEquals(0.3f, computeLevel(0.8f, 500f, 1000f), 0.0001f)
    }

    @Test
    fun clampsToOne() {
        assertEquals(1f, computeLevel(0.9f, -500f, 1000f), 0.0001f)
    }

    @Test
    fun clampsToZero() {
        assertEquals(0f, computeLevel(0.1f, 500f, 1000f), 0.0001f)
    }

    @Test
    fun zeroHeightReturnsStartClamped() {
        assertEquals(0.5f, computeLevel(0.5f, -100f, 0f), 0.0001f)
    }

    @Test
    fun zeroHeightClampsOutOfRangeStartLevel() {
        assertEquals(1f, computeLevel(1.5f, -100f, 0f), 0.0001f)
        assertEquals(0f, computeLevel(-0.3f, 100f, 0f), 0.0001f)
    }

    @Test
    fun volumeIndexExactMidpoint() {
        assertEquals(5, levelToVolumeIndex(0.5f, 10))
    }

    @Test
    fun volumeIndexFull() {
        assertEquals(15, levelToVolumeIndex(1f, 15))
    }

    @Test
    fun volumeIndexZeroLevel() {
        assertEquals(0, levelToVolumeIndex(0f, 15))
    }

    @Test
    fun volumeIndexZeroMaxGuard() {
        assertEquals(0, levelToVolumeIndex(0.5f, 0))
    }

    @Test
    fun reachingZeroWhileUnmutedMutes() {
        assertEquals(MuteAction.Mute, muteActionFor(level = 0f, movedUp = false, isMuted = false))
    }

    @Test
    fun reachingZeroWhileMutedIsNoop() {
        assertEquals(MuteAction.None, muteActionFor(level = 0f, movedUp = false, isMuted = true))
    }

    @Test
    fun movingUpWhileMutedUnmutes() {
        assertEquals(MuteAction.Unmute, muteActionFor(level = 0.5f, movedUp = true, isMuted = true))
    }

    @Test
    fun movingUpAtMaxWhileMutedStillUnmutes() {
        // Device volume pinned at 1.0 can't rise, but the upward drag still expresses intent.
        assertEquals(MuteAction.Unmute, muteActionFor(level = 1f, movedUp = true, isMuted = true))
    }

    @Test
    fun movingUpWhileUnmutedIsNoop() {
        assertEquals(MuteAction.None, muteActionFor(level = 0.5f, movedUp = true, isMuted = false))
    }

    @Test
    fun movingDownAboveZeroWhileMutedIsNoop() {
        assertEquals(MuteAction.None, muteActionFor(level = 0.5f, movedUp = false, isMuted = true))
    }
}
