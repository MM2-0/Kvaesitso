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

/**
 * Lived in `androidDeviceTest` until it was moved here: nothing in it is Android-specific, and on
 * a device-only source set it never ran in CI -- every attribute shape below was unguarded.
 */
class MetaTagsParserTest {
    @Test
    fun testParse() {
        val input =
            """<html>
            |  <head>
            |    <meta charset="utf-8">
            |    <meta http-equiv="content-type" content="text/html; charset=utf-8">
            |    <meta property="og:title" content=title>
            |    <meta property="og:description" content='description'>
            |    <meta property="og:image" content="https://example.com/img/foo.png">
            |    <!-- edge cases -->
            |    <meta
            |       name="newline"
            |       content="newline"
            |    >
            |    <meta name="space before gt"    >
            |    <meta name     ="space before =">
            |    <meta name=    "space after =">
            |    <META NAME="CAPITAL">
            |    <meta name="character reference" content="&lt;meta&gt;">
            |    <meta name="attr value with end of head doesn't harm" content="<head>bang!</head>">
            |    <meta name="ignore tags with duplicated attr" name="dup">
            |  </head>
            |  <body>
            |    <meta name="ignore meta tags in body">
            |  </body>
            |</html>
            """.trimMargin()

        val exp =
            listOf(
                listOf("charset" to "utf-8"),
                listOf("http-equiv" to "content-type", "content" to "text/html; charset=utf-8"),
                listOf("property" to "og:title", "content" to "title"),
                listOf("property" to "og:description", "content" to "description"),
                listOf("property" to "og:image", "content" to "https://example.com/img/foo.png"),
                listOf("name" to "newline", "content" to "newline"),
                listOf("name" to "space before gt"),
                listOf("name" to "space before ="),
                listOf("name" to "space after ="),
                listOf("name" to "CAPITAL"),
                listOf("name" to "character reference", "content" to "<meta>"),
                listOf("name" to "attr value with end of head doesn't harm", "content" to "<head>bang!</head>"),
            )

        val metaTags = MetaTagsParser.parse(input).toList()
        println(metaTags)
        assertEquals(exp.size, metaTags.size)
        metaTags.zip(exp).forEach { (meta, expAttrs) ->
            expAttrs.forEach { (name, expValue) ->
                assertEquals(expValue, meta.attr(name))
            }
        }
    }

    @Test
    fun testParseWithAdditionalTags() {
        val input =
            """<html>
            |  <head>
            |    <meta data-preact-helmet property="og:title" content="Uma conversa com Antonio Vargas">
            |  </head>
            |</html>
            """.trimMargin()

        val exp =
            listOf(
                listOf("property" to "og:title", "content" to "Uma conversa com Antonio Vargas"),
            )

        val metaTags = MetaTagsParser.parse(input).toList()
        println(metaTags)
        assertEquals(exp.size, metaTags.size)
        metaTags.zip(exp).forEach { (meta, expAttrs) ->
            expAttrs.forEach { (name, expValue) ->
                assertEquals(expValue, meta.attr(name))
            }
        }
    }
}
