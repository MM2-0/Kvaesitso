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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchActionsTest {
    private val priv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val signer = NostrSignerInternal(KeyPair(priv.hexToByteArray()))

    @Test
    fun searchProfilesFilter_buildsKind0FilterWithSearchField() {
        val filter = SearchActions.searchProfilesFilter("alice", limit = 10)

        assertNotNull(filter)
        assertEquals(listOf(MetadataEvent.KIND), filter.kinds)
        assertEquals("alice", filter.search)
        assertEquals(10, filter.limit)
        assertNull(filter.authors, "must not constrain authors — search is global")
    }

    @Test
    fun searchProfilesFilter_trimsWhitespace() {
        val filter = SearchActions.searchProfilesFilter("   alice   ")
        assertNotNull(filter)
        assertEquals("alice", filter.search)
    }

    @Test
    fun searchProfilesFilter_returnsNullForBlankQuery() {
        assertNull(SearchActions.searchProfilesFilter(""))
        assertNull(SearchActions.searchProfilesFilter("   "))
    }

    @Test
    fun searchNotesFilter_defaultsToKind1() {
        val filter = SearchActions.searchNotesFilter("hello")
        assertNotNull(filter)
        assertEquals(listOf(TextNoteEvent.KIND), filter.kinds)
        assertEquals("hello", filter.search)
    }

    @Test
    fun searchNotesFilter_acceptsCustomKindsAndTimeWindow() {
        val filter =
            SearchActions.searchNotesFilter(
                query = "music",
                kinds = listOf(1, 30023),
                limit = 100,
                since = 1_700_000_000,
                until = 1_800_000_000,
            )
        assertNotNull(filter)
        assertEquals(listOf(1, 30023), filter.kinds)
        assertEquals(100, filter.limit)
        assertEquals(1_700_000_000, filter.since)
        assertEquals(1_800_000_000, filter.until)
    }

    @Test
    fun searchNotesFilter_returnsNullForBlankQuery() {
        assertNull(SearchActions.searchNotesFilter(""))
        assertNull(SearchActions.searchNotesFilter("\t\n"))
    }

    @Test
    fun resolveSearchRelays_fallsBackToDefaultsWhenNoListConfigured() =
        runTest {
            val relays = SearchActions.resolveSearchRelays(signer, currentList = null)
            assertEquals(DefaultSearchRelayList, relays)
        }

    @Test
    fun resolveSearchRelays_usesConfiguredPublicRelaysWhenAvailable() =
        runTest {
            val customRelay = RelayUrlNormalizer.normalizeOrNull("wss://search.example.com")
            assertNotNull(customRelay)

            val list = SearchRelayListEvent.create(relays = listOf(customRelay), signer = signer)
            val relays = SearchActions.resolveSearchRelays(signer, currentList = list)

            assertEquals(setOf(customRelay), relays)
        }

    @Test
    fun resolveSearchRelays_respectsCustomFallback() =
        runTest {
            val custom =
                listOfNotNull(
                    RelayUrlNormalizer.normalizeOrNull("wss://only-fallback.example"),
                )
            val relays =
                SearchActions.resolveSearchRelays(
                    signer = signer,
                    currentList = null,
                    fallback = custom,
                )
            assertEquals(custom.toSet(), relays)
        }

    @Test
    fun resolveSearchRelays_emptyConfiguredListFallsBack() =
        runTest {
            val emptyList = SearchRelayListEvent.create(relays = emptyList(), signer = signer)
            val relays = SearchActions.resolveSearchRelays(signer, currentList = emptyList)

            // An author who published a kind:10007 with no relays is treated
            // the same as no list at all — we don't want to query nothing.
            assertTrue(relays.isNotEmpty())
            assertEquals(DefaultSearchRelayList, relays)
        }
}
