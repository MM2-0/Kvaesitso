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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A page with no OpenGraph image is still worth a card when it has text: the compact icon + text
 * card, built from its title, description and favicon.
 */
class UrlInfoItemTextPreviewTest {
    private fun page(
        url: String = "https://example.nsite.lol/",
        title: String = "",
        description: String = "",
        image: String = "",
        icon: String = "",
        mimeType: String = "text/html; charset=utf-8",
    ) = UrlInfoItem(url = url, title = title, description = description, image = image, mimeType = mimeType, icon = icon)

    @Test
    fun aTitleAloneIsEnoughToKeepThePreview() {
        assertTrue(page(title = "Private Provider").fetchComplete())
    }

    @Test
    fun aDescriptionAloneIsEnoughToKeepThePreview() {
        assertTrue(page(description = "A workspace").fetchComplete())
    }

    @Test
    fun aPageWithNothingStaysEmpty() {
        assertFalse(page().fetchComplete())
        assertFalse(page(title = "   ").fetchComplete())
    }

    @Test
    fun aRelativeIconIsResolvedAgainstThePage() {
        assertEquals(
            "https://example.nsite.lol/docs/favicon.svg",
            page(url = "https://example.nsite.lol/docs/index.html", icon = "favicon.svg").iconUrlFullPath,
        )
    }

    @Test
    fun noDeclaredIconFallsBackToTheOriginFavicon() {
        assertEquals(
            "https://example.nsite.lol/favicon.ico",
            page(url = "https://example.nsite.lol/a/b/c.html").iconUrlFullPath,
        )
    }

    @Test
    fun aNonHttpIconFallsBackToTheOriginFavicon() {
        assertEquals(
            "https://example.nsite.lol/favicon.ico",
            page(icon = "file:///etc/passwd").iconUrlFullPath,
        )
    }

    @Test
    fun nonHtmlUrlsHaveNoIcon() {
        assertNull(page(url = "https://example.com/a.png", image = "https://example.com/a.png", mimeType = "image/png").iconUrlFullPath)
    }

    @Test
    fun anUppercaseHtmlMimeStillGetsAnIcon() {
        // UrlPreview stores MediaType.toString(), which keeps the server's spelling.
        assertEquals(
            "https://example.nsite.lol/favicon.ico",
            page(mimeType = "Text/HTML; charset=UTF-8").iconUrlFullPath,
        )
    }

    @Test
    fun anEmptyDataIconMeansTheSiteHasNoIcon() {
        // `<link rel="icon" href="data:,">` is the common way to tell browsers not to request
        // /favicon.ico. Requesting it anyway is a guaranteed miss.
        assertNull(page(icon = "data:,").iconUrlFullPath)
    }

    @Test
    fun anInlineImageIconIsUsedAsIs() {
        val inline = "data:image/png;base64,iVBORw0KGgo="
        assertEquals(inline, page(icon = inline).iconUrlFullPath)
    }
}
