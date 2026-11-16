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
package com.vitorpamplona.amethyst.commons.service.uploads

import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoHdrInfo

/** What an upload dialog tells the user happens to a picked HDR video when it is compressed. */
enum class HdrUploadOutcome {
    /** H.265 is on and the device keeps the HDR. */
    KEPT,

    /** H.264 always tone maps to SDR; turning H.265 on may keep it. */
    CONVERTED_BY_CODEC,

    /** H.265 is on but this device's encoder cannot keep HDR. */
    CONVERTED_BY_DEVICE,
}

/**
 * The outcome for the picked videos whose [probes] came back, with the H.265 switch at [useH265].
 * Null when no picked video is HDR, or none has been probed yet, so the dialog shows nothing.
 */
fun hdrUploadOutcome(
    probes: List<VideoHdrInfo>,
    useH265: Boolean,
): HdrUploadOutcome? =
    when {
        probes.none { it.isHdr } -> null
        !useH265 -> HdrUploadOutcome.CONVERTED_BY_CODEC
        probes.any { it.canKeepHdr } -> HdrUploadOutcome.KEPT
        else -> HdrUploadOutcome.CONVERTED_BY_DEVICE
    }
