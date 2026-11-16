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
package com.vitorpamplona.amethyst.commons.util

// KMP code-point helpers — replacements for `java.lang.Character.*` and the
// JVM-only `String.codePointAt(Int)` / `String.offsetByCodePoints(Int, Int)`
// extensions. UTF-16 surrogate-pair aware so emoji and other supplementary
// code points are handled correctly on Native targets.

/** Number of UTF-16 code units (1 or 2) needed for [codePoint]. */
fun codePointCharCount(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

/**
 * Encode [codePoint] as UTF-16. Returns a 1-char array for BMP code points and
 * a high+low surrogate pair for supplementary code points (≥ U+10000).
 */
fun codePointToChars(codePoint: Int): CharArray =
    if (codePoint < 0x10000) {
        charArrayOf(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        charArrayOf(
            (0xD800 + (offset ushr 10)).toChar(),
            (0xDC00 + (offset and 0x3FF)).toChar(),
        )
    }

/**
 * Decode the Unicode code point that starts at [index]. Mirrors
 * `java.lang.Character.codePointAt(CharSequence, int)`: returns the
 * supplementary code point when [index] points at a well-formed high surrogate
 * followed by a low surrogate; otherwise returns the raw `Char.code`.
 */
fun String.codePointAtKmp(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
        }
    }
    return high.code
}

/**
 * Return the index that is [codePointOffset] code points away from [index].
 * Negative offsets are not supported (matches the only call site usage today).
 */
fun String.offsetByCodePointsKmp(
    index: Int,
    codePointOffset: Int,
): Int {
    var i = index
    repeat(codePointOffset) {
        i += codePointCharCount(codePointAtKmp(i))
    }
    return i
}
