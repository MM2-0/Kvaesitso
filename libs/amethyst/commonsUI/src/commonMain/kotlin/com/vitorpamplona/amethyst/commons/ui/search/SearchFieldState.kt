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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.vitorpamplona.amethyst.commons.search.ActivePicker
import com.vitorpamplona.amethyst.commons.search.PartialToken
import com.vitorpamplona.amethyst.commons.search.PartialTokens
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchCalendar
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * The search field's editing state: the text, where the caret is, and which picker that position
 * calls for.
 *
 * Which picker is open is *derived* from the text and the caret, never stored: a field can be
 * blurred with a token half-written and refocused an hour later, and it has to pick up exactly
 * where it left off. Storing "the people picker is open" instead would mean a second source of
 * truth that the caret could silently drift away from.
 */
@Stable
class SearchFieldState(
    initial: String = "",
) {
    /**
     * The field's own editing state. The text field edits it directly — keystrokes, IME
     * composition and selection never pass through this class — so everything below *derives*
     * from it rather than being told about changes.
     */
    val textState = TextFieldState(initial, TextRange(initial.length))

    /** True while the field has focus; a picker only belongs under a field being typed in. */
    var focused by mutableStateOf(false)
        private set

    /**
     * Where the date token the calendar state below belongs to starts. A different date token is
     * a different calendar: the month the reader paged to belonged to the token they left, so
     * both values only count while the caret is still in the token they were set for.
     */
    private var calendarToken by mutableStateOf<Int?>(null)

    /** The month the calendar is showing, once the reader has paged away from the token's own. */
    private var pagedMonthValue by mutableStateOf<SearchDate?>(null)
    private val pagedMonth: SearchDate? get() = pagedMonthValue?.takeIf { calendarToken == currentDateToken() }

    private var calendarCursorValue by mutableStateOf<SearchDate?>(null)

    /** The day the keyboard cursor is on inside the grid, or null while the pointer leads. */
    val calendarCursor: SearchDate? get() = calendarCursorValue?.takeIf { calendarToken == currentDateToken() }

    val text: String get() = textState.text.toString()

    /**
     * Where a token would settle, for the renderer: the caret while the reader is typing, and
     * null once they have left, so every token pills.
     */
    val settleCaret: Int?
        get() {
            val selection = textState.selection
            return if (focused && selection.collapsed) selection.start else null
        }

    /** Which picker the caret's position calls for, or null. */
    val activePicker: ActivePicker?
        get() {
            val caret = settleCaret ?: return null
            return PartialTokens.activePicker(text, caret)
        }

    /**
     * The month the calendar draws: the one the reader paged to, else the one they half-typed,
     * else this month.
     */
    fun calendarMonth(token: PartialToken): SearchDate = pagedMonth ?: SearchCalendar.typedMonth(token.partial) ?: LocalClock.today().firstOfMonth()

    private fun currentDateToken(): Int? = PartialTokens.dateAt(text, textState.selection.start)?.start

    fun setText(
        next: String,
        caret: Int = next.length,
    ) {
        textState.edit {
            replace(0, length, next)
            selection = TextRange(caret.coerceIn(0, next.length))
        }
        resetCalendar()
    }

    fun onFocusChanged(hasFocus: Boolean) {
        focused = hasFocus
        if (!hasFocus) resetCalendar()
    }

    fun clear() = setText("")

    // ---- picking ---------------------------------------------------------------------------

    /** Splice a finished token over the partial the caret is in, and put the caret past it. */
    fun replace(
        token: PartialToken,
        replacement: String,
    ) {
        val (next, caret) = PartialTokens.replaceToken(text, token, replacement)
        setText(next, caret)
    }

    /** The people picker's pick: the key is always written back as an npub, never as hex. */
    fun pickPerson(
        picker: ActivePicker.People,
        pubkeyHex: String,
    ) {
        val npub =
            try {
                NPub.create(pubkeyHex)
            } catch (_: Exception) {
                return
            }
        replace(picker.token, "${picker.keyField.token}:$npub")
    }

    fun pickDay(
        picker: ActivePicker.Calendar,
        day: String,
    ) = replace(picker.token, "${picker.dateField.token}:$day")

    fun pickGroup(
        picker: ActivePicker.Group,
        id: String,
    ) = replace(picker.token, "group:$id")

    fun pickKind(
        picker: ActivePicker.Kind,
        alias: String,
    ) = replace(picker.token, "kind:$alias")

    // ---- walking the calendar with the keyboard --------------------------------------------

    fun stepMonth(
        token: PartialToken,
        months: Int,
    ) {
        val next = calendarMonth(token).firstOfMonth(months)
        // A cursor that fell off the month it was on is not on any day the grid draws.
        val cursor = calendarCursor?.takeIf { next.sameMonth(it) }
        setCalendar(token, next, cursor)
    }

    /** Move the keyboard cursor by [days], opening the month it lands in. */
    fun moveCalendarCursor(
        token: PartialToken,
        days: Int,
    ) {
        val today = LocalClock.today()
        val shown = calendarMonth(token)
        val next =
            calendarCursor?.plusDays(days)
                ?: if (today.sameMonth(shown)) today else shown.firstOfMonth()
        setCalendar(token, next.firstOfMonth(), next)
    }

    fun hoverCalendar(day: SearchDate?) {
        val token = currentDateToken() ?: return
        setCalendar(token, pagedMonth, day)
    }

    private fun setCalendar(
        token: PartialToken,
        month: SearchDate?,
        cursor: SearchDate?,
    ) = setCalendar(token.start, month, cursor)

    private fun setCalendar(
        tokenStart: Int?,
        month: SearchDate?,
        cursor: SearchDate?,
    ) {
        calendarToken = tokenStart
        pagedMonthValue = month
        calendarCursorValue = cursor
    }

    private fun resetCalendar() = setCalendar(null, null, null)
}
