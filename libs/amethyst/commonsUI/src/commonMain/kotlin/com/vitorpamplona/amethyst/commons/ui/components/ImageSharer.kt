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
import androidx.compose.ui.graphics.ImageBitmap
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri

/** Hands a picture the app rendered, like a note shared as an image, to the platform. */
interface ImageSharer {
    /** False where [share] has nowhere to send the image yet, so the option is left out. */
    val canShare: Boolean

    /**
     * True when [share] opens a share sheet. False on platforms without one, where [share] and
     * [rememberTextSharer] put things on the clipboard instead and the caller should say so.
     */
    val opensShareSheet: Boolean

    /** Writes [image] as a PNG to a temporary file the upload pipeline can read. */
    suspend fun writePng(image: ImageBitmap): MediaUri

    /**
     * Shares [image] as a PNG, behind a chooser titled [chooserTitle] where the platform has one.
     * False when the platform refused it, like a clipboard another app holds.
     */
    suspend fun share(
        image: ImageBitmap,
        chooserTitle: String,
    ): Boolean
}

/**
 * The platform's image sharing: Android writes a PNG to its cache and opens the share sheet
 * through the app's `FileProvider`; desktop copies the image to the clipboard; iOS has no
 * share sheet wired yet and does nothing.
 */
@Composable
expect fun rememberImageSharer(): ImageSharer
