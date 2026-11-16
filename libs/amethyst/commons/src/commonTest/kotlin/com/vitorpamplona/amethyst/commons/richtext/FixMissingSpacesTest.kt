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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Focused guard for [RichTextParser.fixMissingSpaces]. The earlier
 * `Regex("([^ \n])?(url)([^ \n])?")` implementation corrupted every URL on
 * Kotlin/Native (e.g. iOS) because the engine fails to backtrack the optional
 * `([^ \n])?` groups to zero width — `"https://x"` came back as `"h https://x"`.
 * These cases run on every target (incl. iosSimulatorArm64) and pin the behaviour.
 */
class FixMissingSpacesTest {
    private val parser = RichTextParser()

    @Test
    fun leavesAlreadySeparatedUrlUntouched() {
        val url = "https://example.com/audio/track.f4a"
        assertEquals(url, parser.fixMissingSpaces(url, setOf(url)))
    }

    @Test
    fun leavesUrlWithRegexMetacharactersUntouched() {
        val url = "universe.nostrich.land?lang=zh"
        val text = "foo $url bar"
        assertEquals(text, parser.fixMissingSpaces(text, setOf(url)))
    }

    @Test
    fun insertsSpacesAroundGluedUrl() {
        assertEquals(
            "a https://example.com/x b",
            parser.fixMissingSpaces("ahttps://example.com/xb", setOf("https://example.com/x")),
        )
    }

    @Test
    fun emptyUrlSetIsNoOp() {
        val text = "no urls here"
        assertEquals(text, parser.fixMissingSpaces(text, emptySet()))
    }
}
