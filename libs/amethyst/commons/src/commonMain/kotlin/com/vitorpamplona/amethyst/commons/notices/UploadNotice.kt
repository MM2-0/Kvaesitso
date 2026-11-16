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
package com.vitorpamplona.amethyst.commons.notices

/** What the media uploader tells the user about a video it prepared. */
sealed interface UploadNotice : UserNotice {
    /** Every encoder failed (or the GIF would not convert); the original is uploaded instead. [reason] is the encoder's last stderr. */
    data class VideoCompressionFailed(
        val reason: String?,
    ) : UploadNotice

    /** H.265 was asked for but no HEVC encoder worked, so the video was encoded as H.264. */
    data object HevcUnavailable : UploadNotice

    /** The source is HDR (PQ or HLG); compressed output will look flat. */
    data object HdrSource : UploadNotice

    /** The encode came out no smaller than the source, so the original (remuxed) is uploaded instead. */
    data object CompressionNotSmaller : UploadNotice

    /**
     * The chosen server ([chosenServer]) refused the upload, so it went to [usedServer], the next
     * one in the list, and the note links there. [reason] is the chosen server's answer.
     */
    data class UploadedToAnotherServer(
        val chosenServer: String,
        val usedServer: String,
        val reason: String?,
    ) : UploadNotice
}
