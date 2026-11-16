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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import kotlin.test.Test
import kotlin.test.assertEquals

class BalancedRowSizesTest {
    private fun rows(
        count: Int,
        max: Int = 4,
    ) = balancedRowSizes(count, max)

    @Test
    fun `six fills two rows evenly instead of stranding two`() {
        assertEquals(listOf(3, 3), rows(6))
    }

    @Test
    fun `five is three and two, not four and one`() {
        assertEquals(listOf(3, 2), rows(5))
    }

    @Test
    fun `seven cannot be even and stays four and three`() {
        assertEquals(listOf(4, 3), rows(7))
    }

    @Test
    fun `a full row is left alone`() {
        assertEquals(listOf(4), rows(4))
        assertEquals(listOf(4, 4), rows(8))
    }

    @Test
    fun `no row is ever wider than the maximum, and every tile is placed`() {
        (1..24).forEach { count ->
            val rows = rows(count)
            assertEquals(true, rows.all { it in 1..4 }, "count=$count rows=$rows")
            assertEquals(count, rows.sum(), "count=$count rows=$rows")
        }
    }

    @Test
    fun `rows never differ by more than one`() {
        (1..24).forEach { count ->
            val rows = rows(count)
            assertEquals(true, rows.max() - rows.min() <= 1, "count=$count rows=$rows")
        }
    }

    @Test
    fun `it never puts everything on one row it cannot fit`() {
        assertEquals(listOf(4, 4, 4), rows(12))
        assertEquals(listOf(4, 3, 3), rows(10))
    }

    @Test
    fun `an empty grid does not divide by zero`() {
        assertEquals(emptyList(), balancedRowSizes(0, 4))
    }
}
