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
package com.vitorpamplona.amethyst.commons.preview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HtmlParser {
    companion object {
        // Byte-order marks mapped to their IANA charset names, longest first so
        // a 4-byte BOM is matched before a 2-byte one. (Patterns taken from okhttp.)
        private val UNICODE_BOMS =
            listOf(
                byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xFF.toByte()) to "UTF-32BE",
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00) to "UTF-32LE",
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) to "UTF-8",
                byteArrayOf(0xFE.toByte(), 0xFF.toByte()) to "UTF-16BE",
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) to "UTF-16LE",
            )
    }

    suspend fun parseHtml(
        bodyBytes: ByteArray,
        charsetName: String?,
    ): Sequence<MetaTag> =
        withContext(Dispatchers.Default) {
            // Precedence: explicit charset (e.g. from Content-Type) > BOM >
            // charset sniffed from <meta> tags (defaults to UTF-8).
            val name =
                charsetName
                    ?: bodyBytes.bomCharsetName()
                    ?: HtmlCharsetParser.detectCharset(bodyBytes)
            val content = decodeBytes(bodyBytes, name)
            MetaTagsParser.parse(content, includeTitleAndIcons = true)
        }

    private fun ByteArray.bomCharsetName(): String? {
        for ((bom, name) in UNICODE_BOMS) {
            if (startsWith(bom)) return name
        }
        return null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }
}
