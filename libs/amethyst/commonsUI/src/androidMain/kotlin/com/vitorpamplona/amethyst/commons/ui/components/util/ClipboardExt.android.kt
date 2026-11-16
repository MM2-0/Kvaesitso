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

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.nativeClipboardManager

actual suspend fun Clipboard.setText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
}

actual suspend fun Clipboard.setSensitiveText(text: String) {
    val clip =
        ClipData.newPlainText("", text).apply {
            // ClipDescription.EXTRA_IS_SENSITIVE, spelled out: the constant is API 33, the key works earlier.
            description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
    setClipEntry(ClipEntry(clip))
}

actual suspend fun Clipboard.getText(): String? =
    getClipEntry()
        ?.clipData
        ?.getItemAt(0)
        ?.text
        ?.toString()

// The description is metadata: reading it doesn't trigger the "pasted from your clipboard" toast that
// getPrimaryClip does. Only the types whose items carry the text getText reads: a text/uri-list clip
// (ClipData.newUri) has none, so offering to paste it would do nothing.
actual suspend fun Clipboard.hasText(): Boolean =
    nativeClipboardManager.primaryClipDescription?.let {
        it.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || it.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
    } == true
