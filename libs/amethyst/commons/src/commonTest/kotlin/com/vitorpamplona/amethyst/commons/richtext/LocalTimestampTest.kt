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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** `<t:SECONDS[:STYLE]>`, parsed exactly as marmot-markdown's `try_timestamp` does. */
class LocalTimestampTest {
    @Test
    fun parsesAllStylesAndDefault() {
        assertEquals(LocalTimestamp(1791280800, LocalTimestamp.Style.SHORT_DATE_TIME), LocalTimestamp.parseAt("<t:1791280800>", 0)?.first)
        "tTdDfFsSR".forEach { token ->
            assertEquals(LocalTimestamp.Style.of(token), LocalTimestamp.parseAt("<t:5:$token>", 0)?.first?.style)
        }
        assertEquals(-5L, LocalTimestamp.parseAt("<t:-5:R>", 0)?.first?.unixSeconds)
    }

    @Test
    fun invalidSyntaxStaysText() {
        assertNull(LocalTimestamp.split("<t:>"))
        assertNull(LocalTimestamp.split("<t:12:x>"))
        assertNull(LocalTimestamp.split("<t:12:FF>"))
        assertNull(LocalTimestamp.split("<t:12"))
        assertNull(LocalTimestamp.split("<t:-:F>"))
        // Past year 9999 the milliseconds overflow into a garbage date.
        assertNull(LocalTimestamp.split("<t:9223372036854775:R>"))
        assertNull(LocalTimestamp.split("<t:99999999999999999999>"))
        assertNull(LocalTimestamp.split("plain"))
    }

    @Test
    fun splitsGluedPunctuation() {
        val pieces = LocalTimestamp.split("(<t:10:t>).")!!
        assertEquals(3, pieces.size)
        assertEquals(LocalTimestamp.Piece.Text("("), pieces[0])
        assertIs<LocalTimestamp.Piece.Time>(pieces[1])
        assertEquals(LocalTimestamp.Piece.Text(")."), pieces[2])
    }

    @Test
    fun parserEmitsTimestampSegment() {
        val state = RichTextParser().parseText("meet at <t:1791280800:F> ok", EmptyTagList, null)
        val words = state.paragraphs.flatMap { it.words }
        assertIs<TimestampSegment>(words.first { it.segmentText.startsWith("<t:") })
    }
}
