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

/** Puts [text] on the system clipboard as plain text. */
expect suspend fun Clipboard.setText(text: String)

/**
 * Copies a secret ([text] is a private key or a password): where the platform can, the clip is
 * marked sensitive so the system's copy preview and clipboard history don't show it.
 */
expect suspend fun Clipboard.setSensitiveText(text: String)

/** The clipboard's current plain text, or null when it holds none. */
expect suspend fun Clipboard.getText(): String?

/**
 * Whether the clipboard holds text, answered from its metadata without reading the contents — so offering
 * "Paste and go" neither makes Android announce a paste nor iOS prompt for one. Only the tap should read.
 */
expect suspend fun Clipboard.hasText(): Boolean
