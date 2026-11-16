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
package com.vitorpamplona.amethyst.service.playback.playerPool

import org.junit.Assert.assertEquals
import org.junit.Test

class WarmSlotsTest {
    private val gib = 1024L * 1024 * 1024

    @Test
    fun ramSetsTheSize() {
        assertEquals(4, ExoPlayerPool.warmSlotsFor(poolSize = 16, totalRamBytes = 3 * gib))
        assertEquals(5, ExoPlayerPool.warmSlotsFor(poolSize = 16, totalRamBytes = 6 * gib))
        assertEquals(6, ExoPlayerPool.warmSlotsFor(poolSize = 16, totalRamBytes = 12 * gib))
    }

    @Test
    fun decodersCapTheSize() {
        // 8 decoders leave 4 once the players in use have theirs.
        assertEquals(4, ExoPlayerPool.warmSlotsFor(poolSize = 8, totalRamBytes = 12 * gib))
    }

    @Test
    fun neverBelowTheOldThree() {
        assertEquals(3, ExoPlayerPool.warmSlotsFor(poolSize = 4, totalRamBytes = 12 * gib))
        assertEquals(3, ExoPlayerPool.warmSlotsFor(poolSize = 16, totalRamBytes = 0))
    }

    @Test
    fun memoryLevelShrinksTheCap() {
        assertEquals(6, ExoPlayerPool.warmCapFor(MemoryPressure.Level.NORMAL, warmSlots = 6))
        // Tight memory falls back to what the pool always kept before it was sized by RAM.
        assertEquals(3, ExoPlayerPool.warmCapFor(MemoryPressure.Level.TIGHT, warmSlots = 6))
        assertEquals(0, ExoPlayerPool.warmCapFor(MemoryPressure.Level.LOW, warmSlots = 6))
    }

    @Test
    fun tightNeverRaisesASmallerCache() {
        // A pool capped below three by its decoders stays there under pressure.
        assertEquals(2, ExoPlayerPool.warmCapFor(MemoryPressure.Level.TIGHT, warmSlots = 2))
    }
}
