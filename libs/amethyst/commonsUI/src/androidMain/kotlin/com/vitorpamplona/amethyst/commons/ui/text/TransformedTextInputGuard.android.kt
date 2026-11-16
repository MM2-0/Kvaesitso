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

import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

// One instance for the whole app: InterceptPlatformTextInput restarts the input session whenever
// it is handed a different interceptor.
private val SelectionSafeInterceptor =
    PlatformTextInputInterceptor { request, nextHandler ->
        nextHandler.startInputMethod(
            PlatformTextInputMethodRequest { outAttributes ->
                SelectionSafeInputConnection(request.createInputConnection(outAttributes))
            },
        )
    }

@Composable
actual fun TransformedTextInputGuard(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    if (active) InterceptPlatformTextInput(SelectionSafeInterceptor, content) else content()
}

/**
 * Commits the batch queued so far before forwarding a `setSelection` (or select-all) that arrives
 * inside it, then reopens the batch at the same depth. See [TransformedTextInputGuard] for why.
 *
 * The framework's [InputConnectionWrapper] forwards every other method, including the ones newer
 * than our minSdk (handwriting gestures, text attributes, commitContent).
 */
internal class SelectionSafeInputConnection(
    target: InputConnection,
) : InputConnectionWrapper(target, false) {
    private var batchDepth = 0

    override fun beginBatchEdit(): Boolean {
        batchDepth++
        return super.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        if (batchDepth > 0) batchDepth--
        return super.endBatchEdit()
    }

    override fun closeConnection() {
        batchDepth = 0
        super.closeConnection()
    }

    override fun setSelection(
        start: Int,
        end: Int,
    ): Boolean = outsideBatch { super.setSelection(start, end) }

    // Compose turns select-all into the same setSelection.
    override fun performContextMenuAction(id: Int): Boolean =
        if (id == android.R.id.selectAll) {
            outsideBatch { super.performContextMenuAction(id) }
        } else {
            super.performContextMenuAction(id)
        }

    private inline fun outsideBatch(command: () -> Boolean): Boolean {
        val depth = batchDepth
        if (depth == 0) return command()

        repeat(depth) { super.endBatchEdit() }
        try {
            return command()
        } finally {
            repeat(depth) { super.beginBatchEdit() }
        }
    }
}
