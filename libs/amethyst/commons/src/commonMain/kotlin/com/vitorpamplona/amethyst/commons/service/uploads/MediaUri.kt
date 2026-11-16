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

import okio.Path

/**
 * A picked file's address as the platform hands it over: Android's `content://` [android.net.Uri]
 * (a typealias there, so app code keeps reading it as a `Uri`), a string-backed subclass
 * elsewhere.
 */
expect abstract class MediaUri {
    /** The address as a string (`content://…` on Android). */
    abstract override fun toString(): String
}

/** The address of a local file, such as a voice recording the app wrote itself. */
expect fun mediaUriOfFile(path: Path): MediaUri

/** The last path segment of this address, if it has one (on Android, `Uri.lastPathSegment`). */
expect fun MediaUri.lastPathSegmentOrNull(): String?

/**
 * The last path segment of a string address, percent-decoded, or null when it has no path
 * (`content://host`, `https://host/`). Accepts backslash separators, so a Windows path works too. The
 * string-backed platforms use it to match Android's `Uri.lastPathSegment`.
 */
internal fun lastPathSegmentOf(address: String): String? {
    var path = address.substringBefore('#').substringBefore('?')
    val schemeEnd = path.indexOf("://")
    if (schemeEnd >= 0) {
        val pathStart = path.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return null
        path = path.substring(pathStart)
    }
    val segment = path.replace('\\', '/').trimEnd('/').substringAfterLast('/')
    return if (segment.isEmpty()) null else percentDecodeUtf8(segment)
}

/** Decodes `%XX` escapes as UTF-8 bytes; malformed escapes are kept as written. */
private fun percentDecodeUtf8(input: String): String {
    if ('%' !in input) return input
    val out = StringBuilder(input.length)
    val bytes = ArrayList<Byte>()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        val hex = if (c == '%' && i + 2 < input.length && isHex(input[i + 1]) && isHex(input[i + 2])) input.substring(i + 1, i + 3).toInt(16) else null
        if (hex != null) {
            bytes.add(hex.toByte())
            i += 3
        } else {
            if (bytes.isNotEmpty()) {
                out.append(bytes.toByteArray().decodeToString())
                bytes.clear()
            }
            out.append(c)
            i++
        }
    }
    if (bytes.isNotEmpty()) out.append(bytes.toByteArray().decodeToString())
    return out.toString()
}

private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
