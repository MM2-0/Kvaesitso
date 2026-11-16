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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.ui.text.TextRange
import com.vitorpamplona.amethyst.commons.search.ActivePicker
import com.vitorpamplona.amethyst.commons.search.PartialTokens
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The field's state lives in a `TextFieldState` the text field edits directly, so everything here
 * is derived from its text and selection. These move the caret the way the field would.
 */
class SearchFieldStateTest {
    private fun SearchFieldState.moveCaret(to: Int) = textState.edit { selection = TextRange(to) }

    @Test
    fun setTextPutsTheCaretWhereAsked() {
        val state = SearchFieldState()
        state.setText("#bitcoin rest", caret = 8)
        assertEquals("#bitcoin rest", state.text)
        assertEquals(TextRange(8), state.textState.selection)
        state.setText("abc")
        assertEquals(TextRange(3), state.textState.selection)
    }

    @Test
    fun aPickerAndASettleCaretOnlyExistWhileFocused() {
        val state = SearchFieldState("kind:")
        assertNull(state.settleCaret)
        assertNull(state.activePicker)
        state.onFocusChanged(true)
        assertEquals(5, state.settleCaret)
        assertIs<ActivePicker.Kind>(state.activePicker)
        // A selection is not a caret: nothing settles and nothing is offered.
        state.textState.edit { selection = TextRange(0, 5) }
        assertNull(state.settleCaret)
        assertNull(state.activePicker)
    }

    @Test
    fun aPickSplicesTheTokenAndMovesTheCaretPastIt() {
        val state = SearchFieldState("bitcoin kind:pic")
        state.onFocusChanged(true)
        val picker = assertIs<ActivePicker.Kind>(state.activePicker)
        state.pickKind(picker, "picture")
        assertEquals("bitcoin kind:picture", state.text.trimEnd())
        assertEquals(state.text.length, state.textState.selection.start)
    }

    @Test
    fun aPagedMonthBelongsToTheDateTokenItWasPagedIn() {
        val state = SearchFieldState("since: until:")
        state.onFocusChanged(true)
        state.moveCaret(6)
        val since = PartialTokens.dateAt(state.text, 6)!!
        val thisMonth: SearchDate = LocalClock.today().firstOfMonth()

        state.stepMonth(since, 1)
        assertEquals(thisMonth.firstOfMonth(1), state.calendarMonth(since))

        // The other date token is a different calendar.
        state.moveCaret(state.text.length)
        val until = PartialTokens.dateAt(state.text, state.text.length)!!
        assertEquals(thisMonth, state.calendarMonth(until))
        assertNull(state.calendarCursor)

        // Replacing the text starts over, even in the same place.
        state.moveCaret(6)
        state.setText("since: until:", caret = 6)
        assertEquals(thisMonth, state.calendarMonth(since))
    }

    @Test
    fun theKeyboardCursorWalksAndBelongsToItsToken() {
        val state = SearchFieldState("since:")
        state.onFocusChanged(true)
        val since = PartialTokens.dateAt(state.text, 6)!!
        state.moveCalendarCursor(since, 1)
        val first = state.calendarCursor!!
        state.moveCalendarCursor(since, 7)
        assertEquals(first.plusDays(7), state.calendarCursor)

        state.onFocusChanged(false)
        assertNull(state.calendarCursor)
    }
}
