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

import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuerySerializerTest {
    @Test
    fun emptyQuery() {
        assertEquals("", QuerySerializer.serialize(SearchQuery.EMPTY))
    }

    @Test
    fun textOnly() {
        val q = SearchQuery(text = "bitcoin")
        assertEquals("bitcoin", QuerySerializer.serialize(q))
    }

    @Test
    fun kindOnly() {
        val q = SearchQuery(kinds = persistentListOf(1))
        assertEquals("kind:note", QuerySerializer.serialize(q))
    }

    @Test
    fun kindUnknown() {
        val q = SearchQuery(kinds = persistentListOf(99999))
        assertEquals("kind:99999", QuerySerializer.serialize(q))
    }

    @Test
    fun multipleKinds() {
        val q = SearchQuery(kinds = persistentListOf(1, 30023))
        assertEquals("kind:note kind:article", QuerySerializer.serialize(q))
    }

    @Test
    fun pseudoKinds() {
        val q = SearchQuery(pseudoKinds = persistentListOf("reply", "media"))
        assertEquals("kind:reply kind:media", QuerySerializer.serialize(q))
    }

    @Test
    fun sinceDate() {
        val q = SearchQuery(since = LocalClock.startOfDay(SearchDate(2025, 1, 1)))
        assertEquals("since:2025-01-01", QuerySerializer.serialize(q))
    }

    @Test
    fun untilDate() {
        val q = SearchQuery(until = LocalClock.startOfDay(SearchDate(2025, 1, 1)))
        assertEquals("until:2025-01-01", QuerySerializer.serialize(q))
    }

    @Test
    fun hashtags() {
        val q = SearchQuery(hashtags = persistentListOf("bitcoin", "nostr"))
        assertEquals("#bitcoin #nostr", QuerySerializer.serialize(q))
    }

    @Test
    fun excludeTerms() {
        val q = SearchQuery(excludeTerms = persistentListOf("spam", "scam"))
        assertEquals("-spam -scam", QuerySerializer.serialize(q))
    }

    @Test
    fun orTerms() {
        val q = SearchQuery(orTerms = persistentListOf("bitcoin", "lightning"))
        assertEquals("bitcoin OR lightning", QuerySerializer.serialize(q))
    }

    @Test
    fun language() {
        val q = SearchQuery(language = "en", text = "bitcoin")
        assertEquals("lang:en bitcoin", QuerySerializer.serialize(q))
    }

    @Test
    fun domain() {
        val q = SearchQuery(domain = "nostr.com", text = "hello")
        assertEquals("domain:nostr.com hello", QuerySerializer.serialize(q))
    }

    @Test
    fun authorNames() {
        val q = SearchQuery(authorNames = persistentListOf("vitor"))
        assertEquals("from:vitor", QuerySerializer.serialize(q))
    }

    @Test
    fun combinedQuery() {
        val q =
            SearchQuery(
                authorNames = persistentListOf("vitor"),
                kinds = persistentListOf(1),
                since = LocalClock.startOfDay(SearchDate(2025, 1, 1)),
                hashtags = persistentListOf("bitcoin"),
                text = "lightning",
                excludeTerms = persistentListOf("spam"),
            )
        val result = QuerySerializer.serialize(q)
        assertTrue(result.contains("from:vitor"))
        assertTrue(result.contains("kind:note"))
        assertTrue(result.contains("since:2025-01-01"))
        assertTrue(result.contains("#bitcoin"))
        assertTrue(result.contains("lightning"))
        assertTrue(result.contains("-spam"))
    }

    @Test
    fun orderingOperatorsFirst() {
        val q =
            SearchQuery(
                authorNames = persistentListOf("alice"),
                kinds = persistentListOf(1),
                hashtags = persistentListOf("nostr"),
                text = "hello",
                excludeTerms = persistentListOf("bad"),
            )
        val result = QuerySerializer.serialize(q)
        val fromIdx = result.indexOf("from:")
        val kindIdx = result.indexOf("kind:")
        val hashIdx = result.indexOf("#nostr")
        val textIdx = result.indexOf("hello")
        val excludeIdx = result.indexOf("-bad")
        assertTrue(fromIdx < kindIdx)
        assertTrue(kindIdx < hashIdx)
        assertTrue(hashIdx < textIdx)
        assertTrue(textIdx < excludeIdx)
    }

    @Test
    fun timestampToDateEpoch() {
        assertEquals("1970-01-01", QuerySerializer.timestampToDate(0L))
    }

    @Test
    fun timestampToDate2025() {
        assertEquals("2025-01-01", QuerySerializer.timestampToDate(1735689600L))
    }

    @Test
    fun roundtrip() {
        // Parse a complex query, serialize, parse again — should produce equivalent SearchQuery
        val input = "kind:note since:2025-01-01 #bitcoin lightning -spam"
        val q1 = QueryParser.parse(input)
        val serialized = QuerySerializer.serialize(q1)
        val q2 = QueryParser.parse(serialized)
        assertEquals(q1.kinds, q2.kinds)
        assertEquals(q1.since, q2.since)
        assertEquals(q1.hashtags, q2.hashtags)
        assertEquals(q1.excludeTerms, q2.excludeTerms)
        assertEquals(q1.text, q2.text)
    }
}
