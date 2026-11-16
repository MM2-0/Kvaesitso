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

class CodeHighlighterTest {
    private fun spans(
        code: String,
        language: String? = "json",
    ): List<Pair<String, CodeTokenType>> = CodeHighlighter.tokenize(code, language).filter { it.type != CodeTokenType.PUNCTUATION }.map { code.substring(it.start, it.end) to it.type }

    @Test
    fun jsonKeysStringsNumbersKeywords() {
        assertEquals(
            listOf(
                "\"kind\"" to CodeTokenType.KEY,
                "1" to CodeTokenType.NUMBER,
                "\"ok\"" to CodeTokenType.KEY,
                "true" to CodeTokenType.KEYWORD,
                "\"tags\"" to CodeTokenType.KEY,
                "\"e\"" to CodeTokenType.STRING,
                "\"a:b\"" to CodeTokenType.STRING,
            ),
            spans("{\"kind\": 1, \"ok\": true, \"tags\": [[\"e\", \"a:b\"]]}"),
        )
    }

    @Test
    fun nipStyleCommentsAndPlaceholders() {
        val code = "{\n  \"id\": <32-bytes lowercase hex>, // the event id\n  \"created_at\": <unix timestamp in seconds>,\n}"
        assertEquals(
            listOf(
                "\"id\"" to CodeTokenType.KEY,
                "<32-bytes lowercase hex>" to CodeTokenType.PLACEHOLDER,
                "// the event id" to CodeTokenType.COMMENT,
                "\"created_at\"" to CodeTokenType.KEY,
                "<unix timestamp in seconds>" to CodeTokenType.PLACEHOLDER,
            ),
            spans(code, "js"),
        )
    }

    @Test
    fun yamlKeysAndHashComments() {
        assertEquals(
            listOf(
                "kind" to CodeTokenType.KEY,
                "-1" to CodeTokenType.NUMBER,
                "# note" to CodeTokenType.COMMENT,
                "url" to CodeTokenType.KEY,
            ),
            spans("kind: -1 # note\nurl: https://x.y", "yaml"),
        )
    }

    @Test
    fun neverFailsOnBrokenInput() {
        CodeHighlighter.tokenize("{\"unterminated: <oops", "json")
        CodeHighlighter.tokenize("", null)
        CodeHighlighter.tokenize("/* open", "js")
    }
}
