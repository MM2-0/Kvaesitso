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

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeActionTest {
    @Test
    fun theHintWins() {
        assertEquals(EditorInfo.IME_ACTION_SEND, imeActionFor("send", multiline = true, inputType = "textarea", hasNext = false))
        assertEquals(EditorInfo.IME_ACTION_SEARCH, imeActionFor("search", multiline = true, inputType = "text", hasNext = true))
        assertEquals(EditorInfo.IME_ACTION_PREVIOUS, imeActionFor("previous", multiline = false, inputType = "text", hasNext = true))
        assertEquals(EditorInfo.IME_ACTION_NONE, imeActionFor("enter", multiline = false, inputType = "text", hasNext = true))
    }

    @Test
    fun aMultiLineFieldGetsALineBreak() {
        assertEquals(EditorInfo.IME_ACTION_NONE, imeActionFor("", multiline = true, inputType = "textarea", hasNext = true))
    }

    // Brainstorm's profile form: Enter on the name field used to submit the whole form (a signing prompt).
    @Test
    fun aFieldWithAnotherAfterItInItsFormGetsNext() {
        assertEquals(EditorInfo.IME_ACTION_NEXT, imeActionFor("", multiline = false, inputType = "text", hasNext = true))
        assertEquals(EditorInfo.IME_ACTION_GO, imeActionFor("", multiline = false, inputType = "text", hasNext = false))
    }

    @Test
    fun aSearchFieldGetsSearch() {
        assertEquals(EditorInfo.IME_ACTION_SEARCH, imeActionFor("", multiline = false, inputType = "search", hasNext = true))
    }
}
