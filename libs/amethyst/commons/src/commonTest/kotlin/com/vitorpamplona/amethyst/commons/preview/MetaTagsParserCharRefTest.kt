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

import kotlin.test.Test
import kotlin.test.assertEquals

class MetaTagsParserCharRefTest {
    @Test
    fun decodesNumericCharacterReferencesInContent() {
        val input =
            """
            |<html><head>
            |  <meta property="og:description" content="&#34;You can always buy more tokens, not more time.&#34;">
            |  <meta property="og:title" content="&#x22;hex quotes&#x22;">
            |</head></html>
            """.trimMargin()

        val metaTags = MetaTagsParser.parse(input).toList()
        assertEquals(2, metaTags.size)
        assertEquals(
            "\"You can always buy more tokens, not more time.\"",
            metaTags[0].attr("content"),
        )
        assertEquals("\"hex quotes\"", metaTags[1].attr("content"))
    }
}
