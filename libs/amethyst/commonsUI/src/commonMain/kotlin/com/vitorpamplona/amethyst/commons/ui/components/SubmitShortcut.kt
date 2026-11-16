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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Ctrl+Enter (Cmd+Enter on macOS) anywhere inside runs [onSubmit], the same action as the
 * composer's Post button, when [isActive] says that button is enabled. Put it on the composer's
 * outermost layout: it sees the key in the preview pass, before the focused text field would
 * turn it into a line break, so a plain Enter still starts a new line.
 *
 * Holding the keys down fires once: the key repeats are swallowed until Enter is released.
 */
@Composable
fun Modifier.submitOnCtrlEnter(
    isActive: () -> Boolean,
    onSubmit: () -> Unit,
): Modifier {
    val currentIsActive = rememberUpdatedState(isActive)
    val currentOnSubmit = rememberUpdatedState(onSubmit)
    val latch = remember { SubmitLatch() }
    return onPreviewKeyEvent { event ->
        latch.handle(event, { currentIsActive.value() }, { currentOnSubmit.value() })
    }
}

private class SubmitLatch {
    private var armed = true

    fun handle(
        event: KeyEvent,
        isActive: () -> Boolean,
        onSubmit: () -> Unit,
    ): Boolean {
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return false
        if (event.type == KeyEventType.KeyUp) {
            armed = true
        }
        if (!event.isSubmitChord()) return false
        if (event.type == KeyEventType.KeyDown && armed) {
            armed = false
            if (isActive()) onSubmit()
        }
        // Swallows every part of the chord, the typed character included, so it never becomes a
        // line break, even while the button is disabled.
        return true
    }
}

private fun KeyEvent.isSubmitChord(): Boolean = (isCtrlPressed || isMetaPressed) && !isAltPressed && !isShiftPressed
