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

import androidx.compose.ui.platform.Clipboard
import platform.UIKit.UIPasteboard

// Reads and writes the general pasteboard directly: plain text needs nothing from ClipEntry here.
actual suspend fun Clipboard.setText(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

// hasStrings answers without reading the pasteboard, so it never shows the iOS 16+ "Allow Paste"
// prompt; only an actual read of text that is there can.
actual suspend fun Clipboard.getText(): String? {
    val pasteboard = UIPasteboard.generalPasteboard
    return if (pasteboard.hasStrings) pasteboard.string else null
}

actual suspend fun Clipboard.hasText(): Boolean = UIPasteboard.generalPasteboard.hasStrings

// No system flag to mark a clip sensitive here: an ordinary copy.
actual suspend fun Clipboard.setSensitiveText(text: String) = setText(text)
