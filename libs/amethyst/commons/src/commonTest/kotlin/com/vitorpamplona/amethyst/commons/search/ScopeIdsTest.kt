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
package com.vitorpamplona.amethyst.commons.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeIdsTest {
    @Test
    fun aBareHostnameIsAskedAsBothSchemesAndBothSlashForms() {
        val ids = ScopeIds.siteIds("Example.com/Path")
        // Scheme and host lowercase, canonical first; the path keeps its case, because it is
        // case-sensitive on most servers.
        assertEquals("https://example.com/Path", ids.first())
        assertTrue(ids.contains("http://example.com/Path"))
        assertTrue(ids.contains("https://example.com/Path/"))
        assertTrue(ids.none { it.contains("#") })
    }

    @Test
    fun aTypedSchemeIsNotDoubled() {
        val ids = ScopeIds.siteIds("http://example.com")
        assertTrue(ids.none { it.startsWith("https://") }, ids.toString())
    }

    @Test
    fun aFragmentOnlyValueAsksNothing() {
        assertTrue(ScopeIds.siteIds("#top").isEmpty())
    }

    @Test
    fun anIsbnIsAskedWithAndWithoutItsHyphens() {
        assertEquals(listOf("isbn:9780134685991", "isbn:978-0-13-468599-1"), ScopeIds.scopeIds("isbn", "978-0-13-468599-1"))
    }

    @Test
    fun anIsanIsAlsoAskedAsItsFiveSegmentRoot() {
        val ids = ScopeIds.scopeIds("isan", "0000-0000-401A-0000-7-0000-0000-9")
        assertEquals("isan:0000-0000-401A-0000-7", ids.first())
        assertTrue(ids.contains("isan:0000-0000-401A-0000-7-0000-0000-9"))
    }

    @Test
    fun aPublisherValueTakesAGuidSegmentUnlessItAlreadyHasOne() {
        assertTrue(ScopeIds.scopeIds("podcast:publisher", "AbC").contains("podcast:publisher:guid:AbC"))
        assertTrue(ScopeIds.scopeIds("podcast:publisher", "guid:AbC").none { it.contains("guid:guid:") })
    }

    @Test
    fun caseInsensitiveSchemesAreAskedLowercasedAndAsTyped() {
        assertEquals(listOf("doi:10.1234/abc", "doi:10.1234/ABC"), ScopeIds.scopeIds("doi", "10.1234/ABC"))
        assertEquals(listOf("geo:1,2", "geo:1,2"), ScopeIds.scopeIds("geo", "1,2") + ScopeIds.scopeIds("geo", "1,2"))
    }

    @Test
    fun everyCasingOfATagIsWorthAsking() {
        assertEquals(listOf("Bitcoin", "bitcoin", "Bitcoin", "BITCOIN").distinct(), ScopeIds.tagValues("Bitcoin"))
        assertEquals(listOf("bitcoin", "Bitcoin", "BITCOIN"), ScopeIds.tagValues("bitcoin"))
        assertTrue(ScopeIds.tagValues("").isEmpty())
    }

    @Test
    fun aHashtagIsWrittenAsAnExternalIdBothWays() {
        assertEquals(listOf("#nostr", "nostr"), ScopeIds.hashtagIds(listOf("nostr")))
    }
}
