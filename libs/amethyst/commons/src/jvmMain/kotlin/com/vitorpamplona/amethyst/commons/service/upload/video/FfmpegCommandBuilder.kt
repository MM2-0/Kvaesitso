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

import java.io.File

/** Builds the `ffmpeg` argument lists the desktop compressor runs. Pure, so it is unit-tested without ffmpeg. */
object FfmpegCommandBuilder {
    /**
     * Limits the short side to [shortSide] without ever upscaling and keeps both dimensions even
     * (a yuv420p requirement). Landscape keeps the width derived (`-2`), portrait the height, so a
     * phone video's short side is its width. Null only evens the dimensions.
     *
     * Square pixels first: an anamorphic source (1080x720 stored with 4:9 pixels is a 480x720
     * portrait video) is resized at its display shape, so the limit applies to the side people see
     * and the output plays right in players that ignore the pixel ratio. `sar` is 1 when the source
     * declares none, which leaves an ordinary video as it is.
     */
    fun scaleFilter(shortSide: Int?): String =
        if (shortSide == null) {
            "scale=trunc(iw*sar/2)*2:trunc(ih/2)*2,setsar=1"
        } else {
            "scale=trunc(iw*sar/2)*2:ih,setsar=1,scale='if(gt(iw,ih),-2,trunc(min($shortSide,iw)/2)*2)':'if(gt(iw,ih),trunc(min($shortSide,ih)/2)*2,-2)'"
        }

    fun encode(
        ffmpeg: String,
        input: File,
        output: File,
        encoder: String,
        bitrateBps: Int,
        shortSide: Int?,
    ): List<String> =
        buildList {
            addAll(prefix(ffmpeg, input))
            // First video and (optional) first audio stream only; no subtitles, data tracks or chapters.
            addAll(listOf("-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn", "-map_chapters", "-1"))
            addAll(listOf("-c:v", encoder, "-b:v", "$bitrateBps", "-maxrate", "$bitrateBps", "-bufsize", "${2L * bitrateBps}"))
            addAll(listOf("-pix_fmt", "yuv420p", "-vf", scaleFilter(shortSide)))
            addAll(listOf("-c:a", "aac", "-b:a", "128k"))
            addAll(STRIP_METADATA)
            addAll(listOf("-movflags", "+faststart"))
            if (FfmpegEncoders.isHevc(encoder)) addAll(listOf("-tag:v", "hvc1")) // so Apple players accept it
            addAll(listOf("-progress", "pipe:1", output.path))
        }

    /**
     * Copies the video and audio streams into a fresh MP4 with no metadata. Never `-map 0`: a
     * MOV with a timecode data track (what iPhones write) then fails with exit 234.
     */
    fun remux(
        ffmpeg: String,
        input: File,
        output: File,
    ): List<String> =
        buildList {
            addAll(prefix(ffmpeg, input))
            addAll(listOf("-map", "0:v", "-map", "0:a?", "-dn", "-sn", "-c", "copy"))
            addAll(STRIP_METADATA)
            addAll(listOf("-map_chapters", "-1", "-movflags", "+faststart"))
            addAll(listOf("-progress", "pipe:1", output.path))
        }

    private val STRIP_METADATA = listOf("-map_metadata", "-1", "-map_metadata:s", "-1")

    private fun prefix(
        ffmpeg: String,
        input: File,
    ) = listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", input.absolutePath)
}
