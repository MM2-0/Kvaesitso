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
package com.vitorpamplona.amethyst.commons.feeds

import junit.framework.TestCase.assertTrue
import org.junit.Test

class SupportedContentTest {
    @Test
    fun acceptableUrl() {
        val supportedExtensions = setOf(".mp4", ".webm", ".jpg", ".png")
        val mimeTypes = setOf("video/mp4", "video/webm", "image/jpeg", "image/png")
        val blockedUrls = listOf("youtube.com", "youtu.be")

        val testVideoUrl = "https://example.com/video.mp4"
        val blockedUrl = "https://youtube.com/watch?v=example"
        val urlWithQuery = "https://example.com/media.jpg?param=1"
        val urlWithFragment = "https://example.com/data.png#section"

        val contentSupport = SupportedContent(blockedUrls, mimeTypes, supportedExtensions)

        // Valid scenarios
        assertTrue(contentSupport.acceptableUrl(testVideoUrl, "video/mp4")) // Valid extension and mime
        assertTrue(contentSupport.acceptableUrl(urlWithQuery, "image/jpeg")) // Valid query param extension
        assertTrue(contentSupport.acceptableUrl(urlWithFragment, "image/png")) // Valid fragment extension

        assertTrue(contentSupport.acceptableUrl(testVideoUrl, null))
        assertTrue(contentSupport.acceptableUrl(urlWithQuery, null))
        assertTrue(contentSupport.acceptableUrl(urlWithFragment, null))

        // Blocked URL scenarios
        assertTrue(!contentSupport.acceptableUrl(blockedUrl, null)) // Blocked URL

        // Invalid scenarios
        assertTrue(!contentSupport.acceptableUrl("https://example.com/file.docx", "application/docx")) // Unsupported extension/mime
        assertTrue(!contentSupport.acceptableUrl("https://example.com/file.docx", null)) // Unsupported extension/mime
    }

    @Test
    fun extensionsAreMatchedOnlyAfterADot() {
        // The undotted spelling the production filters actually pass (RichTextParser.videoExt).
        val contentSupport = SupportedContent(emptyList(), emptySet(), setOf("mp4", "mp3", "jpg"))

        assertTrue(contentSupport.acceptableUrl("https://example.com/clip.mp4", null))
        assertTrue(contentSupport.acceptableUrl("https://example.com/clip.MP4?sig=a", null))

        // nostr.build's HTML player page: `_mp3`, not `.mp3`. It used to be admitted into the
        // video feed by a bare endsWith.
        assertTrue(!contentSupport.acceptableUrl("https://e.nostr.build/a_ETvKzX2OdOGmFEp1avRlm5_mp3?t=Aria", null))
        assertTrue(!contentSupport.acceptableUrl("https://e.nostr.build/v_ETvKzX2OdOGmFEp1avRlm5_mp4?t=Clip", null))
        assertTrue(!contentSupport.acceptableUrl("https://example.com/my-thoughts-on-mp3", null))
        assertTrue(!contentSupport.acceptableUrl("https://example.com/mp4", null))
    }
}
