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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip23LongForm

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownImageTest {
    private val url = "https://example.com/d.gif"

    @Test
    fun plainAltIsKept() = assertEquals("![a cat](https://example.com/d.gif)", markdownImage("a cat", url))

    @Test
    fun noAltGivesEmptyBrackets() = assertEquals("![](https://example.com/d.gif)", markdownImage(null, url))

    @Test
    fun bracketsInAltCannotCloseIt() =
        assertEquals(
            "![sketch\\](https://evil.example) \\[x\\]](https://example.com/d.gif)",
            markdownImage("sketch](https://evil.example) [x]", url),
        )

    @Test
    fun backslashIsEscapedFirst() = assertEquals("![a\\\\b](https://example.com/d.gif)", markdownImage("a\\b", url))

    @Test
    fun lineBreaksBecomeSpaces() = assertEquals("![one two  three](https://example.com/d.gif)", markdownImage("one\ntwo\r\nthree", url))
}
