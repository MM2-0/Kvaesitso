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
 * Tag and attribute shapes a preview fetch can meet in the wild, and the ones a truncated or
 * hostile response can produce. The server picks this input, so "it throws" and "it silently eats
 * the rest of the head" both have to be ruled out for every shape here.
 */
class MetaTagsParserEdgeCaseTest {
    private fun contents(html: String) = MetaTagsParser.parse(html).map { it.attr("content") }.toList()

    // -- the end of the scan ------------------------------------------------------------------

    @Test
    fun stopsAtAnUppercaseHeadEndTag() {
        val metas = contents("""<head><meta name="a" content="1"></HEAD><meta name="b" content="2">""")

        assertEquals(listOf("1"), metas)
    }

    @Test
    fun stopsAtAHeadEndTagWithTrailingSpace() {
        val metas = contents("""<head><meta name="a" content="1"></head ><meta name="b" content="2">""")

        assertEquals(listOf("1"), metas)
    }

    @Test
    fun aHeadEndTagInsideAnAttributeValueDoesNotEndTheScan() {
        val metas = contents("""<head><meta name="a" content="</head>"><meta name="b" content="2"></head>""")

        assertEquals(listOf("</head>", "2"), metas)
    }

    @Test
    fun scansTheWholeDocumentWhenThereIsNoHeadEndTag() {
        val metas = contents("""<head><meta name="a" content="1"><body><meta name="b" content="2">""")

        assertEquals(listOf("1", "2"), metas)
    }

    // -- truncated responses ------------------------------------------------------------------

    @Test
    fun aBodyTruncatedRightAfterASlashDoesNotThrow() {
        // The `/` of a `/>` as the very last byte: the self-closing check must not read past it.
        val metas = contents("""<head><meta name="a" content="1" /""")

        assertEquals(listOf("1"), metas)
    }

    @Test
    fun aBodyTruncatedInsideAnAttributeValueYieldsNoValue() {
        val metas = MetaTagsParser.parse("""<head><meta property="og:title" content="Trunc""").toList()

        assertEquals(1, metas.size)
        assertEquals("og:title", metas[0].attr("property"))
        assertEquals("", metas[0].attr("content"))
    }

    @Test
    fun aBodyTruncatedRightAfterALessThanDoesNotThrow() {
        assertEquals(listOf("1"), contents("""<head><meta name="a" content="1"><"""))
    }

    // -- tag shapes ---------------------------------------------------------------------------

    @Test
    fun readsAnUppercaseMetaTagAndUppercaseAttributeNames() {
        val metas = contents("""<head><META PROPERTY="og:title" CONTENT="Real"></head>""")

        assertEquals(listOf("Real"), metas)
    }

    @Test
    fun anEmptySelfClosedMetaIsSkippedWithoutDerailingTheScan() {
        // `<meta/>` has no separator before the `/`, so the name reads as `meta/` and the tag is
        // dropped. It carries nothing anyway; what matters is that the next tag still parses.
        val metas = contents("""<head><meta/><meta property="og:title" content="Real"></head>""")

        assertEquals(listOf("Real"), metas)
    }

    @Test
    fun aSelfClosingSequenceInsideAQuotedValueDoesNotEndTheTag() {
        val metas =
            contents(
                """<head><meta property="og:title" content="a /> b"><meta name="after" content="ok"></head>""",
            )

        assertEquals(listOf("a /> b", "ok"), metas)
    }

    @Test
    fun readsMetaTagsInsideNoscript() {
        // noscript content is markup for a parser that isn't running scripts, and a redirect meta
        // hidden in there is exactly the kind a preview wants to see.
        val metas =
            contents(
                """<head><noscript><meta http-equiv="refresh" content="0"></noscript><meta property="og:title" content="Real"></head>""",
            )

        assertEquals(listOf("0", "Real"), metas)
    }

    @Test
    fun skipsCdataSections() {
        val metas = contents("""<head><![CDATA[ x > y ]]><meta property="og:title" content="Real"></head>""")

        assertEquals(listOf("Real"), metas)
    }

    // -- attribute shapes ---------------------------------------------------------------------

    @Test
    fun keepsAttributesWhenTheTagEndsWithAValuelessOne() {
        val metas = MetaTagsParser.parse("""<head><meta property="og:title" content="T" data-foo></head>""").toList()

        assertEquals(1, metas.size)
        assertEquals("og:title", metas[0].attr("property"))
        assertEquals("T", metas[0].attr("content"))
    }

    @Test
    fun keepsAValueThatSpansLines() {
        val metas = contents("<head><meta property=\"og:title\" content=\"line1\nline2\"></head>")

        assertEquals(listOf("line1\nline2"), metas)
    }

    @Test
    fun anUnknownAttributeIsIgnoredNotFatal() {
        val metas = contents("""<head><meta data-rh="true" property="og:title" content="Real"></head>""")

        assertEquals(listOf("Real"), metas)
    }

    // -- character references in values --------------------------------------------------------

    @Test
    fun keepsQueryStringAmpersandsIntact() {
        // og:image URLs are full of `&`; only a real character reference may be resolved.
        val metas =
            contents(
                """<head><meta property="og:image" content="https://x.com/i.png?w=1200&h=630&fit=crop&amp;q=80"></head>""",
            )

        assertEquals(listOf("https://x.com/i.png?w=1200&h=630&fit=crop&q=80"), metas)
    }

    @Test
    fun decodesCharacterReferencesOutsideTheBasicMultilingualPlane() {
        val metas = contents("""<head><meta property="og:title" content="&#128512; &#x1F600; hi"></head>""")

        assertEquals(listOf("😀 😀 hi"), metas)
    }

    @Test
    fun leavesAnUnknownCharacterReferenceAlone() {
        val metas = contents("""<head><meta property="og:title" content="AT&T &notareference; &#xZZ;"></head>""")

        assertEquals(listOf("AT&T &notareference; &#xZZ;"), metas)
    }

    // -- laziness -------------------------------------------------------------------------------

    @Test
    fun stopsReadingOnceTheConsumerStops() {
        // OpenGraphParser bails as soon as it has title+description+image; the sequence must not
        // have scanned the rest of the document by then. A tag after an unterminated comment is
        // unreachable, so seeing the first one proves the scan was still lazy.
        val html =
            """
            |<head>
            |  <meta property="og:title" content="T">
            |  <!-- an unterminated comment swallows everything after it
            |  <meta property="og:description" content="D">
            """.trimMargin()

        val first = MetaTagsParser.parse(html).first()

        assertEquals("T", first.attr("content"))
        assertTrue(MetaTagsParser.parse(html).count() == 1)
    }
}
