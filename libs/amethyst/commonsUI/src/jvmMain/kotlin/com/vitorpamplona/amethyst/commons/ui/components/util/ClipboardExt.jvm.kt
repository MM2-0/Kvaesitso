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
package com.vitorpamplona.amethyst.commons.ui.components.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setText(text: String) {
    setClipEntry(ClipEntry(StringSelection(text)))
}

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.getText(): String? {
    val entry = getClipEntry() ?: return null
    val transferable = entry.nativeClipEntry as? Transferable ?: return null
    return try {
        transferable.getTransferData(DataFlavor.stringFlavor) as? String
    } catch (_: Exception) {
        null
    }
}

// Off the UI thread: on X11 the flavor query is a round trip to whichever app owns the clipboard.
actual suspend fun Clipboard.hasText(): Boolean =
    withContext(Dispatchers.IO) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
        } catch (_: Exception) {
            // Headless, or another app holds the clipboard open.
            false
        }
    }

// No system flag to mark a clip sensitive here: an ordinary copy.
actual suspend fun Clipboard.setSensitiveText(text: String) = setText(text)
