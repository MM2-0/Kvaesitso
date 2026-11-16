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

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpectrumDelayTest {
    private fun frames(n: Int) = (0 until n).map { Spectrum(floatArrayOf(it.toFloat())) }

    @Test
    fun delaysByFrameCountHoldingBackTheLatest() =
        runTest {
            // 10 frames in, hold back the latest 3 → 7 emitted, each shifted 3 hops earlier.
            val out = frames(10).asFlow().delayedByFrames(3).toList()
            assertEquals(7, out.size)
            assertEquals(0f, out.first().bins[0])
            assertEquals(6f, out.last().bins[0])
        }

    @Test
    fun zeroDelayIsIdentity() =
        runTest {
            val out = frames(4).asFlow().delayedByFrames(0).toList()
            assertEquals(listOf(0f, 1f, 2f, 3f), out.map { it.bins[0] })
        }

    @Test
    fun negativeDelayIsIdentity() =
        runTest {
            assertEquals(
                4,
                frames(4)
                    .asFlow()
                    .delayedByFrames(-5)
                    .toList()
                    .size,
            )
        }

    @Test
    fun fewerFramesThanDelayEmitsNothing() =
        runTest {
            // Everything is still "in the buffer" (not yet audible), so nothing is shown.
            assertEquals(
                0,
                frames(2)
                    .asFlow()
                    .delayedByFrames(5)
                    .toList()
                    .size,
            )
        }
}
