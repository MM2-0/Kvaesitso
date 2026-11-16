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

/**
 * Decodes [bytes] into a String using the charset named [charsetName].
 *
 * [charsetName] is an IANA charset name (e.g. "UTF-8", "ISO-8859-1",
 * "windows-1252"). When it is null or cannot be resolved on the current
 * platform, the implementation falls back to UTF-8.
 *
 * The decode is the only platform-specific step of link-preview HTML parsing:
 * the JVM actual delegates to `java.nio.charset`, which supports every charset
 * the JRE ships; the iOS actual maps the common web charsets to
 * `NSStringEncoding` and falls back to UTF-8 for anything exotic.
 */
expect fun decodeBytes(
    bytes: ByteArray,
    charsetName: String?,
): String
