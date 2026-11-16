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

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfParserTest {
    @Test
    fun detectsPdfByExtension() {
        val url = "https://example.com/docs/paper.pdf"
        val state = RichTextParser().parseText(url, EmptyTagList, null)

        val pdfMedia = state.mediaForPager[url]
        assertTrue(pdfMedia is MediaUrlPdf, "Expected MediaUrlPdf for .pdf URL")

        val segment = state.paragraphs[0].words[0]
        assertTrue(segment is PdfSegment, "Expected PdfSegment for .pdf URL, got ${segment::class.simpleName}")
        assertEquals(url, segment.segmentText)
    }

    @Test
    fun detectsPdfFromImetaMimeTypeWithoutExtension() {
        val url = "https://files.example.com/abcd1234"
        val tags =
            ImmutableListOfLists(
                arrayOf(
                    arrayOf("imeta", "url $url", "m application/pdf"),
                ),
            )

        val state = RichTextParser().parseText(url, tags, null)

        val pdfMedia = state.mediaForPager[url]
        assertTrue(pdfMedia is MediaUrlPdf, "Expected MediaUrlPdf from imeta MIME tag")
        assertEquals("application/pdf", pdfMedia.mimeType)

        val segment = state.paragraphs[0].words[0]
        assertTrue(segment is PdfSegment, "Expected PdfSegment from imeta MIME tag, got ${segment::class.simpleName}")
    }

    @Test
    fun isPdfUrlHelperMatchesPdfExtension() {
        assertTrue(RichTextParser.isPdfUrl("https://example.com/doc.pdf"))
        assertTrue(RichTextParser.isPdfUrl("https://example.com/doc.PDF"))
        assertTrue(RichTextParser.isPdfUrl("https://example.com/doc.pdf?sig=abc"))
    }

    // Primal iOS writes a bare subtype (`m jpeg`) instead of a full MIME (`m image/jpeg`); see
    // normalizeMimeType. End-to-end here because the failure was never just the render decision:
    // dropping the imeta also lost the `dim` that reserves the image's height (feed jump) and
    // forced a URL-preview fetch to rediscover a type the imeta had already declared.
    @Test
    fun detectsImageFromMalformedImetaMimeWithImageExtension() {
        val url = "https://blossom.primal.net/33e7c01afbea894a64e1db44dece460b09a2426108f47143754e1cf4bfdf747c.jpg"
        val content = "\n$url"
        val tags =
            ImmutableListOfLists(
                arrayOf(
                    arrayOf("imeta", "url $url", "m jpeg", "dim 1009.0x680.0"),
                    arrayOf("client", "Primal iOS"),
                ),
            )

        val state = RichTextParser().parseText(content, tags, null)

        val imageMedia = state.mediaForPager[url]
        assertTrue(imageMedia is MediaUrlImage, "Expected MediaUrlImage despite the malformed `m jpeg` mime")
        // Repaired on the model too, not just for the render decision: this field becomes
        // Intent.type when the image is shared, and a slash-less one matches no IntentFilter.
        assertEquals("image/jpeg", imageMedia.mimeType, "The bare subtype must not survive onto the model")
        assertEquals("1009x680", imageMedia.dim?.toString(), "The imeta dim must survive so the loader can reserve space")
        assertEquals(1009f / 680f, imageMedia.dim?.aspectRatio())

        val segment = state.paragraphs.flatMap { it.words }.first { it.segmentText == url }
        assertTrue(segment is ImageSegment, "Expected ImageSegment, got ${segment::class.simpleName}")
    }

    // A malformed video subtype (`m mp4`) must likewise fall back to the `.mp4` extension.
    @Test
    fun detectsVideoFromMalformedImetaMimeWithVideoExtension() {
        val url = "https://cdn.example.com/clip.mp4"
        val tags =
            ImmutableListOfLists(
                arrayOf(
                    arrayOf("imeta", "url $url", "m mp4"),
                ),
            )

        val state = RichTextParser().parseText(url, tags, null)

        val videoMedia = state.mediaForPager[url]
        assertTrue(videoMedia is MediaUrlVideo, "Expected MediaUrlVideo despite the malformed `m mp4` mime")
    }

    // An extensionless URL is the shape Blossom hands out, where the imeta is the only type signal
    // there is: a recognizable bare subtype now carries it, and an unrecognizable one leaves
    // nothing to recover from. The second half documents the limit so it isn't read as a
    // regression.
    @Test
    fun malformedImetaMimeWithoutExtensionIsRecoveredFromTheMimeAlone() {
        val url = "https://files.example.com/abcd1234"

        fun parse(mime: String) =
            RichTextParser()
                .parseText(url, ImmutableListOfLists(arrayOf(arrayOf("imeta", "url $url", "m $mime"))), null)
                .mediaForPager[url]

        assertTrue(parse("jpeg") is MediaUrlImage, "`m jpeg` alone is enough to classify the media")
        assertEquals(null, parse("notarealtype"), "Nothing to recover from -> not treated as media")
    }
}
