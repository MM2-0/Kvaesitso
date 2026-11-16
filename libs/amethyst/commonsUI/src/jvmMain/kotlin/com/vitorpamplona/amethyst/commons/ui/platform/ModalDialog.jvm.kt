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
package com.vitorpamplona.amethyst.commons.ui.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [show], which opens a modal AWT or Swing dialog (a `FileDialog`, a `JFileChooser`) and
 * returns once it closes, from a coroutine.
 *
 * A modal dialog spins a nested event loop, and the window keeps painting in it. A coroutine
 * launched from a click or a composition runs inside Compose's frame dispatcher while it flushes
 * its queue. Blocking there lets a frame painted by the nested loop flush that queue again, which
 * reruns tasks that already ran and kills the event thread with a `CoroutinesInternalError`
 * (`CompletedContinuation cannot be cast to DispatchedContinuation`). [Dispatchers.Main], Swing's
 * dispatcher, posts [show] as an event of its own, outside that flush.
 */
suspend fun <T> showModalDialog(show: () -> T): T = withContext(Dispatchers.Main) { show() }
