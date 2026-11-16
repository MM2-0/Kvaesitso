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

import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterMatcher
import com.vitorpamplona.quartz.nip50Search.EventSearchMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The local search engine, checked against a real relay's answers over a real corpus.
 *
 * ## What this can and cannot prove
 *
 * The oracle is `search.brainstorm.world`, a vespa-relay — the same software Amethyst's
 * token language was ported from. It is a **valid oracle for NIP-01 filtering** and **not one for
 * NIP-50 text matching**, and the difference is the whole design of this file.
 *
 * NIP-01 is a closed specification: given `kinds`, `#t`, `since` and `until`, either an event
 * satisfies the filter or it does not, and there is exactly one right answer. Every event the
 * relay chose to return for a filter must therefore satisfy that filter under our matcher too.
 * Across the recorded corpus that holds for 64 of 64 events, and [everyEventTheRelayReturnedSatisfiesTheFilterItAnsweredFor]
 * pins it.
 *
 * NIP-50 is not closed. This relay retrieves topically, not lexically: asked for `bitcoin` it
 * returns a liquidation-levels heatmap that only ever says "BTC", and asked for `"open source"` it
 * returns "FOSS you say will save you". Eight of the 64 recorded events — 12% —
 * contain no literal occurrence of the term that fetched them.
 *
 * The fixture is recorded with the `include:spam` lens, which waives the web-of-trust gate. That
 * was measured, not assumed: waiving it makes the corpus reproducible (the alternative,
 * `observer:<pubkey>`, pins every answer to one account's trust graph) but it does not make
 * retrieval lexical — the divergence actually widens, from 4 events to 8, because dropping the
 * gate lets more topical matches through. Asserting that
 * our substring matcher reproduces those results would encode someone else's semantic expansion as
 * a requirement on a lexical matcher — a test that fails on correct code. So this file deliberately
 * makes **no** claim that our text results equal the relay's, and
 * [theRelayRetrievesSemanticallyWhichIsWhyTextResultsAreNotCompared] documents the divergence as a
 * fact about the corpus rather than a failure.
 *
 * What the corpus is still worth beyond that: 64 events of real, adversarial content — newlines,
 * currency symbols, URLs, multi-byte text, empty tag arrays — to run the rest of the engine over.
 */
class SearchRelayParityTest {
    private val cases = SearchParityFixture.cases

    @Test
    fun theFixtureIsPresentAndNotSilentlyEmpty() {
        // A relay that was down would otherwise turn every assertion below into a no-op.
        assertTrue(cases.isNotEmpty(), "no cases in the fixture")
        assertTrue(cases.all { it.events.isNotEmpty() }, "a case recorded zero events: ${cases.filter { it.events.isEmpty() }.map { it.name }}")
        assertTrue(cases.sumOf { it.events.size } >= 40, "the corpus is too small to be worth asserting over")
    }

    @Test
    fun everyEventTheRelayReturnedSatisfiesTheFilterItAnsweredFor() {
        // The core parity claim: our NIP-01 matcher agrees with a production relay about its own
        // answers, over data neither of us invented.
        cases.forEach { case ->
            case.events.forEach { event ->
                assertTrue(
                    FilterMatcher.match(
                        event = event,
                        kinds = case.filter.kinds,
                        tags = case.filter.tags,
                        since = case.filter.since,
                        until = case.filter.until,
                    ),
                    "case ${case.name}: the relay returned ${event.id.take(8)} (kind ${event.kind}, " +
                        "created_at ${event.createdAt}) but our matcher rejects it for " +
                        "kinds=${case.filter.kinds} tags=${case.filter.tags} " +
                        "since=${case.filter.since} until=${case.filter.until}",
                )
            }
        }
    }

    @Test
    fun aFilterThatExcludesAnEventRejectsIt() {
        // The converse, so the assertion above cannot pass by matching everything.
        val corpus = cases.flatMap { it.events }.distinctBy { it.id }
        val impossible = corpus.count { FilterMatcher.match(it, kinds = listOf(31337)) }
        assertEquals(0, impossible, "a kind filter nothing in the corpus carries still matched")

        val ancient = corpus.count { FilterMatcher.match(it, until = 1L) }
        assertEquals(0, ancient, "an until of 1970 still matched real events")
    }

    @Test
    fun theWindowedCasesReallyExerciseTheirBounds() {
        // Guards against a window so wide it would pass whatever the matcher did.
        val since = cases.first { it.name == "window_since" }
        assertTrue(since.events.all { it.createdAt >= since.filter.since!! })
        val until = cases.first { it.name == "window_until" }
        assertTrue(until.events.all { it.createdAt <= until.filter.until!! })
    }

    @Test
    fun theHashtagCaseCarriesTheTagOnEveryEvent() {
        val case = cases.first { it.name == "tag_hashtag" }
        assertTrue(case.filter.tags?.containsKey("t") == true, "the fixture lost its tag filter")
        case.events.forEach { event ->
            assertTrue(
                event.tags.any { it.size > 1 && it[0] == "t" && it[1] == "bitcoin" },
                "${event.id.take(8)} came back for #t=bitcoin without carrying it",
            )
        }
    }

    @Test
    fun ourOwnQueryLanguageBuildsFiltersThatAcceptTheRelaysAnswers() {
        // End to end over real data: text through QueryParser and SearchFilterBuilder must produce
        // filters that still accept what the relay returned for the same words and kinds.
        val case = cases.first { it.name == "tag_hashtag" }
        val query = QueryParser.parse("#bitcoin")
        val filters = SearchFilterBuilder.build(query, kinds = listOf(1), limit = 100)
        assertTrue(filters.isNotEmpty())

        val tagArm = filters.first { it.tags?.containsKey("t") == true }
        case.events.forEach { event ->
            assertTrue(
                FilterMatcher.match(event, kinds = tagArm.kinds, tags = tagArm.tags),
                "our #t arm rejects ${event.id.take(8)}, which the relay returned for the same tag",
            )
        }
    }

    @Test
    fun theSearchMatcherSurvivesRealContent() {
        // Real notes carry newlines, `$`, URLs and multi-byte text. The matcher must answer
        // without throwing and must agree with a plain substring test on the content it holds.
        val corpus = cases.flatMap { it.events }.distinctBy { it.id }
        listOf("bitcoin", "BITCOIN", "sat/vByte", "$", "\"open source\"", "").forEach { term ->
            val matcher = EventSearchMatcher(term)
            corpus.forEach { event ->
                val matched = matcher.match(event)
                if (term.isBlank()) {
                    assertTrue(matched, "an empty search must constrain nothing")
                } else if (!term.startsWith("\"") && event.content.contains(term, true)) {
                    assertTrue(matched, "term $term is literally in ${event.id.take(8)} but did not match")
                }
            }
        }
    }

    @Test
    fun theRelayRetrievesSemanticallyWhichIsWhyTextResultsAreNotCompared() {
        // Not a failure — a recorded fact about the oracle, kept executable so that if the relay
        // ever becomes purely lexical the divergence drops to zero and this test tells us.
        val lexicalMisses =
            cases.sumOf { case ->
                val terms =
                    case.terms
                        .replace("\"", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                case.events.count { event ->
                    val matcher = EventSearchMatcher(terms.joinToString(" "))
                    !matcher.match(event)
                }
            }
        val total = cases.sumOf { it.events.size }
        assertTrue(
            lexicalMisses in 1..(total / 4),
            "expected a small semantic divergence from the relay, got $lexicalMisses of $total — " +
                "0 would mean the relay turned lexical (drop this test and compare results directly); " +
                "a quarter or more means our matcher regressed",
        )
    }
}
