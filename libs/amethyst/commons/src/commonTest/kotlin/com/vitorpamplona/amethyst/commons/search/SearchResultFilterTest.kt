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

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The parts of a query no relay can answer.
 *
 * NIP-50 has no negation operator, and "is a reply" is a shape of an event's tags rather than
 * something a relay indexes, so `-term` and the pseudo-kinds only ever narrow anything if the
 * client applies them to what came back. A front end that forgets leaves the reader typing
 * filters that do nothing — which is exactly what happened on Android.
 */
class SearchResultFilterTest {
    private fun event(
        id: String,
        content: String = "",
        kind: Int = 1,
        tags: Array<Array<String>> = emptyArray(),
        createdAt: Long = 1,
    ) = Event(
        id = id,
        pubKey = "abc123def456abc123def456abc123def456abc123def456abc123def456abcd",
        createdAt = createdAt,
        kind = kind,
        tags = tags,
        content = content,
        sig = "sig",
    )

    private fun query(text: String) = QueryParser.parse(text)

    @Test
    fun anExclusionDropsWhatItNames() {
        val q = query("bitcoin -scam")
        assertEquals(listOf("scam"), q.excludeTerms)
        assertTrue(SearchResultFilter.matches(event("a", "bitcoin is fine"), q))
        assertFalse(SearchResultFilter.matches(event("b", "bitcoin scam alert"), q))
    }

    @Test
    fun anExclusionIgnoresCase() {
        val q = query("-Scam")
        assertFalse(SearchResultFilter.matches(event("a", "a SCAM"), q))
        assertFalse(SearchResultFilter.matches(event("b", "a scam"), q))
    }

    @Test
    fun severalExclusionsAllApply() {
        val q = query("bitcoin -scam -airdrop")
        assertTrue(SearchResultFilter.matches(event("a", "bitcoin"), q))
        assertFalse(SearchResultFilter.matches(event("b", "bitcoin airdrop"), q))
        assertFalse(SearchResultFilter.matches(event("c", "bitcoin scam"), q))
    }

    @Test
    fun theReplyPseudoKindKeepsOnlyRepliesOfKindOne() {
        val q = query("kind:reply bitcoin")
        assertEquals(listOf("reply"), q.pseudoKinds)
        assertTrue(SearchResultFilter.matches(event("a", tags = arrayOf(arrayOf("e", "parent"))), q))
        assertFalse(SearchResultFilter.matches(event("b"), q))
        // A kind-30023 article carrying an `e` tag is not a reply.
        assertFalse(SearchResultFilter.matches(event("c", kind = 30023, tags = arrayOf(arrayOf("e", "parent"))), q))
    }

    @Test
    fun theMediaPseudoKindTakesAnImetaTagOrAMediaUrl() {
        val q = query("kind:media")
        assertTrue(SearchResultFilter.matches(event("a", tags = arrayOf(arrayOf("imeta", "url x"))), q))
        assertTrue(SearchResultFilter.matches(event("b", "look https://example.com/cat.jpg"), q))
        assertFalse(SearchResultFilter.matches(event("c", "no media here"), q))
    }

    @Test
    fun aQueryWithNoPostFiltersKeepsEverything() {
        val q = query("from:npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6 bitcoin")
        assertTrue(SearchResultFilter.matches(event("a", "anything at all"), q))
        assertTrue(SearchResultFilter.matches(event("b", "", kind = 30023), q))
    }

    @Test
    fun anExclusionAndAPseudoKindComposeRatherThanOverride() {
        val q = query("kind:reply -scam")
        val reply = arrayOf(arrayOf("e", "parent"))
        assertTrue(SearchResultFilter.matches(event("a", "fine", tags = reply), q))
        assertFalse(SearchResultFilter.matches(event("b", "scam", tags = reply), q))
        assertFalse(SearchResultFilter.matches(event("c", "fine"), q))
    }

    @Test
    fun filterDedupesAndOrdersNewestFirstWhileApplyingTheSameRules() {
        val q = query("-scam")
        val kept = event("aa", "fine", createdAt = 10)
        val out =
            SearchResultFilter.filter(
                listOf(kept, kept, event("bb", "scam", createdAt = 20), event("cc", "also fine", createdAt = 5)),
                q,
            )
        assertEquals(listOf("aa", "cc"), out.map { it.id })
    }
}
