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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSASCIIStringEncoding
import platform.Foundation.NSData
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSString
import platform.Foundation.NSStringEncoding
import platform.Foundation.NSUTF16BigEndianStringEncoding
import platform.Foundation.NSUTF16LittleEndianStringEncoding
import platform.Foundation.NSUTF32BigEndianStringEncoding
import platform.Foundation.NSUTF32LittleEndianStringEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSWindowsCP1252StringEncoding
import platform.Foundation.create

/**
 * iOS decode of HTML bytes by charset name. The common web charsets are mapped
 * to their `NSStringEncoding`; anything else falls back to UTF-8 (matching the
 * "defaults to UTF-8" behaviour of the charset sniffer).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun decodeBytes(
    bytes: ByteArray,
    charsetName: String?,
): String {
    if (bytes.isEmpty()) return ""

    val encoding = encodingFor(charsetName)

    val data =
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }

    return NSString.create(data = data, encoding = encoding)?.toString()
        ?: bytes.decodeToString()
}

private fun encodingFor(charsetName: String?): NSStringEncoding =
    when (charsetName?.trim()?.uppercase()) {
        "UTF-16", "UTF-16BE", "UTF16" -> NSUTF16BigEndianStringEncoding
        "UTF-16LE" -> NSUTF16LittleEndianStringEncoding
        "UTF-32", "UTF-32BE", "UTF32" -> NSUTF32BigEndianStringEncoding
        "UTF-32LE" -> NSUTF32LittleEndianStringEncoding
        "ISO-8859-1", "ISO8859-1", "ISO_8859-1", "LATIN1", "L1", "CP819" -> NSISOLatin1StringEncoding
        "WINDOWS-1252", "CP1252" -> NSWindowsCP1252StringEncoding
        "US-ASCII", "ASCII", "ANSI_X3.4-1968" -> NSASCIIStringEncoding
        else -> NSUTF8StringEncoding
    }
