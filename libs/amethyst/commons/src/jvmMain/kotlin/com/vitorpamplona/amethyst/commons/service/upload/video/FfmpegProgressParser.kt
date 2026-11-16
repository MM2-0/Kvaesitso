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

/**
 * Turns the `key=value` lines of `ffmpeg -progress pipe:1` into a fraction of [durationSeconds].
 * `out_time_us=N/A` (seen before the first frame) and unrelated keys yield null; `progress=end`
 * is 1.0 whether or not the duration is known.
 */
class FfmpegProgressParser(
    private val durationSeconds: Double?,
) {
    fun onLine(line: String): Double? {
        val trimmed = line.trim()
        if (trimmed == "progress=end") return 1.0
        if (!trimmed.startsWith(OUT_TIME_KEY)) return null
        val duration = durationSeconds?.takeIf { it > 0 } ?: return null
        val micros = trimmed.substring(OUT_TIME_KEY.length).toLongOrNull() ?: return null
        return (micros / 1e6 / duration).coerceIn(0.0, 1.0)
    }

    private companion object {
        const val OUT_TIME_KEY = "out_time_us="
    }
}
