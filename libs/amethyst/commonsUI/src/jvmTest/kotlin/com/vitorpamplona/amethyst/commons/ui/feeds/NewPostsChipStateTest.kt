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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPostsChipStateTest {
    @Test
    fun `hides when user is at top`() {
        assertFalse(
            shouldShowNewPostsChip(
                isAtTop = true,
                currentTopId = "new",
                lastSeenTopId = "old",
            ),
        )
    }

    @Test
    fun `hides when feed has no top item yet`() {
        assertFalse(
            shouldShowNewPostsChip(
                isAtTop = false,
                currentTopId = null,
                lastSeenTopId = null,
            ),
        )
    }

    @Test
    fun `hides before first acknowledgement is recorded`() {
        // First paint: user has not yet seen any top; we initialize the baseline
        // synchronously in the LaunchedEffect, but the predicate must be false
        // when lastSeenTopId is null so we never show a chip during bootstrap.
        assertFalse(
            shouldShowNewPostsChip(
                isAtTop = false,
                currentTopId = "a",
                lastSeenTopId = null,
            ),
        )
    }

    @Test
    fun `hides when current top equals last seen top`() {
        assertFalse(
            shouldShowNewPostsChip(
                isAtTop = false,
                currentTopId = "a",
                lastSeenTopId = "a",
            ),
        )
    }

    @Test
    fun `shows when user is scrolled down and a new top has arrived`() {
        assertTrue(
            shouldShowNewPostsChip(
                isAtTop = false,
                currentTopId = "new",
                lastSeenTopId = "old",
            ),
        )
    }
}
