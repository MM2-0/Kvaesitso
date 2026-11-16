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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchResultSorterTest {
    private fun event(
        id: String,
        createdAt: Long,
        content: String = "",
        kind: Int = 1,
    ) = Event(
        id = id,
        pubKey = "abc123def456abc123def456abc123def456abc123def456abc123def456abcd",
        createdAt = createdAt,
        kind = kind,
        tags = emptyArray(),
        content = content,
        sig = "sig",
    )

    private fun article(
        id: String,
        createdAt: Long,
        content: String = "",
        title: String? = null,
    ): LongFormContentEvent {
        val tags =
            if (title != null) {
                arrayOf(arrayOf("title", title))
            } else {
                emptyArray()
            }
        return LongFormContentEvent(
            id = id,
            pubKey = "abc123def456abc123def456abc123def456abc123def456abc123def456abcd",
            createdAt = createdAt,
            tags = tags,
            content = content,
            sig = "sig",
        )
    }

    private fun user(
        hex: String,
        displayName: String,
    ): User {
        val u = User(hex) { addr -> Note(addr.toValue()) }
        val meta = UserMetadata().apply { this.displayName = displayName }
        val metaEvent =
            MetadataEvent(
                id = "meta-$hex",
                pubKey = hex,
                createdAt = 0L,
                tags = emptyArray(),
                content = "{}",
                sig = "sig",
            )
        u.updateUserInfo(meta, metaEvent)
        return u
    }

    // --- Event sorting ---

    private val asEvent: (Event) -> Event? = { it }

    @Test
    fun newestSortsDescending() {
        val events = listOf(event("a", 100), event("b", 300), event("c", 200))
        val sorted = SearchPipeline.rank(events, SearchSortOrder.NEWEST, "", asEvent)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun oldestSortsAscending() {
        val events = listOf(event("a", 300), event("b", 100), event("c", 200))
        val sorted = SearchPipeline.rank(events, SearchSortOrder.OLDEST, "", asEvent)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun relevanceEmptyQueryFallsBackToRecency() {
        val events = listOf(event("a", 100), event("b", 300), event("c", 200))
        val sorted = SearchPipeline.rank(events, SearchSortOrder.RELEVANCE, "", asEvent)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun relevanceExactMatchBeatsPartial() {
        val exact = event("exact", 100, content = "bitcoin is great")
        val partial = event("partial", 100, content = "bit of something")
        val sorted = SearchPipeline.rank(listOf(partial, exact), SearchSortOrder.RELEVANCE, "bitcoin", asEvent)
        assertEquals("exact", sorted.first().id)
    }

    @Test
    fun relevanceWordBoundaryBeatsSubstring() {
        val boundary = event("boundary", 100, content = "I love bitcoin and lightning")
        val substring = event("substr", 100, content = "bitcoinery is not a word")
        val sorted = SearchPipeline.rank(listOf(substring, boundary), SearchSortOrder.RELEVANCE, "bitcoin", asEvent)
        assertEquals("boundary", sorted.first().id)
    }

    @Test
    fun relevanceArticleTitleBoost() {
        val withTitle = article("titled", 100, content = "some content", title = "Bitcoin Guide")
        val withoutTitle = event("notitle", 100, content = "bitcoin bitcoin bitcoin")
        val sorted = SearchPipeline.rank(listOf(withoutTitle, withTitle), SearchSortOrder.RELEVANCE, "bitcoin", asEvent)
        assertEquals("titled", sorted.first().id)
    }

    @Test
    fun relevanceMultipleTokensAddUp() {
        val multi = event("multi", 100, content = "bitcoin and lightning network")
        val single = event("single", 100, content = "bitcoin only here")
        val sorted =
            SearchPipeline.rank(
                listOf(single, multi),
                SearchSortOrder.RELEVANCE,
                "bitcoin lightning",
                asEvent,
            )
        assertEquals("multi", sorted.first().id)
    }

    // --- People sorting ---

    @Test
    fun nameAzSortsAlphabetically() {
        val people =
            listOf(
                user("cc00000000000000000000000000000000000000000000000000000000000000", "Charlie"),
                user("aa00000000000000000000000000000000000000000000000000000000000000", "Alice"),
                user("bb00000000000000000000000000000000000000000000000000000000000000", "Bob"),
            )
        val sorted = SearchResultSorter.sortPeople(people, SearchSortOrder.NAME_AZ)
        assertEquals(listOf("Alice", "Bob", "Charlie"), sorted.map { it.toBestDisplayName() })
    }

    @Test
    fun nameZaSortsReverseAlphabetically() {
        val people =
            listOf(
                user("aa00000000000000000000000000000000000000000000000000000000000000", "Alice"),
                user("cc00000000000000000000000000000000000000000000000000000000000000", "Charlie"),
                user("bb00000000000000000000000000000000000000000000000000000000000000", "Bob"),
            )
        val sorted = SearchResultSorter.sortPeople(people, SearchSortOrder.NAME_ZA)
        assertEquals(listOf("Charlie", "Bob", "Alice"), sorted.map { it.toBestDisplayName() })
    }

    @Test
    fun nameSortIsCaseInsensitive() {
        val people =
            listOf(
                user("aa00000000000000000000000000000000000000000000000000000000000000", "alice"),
                user("bb00000000000000000000000000000000000000000000000000000000000000", "Bob"),
            )
        val sorted = SearchResultSorter.sortPeople(people, SearchSortOrder.NAME_AZ)
        assertEquals("alice", sorted.first().toBestDisplayName())
    }

    // --- Score function ---

    @Test
    fun scoreExactPhraseHigherThanTokens() {
        val exactEvent = event("e", 100, content = "bitcoin lightning network")
        val tokenEvent = event("t", 100, content = "lightning and bitcoin elsewhere network")
        val exactScore = SearchResultSorter.scoreEvent(exactEvent, "bitcoin lightning")
        val tokenScore = SearchResultSorter.scoreEvent(tokenEvent, "bitcoin lightning")
        assertTrue(exactScore > tokenScore)
    }
}
