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
import kotlin.test.assertTrue

/**
 * A page with no OpenGraph still has what a browser tab shows: its `<title>` and its icon
 * `<link>`s. These are read only when asked for, and only ever as a fallback.
 */
class TitleAndIconFallbackTest {
    private fun headTags(html: String) = MetaTagsParser.parse(html, includeTitleAndIcons = true).toList()

    private fun extract(html: String) = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(html, includeTitleAndIcons = true))

    // Trimmed from the nsite that prompted this: no og:*, no twitter:*, only a title, a meta
    // description and an SVG favicon.
    private val noOpenGraphPage =
        """
        |<!DOCTYPE html>
        |<html lang="en">
        |<head>
        |<meta charset="utf-8">
        |<meta name="viewport" content="width=device-width, initial-scale=1">
        |<title>Private Provider &mdash; Confidential AI</title>
        |<meta name="description" content="A phone-first AI workspace for Android.">
        |<link rel="icon" href="favicon.svg" type="image/svg+xml">
        |<link rel="stylesheet" href="styles.css">
        |</head>
        |<body><h1>Hi</h1></body>
        |</html>
        """.trimMargin()

    @Test
    fun aPageWithoutOpenGraphStillYieldsTitleDescriptionAndIcon() {
        val info = extract(noOpenGraphPage)

        assertEquals("Private Provider — Confidential AI", info.title)
        assertEquals("A phone-first AI workspace for Android.", info.description)
        assertEquals("favicon.svg", info.icon)
        assertEquals("", info.image)
    }

    @Test
    fun theDefaultParseStillYieldsOnlyMetaTags() {
        val tags = MetaTagsParser.parse(noOpenGraphPage).toList()

        assertEquals(3, tags.size)
        assertTrue(tags.all { it.element == HeadElement.META })
    }

    @Test
    fun onlyIconLinksAreYielded() {
        val tags = headTags(noOpenGraphPage)

        val links = tags.filter { it.element == HeadElement.LINK }
        assertEquals(1, links.size)
        assertEquals("favicon.svg", links[0].attr("href"))
    }

    @Test
    fun openGraphTitleWinsOverTheDocumentTitle() {
        val info =
            extract(
                """
                |<head>
                |  <title>Document Title</title>
                |  <meta property="og:title" content="OG Title">
                |</head>
                """.trimMargin(),
            )

        assertEquals("OG Title", info.title)
    }

    @Test
    fun titleWhitespaceIsCollapsed() {
        val info = extract("<head><title>\n   Spread\n\t  Out   </title></head>")

        assertEquals("Spread Out", info.title)
    }

    @Test
    fun anUnclosedTitleIsNotATitle() {
        val info = extract("<head><title>runs to the end of the truncated body")

        assertEquals("", info.title)
    }

    @Test
    fun aTitleOutsideTheHeadIsIgnored() {
        // An SVG in the body carries its own <title>; the scan stops at </head> before it.
        val info = extract("<head></head><body><svg><title>Close</title></svg></body>")

        assertEquals("", info.title)
    }

    @Test
    fun theFirstTitleWins() {
        val info = extract("<head><title>First</title><title>Second</title></head>")

        assertEquals("First", info.title)
    }

    @Test
    fun theLargestIconWins() {
        val info =
            extract(
                """
                |<head>
                |  <link rel="shortcut icon" href="/favicon.ico">
                |  <link rel="icon" sizes="16x16 32x32" href="/icon-32.png">
                |  <link rel="apple-touch-icon" href="/apple-touch-icon.png">
                |  <link rel="icon" sizes="96X96" href="/icon-96.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("/apple-touch-icon.png", info.icon)
    }

    @Test
    fun aScalableIconBeatsBitmaps() {
        val info =
            extract(
                """
                |<head>
                |  <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
                |  <link rel="icon" href="/icon.svg?v=2">
                |</head>
                """.trimMargin(),
            )

        assertEquals("/icon.svg?v=2", info.icon)
    }

    @Test
    fun maskIconIsNotUsed() {
        // Safari's monochrome pinned-tab silhouette renders as a black blob anywhere else.
        val info =
            extract(
                """
                |<head>
                |  <link rel="mask-icon" href="/safari-pinned-tab.svg" color="#5bbad5">
                |  <link rel="icon" href="/favicon-32.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("/favicon-32.png", info.icon)
    }

    @Test
    fun iconLinksAfterTheHeadAreIgnored() {
        val info = extract("""<head></head><body><link rel="icon" href="/late.png"></body>""")

        assertEquals("", info.icon)
    }

    @Test
    fun aBodyTitleIsIgnoredWhenTheHeadIsNeverClosed() {
        // `</head>` is optional: `<body>` closes the head implicitly. An inline SVG's <title>
        // ("Close", "Menu") is the classic thing to then mistake for the page title.
        val info = extract("""<head><meta name="description" content="D"><body><svg><title>Close</title></svg></body>""")

        assertEquals("", info.title)
        assertEquals("D", info.description)
    }

    @Test
    fun aBodyIconLinkIsIgnoredWhenTheHeadIsNeverClosed() {
        val info = extract("""<head><title>T</title><body><link rel="icon" href="/late.png"></body>""")

        assertEquals("T", info.title)
        assertEquals("", info.icon)
    }

    @Test
    fun aTitleWithAttributesIsRead() {
        val info = extract("""<head><title data-rh="true">Helmet</title></head>""")

        assertEquals("Helmet", info.title)
    }

    @Test
    fun aLongTitleIsNotCutInsideASurrogatePair() {
        // 299 ASCII chars then an emoji: a plain take(300) keeps only its high surrogate.
        val info = extract("<head><title>" + "a".repeat(299) + "\uD83D\uDE00 tail</title></head>")

        assertTrue(info.title.isNotEmpty())
        assertTrue(!info.title.last().isHighSurrogate(), "title ends in a dangling high surrogate")
    }
}
