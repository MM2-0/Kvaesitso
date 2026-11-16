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
package com.vitorpamplona.amethyst.commons.browser

import com.vitorpamplona.amethyst.commons.browser.OmniboxSuggestions.Candidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmniboxSuggestionsTest {
    private val candidates =
        listOf(
            Candidate("https://github.com", "GitHub", isFavorite = true),
            Candidate("https://gitlab.com", "GitLab", isFavorite = false, visitCount = 30, lastVisitedAt = 100),
            Candidate("https://news.ycombinator.com", "Hacker News", isFavorite = false, visitCount = 5),
            Candidate("https://example.com/git", "Example Git Page", isFavorite = false, visitCount = 1),
        )

    @Test
    fun emptyTypedYieldsNothing() {
        assertTrue(OmniboxSuggestions.rank("", candidates).isEmpty())
    }

    @Test
    fun hostPrefixRanksAndFavoriteWins() {
        val result = OmniboxSuggestions.rank("git", candidates)
        // github (favorite, host-prefix) ranks above gitlab (host-prefix), both above example.com (path/substring).
        assertEquals(listOf("github.com", "gitlab.com", "example.com"), result.map { it.host })
    }

    @Test
    fun noMatchIsFilteredOut() {
        val result = OmniboxSuggestions.rank("github", candidates)
        assertEquals(listOf("github.com"), result.map { it.host })
    }

    @Test
    fun dedupesByHost() {
        val dupes =
            listOf(
                Candidate("https://github.com/a", "A", isFavorite = false, visitCount = 1),
                Candidate("https://github.com/b", "B", isFavorite = false, visitCount = 9),
            )
        val result = OmniboxSuggestions.rank("github", dupes)
        assertEquals(1, result.size)
        assertEquals("https://github.com/b", result.first().url)
    }

    @Test
    fun completionOffersHostForBareFragment() {
        val ranked = OmniboxSuggestions.rank("git", candidates)
        assertEquals("github.com", OmniboxSuggestions.completion("git", ranked))
    }

    @Test
    fun completionSuppressedForSchemeSlashOrSpaces() {
        val ranked = OmniboxSuggestions.rank("git", candidates)
        assertNull(OmniboxSuggestions.completion("https://git", ranked))
        assertNull(OmniboxSuggestions.completion("github.com/", ranked))
        assertNull(OmniboxSuggestions.completion("git hub", ranked))
    }

    @Test
    fun completionNullWhenAlreadyComplete() {
        val ranked = OmniboxSuggestions.rank("github.com", candidates)
        assertNull(OmniboxSuggestions.completion("github.com", ranked))
    }
}
