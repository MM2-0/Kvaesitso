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

/** Hands generated files to the platform's share sheet. */
interface FileSharer {
    /**
     * Packs [files] (name to text content) into a zip named [zipName] and shares it, behind a
     * chooser titled [title] where the platform has one.
     */
    fun shareTextFilesAsZip(
        zipName: String,
        files: List<Pair<String, String>>,
        title: String,
    )

    /**
     * Writes [content] to a file named [fileName] and shares it as [mimeType], behind a chooser
     * titled [title] where the platform has one.
     */
    fun shareTextFile(
        fileName: String,
        mimeType: String,
        content: String,
        title: String,
    )
}

/**
 * The platform's file sharing: Android writes the zip to its cache and shares it through the
 * app's `FileProvider`. Desktop and iOS have no share sheet wired yet and do nothing.
 */
@Composable
expect fun rememberFileSharer(): FileSharer
