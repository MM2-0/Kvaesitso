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
package com.vitorpamplona.amethyst.service.cast

/**
 * Short human-readable description of what a video actually is — "H.265 (HEVC) 1920x1080".
 *
 * A Cast receiver that dislikes a video usually says nothing useful: an LG webOS TV accepts the
 * load, reports LOADING, and then goes silent, with no error and no reason. The phone, meanwhile,
 * has already decoded the same file locally and knows exactly what it is. Reporting that turns "it
 * didn't play" into "it's HEVC", which is the fact that explains the failure and tells the user
 * whether another device or another copy would do better.
 *
 * Codec names are the ones people recognise from device spec sheets rather than the raw mime types,
 * because the point of the message is to be recognisable. Anything unrecognised passes through
 * unchanged — a mime type we don't have a friendly name for is still better than nothing.
 */
fun summarizeCastFormat(
    sampleMimeType: String?,
    codecs: String?,
    width: Int,
    height: Int,
): String? {
    val codec = friendlyCodecName(sampleMimeType) ?: codecs?.takeIf { it.isNotBlank() }
    val resolution = if (width > 0 && height > 0) "${width}x$height" else null
    return when {
        codec != null && resolution != null -> "$codec $resolution"
        codec != null -> codec
        resolution != null -> resolution
        else -> null
    }
}

private fun friendlyCodecName(sampleMimeType: String?): String? {
    val mime = sampleMimeType?.takeIf { it.isNotBlank() } ?: return null
    return when (mime.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc", "video/dolby-vision" -> "H.265 (HEVC)"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/mp4v-es" -> "MPEG-4"
        "video/mpeg2" -> "MPEG-2"
        else -> mime
    }
}
