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
package com.vitorpamplona.amethyst.commons.service.upload

sealed class HlsPublishState {
    data object Idle : HlsPublishState()

    data class Transcoding(
        val currentLabel: String,
        val percent: Int,
    ) : HlsPublishState()

    data class Uploading(
        val done: Int,
        val total: Int,
        val currentLabel: String = "",
        // Fraction (0f..1f) through the currently-in-flight file, driven by the uploader's
        // byte-progress callback. 0f when a new file starts, 1f when it finishes. Lets the
        // progress bar move smoothly within a single file's slice instead of only ticking
        // once per file.
        val currentFileFraction: Float = 0f,
    ) : HlsPublishState()

    data object Publishing : HlsPublishState()

    data class Success(
        val eventId: String,
        val masterUrl: String,
    ) : HlsPublishState()

    data class Failure(
        val message: String,
    ) : HlsPublishState()
}
