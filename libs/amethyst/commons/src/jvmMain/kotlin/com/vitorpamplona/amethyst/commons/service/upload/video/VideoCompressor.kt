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
package com.vitorpamplona.amethyst.commons.service.upload.video

import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import java.io.File

/** What preparing a video for upload produced. Files handed back are the caller's to delete. */
sealed interface VideoCompressionResult {
    /** A freshly encoded MP4 temp file. */
    data class Compressed(
        val file: File,
        val notices: List<UserNotice> = emptyList(),
    ) : VideoCompressionResult

    /**
     * The original is to be uploaded: [stripped] is a remuxed, metadata-free temp copy of it,
     * or null when no remux was possible (no ffmpeg, remux failed, or the source is a GIF).
     */
    data class Original(
        val stripped: File?,
        val notices: List<UserNotice> = emptyList(),
    ) : VideoCompressionResult
}

/** Prepares videos (and GIFs) for upload on a platform that can transcode. */
interface VideoCompressor {
    /** Whether an encoder is at hand; false means [compress] and [strip] return the original untouched. */
    val available: Boolean

    /** The source's dimensions and frame rate, for the slider label; null when it cannot be read. */
    suspend fun probe(source: File): VideoInfo?

    /**
     * Transcodes [source] at [quality] (through its video level), H.265 when [useH265] and an
     * HEVC encoder works. [onProgress] receives fractions in 0..1.
     */
    suspend fun compress(
        source: File,
        quality: CompressorQuality,
        useH265: Boolean,
        onProgress: (Double) -> Unit,
    ): VideoCompressionResult

    /** Turns a GIF into an MP4 at its own size; a failure hands the GIF back untouched. */
    suspend fun convertGif(
        source: File,
        onProgress: (Double) -> Unit,
    ): VideoCompressionResult

    /** A remuxed copy of [source] with no metadata, chapters, subtitle or data tracks; null when unavailable or failed. */
    suspend fun strip(source: File): File?
}
