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
import com.vitorpamplona.amethyst.commons.ui.platform.showModalDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

// The system's file dialog. It is modal, so it opens through showModalDialog: blocking inside the
// composition's dispatcher crashes the event thread when the window paints in its nested loop.
// The bytes move on the IO dispatcher.
private object DesktopFileBytesAccess : FileBytesAccess {
    override suspend fun save(
        suggestedName: String,
        mimeType: String,
        content: suspend () -> ByteArray,
    ): Boolean {
        val target =
            showModalDialog {
                val dialog = FileDialog(null as Frame?, suggestedName, FileDialog.SAVE).apply { file = suggestedName }
                dialog.isVisible = true
                dialog.file?.let { File(dialog.directory, it) }
            } ?: return false
        val bytes = content()
        withContext(Dispatchers.IO) { target.writeBytes(bytes) }
        return true
    }

    override suspend fun open(mimeTypes: List<String>): ByteArray? {
        val source =
            showModalDialog {
                val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD)
                dialog.isVisible = true
                dialog.file?.let { File(dialog.directory, it) }
            } ?: return null
        return withContext(Dispatchers.IO) { source.readBytes() }
    }
}

@Composable
actual fun rememberFileBytesAccess(): FileBytesAccess = remember { DesktopFileBytesAccess }
