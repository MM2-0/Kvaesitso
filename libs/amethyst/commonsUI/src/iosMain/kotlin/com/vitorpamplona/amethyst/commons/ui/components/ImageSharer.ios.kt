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
import com.vitorpamplona.amethyst.commons.service.uploads.mediaUriOfFile
import okio.FileSystem
import kotlin.time.Clock

private object NoImageSharer : ImageSharer {
    override val canShare = false

    override val opensShareSheet = false

    override suspend fun writePng(image: ImageBitmap): MediaUri {
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "amethyst_share_${Clock.System.now().toEpochMilliseconds()}.png"
        FileSystem.SYSTEM.write(path) { write(image.encodePng()) }
        return mediaUriOfFile(path)
    }

    override suspend fun share(
        image: ImageBitmap,
        chooserTitle: String,
    ): Boolean = false
}

/** No share sheet wired yet. */
@Composable
actual fun rememberImageSharer(): ImageSharer = NoImageSharer
