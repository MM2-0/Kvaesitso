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
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GalleryParserTest {
    @Test
    fun testMixedImageAndVideoRenderedIndividually() {
        // Test that when image + video are in consecutive paragraphs, both should be rendered individually (not as gallery)
        val text =
            "Renfield (2023)\n" +
                "https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg\n" +
                "https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 3 paragraphs (text, image, video)
        assertEquals(3, state.paragraphs.size)

        // First paragraph is text
        assertTrue(state.paragraphs[0] !is ImageGalleryParagraph)
        assertEquals(1, state.paragraphs[0].words.size)

        // Second paragraph is image (rendered individually, not as gallery)
        assertTrue(state.paragraphs[1] !is ImageGalleryParagraph)
        assertEquals(1, state.paragraphs[1].words.size)
        assertTrue(state.paragraphs[1].words[0] is ImageSegment)
        assertEquals("https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg", state.paragraphs[1].words[0].segmentText)

        // Third paragraph is video (rendered individually)
        assertTrue(state.paragraphs[2] !is ImageGalleryParagraph)
        assertEquals(1, state.paragraphs[2].words.size)
        assertTrue(state.paragraphs[2].words[0] is VideoSegment)
        assertEquals("https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4", state.paragraphs[2].words[0].segmentText)
    }

    @Test
    fun testMixedImageAndVideoRenderedIndividuallyDoubled() {
        // Test that when image + video are repeated, both should be rendered individually (not as gallery)
        val text =
            "Renfield (2023)\n" +
                "https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg\n" +
                "https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4\n" +
                "Renfield (2023)\n" +
                "https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg\n" +
                "https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 6 paragraphs (text, image, video, text, image, video)
        assertEquals(6, state.paragraphs.size)

        // First set: text, image, video
        assertTrue(state.paragraphs[0] !is ImageGalleryParagraph)
        assertEquals(1, state.paragraphs[0].words.size)
        assertTrue(state.paragraphs[1] !is ImageGalleryParagraph)
        assertTrue(state.paragraphs[1].words[0] is ImageSegment)
        assertEquals("https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg", state.paragraphs[1].words[0].segmentText)
        assertTrue(state.paragraphs[2] !is ImageGalleryParagraph)
        assertTrue(state.paragraphs[2].words[0] is VideoSegment)
        assertEquals("https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4", state.paragraphs[2].words[0].segmentText)

        // Second set: text, image, video
        assertTrue(state.paragraphs[3] !is ImageGalleryParagraph)
        assertEquals(1, state.paragraphs[3].words.size)
        assertTrue(state.paragraphs[4] !is ImageGalleryParagraph)
        assertTrue(state.paragraphs[4].words[0] is ImageSegment)
        assertEquals("https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg", state.paragraphs[4].words[0].segmentText)
        assertTrue(state.paragraphs[5] !is ImageGalleryParagraph)
        assertTrue(state.paragraphs[5].words[0] is VideoSegment)
        assertEquals("https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4", state.paragraphs[5].words[0].segmentText)
    }

    @Test
    fun testMultipleImagesRenderedAsGallery() {
        // Test that multiple images (no videos) are correctly grouped as gallery
        val text =
            "Gallery:\n" +
                "https://example.com/image1.jpg\n" +
                "https://example.com/image2.png"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 2 image segments (image + video URLs)
        assertEquals(2, state.paragraphs.size)

        // Should render as gallery (1 gallery with 2 images)
        assertTrue("Should render 1 gallery", state.paragraphs[1] is ImageGalleryParagraph)
        assertEquals(2, state.paragraphs[1].words.size, "Gallery should contain 2 images")
        assertEquals("https://example.com/image1.jpg", state.paragraphs[1].words[0].segmentText)
        assertEquals("https://example.com/image2.png", state.paragraphs[1].words[1].segmentText)
    }

    @Test
    fun testMultipleImagesInAParagraphRenderedAsGallery() {
        // Test that multiple images (no videos) are correctly grouped as gallery
        val text =
            "Gallery:\n" +
                "https://example.com/image1.jpg https://example.com/image2.png"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 2 image segments (image + video URLs)
        assertEquals(2, state.paragraphs.size)

        // Should render as gallery (1 gallery with 2 images)
        assertTrue("Should render 1 gallery", state.paragraphs[1] is ImageGalleryParagraph)
        assertEquals(2, state.paragraphs[1].words.size, "Gallery should contain 2 images")
        assertEquals("https://example.com/image1.jpg", state.paragraphs[1].words[0].segmentText)
        assertEquals("https://example.com/image2.png", state.paragraphs[1].words[1].segmentText)
    }

    @Test
    fun testSingleImageRenderedIndividually() {
        // Test that a single image is rendered individually, not as gallery
        val text = "Single image:\nhttps://example.com/image.jpg"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should render individually (not as gallery)
        assertTrue("Should render 1 individual image", state.paragraphs[1] !is ImageGalleryParagraph)
        assertTrue("Should not render as gallery", state.paragraphs[0] !is ImageGalleryParagraph)
        assertEquals("https://example.com/image.jpg", state.paragraphs[1].words[0].segmentText)
    }

    @Test
    fun testGigiMessage() {
        val text = "I played The Typing of the Dead a lot when I was younger, and now I just found out that there's a new take on it: The Last Sentence. Tempted! https://relay.dergigi.com/d6a3e33b101fe219ef251ac6261c10392c2af9918c3c252d4c202016b0b4ec83.jpg https://relay.dergigi.com/d60c9c562912573f214c2b1958cc20bf8913cd718d2ed9e020621e3e3120634b.jpg"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should render individually (not as gallery)
        assertEquals("I played The Typing of the Dead a lot when I was younger, and now I just found out that there's a new take on it: The Last Sentence. Tempted!", state.paragraphs[0].words[0].segmentText)
        assertTrue("Should render as gallery", state.paragraphs[1] is ImageGalleryParagraph)
        assertEquals("https://relay.dergigi.com/d6a3e33b101fe219ef251ac6261c10392c2af9918c3c252d4c202016b0b4ec83.jpg", state.paragraphs[1].words[0].segmentText)
        assertEquals("https://relay.dergigi.com/d60c9c562912573f214c2b1958cc20bf8913cd718d2ed9e020621e3e3120634b.jpg", state.paragraphs[1].words[1].segmentText)
    }

    @Test
    fun testMixedImageAndVideoInSameParagraph() {
        // Test the user's specific example: mixed image and video in same paragraph should render individually
        val text =
            "The Crow (1994)\n" +
                "https://image.tmdb.org/t/p/original/1r7iOhXSbbuUTORNEUOvCUkQ86K.jpg\n" +
                "https://archive.org/download/the-crow-1994_20231024/The%20Crow%201994.rar/The%20Crow%201994.1080p.BluRay.x264%20.%20NVEE%2FThe%20Crow%201994.1080p.BluRay.x264%20.%20NVEE.mp4\n" +
                "#fantasy #action #thriller #film #movies #kinostr"
        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 4 paragraphs (text, image, video, hashtags)
        assertEquals(4, state.paragraphs.size)

        // First paragraph is text
        assertTrue(state.paragraphs[0] !is ImageGalleryParagraph)

        // Second paragraph is image (rendered individually, not as gallery)
        assertTrue(state.paragraphs[1] !is ImageGalleryParagraph)
        assertTrue(state.paragraphs[1].words[0] is ImageSegment)
        assertEquals("https://image.tmdb.org/t/p/original/1r7iOhXSbbuUTORNEUOvCUkQ86K.jpg", state.paragraphs[1].words[0].segmentText)

        // Third paragraph is video (rendered individually)
        assertTrue(state.paragraphs[2] !is ImageGalleryParagraph)
        assertTrue(state.paragraphs[2].words[0] is VideoSegment)
        assertEquals("https://archive.org/download/the-crow-1994_20231024/The%20Crow%201994.rar/The%20Crow%201994.1080p.BluRay.x264%20.%20NVEE%2FThe%20Crow%201994.1080p.BluRay.x264%20.%20NVEE.mp4", state.paragraphs[2].words[0].segmentText)

        // Fourth paragraph is hashtags
        assertTrue(state.paragraphs[3] !is ImageGalleryParagraph)
    }

    @Test
    fun testDesperadoExample() {
        // Test the exact example provided by the user
        val text =
            "Desperado (1995)\n" +
                "https://media.themoviedb.org/t/p/w440_and_h660_face/e3gwpBeXpvGZsxUya9zNym5QXrw.jpg\n" +
                "https://archive.org/download/desperado.-1995.1080p.-blu-ray.x-264.-yify/Desperado.1995.1080p.BluRay.x264.YIFY.mp4\n" +
                "#action #crime #film #movies #kinostr"
        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 4 paragraphs (text, image, video, hashtags)
        assertEquals(4, state.paragraphs.size)

        // First paragraph is text
        assertTrue(state.paragraphs[0] !is ImageGalleryParagraph)
        assertEquals("Desperado (1995)", state.paragraphs[0].words[0].segmentText)

        // Second paragraph is image (rendered individually, not as gallery)
        assertTrue(state.paragraphs[1] !is ImageGalleryParagraph)
        assertEquals(1, state.paragraphs[1].words.size)
        assertTrue(state.paragraphs[1].words[0] is ImageSegment)
        assertEquals("https://media.themoviedb.org/t/p/w440_and_h660_face/e3gwpBeXpvGZsxUya9zNym5QXrw.jpg", state.paragraphs[1].words[0].segmentText)

        // Third paragraph is video (rendered individually)
        assertTrue(state.paragraphs[2] !is ImageGalleryParagraph)
        assertEquals(1, state.paragraphs[2].words.size)
        assertTrue(state.paragraphs[2].words[0] is VideoSegment)
        assertEquals("https://archive.org/download/desperado.-1995.1080p.-blu-ray.x-264.-yify/Desperado.1995.1080p.BluRay.x264.YIFY.mp4", state.paragraphs[2].words[0].segmentText)

        // Fourth paragraph is hashtags
        assertTrue(state.paragraphs[3] !is ImageGalleryParagraph)
    }
}
