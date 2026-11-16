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
package com.vitorpamplona.amethyst.commons.ui.feeds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackToTopStateTest {
    private val sent = mutableListOf<String>()

    private fun entry(name: String) = BackToTopEntry { sent.add(name) }

    @Test
    fun theButtonShowsOnlyWhileTheActiveListIsScrolledAway() {
        val state = BackToTopState()
        val home = entry("home")
        state.activate(home)
        assertFalse(state.isVisible)

        home.isScrolledAway = true
        assertTrue(state.isVisible)
    }

    @Test
    fun aCancelledSwipeHandsTheButtonBackToThePageStillShowing() {
        val state = BackToTopState()
        val tabA = entry("A").also { it.isScrolledAway = true }
        val tabB = entry("B")
        state.activate(tabA)
        // Swiping toward B composes it, then cancelling disposes it.
        state.activate(tabB)
        state.remove(tabB)

        assertTrue(state.isVisible)
        state.sendToTop()
        assertEquals(listOf("A"), sent)
    }

    @Test
    fun removingAnInactiveListKeepsTheActiveOne() {
        val state = BackToTopState()
        val tabA = entry("A")
        val tabB = entry("B")
        state.activate(tabA)
        state.activate(tabB)
        state.remove(tabA)

        state.sendToTop()
        assertEquals(listOf("B"), sent)
    }

    @Test
    fun theScreensOwnScrollRunsAfterTheList() {
        var headerScrolled = false
        val state = BackToTopState { headerScrolled = true }
        state.activate(entry("profile"))

        state.sendToTop()
        assertEquals(listOf("profile"), sent)
        assertTrue(headerScrolled)
    }
}
