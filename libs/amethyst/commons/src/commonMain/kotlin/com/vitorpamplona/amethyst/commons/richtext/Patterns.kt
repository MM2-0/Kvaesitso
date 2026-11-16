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
package com.vitorpamplona.amethyst.commons.richtext

/**
 * Pattern constants for email and phone validation.
 * These replace android.util.Patterns for KMP compatibility.
 */
object Patterns {
    /**
     * Email address pattern from RFC 5322... From android.util.Patterns.
     */
    val EMAIL_ADDRESS: Regex =
        Regex(
            "[a-zA-Z0-9+._%-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+",
        )

    /**
     * Phone number pattern - matches common phone formats.
     */
    val PHONE: Regex =
        Regex(
            "^[+]?[(]?[0-9]{1,4}[)]?[-\\s./0-9]*\$",
        )

    val BASE64_IMAGE: Regex =
        Regex(
            "data:image/(${RichTextParser.imageExtensions.joinToString(separator = "|")});base64,([a-zA-Z0-9+/]+={0,2})",
        )
}
