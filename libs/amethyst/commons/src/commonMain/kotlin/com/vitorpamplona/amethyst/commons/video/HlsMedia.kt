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
package com.vitorpamplona.amethyst.commons.video

/** The mime HLS playlists are normalized to — the value of media3's `MimeTypes.APPLICATION_M3U8`. */
const val HLS_MIME_TYPE = "application/x-mpegURL"

/**
 * The mime a player should be handed for [mimeType] on [url]: an explicit mime wins (the four HLS
 * aliases map onto [HLS_MIME_TYPE]); without one, a path-anchored `.m3u8` means HLS; otherwise
 * null, letting the player infer the format.
 */
fun normalizeStreamMimeType(
    mimeType: String?,
    url: String? = null,
): String? {
    if (!mimeType.isNullOrBlank()) {
        return when (mimeType.lowercase()) {
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/x-mpegurl",
            "audio/mpegurl",
            -> HLS_MIME_TYPE

            else -> mimeType
        }
    }
    if (url != null && hasM3u8PathExtension(url)) {
        return HLS_MIME_TYPE
    }
    return null
}

/**
 * Whether a URL plus its imeta mime identifies HLS, for code that holds a URL and a mime but no
 * player item yet. Defined through [normalizeStreamMimeType] so it cannot drift from the mime that
 * actually reaches the player.
 *
 * Both halves matter. A BUD-10 blossom playlist is `https://host/<sha256>` with no extension at all,
 * so only the mime identifies it; and anchoring to the path stops `video.mp4?ref=a.m3u8` counting as
 * HLS on the strength of its query string.
 *
 * Note this answers *is it HLS*, which callers use as a proxy for *is it live*: an on-demand HLS
 * playlist also answers true. Liveness is only truly knowable from `#EXT-X-ENDLIST` once the
 * playlist is loaded.
 */
fun isHlsMedia(
    url: String,
    mimeType: String?,
): Boolean = normalizeStreamMimeType(mimeType, url) == HLS_MIME_TYPE

// Restrict `.m3u8` matching to the path component so a query param or fragment that happens to
// mention .m3u8 (e.g. `?ref=a.m3u8`) on an MP4 URI doesn't misroute to the HLS source.
private fun hasM3u8PathExtension(uri: String): Boolean {
    val path = uri.substringBefore('?').substringBefore('#')
    return path.endsWith(".m3u8", ignoreCase = true)
}
