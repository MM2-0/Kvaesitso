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
 * The pipeline's contract — which is really the contract the two front ends kept breaking by
 * assembling the steps themselves.
 *
 * Each test here corresponds to a bug that shipped: a query's kinds not reaching the filters, the
 * post-filters never being applied, Relevance ranking by date. Written against the pipeline so
 * that both callers inherit the fix by construction rather than by remembering.
 */
class SearchPipelineTest {
    private companion object {
        const val PUBKEY = "abc123def456abc123def456abc123def456abc123def456abc123def456abcd"
    }

    private fun event(
        id: String,
        content: String = "",
        kind: Int = 1,
        createdAt: Long = 1,
        tags: Array<Array<String>> = emptyArray(),
    ) = Event(id = id, pubKey = PUBKEY, createdAt = createdAt, kind = kind, tags = tags, content = content, sig = "sig")

    private val asEvent: (Event) -> Event? = { it }

    // ---- filters ----------------------------------------------------------------------------

    @Test
    fun theQuerysOwnKindsWinOverTheCallersWindow() {
        // The bug: the caller's fallback window was used and `kind:article` reached no relay, so
        // a chip on screen promised a narrowing the REQ never carried.
        val filters = SearchPipeline.filters(SearchPipeline.parse("kind:article bitcoin"), kinds = listOf(1, 20, 30))
        assertTrue(filters.isNotEmpty())
        assertTrue(filters.all { it.kinds == listOf(30023) }, "was ${filters.map { it.kinds }}")
    }

    @Test
    fun theCallersWindowIsUsedWhenTheQueryNamesNoKind() {
        val filters = SearchPipeline.filters(SearchPipeline.parse("bitcoin"), kinds = listOf(1, 30023))
        assertTrue(filters.all { it.kinds == listOf(1, 30023) })
    }

    @Test
    fun aQueryThatAsksNothingBuildsNoFilters() {
        assertTrue(SearchPipeline.filters(SearchPipeline.parse("kind:article")).isEmpty())
        assertTrue(SearchPipeline.filters(SearchPipeline.parse("")).isEmpty())
    }

    // ---- keep -------------------------------------------------------------------------------

    @Test
    fun keepAppliesExclusionsAndPseudoKinds() {
        val q = SearchPipeline.parse("kind:reply bitcoin -scam")
        val reply = arrayOf(arrayOf("e", "parent"))
        val kept = event("a", "bitcoin", tags = reply)
        val excluded = event("b", "bitcoin scam", tags = reply)
        val notAReply = event("c", "bitcoin")
        assertEquals(listOf("a"), SearchPipeline.keep(listOf(kept, excluded, notAReply), q, asEvent).map { it.id })
    }

    @Test
    fun keepLetsAnItemWithNoEventThrough() {
        // A Note whose event has not arrived yet is not evidence that it fails the query.
        val q = SearchPipeline.parse("-scam")
        val orphan: String? = null
        assertEquals(1, SearchPipeline.keep(listOf(orphan), q) { null }.size)
    }

    @Test
    fun keepIsANoOpForAQueryWithNoPostFilters() {
        val q = SearchPipeline.parse("bitcoin")
        val items = listOf(event("a"), event("b", "scam"))
        assertEquals(2, SearchPipeline.keep(items, q, asEvent).size)
    }

    // ---- rank -------------------------------------------------------------------------------

    @Test
    fun relevanceRanksByScoreNotByDate() {
        // The bug: RELEVANCE shared a branch with NEWEST, so the newest won regardless of match.
        val old = event("aa", "bitcoin is the topic here", createdAt = 1)
        val new = event("bb", "unrelated musings", createdAt = 999)
        val ranked = SearchPipeline.rank(listOf(new, old), SearchSortOrder.RELEVANCE, "bitcoin", asEvent)
        assertEquals("aa", ranked.first().id)
    }

    @Test
    fun relevanceWithNothingToScoreFallsBackToNewest() {
        val old = event("aa", createdAt = 1)
        val new = event("bb", createdAt = 999)
        assertEquals("bb", SearchPipeline.rank(listOf(old, new), SearchSortOrder.RELEVANCE, "", asEvent).first().id)
    }

    @Test
    fun newestAndOldestAreExactOpposites() {
        val items = listOf(event("aa", createdAt = 1), event("bb", createdAt = 2), event("cc", createdAt = 3))
        val newest = SearchPipeline.rank(items, SearchSortOrder.NEWEST, "", asEvent).map { it.id }
        val oldest = SearchPipeline.rank(items, SearchSortOrder.OLDEST, "", asEvent).map { it.id }
        assertEquals(listOf("cc", "bb", "aa"), newest)
        assertEquals(newest.reversed(), oldest)
    }

    @Test
    fun equalTimestampsBreakTiesOnIdSoTheOrderIsStable() {
        // Without a tiebreak the same query returns a different order each scan.
        val items = listOf(event("cc", createdAt = 5), event("aa", createdAt = 5), event("bb", createdAt = 5))
        assertEquals(listOf("aa", "bb", "cc"), SearchPipeline.rank(items, SearchSortOrder.NEWEST, "", asEvent).map { it.id })
    }

    @Test
    fun popularRanksOnZapTotalAndFallsBackToNewestWithoutOne() {
        val a = event("aa", createdAt = 1)
        val b = event("bb", createdAt = 2)
        val zapped = SearchPipeline.rank(listOf(a, b), SearchSortOrder.POPULAR, "", asEvent) { if (it.id == "aa") 100.0 else 0.0 }
        assertEquals("aa", zapped.first().id, "the zapped note outranks the newer one")

        // A caller with no zap totals gets newest, which is what a raw Event can honestly answer.
        assertEquals("bb", SearchPipeline.rank(listOf(a, b), SearchSortOrder.POPULAR, "", asEvent).first().id)
    }

    @Test
    fun peopleOrdersLeaveAnEventListAlone() {
        val items = listOf(event("cc", createdAt = 1), event("aa", createdAt = 2))
        assertEquals(listOf("cc", "aa"), SearchPipeline.rank(items, SearchSortOrder.NAME_AZ, "", asEvent).map { it.id })
    }

    @Test
    fun anEmptyListSurvivesEveryOrder() {
        SearchSortOrder.entries.forEach {
            assertTrue(SearchPipeline.rank(emptyList<Event>(), it, "bitcoin", asEvent).isEmpty(), "$it")
        }
    }

    // ---- the whole thing --------------------------------------------------------------------

    @Test
    fun narrowAndOrderKeepsBeforeItRanks() {
        // Ranking first and filtering after would be the same set; the ordering matters because
        // `keep` is what makes the result set honest, and a caller doing it by hand can do either.
        val q = SearchPipeline.parse("bitcoin -scam")
        val items =
            listOf(
                event("aa", "bitcoin", createdAt = 1),
                event("bb", "bitcoin scam", createdAt = 999),
                event("cc", "bitcoin", createdAt = 5),
            )
        val out = SearchPipeline.narrowAndOrder(items, q, SearchSortOrder.NEWEST, asEvent)
        assertEquals(listOf("cc", "aa"), out.map { it.id })
        assertFalse(out.any { it.id == "bb" })
    }

    @Test
    fun narrowAndOrderScoresTheLeftoverTermsNotTheWholeBox() {
        // `from:` and `kind:` are filters; hunting for their literal text inside content ranks on
        // noise, and a query that is all chips has nothing to be more or less relevant to.
        val q = SearchPipeline.parse("kind:note bitcoin")
        assertEquals("bitcoin", q.text)
        val match = event("aa", "all about bitcoin", createdAt = 1)
        val newer = event("bb", "something else", createdAt = 999)
        val out = SearchPipeline.narrowAndOrder(listOf(newer, match), q, SearchSortOrder.RELEVANCE, asEvent)
        assertEquals("aa", out.first().id)
    }
}
