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
package com.vitorpamplona.amethyst

import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.ui.text.TransformedTextInputGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The crash from the post editor: `IllegalArgumentException: Expected TextRange(68, 68) to be in
 * TextRange(0, 18)` from `endBatchEdit`, on Samsung Keyboard, while swapping one mention for
 * another.
 *
 * Compose maps each IME command in a batch through the offset mapping of the text before the
 * batch, and `setSelection` does not clamp the mapped result. When an earlier command in the batch
 * deletes a mention chip, the cursor the IME asks for lands past the end of the shortened text.
 */
@RunWith(AndroidJUnit4::class)
class TransformedTextInputGuardTest {
    @get:Rule
    val rule = createComposeRule()

    /** 64 characters, drawn as the 2-character chip `@N`. */
    private val mention = "@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"

    private val chip =
        OutputTransformation {
            val start = asCharSequence().indexOf(mention)
            if (start >= 0) replace(start, start + mention.length, "@N")
        }

    /** Raw `ab@npub1…cd`, shown as `ab@Ncd`, with the cursor right after the chip. */
    private fun focusedField(guarded: Boolean): Pair<TextFieldState, InputConnection> {
        val raw = "ab${mention}cd"
        val state = TextFieldState(raw, initialSelection = TextRange(2 + mention.length))
        val focus = FocusRequester()
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            if (guarded) {
                TransformedTextInputGuard { BasicTextField(state, Modifier.focusRequester(focus), outputTransformation = chip) }
            } else {
                BasicTextField(state, Modifier.focusRequester(focus), outputTransformation = chip)
            }
        }
        rule.runOnIdle { focus.requestFocus() }
        rule.waitForIdle()
        val connection = rule.runOnIdle { view.onCreateInputConnection(EditorInfo()) }
        return state to connection
    }

    /** Backspace over the chip, then place the cursor at the end of what the IME sees: `abcd`. */
    private fun InputConnection.deleteChipAndMoveToEnd() {
        beginBatchEdit()
        deleteSurroundingText(2, 0)
        setSelection(4, 4)
        endBatchEdit()
    }

    @Test
    fun unguardedField_crashesOnTheBatch() {
        // Documents the Compose bug. If this starts failing, Compose fixed it and the guard can go.
        val (_, connection) = focusedField(guarded = false)
        rule.runOnUiThread {
            assertThrows(IllegalArgumentException::class.java) { connection.deleteChipAndMoveToEnd() }
        }
    }

    @Test
    fun guardedField_appliesTheBatch() {
        val (state, connection) = focusedField(guarded = true)
        rule.runOnUiThread { connection.deleteChipAndMoveToEnd() }
        rule.runOnIdle {
            assertEquals("abcd", state.text.toString())
            assertEquals(TextRange(4), state.selection)
        }
    }
}
