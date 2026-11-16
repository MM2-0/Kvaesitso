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
package com.vitorpamplona.amethyst.commons.drawing

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import okio.Path

/**
 * A finished drawing, ready to upload: the replay as a looping GIF and the last
 * frame as a PNG, both temporary files the uploader owns once it has them.
 *
 * [blurhash] and [thumbhash] describe the finished drawing. They are computed
 * here rather than by the uploader because the uploader hashes a file's first
 * frame, and a replay's first frame is blank paper.
 */
@Immutable
class RenderedDrawing(
    val gif: Path,
    val still: Path,
    val width: Int,
    val height: Int,
    val blurhash: String?,
    val thumbhash: String?,
) {
    fun delete() {
        platformFileSystem.delete(gif, mustExist = false)
        platformFileSystem.delete(still, mustExist = false)
    }

    companion object {
        const val GIF_MIME_TYPE = "image/gif"
        const val PNG_MIME_TYPE = "image/png"
    }
}
