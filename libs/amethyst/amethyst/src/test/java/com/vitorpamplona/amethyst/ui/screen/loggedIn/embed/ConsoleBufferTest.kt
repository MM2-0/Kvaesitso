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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleLine
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleBufferTest {
    private fun line(level: ConsoleLine.Level) = ConsoleLine(level, "m", "s", 1)

    @Test
    fun countsErrors() {
        val buffer = ConsoleBuffer(max = 10)
        buffer.add(line(ConsoleLine.Level.LOG))
        buffer.add(line(ConsoleLine.Level.ERROR))
        buffer.add(line(ConsoleLine.Level.ERROR))
        assertEquals(2, buffer.errorCount.intValue)
        assertEquals(3, buffer.lines.size)
    }

    @Test
    fun evictingAnErrorLowersTheCount() {
        val buffer = ConsoleBuffer(max = 2)
        buffer.add(line(ConsoleLine.Level.ERROR))
        buffer.add(line(ConsoleLine.Level.LOG))
        buffer.add(line(ConsoleLine.Level.LOG))
        assertEquals(0, buffer.errorCount.intValue)
        assertEquals(2, buffer.lines.size)
    }

    @Test
    fun evictingANonErrorKeepsTheCount() {
        val buffer = ConsoleBuffer(max = 2)
        buffer.add(line(ConsoleLine.Level.LOG))
        buffer.add(line(ConsoleLine.Level.ERROR))
        buffer.add(line(ConsoleLine.Level.ERROR))
        assertEquals(2, buffer.errorCount.intValue)
    }

    @Test
    fun clearResets() {
        val buffer = ConsoleBuffer(max = 2)
        buffer.add(line(ConsoleLine.Level.ERROR))
        buffer.clear()
        assertEquals(0, buffer.errorCount.intValue)
        assertEquals(0, buffer.lines.size)
    }
}
