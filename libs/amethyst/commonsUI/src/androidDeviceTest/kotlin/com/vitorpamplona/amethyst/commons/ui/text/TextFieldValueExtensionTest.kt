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
package com.vitorpamplona.amethyst.commons.ui.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextFieldValueExtensionTest {
    @Test
    fun testInsertTwoCharsStart() {
        val current = TextFieldValue("ab", selection = TextRange(0, 0))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("http://a.b ab", next.text)
        assertEquals(TextRange(10, 10), next.selection)
    }

    @Test
    fun testInsertTwoCharsMiddle() {
        val current = TextFieldValue("ab", selection = TextRange(1, 1))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("a http://a.b b", next.text)
        assertEquals(TextRange(12, 12), next.selection)
    }

    @Test
    fun testInsertTwoCharsEnd() {
        val current = TextFieldValue("ab", selection = TextRange(2, 2))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("ab http://a.b", next.text)
        assertEquals(TextRange(13, 13), next.selection)
    }

    @Test
    fun testInsertOneCharStart() {
        val current = TextFieldValue("a", selection = TextRange(0, 0))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("http://a.b a", next.text)
        assertEquals(TextRange(10, 10), next.selection)
    }

    @Test
    fun testInsertOneCharEnd() {
        val current = TextFieldValue("a", selection = TextRange(1, 1))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("a http://a.b", next.text)
        assertEquals(TextRange(12, 12), next.selection)
    }

    @Test
    fun testInsertTwoCharsWithSpaceStart() {
        val current = TextFieldValue("a b", selection = TextRange(1, 1))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("a http://a.b b", next.text)
        assertEquals(TextRange(12, 12), next.selection)
    }

    @Test
    fun testInsertTwoCharsWithSpaceEnd() {
        val current = TextFieldValue("a b", selection = TextRange(2, 2))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("a http://a.b b", next.text)
        assertEquals(TextRange(12, 12), next.selection)
    }

    @Test
    fun testInsertTwoCharsWithThreeSpaceStart() {
        val current = TextFieldValue("a  b", selection = TextRange(1, 1))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("a http://a.b  b", next.text)
        assertEquals(TextRange(12, 12), next.selection)
    }

    @Test
    fun testInsertTwoCharsWithThreeSpaceMiddle() {
        val current = TextFieldValue("a  b", selection = TextRange(2, 2))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("a http://a.b b", next.text)
        assertEquals(TextRange(12, 12), next.selection)
    }

    @Test
    fun testInsertTwoCharsWithThreeSpaceEnd() {
        val current = TextFieldValue("a  b", selection = TextRange(3, 3))
        val next = current.insertUrlAtCursor("http://a.b")

        assertEquals("a  http://a.b b", next.text)
        assertEquals(TextRange(13, 13), next.selection)
    }

    @Test
    fun testInsertAfterNewLine() {
        val current = TextFieldValue("ab\n\n", selection = TextRange(4, 4))
        val next = current.insertUrlAtCursor("https://i.nostr.build/zdMW4.jpg")

        assertEquals("ab\n\nhttps://i.nostr.build/zdMW4.jpg", next.text)
        assertEquals(TextRange(35, 35), next.selection)
    }
}
