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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [block] on the UI thread, suspending until it finishes. Callers that are already on
 * the UI thread run it inline, so the ordering of everything around the call is unchanged.
 *
 * Compose's `TextFieldState` is UI-thread confined. Only its `value` is snapshot state; the
 * buffer the platform input connection edits while the soft keyboard holds a batch open is a
 * plain field on the state, published by nothing. Writing to a field from a background
 * dispatcher therefore races the keyboard: the new `value` becomes visible to the main
 * thread, the buffer may not, and the undo manager ends up mapping an edit range recorded
 * against the old text onto text that no longer has those offsets. That is the
 * `StringIndexOutOfBoundsException: begin 3, end 4, length 0` thrown out of
 * `TextUndoManager.recordChanges` when a composer clears its message field on
 * [Dispatchers.IO] while the user is still typing into it.
 *
 * The composers sign, save drafts and upload on [Dispatchers.IO], so every field write on
 * those paths — clearing after a send or a cancel, loading a draft, inserting an uploaded
 * URL — has to come back to the UI thread first.
 */
suspend fun <T> onUiThread(block: () -> T): T = withContext(Dispatchers.Main.immediate) { block() }
