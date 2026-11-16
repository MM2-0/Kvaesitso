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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * `LocalCache.filter` over a real corpus — 60-odd notes recorded from a live vespa-relay by
 * `tools/search-parity/fetch_fixtures.py`, the same fixture the engine-level parity test in
 * `:commons` reads.
 *
 * The engine test proves our NIP-01 matcher agrees with the relay about individual events. This
 * one covers what sits above the matcher and the relay knows nothing about: the split between
 * regular and addressable notes, the viewer-policy predicate, the result cap, and the ordering the
 * cap depends on — where a bug had `take(limit)` running before the sort and silently dropping the
 * newest matches.
 *
 * `LocalCache` is a process-wide object and JUnit's method order is hash-based, so the corpus is
 * loaded once and every assertion here is read-only.
 *
 * The corpus' notes are taken from the cache *before* consuming and kept in [pinned] for the whole
 * class. `LocalCache` holds notes weakly (LargeSoftCache) and nothing else references a corpus note,
 * so without the pin any GC between loading and a query drops notes and the query comes back short.
 * In the app the screen showing a note is that strong referent.
 */
class LocalCacheSearchParityTest {
    companion object {
        /** Hoisted out of [loadCorpus]: building a Json format is expensive enough that the compiler warns on it. */
        private val json = Json { ignoreUnknownKeys = true }

        private lateinit var corpus: List<Event>

        /** Strong references to every corpus note. See the class KDoc. */
        private lateinit var pinned: List<Note>

        @BeforeClass
        @JvmStatic
        fun loadCorpus() {
            val file =
                listOf(File("../tools/search-parity/fixture.json"), File("tools/search-parity/fixture.json"))
                    .firstOrNull { it.isFile }
                    ?: error("tools/search-parity/fixture.json is missing; run tools/search-parity/fetch_fixtures.py")

            val root = json.parseToJsonElement(file.readText()).jsonObject
            corpus =
                root["cases"]!!
                    .jsonArray
                    .flatMap { it.jsonObject["events"]!!.jsonArray }
                    .map { Event.fromJson(it.toString()) }
                    .distinctBy { it.id }

            pinned = corpus.map { LocalCache.getOrCreateNote(it) }

            // LocalCache.consume refuses the main thread; a plain JVM test has no Looper, so the
            // check passes and the events land synchronously.
            //
            // The corpus is live relay data and carries the same long-form article posted by two
            // authors, which the anti-spam filter keeps out of the cache as a copy. This class tests
            // the filter path, not spam policy, so the whole corpus goes in; the flag is restored
            // because LocalCache is shared with every other test class in the JVM.
            val antiSpamWasActive = LocalCache.antiSpam.active
            LocalCache.antiSpam.active = false
            try {
                corpus.forEach { LocalCache.justConsumeMyOwnEvent(it) }
            } finally {
                LocalCache.antiSpam.active = antiSpamWasActive
            }
        }
    }

    private fun idsFor(filter: Filter) = LocalCache.filter(filter).mapNotNull { it.event?.id }

    @Test
    fun theCorpusLoadedAndIsWorthAssertingOver() {
        assertTrue("fixture produced no events", corpus.size >= 40)
        // Both halves of the filter path — regular notes and addressables — must be represented,
        // or the split below is only tested on one branch.
        assertTrue("no regular notes in the corpus", corpus.any { it.kind == 1 })
        assertTrue("no addressable notes in the corpus", corpus.any { it.kind == 30023 })
    }

    @Test
    fun aKindFilterReturnsExactlyTheEventsOfThatKind() {
        val expected = corpus.filter { it.kind == 1 }.map { it.id }.toSet()
        val found = idsFor(Filter(kinds = listOf(1))).toSet()
        assertTrue("kind:1 missed ${(expected - found).size} of ${expected.size}", expected.all { it in found })
        assertTrue("kind:1 returned events of another kind", found.all { id -> corpus.first { it.id == id }.kind == 1 })
    }

    @Test
    fun anAddressableKindComesBackThroughTheAddressablePath() {
        val expected = corpus.filter { it.kind == 30023 }.map { it.id }.toSet()
        if (expected.isEmpty()) return
        val found = idsFor(Filter(kinds = listOf(30023))).toSet()
        assertTrue("long-form articles did not come back: ${expected - found}", expected.all { it in found })
    }

    @Test
    fun anAuthorFilterNarrowsToThatAuthor() {
        val author = corpus.groupBy { it.pubKey }.maxByOrNull { it.value.size }!!.key
        val found = LocalCache.filter(Filter(authors = listOf(author)))
        assertTrue("no results for the corpus' most prolific author", found.isNotEmpty())
        assertTrue("an author filter returned somebody else", found.all { it.event?.pubKey == author })
    }

    @Test
    fun aTagFilterNarrowsToCarriersOfThatTag() {
        val tagged = corpus.filter { e -> e.tags.any { it.size > 1 && it[0] == "t" } }
        if (tagged.isEmpty()) return
        val value = tagged.first().tags.first { it.size > 1 && it[0] == "t" }[1]
        val found = LocalCache.filter(Filter(tags = mapOf("t" to listOf(value))))
        assertTrue(
            "a #t filter returned an event without the tag",
            found.all { note ->
                note.event?.tags?.any { it.size > 1 && it[0] == "t" && it[1] == value } == true
            },
        )
    }

    @Test
    fun aWindowIsInclusiveAtBothEnds() {
        val sorted = corpus.map { it.createdAt }.sorted()
        val since = sorted[sorted.size / 4]
        val until = sorted[sorted.size * 3 / 4]
        val found = LocalCache.filter(Filter(since = since, until = until))
        assertTrue("a window returned something outside it", found.all { (it.event?.createdAt ?: 0) in since..until })
        // Inclusive: the events sitting exactly on each bound are in.
        val onBounds = corpus.filter { it.createdAt == since || it.createdAt == until }.map { it.id }
        val foundIds = found.mapNotNull { it.event?.id }.toSet()
        assertTrue("a bound event was excluded", onBounds.all { it in foundIds })
    }

    @Test
    fun theResultCapKeepsTheNewestNotWhicheverTheWalkReachedFirst() {
        // The bug this pins: `take(limit)` ran before the sort, so the cap dropped whatever the
        // hash walk happened to reach last — the newest as often as not.
        val all = LocalCache.filter(Filter(kinds = listOf(1)))
        if (all.size < 3) return
        val cap = all.size / 2
        val capped = LocalCache.filter(Filter(kinds = listOf(1), limit = cap))

        assertEquals("the cap was not honoured", cap, capped.size)
        val newest = all.take(cap).mapNotNull { it.event?.id }
        assertEquals("the cap did not keep the newest results", newest, capped.mapNotNull { it.event?.id })
    }

    @Test
    fun thePredicateComposesWithTheFilterRatherThanReplacingIt() {
        val author = corpus.groupBy { it.pubKey }.maxByOrNull { it.value.size }!!.key
        val byKind = LocalCache.filter(Filter(kinds = listOf(1)))
        val byBoth = LocalCache.filter(Filter(kinds = listOf(1))) { it.event?.pubKey == author }

        assertTrue("the predicate widened the result", byBoth.size <= byKind.size)
        assertTrue("the predicate was not applied", byBoth.all { it.event?.pubKey == author })
        assertTrue("the filter was dropped when a predicate was given", byBoth.all { it.event?.kind == 1 })
    }

    @Test
    fun aPredicateThatRefusesEverythingReturnsNothing() {
        assertTrue(LocalCache.filter(Filter(kinds = listOf(1))) { false }.isEmpty())
    }

    @Test
    fun resultsComeBackNewestFirst() {
        val found = LocalCache.filter(Filter(kinds = listOf(1))).mapNotNull { it.event?.createdAt }
        assertEquals("results are not sorted newest-first", found.sortedDescending(), found)
    }
}
