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
package com.vitorpamplona.amethyst.commons.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTopLevelUrlsTest {
    @Test
    fun imageOnItsOwnLineIsTopLevel() =
        assertEquals(
            setOf("https://example.com/a.jpg"),
            markdownTopLevelUrls("Some **bold** text\n\n![cat](https://example.com/a.jpg)\n"),
        )

    @Test
    fun bareUrlAfterAListIsTopLevel() =
        assertEquals(
            setOf("https://example.com/b.png"),
            markdownTopLevelUrls("The amount of attention\n\n- @jack\n\n#tag\nhttps://example.com/b.png"),
        )

    @Test
    fun listItemsAndTheirContinuationsAreNot() =
        assertEquals(
            emptySet(),
            markdownTopLevelUrls("- item https://example.com/1.jpg\n  ![x](https://example.com/2.jpg)\nlazy https://example.com/3.jpg\n1. https://example.com/4.jpg"),
        )

    @Test
    fun blockQuotesAreNot() = assertEquals(emptySet(), markdownTopLevelUrls("> quoted\n> https://example.com/q.jpg"))

    @Test
    fun codeBlocksAreNot() = assertEquals(emptySet(), markdownTopLevelUrls("```\nhttps://example.com/c.jpg\n```"))

    @Test
    fun tablesAndHtmlBlocksAreNot() =
        assertEquals(
            emptySet(),
            markdownTopLevelUrls("| ![](https://example.com/t.png) | caption |\n|---|---|\n<div><img src=\"https://example.com/h.png\"></div>"),
        )

    @Test
    fun tablesWithoutLeadingPipesAreNot() =
        assertEquals(
            setOf("https://example.com/after.png"),
            markdownTopLevelUrls("Photo https://example.com/head.png | Caption\n--- | ---\n![](https://example.com/a.png) | cat\n\nhttps://example.com/after.png"),
        )

    @Test
    fun aBlankLineEndsTheList() =
        assertEquals(
            setOf("https://example.com/after.jpg"),
            markdownTopLevelUrls("- one\n- two\n\nhttps://example.com/after.jpg."),
        )
}
