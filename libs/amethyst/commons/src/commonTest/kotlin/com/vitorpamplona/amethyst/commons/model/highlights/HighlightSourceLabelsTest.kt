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
package com.vitorpamplona.amethyst.commons.model.highlights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HighlightSourceLabelsTest {
    @Test
    fun mediumSlugDropsTheTrailingHash() {
        val url = "https://medium.com/feenk/rewilding-software-engineering-083b86c81c43"
        assertEquals("Rewilding software engineering", HighlightSourceLabels.titleFromUrl(url))
        assertEquals("medium.com/feenk", HighlightSourceLabels.siteFromUrl(url))
    }

    @Test
    fun commonBlogLayouts() {
        assertEquals("Why we built it", HighlightSourceLabels.titleFromUrl("https://example.substack.com/p/why-we-built-it"))
        assertEquals("Hello world post", HighlightSourceLabels.titleFromUrl("https://blog.example.com/2024/05/12/hello-world-post/"))
        assertEquals("Software engineering", HighlightSourceLabels.titleFromUrl("https://en.wikipedia.org/wiki/Software_engineering"))
        assertEquals("Getting started guide", HighlightSourceLabels.titleFromUrl("https://example.com/docs/getting-started-guide.html?utm_source=x#intro"))
        assertEquals("My great essay", HighlightSourceLabels.titleFromUrl("https://example.com/my-great-essay/amp"))
        assertEquals("Thoughts on nostr", HighlightSourceLabels.titleFromUrl("https://example.com/posts/12345-thoughts-on-nostr-98765"))
    }

    @Test
    fun percentEncodedSlugsAreDecoded() {
        assertEquals("Café society", HighlightSourceLabels.titleFromUrl("https://example.com/caf%C3%A9-society"))
    }

    @Test
    fun segmentsAreDecodedOnce() {
        // A literal "%41" in the slug is spelled %2541; decoding twice would make it "A".
        assertEquals("Save %41 now", HighlightSourceLabels.titleFromUrl("https://example.com/save-%2541-now"))
        assertEquals("medium.com/@tudor", HighlightSourceLabels.siteFromUrl("https://medium.com/%40tudor/some-post-slug-1234abcd5678"))
    }

    @Test
    fun signedEscapesAreNotDecoded() {
        // `%-1` is not an escape. Read as one it became byte 0xFF, i.e. "Odd \uFFFD slug";
        // left as text, `%` and `1` aren't words and the slug is (rightly) not a title.
        assertNull(HighlightSourceLabels.titleFromUrl("https://example.com/odd-%-1-slug"))
    }

    @Test
    fun opaquePathsGiveNoTitle() {
        assertNull(HighlightSourceLabels.titleFromUrl("https://medium.com/"))
        assertNull(HighlightSourceLabels.titleFromUrl("https://medium.com"))
        assertNull(HighlightSourceLabels.titleFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(HighlightSourceLabels.titleFromUrl("https://example.com/blog"))
        assertNull(HighlightSourceLabels.titleFromUrl("https://example.com/items/123456"))
        assertNull(HighlightSourceLabels.titleFromUrl("https://example.com/d/3f2a1b2c-1a2b-4c3d-8e9f-0a1b2c3d4e5f"))
        assertNull(HighlightSourceLabels.titleFromUrl("https://example.com/a1b2c3d4e5f6"))
    }

    @Test
    fun addressableSlugs() {
        assertEquals("Bitcoin here now", HighlightSourceLabels.titleFromSlug("bitcoin-here-now"))
        assertNull(HighlightSourceLabels.titleFromSlug("1700000000123"))
        assertNull(HighlightSourceLabels.titleFromSlug("Ab3kd92LxQ"))
        assertNull(HighlightSourceLabels.titleFromSlug(""))
    }

    @Test
    fun siteNamesAPublicationButNotASection() {
        assertEquals("medium.com/@tudor", HighlightSourceLabels.siteFromUrl("https://medium.com/@tudor/some-post-slug-1234abcd5678"))
        assertEquals("example.substack.com", HighlightSourceLabels.siteFromUrl("https://example.substack.com/p/why-we-built-it"))
        assertEquals("example.com", HighlightSourceLabels.siteFromUrl("https://www.example.com/blog/hello-world"))
        assertEquals("example.com", HighlightSourceLabels.siteFromUrl("https://example.com/2024/05/hello-world"))
        assertEquals("example.com", HighlightSourceLabels.siteFromUrl("https://example.com/en/hello-world"))
        assertEquals("example.com", HighlightSourceLabels.siteFromUrl("https://example.com/hello-world"))
        assertEquals("github.com/vitorpamplona", HighlightSourceLabels.siteFromUrl("https://github.com/vitorpamplona/amethyst"))
        assertNull(HighlightSourceLabels.siteFromUrl("not a url"))
    }

    @Test
    fun cleanTitleDropsTheRepeatedSiteName() {
        assertEquals("Rewilding Software Engineering", HighlightSourceLabels.cleanTitle("Rewilding Software Engineering | Medium", "Medium"))
        assertEquals("A post", HighlightSourceLabels.cleanTitle("A post - The Blog", "The Blog"))
        assertEquals("A post | Other", HighlightSourceLabels.cleanTitle("A post | Other", "Medium"))
        assertEquals("Medium", HighlightSourceLabels.cleanTitle("Medium", "Medium"))
        assertEquals("A post", HighlightSourceLabels.cleanTitle("  A post ", null))
    }
}
