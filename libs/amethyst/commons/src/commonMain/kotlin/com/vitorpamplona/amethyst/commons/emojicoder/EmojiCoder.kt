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
package com.vitorpamplona.amethyst.commons.emojicoder

import com.vitorpamplona.amethyst.commons.util.codePointAtKmp
import com.vitorpamplona.amethyst.commons.util.codePointCharCount
import com.vitorpamplona.amethyst.commons.util.codePointToChars

object EmojiCoder {
    // Variation selectors block https://unicode.org/charts/nameslist/n_FE00.html
    // VS1..=VS16
    const val VARIATION_SELECTOR_START = 0xfe00
    const val VARIATION_SELECTOR_END = 0xfe0f

    // Variation selectors supplement https://unicode.org/charts/nameslist/n_E0100.html
    // VS17..=VS256
    const val VARIATION_SELECTOR_SUPPLEMENT_START = 0xe0100
    const val VARIATION_SELECTOR_SUPPLEMENT_END = 0xe01ef

    val toVariationArray =
        Array<CharArray>(256) {
            // converts to UTF-16. Always char[2] back
            when (it) {
                in 0..15 -> codePointToChars(VARIATION_SELECTOR_START + it)
                in 16..255 -> codePointToChars(VARIATION_SELECTOR_SUPPLEMENT_START + it - 16)
                else -> throw RuntimeException("This should never happen")
            }
        }

    fun fromVariationSelector(codePoint: Int): Int? =
        when (codePoint) {
            in VARIATION_SELECTOR_START..VARIATION_SELECTOR_END -> codePoint - VARIATION_SELECTOR_START
            in VARIATION_SELECTOR_SUPPLEMENT_START..VARIATION_SELECTOR_SUPPLEMENT_END -> codePoint - VARIATION_SELECTOR_SUPPLEMENT_START + 16
            else -> null
        }

    fun isVariationChar(charCode: Int) =
        charCode in VARIATION_SELECTOR_START..VARIATION_SELECTOR_END ||
            charCode in VARIATION_SELECTOR_SUPPLEMENT_START..VARIATION_SELECTOR_SUPPLEMENT_END

    fun isCoded(text: String): Boolean {
        if (text.length <= 3) return false

        if (!isVariationChar(text.codePointAtKmp(text.length - 2))) {
            return false
        }

        if (text.length > 4 && !isVariationChar(text.codePointAtKmp(text.length - 4))) {
            return false
        }

        return true
    }

    fun encode(
        emoji: String,
        text: String,
    ): String {
        val input = text.encodeToByteArray()
        val out = CharArray(input.size * 2)
        var outIdx = 0
        for (i in 0 until input.size) {
            val chars = toVariationArray[input[i].toInt() and 0xFF]
            out[outIdx++] = chars[0]
            out[outIdx++] = chars[1]
        }
        return emoji + out.concatToString()
    }

    /**
     * Scans [text] collecting variation-selector bytes until a non-selector is found
     * after at least one byte has been collected.
     * Returns the end index (exclusive) in [text] and the collected byte values.
     */
    private fun scanVariationBytes(text: String): Pair<Int, List<Int>> {
        val bytes = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAtKmp(i)
            val byte = fromVariationSelector(codePoint)
            if (byte == null && bytes.isNotEmpty()) break
            i += codePointCharCount(codePoint) // Advance by correct char count
            if (byte != null) bytes.add(byte)
        }
        return i to bytes
    }

    fun decode(text: String): String {
        val (_, bytes) = scanVariationBytes(text)
        return ByteArray(bytes.size) { bytes[it].toByte() }.decodeToString()
    }

    fun cropToFirstMessage(text: String): String {
        val (endIndex, _) = scanVariationBytes(text)
        return text.substring(0, endIndex)
    }
}
