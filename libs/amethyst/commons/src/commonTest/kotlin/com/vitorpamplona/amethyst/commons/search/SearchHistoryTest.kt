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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The history, over the storage it is now independent of.
 *
 * All of this used to live in a desktop `object` bolted to `java.util.prefs`, which is the only
 * reason Android never had it and the only reason none of it was ever tested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryTest {
    private class InMemory(
        vararg seed: Pair<String, String>,
    ) : SearchHistoryStorage {
        val values = seed.toMap().toMutableMap()

        override suspend fun read(key: String) = values[key]

        override suspend fun write(
            key: String,
            value: String?,
        ) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    private fun q(text: String) = QueryParser.parse(text)

    @Test
    fun whatWasStoredComesBack() =
        runTest {
            val storage = InMemory(SearchHistory.KEY_RECENT to "kind:article bitcoin\n#nostr")
            val history = SearchHistory(storage, backgroundScope)
            runCurrent()
            assertEquals(
                listOf(30023),
                history.recent.value
                    .first()
                    .kinds,
            )
            assertEquals(listOf("nostr"), history.recent.value[1].hashtags)
        }

    @Test
    fun aRepeatedSearchMovesUpRatherThanAppearingTwice() =
        runTest {
            val history = SearchHistory(InMemory(), backgroundScope)
            runCurrent()
            history.remember(q("bitcoin"))
            history.remember(q("nostr"))
            history.remember(q("bitcoin"))
            runCurrent()
            assertEquals(2, history.recent.value.size)
            assertEquals(
                "bitcoin",
                history.recent.value
                    .first()
                    .text,
            )
        }

    @Test
    fun twoQueriesThatMeanTheSameThingCountAsOne() =
        runTest {
            // Compared on the serialized form, so the order the tokens were typed in is not a
            // second entry.
            val history = SearchHistory(InMemory(), backgroundScope)
            runCurrent()
            history.remember(q("kind:article bitcoin"))
            history.remember(q("bitcoin kind:article"))
            runCurrent()
            assertEquals(1, history.recent.value.size)
        }

    @Test
    fun anEmptyQueryIsNotASearchWorthRemembering() =
        runTest {
            val history = SearchHistory(InMemory(), backgroundScope)
            runCurrent()
            history.remember(SearchQuery.EMPTY)
            history.remember(q("   "))
            runCurrent()
            assertTrue(history.recent.value.isEmpty())
        }

    @Test
    fun theListStopsAtItsCapKeepingTheNewest() =
        runTest {
            val history = SearchHistory(InMemory(), backgroundScope)
            runCurrent()
            repeat(SearchHistory.MAX_RECENT + 5) { history.remember(q("term$it")) }
            runCurrent()
            assertEquals(SearchHistory.MAX_RECENT, history.recent.value.size)
            assertEquals(
                "term${SearchHistory.MAX_RECENT + 4}",
                history.recent.value
                    .first()
                    .text,
            )
        }

    @Test
    fun everyChangeReachesTheStorage() =
        runTest {
            val storage = InMemory()
            val history = SearchHistory(storage, backgroundScope)
            runCurrent()

            history.remember(q("bitcoin"))
            runCurrent()
            assertEquals("bitcoin", storage.values[SearchHistory.KEY_RECENT])

            history.clearRecent()
            runCurrent()
            assertTrue(SearchHistory.KEY_RECENT !in storage.values, "a cleared history is removed, not stored empty")
        }

    @Test
    fun aSavedSearchKeepsItsNameAcrossTheRoundTrip() =
        runTest {
            val storage = InMemory()
            val history = SearchHistory(storage, backgroundScope)
            runCurrent()
            history.save(q("kind:article bitcoin"), "Bitcoin writing")
            runCurrent()

            val reloaded = SearchHistory(InMemory(SearchHistory.KEY_SAVED to storage.values.getValue(SearchHistory.KEY_SAVED)), backgroundScope)
            runCurrent()
            assertEquals(
                "Bitcoin writing",
                reloaded.saved.value
                    .single()
                    .label,
            )
            assertEquals(
                listOf(30023),
                reloaded.saved.value
                    .single()
                    .query.kinds,
            )
        }

    @Test
    fun aLabelWithATabInItDoesNotEatTheQueryBesideIt() =
        runTest {
            // The record separator is a tab, so a label carrying one would shift every field
            // after it. Whatever the encoding does with it, the query must survive.
            val storage = InMemory()
            val history = SearchHistory(storage, backgroundScope)
            runCurrent()
            history.save(q("bitcoin"), "mine\tyours")
            runCurrent()

            val reloaded = SearchHistory(InMemory(SearchHistory.KEY_SAVED to storage.values.getValue(SearchHistory.KEY_SAVED)), backgroundScope)
            runCurrent()
            assertEquals(
                "bitcoin",
                reloaded.saved.value
                    .singleOrNull()
                    ?.query
                    ?.text,
            )
            assertEquals(
                "mine\tyours",
                reloaded.saved.value
                    .single()
                    .label,
            )
        }

    @Test
    fun forgettingRemovesOnlyTheOneNamed() =
        runTest {
            val history = SearchHistory(InMemory(), backgroundScope)
            runCurrent()
            history.save(q("bitcoin"), "one")
            history.save(q("nostr"), "two")
            runCurrent()
            history.forget(
                history.saved.value
                    .first()
                    .id,
            )
            runCurrent()
            assertEquals(listOf("two"), history.saved.value.map { it.label })
        }

    @Test
    fun aSearchRememberedBeforeDiskAnswersIsNotLost() =
        runTest {
            // The screen opens and the file is read in the background. A reader who presses Enter
            // inside that window had their search assigned away by the restore — in memory and
            // then on disk, because the next write persists whatever survived.
            val storage = InMemory(SearchHistory.KEY_RECENT to "nostr")
            val history = SearchHistory(storage, backgroundScope)
            history.remember(q("bitcoin"))
            runCurrent()

            assertEquals(listOf("bitcoin", "nostr"), history.recent.value.map { it.text })
            assertEquals("bitcoin\nnostr", storage.values[SearchHistory.KEY_RECENT])
        }

    @Test
    fun aSavedSearchMadeBeforeDiskAnswersSurvivesToo() =
        runTest {
            val storage = InMemory()
            val seeded = SearchHistory(storage, backgroundScope)
            runCurrent()
            seeded.save(q("nostr"), "stored")
            runCurrent()

            val history = SearchHistory(InMemory(SearchHistory.KEY_SAVED to storage.values.getValue(SearchHistory.KEY_SAVED)), backgroundScope)
            history.save(q("bitcoin"), "pending")
            runCurrent()
            assertEquals(
                setOf("stored", "pending"),
                history.saved.value
                    .map { it.label }
                    .toSet(),
            )
        }

    @Test
    fun aStoredFileWithDuplicatesComesBackWithoutThem() =
        runTest {
            // Two rows with the same text are two rows with the same key, and a lazy list treats
            // that as a crash rather than a duplicate.
            val history = SearchHistory(InMemory(SearchHistory.KEY_RECENT to "bitcoin\nnostr\nbitcoin"), backgroundScope)
            runCurrent()
            assertEquals(listOf("bitcoin", "nostr"), history.recent.value.map { it.text })
        }

    @Test
    fun twoSearchesSavedInTheSameSecondAreTwoSearches() =
        runTest {
            // The id was the timestamp plus the list's size, so a second save in the same second
            // could land on the id the first one already had — and an id is what forget() deletes
            // by, and what the restore merge de-duplicates by.
            val history = SearchHistory(InMemory(), backgroundScope)
            runCurrent()
            history.save(q("bitcoin"), "one")
            history.save(q("nostr"), "two")
            runCurrent()
            assertEquals(
                2,
                history.saved.value
                    .map { it.id }
                    .toSet()
                    .size,
                "ids collided",
            )
        }

    @Test
    fun anIdIsNotReusedAfterTheSearchThatHadItIsDeleted() =
        runTest {
            val history = SearchHistory(InMemory(), backgroundScope)
            runCurrent()
            history.save(q("bitcoin"), "one")
            runCurrent()
            val first =
                history.saved.value
                    .single()
                    .id
            history.forget(first)
            history.save(q("nostr"), "two")
            runCurrent()
            assertNotEquals(
                first,
                history.saved.value
                    .single()
                    .id,
            )
        }
}
