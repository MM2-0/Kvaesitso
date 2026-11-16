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
package com.vitorpamplona.nestsclient.audio

/**
 * Peak amplitude of a 16-bit PCM frame, normalized to `[0, 1]`. Peak
 * (vs RMS) is jittery on its own but responds instantly to onsets,
 * which suits a visual ring that the UI smooths via animateDpAsState.
 *
 * Shared between [NestPlayer] (decoded remote audio) and the speaker
 * broadcasters (raw mic capture) so the on-screen ring around the
 * local avatar uses the same scale as remote speakers' rings.
 */
internal fun peakAmplitude(pcm: ShortArray): Float {
    var maxAbs = 0
    for (s in pcm) {
        val abs = if (s.toInt() < 0) -s.toInt() else s.toInt()
        if (abs > maxAbs) maxAbs = abs
    }
    return (maxAbs / 32768f).coerceIn(0f, 1f)
}
