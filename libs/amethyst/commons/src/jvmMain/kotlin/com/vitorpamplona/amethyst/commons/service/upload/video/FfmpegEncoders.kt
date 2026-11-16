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

import java.util.concurrent.ConcurrentHashMap

/**
 * Which video encoders an `ffmpeg` build offers and which to try, in order.
 *
 * Software encoders come first for predictable quality; the hardware ones follow because a
 * listed hardware encoder can still fail at run time (no GPU, headless session, session
 * limit), and `libopenh264` closes the list so an LGPL build without x264 still encodes.
 * `*_vaapi` is left out: it needs a device and an upload filter a one-size command cannot carry.
 */
object FfmpegEncoders {
    private const val TAG = "FfmpegEncoders"

    val H264: List<String> = listOf("libx264", "h264_videotoolbox", "h264_nvenc", "h264_qsv", "h264_amf", "h264_mf", "libopenh264")
    val H265: List<String> = listOf("libx265", "hevc_videotoolbox", "hevc_nvenc", "hevc_qsv", "hevc_amf", "hevc_mf")

    /**
     * Only the rows after the ` ------` separator count: the legend above it (` V..... = Video`)
     * has the same six-character shape and would otherwise yield `=` as an encoder name.
     */
    private val encoderLine = Regex("""^\s*[VAS][.F][.S][.X][.B][.D]\s+([A-Za-z0-9_]+)""")

    private val cache = ConcurrentHashMap<String, Set<String>>()

    fun parseAvailable(encodersOutput: String): Set<String> =
        encodersOutput
            .lineSequence()
            .dropWhile { it.trim() != "------" }
            .mapNotNull { encoderLine.find(it)?.groupValues?.get(1) }
            .toSet()

    /** The available HEVC encoders in preference order (when asked for), then the available H.264 ones. */
    fun candidates(
        available: Set<String>,
        useH265: Boolean,
    ): List<String> = (if (useH265) H265.filter { it in available } else emptyList()) + H264.filter { it in available }

    fun isHevc(encoder: String): Boolean = encoder in H265

    /**
     * Runs `ffmpeg -hide_banner -encoders` once per [ffmpeg] path and remembers the answer. A run
     * that fails or stalls is not remembered: an empty set comes back and the next call tries again.
     */
    suspend fun listAvailable(
        ffmpeg: String,
        stallTimeoutMs: Long = 5_000,
    ): Set<String> {
        cache[ffmpeg]?.let { return it }
        val listing = FfmpegProcesses.captureStdout(listOf(ffmpeg, "-hide_banner", "-encoders"), stallTimeoutMs, TAG, "Listing encoders of $ffmpeg") ?: return emptySet()
        return parseAvailable(listing).also { cache[ffmpeg] = it }
    }
}
