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

/** Saving bytes to, and reading them back from, a file the user picks in the platform's own dialog. */
interface FileBytesAccess {
    /**
     * Asks where to save a file (suggesting [suggestedName]), then writes what [content] produces
     * there. Returns false when the user cancels; [content] only runs once a place was chosen.
     */
    suspend fun save(
        suggestedName: String,
        mimeType: String,
        content: suspend () -> ByteArray,
    ): Boolean

    /** Asks for a file of [mimeTypes] and reads it whole; null when the user cancels. */
    suspend fun open(mimeTypes: List<String>): ByteArray?

    /** Platforms with no file dialog wired: every request reads as a cancel. */
    object None : FileBytesAccess {
        override suspend fun save(
            suggestedName: String,
            mimeType: String,
            content: suspend () -> ByteArray,
        ) = false

        override suspend fun open(mimeTypes: List<String>): ByteArray? = null
    }
}

/** The platform's save and open dialogs for whole files: Android's document UI, the desktop's file dialog. */
@Composable
expect fun rememberFileBytesAccess(): FileBytesAccess
