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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where the day pill and the docked day bar go, from a reverse-laid-out feed: index 0 is the newest
 * row at the bottom, item offsets run up from the bottom, and the viewport's top is at
 * [viewportEnd]. Rows 0-2 are from today, 3-5 from yesterday.
 */
class ChatFeedDayIndicatorTest {
    private val today = 2
    private val yesterday = 1
    private val days = listOf(today, today, today, yesterday, yesterday, yesterday)

    private class Item(
        override val index: Int,
        override val offset: Int,
        override val size: Int = 100,
    ) : LazyListItemInfo {
        override val key: Any = index
    }

    private class Layout(
        override val visibleItemsInfo: List<LazyListItemInfo>,
        override val viewportEndOffset: Int,
    ) : LazyListLayoutInfo {
        override val viewportStartOffset = 0
        override val totalItemsCount = 6
    }

    private fun state(
        viewportEnd: Int,
        vararg items: Item,
        docked: Boolean = false,
    ) = chatDayIndicatorState(
        info = Layout(items.toList(), viewportEnd),
        rowCount = days.size,
        docked = docked,
        barHeightPx = 40,
        pillZonePx = 56,
        dividerLabelBottomPx = 40,
        dayKeyAt = { days.getOrNull(it) },
    )!!

    @Test
    fun aShortChatShowsItsOldestDividerInsteadOfThePill() {
        // Six rows fill 600 of 1000: the oldest row's divider sits 400 below the top, in plain view.
        val items = (0..5).map { Item(it, offset = it * 100) }.toTypedArray()
        val pill = state(1000, *items)
        assertEquals(5, pill.rowIndex)
        assertFalse(pill.visible)

        // The bar lies over that divider rather than floating above the messages.
        assertEquals(400, state(1000, *items, docked = true).offsetPx)
    }

    @Test
    fun thePillAppearsOnceTheTopDayDividerHasScrolledAway() {
        // Row 5's top edge is 50 above the viewport, past where its divider's label ends (40).
        val pill = state(250, Item(3, -50), Item(4, 50), Item(5, 200))
        assertEquals(5, pill.rowIndex)
        assertTrue(pill.visible)

        // Only 30 above: the label's last 10 still show, so no pill doubling the day.
        assertFalse(state(250, Item(3, -50), Item(4, 50), Item(5, 180)).visible)

        // Its top exactly at the edge: the whole divider is on screen.
        assertFalse(state(250, Item(3, -50), Item(4, 50), Item(5, 150)).visible)
    }

    @Test
    fun theNextDaysDividerTakesOverFromThePillAndPushesTheBar() {
        // Row 3 (yesterday) is at the top; row 2 opens today, its divider 30 below the top edge.
        val pill = state(250, Item(1, 20), Item(2, 120), Item(3, 220))
        assertEquals(3, pill.rowIndex)
        assertFalse(pill.visible)

        val bar = state(250, Item(1, 20), Item(2, 120), Item(3, 220), docked = true)
        assertTrue(bar.visible)
        assertEquals(30 - 40, bar.offsetPx)
    }

    @Test
    fun theBarRestsAtTheTopMidDay() {
        val bar = state(250, Item(3, -50), Item(4, 50), Item(5, 200), docked = true)
        assertEquals(0, bar.offsetPx)
    }
}
