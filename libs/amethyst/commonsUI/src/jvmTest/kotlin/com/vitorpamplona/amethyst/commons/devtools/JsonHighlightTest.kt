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
package com.vitorpamplona.amethyst.commons.devtools

import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.JsonColors
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.JsonValueContext
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.highlightJson
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonHighlightTest {
    private val colors = JsonColors(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Gray, Color.Magenta)

    private fun colorOf(
        json: String,
        token: String,
    ): Color? {
        val start = json.indexOf(token)
        return highlightJson(json, colors)
            .spanStyles
            .single { it.start == start && it.end == start + token.length }
            .item.color
    }

    @Test
    fun keysValuesNumbersAndLiteralsGetTheirOwnColour() {
        val json = """{"kind": 1, "content": "a \"quoted\": b", "ok": true, "x": null, "n": -1.5e3}"""
        assertEquals(json, highlightJson(json, colors).text)
        assertEquals(Color.Red, colorOf(json, "\"kind\""))
        assertEquals(Color.Blue, colorOf(json, "1"))
        assertEquals(Color.Green, colorOf(json, "\"a \\\"quoted\\\": b\""))
        assertEquals(Color.Cyan, colorOf(json, "true"))
        assertEquals(Color.Cyan, colorOf(json, "null"))
        assertEquals(Color.Blue, colorOf(json, "-1.5e3"))
    }

    @Test
    fun brokenJsonDoesNotThrow() {
        assertEquals("{\"open", highlightJson("{\"open", colors).text)
    }

    @Test
    fun notesLandAtTheEndOfTheValuesLine() {
        val json =
            """
            {
              "kind": 1,
              "tags": [
                ["p", "abc", "wss://r"],
                ["e", "def"]
              ],
              "created_at": 5
            }
            """.trimIndent()
        val seen = mutableListOf<String>()
        val text =
            highlightJson(json, colors) { c: JsonValueContext ->
                seen.add("${c.key}/${c.arrayHead}/${c.index}=${c.value}")
                when {
                    c.key == "kind" -> "Text"
                    c.arrayHead == "p" && c.index == 1 -> "Alice"
                    c.arrayHead == "p" && c.index == 2 -> "relay"
                    c.key == "created_at" -> "epoch"
                    else -> null
                }
            }.text

        assertEquals(
            listOf("kind/null/-1=1", "tags/p/0=p", "tags/p/1=abc", "tags/p/2=wss://r", "tags/e/0=e", "tags/e/1=def", "created_at/null/-1=5"),
            seen,
        )
        val lines = text.lines()
        assertEquals("  \"kind\": 1,  // Text", lines[1])
        assertEquals("    [\"p\", \"abc\", \"wss://r\"],  // Alice \u00B7 relay", lines[3])
        assertEquals("    [\"e\", \"def\"]", lines[4])
        assertEquals("  \"created_at\": 5  // epoch", lines[6])
    }
}
