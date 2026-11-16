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

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LatencyRingBufferTest {
    @Test
    fun emptyBufferReturnsNullMedianAndZeroSize() {
        val r = LatencyRingBuffer(5)
        assertNull(r.snapshotMedian())
        assertEquals(0, r.size)
        assertEquals(0, r.snapshotSamples().size)
    }

    @Test
    fun medianOddCountIsMiddleSample() {
        val r = LatencyRingBuffer(5)
        listOf(100, 300, 200).forEach { r.push(it) }
        assertEquals(200, r.snapshotMedian())
        assertEquals(3, r.size)
    }

    @Test
    fun medianEvenCountReturnsLowerMiddle() {
        val r = LatencyRingBuffer(4)
        listOf(100, 200, 300, 400).forEach { r.push(it) }
        // Lower middle is values.sorted()[n/2 - 1] = 200
        assertEquals(200, r.snapshotMedian())
    }

    @Test
    fun pushBeyondCapacityOverwritesOldest() {
        val r = LatencyRingBuffer(3)
        listOf(1000, 1000, 1000, 50, 50, 50).forEach { r.push(it) }
        // Only the last 3 (all 50) remain.
        assertEquals(3, r.size)
        assertEquals(50, r.snapshotMedian())
    }

    @Test
    fun snapshotSamplesReturnsChronologicalOrder() {
        val r = LatencyRingBuffer(4)
        listOf(10, 20, 30).forEach { r.push(it) }
        assertContentEquals(intArrayOf(10, 20, 30), r.snapshotSamples())
        // After wrap, oldest-to-newest is 20, 30, 40, 50
        r.push(40)
        r.push(50)
        assertContentEquals(intArrayOf(20, 30, 40, 50), r.snapshotSamples())
    }

    @Test
    fun restoreFromArrayWhenSmallerThanCapacity() {
        val r = LatencyRingBuffer(5)
        r.restore(intArrayOf(100, 200, 300))
        assertEquals(3, r.size)
        assertContentEquals(intArrayOf(100, 200, 300), r.snapshotSamples())
        // Subsequent pushes append at the tail.
        r.push(400)
        assertContentEquals(intArrayOf(100, 200, 300, 400), r.snapshotSamples())
    }

    @Test
    fun restoreFromArrayLargerThanCapacityKeepsMostRecent() {
        val r = LatencyRingBuffer(3)
        r.restore(intArrayOf(100, 200, 300, 400, 500))
        assertEquals(3, r.size)
        assertContentEquals(intArrayOf(300, 400, 500), r.snapshotSamples())
    }
}
