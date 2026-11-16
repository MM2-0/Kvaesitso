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
package com.vitorpamplona.amethyst.commons.model.buzz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuzzChannelStarsTest {
    // A fresh instance per test: stars are per-account state now, not a process-wide singleton.
    private val stars = BuzzChannelStars()

    private val a = "channel-a"
    private val b = "channel-b"

    @Test
    fun toggleStarsAndUnstars() {
        assertFalse(stars.isStarred(a))
        assertTrue(stars.toggle(a))
        assertTrue(stars.isStarred(a))
        assertEquals(setOf(a), stars.flow.value)

        assertFalse(stars.toggle(a))
        assertFalse(stars.isStarred(a))
        assertEquals(emptySet(), stars.flow.value)
    }

    @Test
    fun restoreReplacesTheWholeSet() {
        stars.toggle(a)
        stars.restore(setOf(b))
        assertEquals(setOf(b), stars.flow.value)
        assertFalse(stars.isStarred(a))
    }

    @Test
    fun oneAccountsStarsDoNotPinForAnother() {
        // Why this is per account: a star reorders and badges the community channel list. While the
        // set was a process-wide singleton, one account pinning a channel reordered every other
        // logged-in account's list, and switching accounts rewrote the set they shared.
        val mine = BuzzChannelStars()
        val theirs = BuzzChannelStars()

        mine.toggle(a)

        assertTrue(mine.isStarred(a))
        assertFalse(theirs.isStarred(a))
        assertEquals(emptySet(), theirs.flow.value)
    }
}
