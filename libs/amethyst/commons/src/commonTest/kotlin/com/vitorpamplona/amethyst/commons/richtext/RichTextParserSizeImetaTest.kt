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

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The imeta `size` reaches every media model, so a held-back placeholder can show it without a request. */
class RichTextParserSizeImetaTest {
    private fun parse(
        url: String,
        vararg imeta: String,
    ) = RichTextParser()
        .parseText(
            "look at this\n$url",
            ImmutableListOfLists(arrayOf(arrayOf("imeta", "url $url", *imeta))),
            null,
        ).mediaList
        .single()

    @Test
    fun imageSizeFromImeta() {
        val media = parse("https://example.com/photo.jpg", "m image/jpeg", "size 245000")
        assertIs<MediaUrlImage>(media)
        assertEquals(245_000L, media.sizeBytes)
    }

    @Test
    fun pdfSizeFromImeta() {
        val media = parse("https://example.com/paper.pdf", "m application/pdf", "size 2100000")
        assertIs<MediaUrlPdf>(media)
        assertEquals(2_100_000L, media.sizeBytes)
    }

    @Test
    fun videoSizeStillParsed() {
        val media = parse("https://example.com/clip.mp4", "m video/mp4", "size 12400000")
        assertIs<MediaUrlVideo>(media)
        assertEquals(12_400_000L, media.sizeBytes)
    }

    @Test
    fun sizeFromUrlFragment() {
        val media = RichTextParser().createMediaContent("https://example.com/a.jpg#size=300000", emptyMap(), null)
        assertIs<MediaUrlImage>(media)
        assertEquals(300_000L, media.sizeBytes)
    }

    @Test
    fun zeroOrNegativeSizeIsNull() {
        // Some uploaders write `size 0` for "unknown": it must not read "1 KB" or stop the HEAD fallback.
        assertNull(assertIs<MediaUrlImage>(parse("https://example.com/photo.jpg", "m image/jpeg", "size 0")).sizeBytes)
        assertNull(MediaUrlVideo("https://example.com/clip.mp4", sizeBytes = -5).sizeBytes)
        assertNull(EncryptedMediaUrlImage("https://example.com/e", encryptionAlgo = "aes-gcm", encryptionKey = ByteArray(32), encryptionNonce = ByteArray(12), sizeBytes = 0).sizeBytes)
    }

    @Test
    fun garbageSizeIsNull() {
        val media = parse("https://example.com/photo.jpg", "m image/jpeg", "size abc")
        assertIs<MediaUrlImage>(media)
        assertNull(media.sizeBytes)
    }
}
