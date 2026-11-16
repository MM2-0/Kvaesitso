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
package com.vitorpamplona.amethyst.commons.relayClient.search

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The search box's tokens have to survive the trip to the REQ.
 *
 * They did not before: the whole box, chips and all, went into the NIP-50 `search` string, so
 * `from:npub1… bitcoin` asked relays for the literal text of its own tokens. These pin the
 * conversion so that cannot come back.
 */
class SearchPostsByTextTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
    }

    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun filtersFor(text: String) = searchPostsByText(text, relay).map { it.filter }

    @Test
    fun plainTextStillTravelsAsTheSearchString() {
        val filters = filtersFor("bitcoin lightning")
        assertTrue(filters.isNotEmpty())
        assertTrue(filters.all { it.search == "bitcoin lightning" })
        assertTrue(filters.all { it.authors == null })
    }

    @Test
    fun aFromTokenBecomesAnAuthorsFieldAndLeavesTheSearchString() {
        val filters = filtersFor("from:$NPUB bitcoin")
        assertTrue(filters.isNotEmpty())
        // The npub is asked as `authors`, never as text — the whole point.
        assertTrue(filters.all { it.authors?.size == 1 })
        assertTrue(filters.all { it.search == "bitcoin" })
        assertTrue(filters.none { it.search?.contains("npub1") == true })
    }

    @Test
    fun aDateTokenBecomesAWindowOnEveryFilter() {
        val filters = filtersFor("since:2026-01-01 until:2026-12-31 zaps")
        assertTrue(filters.isNotEmpty())
        assertTrue(filters.all { it.since != null && it.until != null })
        assertTrue(filters.all { it.search == "zaps" })
    }

    @Test
    fun aHashtagBecomesATagFilterRatherThanASearchTerm() {
        val filters = filtersFor("#bitcoin")
        assertTrue(filters.any { it.tags?.get("t")?.contains("bitcoin") == true })
        // Nothing is left for NIP-50 once the tag is lifted out.
        assertTrue(filters.all { it.search == null })
    }

    @Test
    fun aQueryThatNamesNothingAsksNothing() {
        assertEquals(emptyList(), searchPostsByText("", relay))
        assertEquals(emptyList(), searchPostsByText("   ", relay))
    }

    @Test
    fun everyFilterCarriesTheSearchPurposeAndAKindWindow() {
        filtersFor("bitcoin").forEach {
            assertTrue(it.kinds?.isNotEmpty() == true, "a search filter must name its kinds")
            assertNull(it.ids)
        }
    }

    @Test
    fun aKindTokenNarrowsTheWindowInsteadOfBeingIgnored() {
        // The box drew a `kind:` chip while the REQ still asked for all three fallback groups —
        // the field promising a narrowing that never reached the relay.
        val filters = filtersFor("kind:article bitcoin")
        assertTrue(filters.isNotEmpty())
        assertTrue(filters.all { it.kinds == listOf(30023) }, "kinds were ${filters.map { it.kinds }}")
        assertTrue(filters.all { it.search == "bitcoin" })
    }

    @Test
    fun aQueryNamingNoKindStillAsksTheWholeRenderableSet() {
        val named = filtersFor("kind:article bitcoin")
        val unnamed = filtersFor("bitcoin")
        assertTrue(unnamed.size > named.size, "the fallback fan-out must survive")
        assertTrue(unnamed.any { (it.kinds?.size ?: 0) > 1 })
    }

    @Test
    fun aKindOnlyQueryStillAsksNothing() {
        // A bare kind window is not a search: it would be an unbounded REQ for every recent
        // event of that kind, which the builder refuses for exactly that reason.
        assertTrue(filtersFor("kind:article").isEmpty())
    }
}
