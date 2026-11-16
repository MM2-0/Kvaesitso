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
@file:OptIn(ExperimentalFoundationApi::class)

package com.vitorpamplona.amethyst

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives [MentionPreservingInputTransformation] against a real [TextFieldState]
 * with simulated IME edits. The npub literal has no metadata loaded — these
 * tests only exercise the input-side guard, which keys off the underlying bech32
 * text rather than any display-name resolution.
 */
@RunWith(AndroidJUnit4::class)
class MentionPreservingInputTransformationTest {
    /** 64 characters: leading `@` + bech32 (`npub1` + 58 chars). */
    private val npub = "@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"

    /**
     * Apply [stage] inside an edit session, then run the InputTransformation
     * exactly as the framework would, and return the committed text.
     */
    private fun TextFieldState.applyChange(stage: TextFieldBuffer.() -> Unit): String {
        edit {
            stage()
            with(MentionPreservingInputTransformation) {
                transformInput()
            }
        }
        return text.toString()
    }

    @Test
    fun mentionFreeText_passesThrough() {
        val state = TextFieldState("hello world")
        val result = state.applyChange { replace(0, 5, "HELLO") }
        assertEquals("HELLO world", result)
    }

    @Test
    fun pureDeleteFullyCoveringMention_passesThrough() {
        val state = TextFieldState(npub)
        val result = state.applyChange { replace(0, npub.length, "") }
        assertEquals("", result)
    }

    @Test
    fun partialDeleteInsideMention_collapsesAtomically() {
        val state = TextFieldState(npub)
        // delete a chunk near the end of the bech32
        val result = state.applyChange { replace(60, npub.length, "") }
        assertEquals("", result)
    }

    @Test
    fun partialDeleteAtMentionStart_collapsesAtomically() {
        val state = TextFieldState(npub)
        // delete the leading "@npub" prefix only
        val result = state.applyChange { replace(0, 5, "") }
        assertEquals("", result)
    }

    @Test
    fun scopeExactReplaceWithNonEmpty_collapsesAtomically() {
        // SwiftKey case: IME fully covers the mention range and writes a
        // shortened replacement (e.g. one of the multi-word display tokens).
        val state = TextFieldState(npub)
        val result = state.applyChange { replace(0, npub.length, "@John") }
        assertEquals("", result)
    }

    @Test
    fun scopeBroaderReplace_passesThrough() {
        // Select-all + type: change covers the mention plus surrounding text.
        // Treated as a deliberate broader edit; the typed character is preserved.
        val state = TextFieldState("hi $npub world")
        val result = state.applyChange { replace(0, length, "x") }
        assertEquals("x", result)
    }

    @Test
    fun appendAfterMention_passesThrough() {
        val state = TextFieldState(npub)
        val result = state.applyChange { append(" hello") }
        assertEquals("$npub hello", result)
    }

    @Test
    fun mentionWithTrailingSpace_collapseConsumesSpace() {
        val state = TextFieldState("$npub hello")
        val result = state.applyChange { replace(60, npub.length, "") }
        assertEquals("hello", result)
    }

    @Test
    fun mentionWithTrailingNewline_collapseConsumesNewline() {
        val state = TextFieldState("$npub\nhello")
        val result = state.applyChange { replace(60, npub.length, "") }
        assertEquals("hello", result)
    }

    @Test
    fun multipleMentions_partialOnSecond_onlySecondCollapsed() {
        val text = "$npub and $npub"
        val state = TextFieldState(text)
        // partial delete inside the second mention only
        val result = state.applyChange { replace(text.length - 4, text.length, "") }
        assertEquals("$npub and ", result)
    }

    @Test
    fun mentionFreeChange_skipsRegexEntirely() {
        // No "npub1" or "nprofile1" substring in the original text — the
        // cheap-gate path should exit before any regex work.
        val state = TextFieldState("hello world this is plain text")
        val result = state.applyChange { replace(5, 11, "") }
        assertEquals("hello this is plain text", result)
    }
}
