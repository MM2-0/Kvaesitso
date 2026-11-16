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
package com.vitorpamplona.amethyst.commons.relayClient.home.nip90AlgoFeeds

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FilterHomePostsByAlgoFeedIdsTest {
    private val userRelay = RelayUrlNormalizer.normalizeOrNull("wss://user.example/")!!
    private val dvmRelay = RelayUrlNormalizer.normalizeOrNull("wss://dvm.example/")!!

    @Test
    fun emptyFilterSetProducesNoRequests() {
        val set =
            FavoriteAlgoFeedTopNavPerRelayFilterSet(
                contentFetches = emptyMap(),
                listenRelays = emptySet(),
                requestIds = emptySet(),
            )

        assertTrue(filterHomePostsByAlgoFeedIds(set, since = null, defaultSince = null).isEmpty())
    }

    @Test
    fun contentFetchIssuedOnUserRelayWithIdsFilter() {
        val ids = setOf("a".repeat(64), "b".repeat(64))
        val set =
            FavoriteAlgoFeedTopNavPerRelayFilterSet(
                contentFetches =
                    mapOf(userRelay to FavoriteAlgoFeedTopNavPerRelayFilter(ids = ids, addresses = emptySet())),
                listenRelays = emptySet(),
                requestIds = emptySet(),
            )

        val filters = filterHomePostsByAlgoFeedIds(set, since = null, defaultSince = null)

        assertEquals(1, filters.size)
        val single = filters.single()
        assertEquals(userRelay, single.relay)
        assertEquals(ids.sorted(), single.filter.ids?.sorted())
        // Content fetch should not be restricted to a kind — the DVM curates freely.
        assertEquals(null, single.filter.kinds)
    }

    @Test
    fun listenFilterIssuedOnDvmRelayWithKinds6300And7000() {
        val requestId = "9".repeat(64)
        val set =
            FavoriteAlgoFeedTopNavPerRelayFilterSet(
                contentFetches = emptyMap(),
                listenRelays = setOf(dvmRelay),
                requestIds = setOf(requestId),
            )

        val filters = filterHomePostsByAlgoFeedIds(set, since = null, defaultSince = null)

        assertEquals(1, filters.size)
        val listen = filters.single()
        assertEquals(dvmRelay, listen.relay)
        assertEquals(
            listOf(DvmContentDiscoveryResponseEvent.KIND, DvmStatusEvent.KIND),
            listen.filter.kinds,
        )
        val eTag = listen.filter.tags?.get("e")
        assertNotNull(eTag)
        assertEquals(listOf(requestId), eTag)
    }

    @Test
    fun mergedRequestIdsAllRideOnOneListenFilterPerRelay() {
        val req1 = "1".repeat(64)
        val req2 = "2".repeat(64)
        val set =
            FavoriteAlgoFeedTopNavPerRelayFilterSet(
                contentFetches = emptyMap(),
                listenRelays = setOf(dvmRelay),
                requestIds = setOf(req1, req2),
            )

        val filters = filterHomePostsByAlgoFeedIds(set, since = null, defaultSince = null)

        assertEquals(1, filters.size)
        val eTag =
            filters
                .single()
                .filter.tags
                ?.get("e")
                .orEmpty()
        assertTrue(eTag.containsAll(listOf(req1, req2)))
        assertEquals(2, eTag.size)
    }

    @Test
    fun contentAndListenSubscriptionsSplitAcrossTheirRespectiveRelays() {
        val ids = setOf("a".repeat(64))
        val requestId = "9".repeat(64)
        val set =
            FavoriteAlgoFeedTopNavPerRelayFilterSet(
                contentFetches =
                    mapOf(userRelay to FavoriteAlgoFeedTopNavPerRelayFilter(ids = ids, addresses = emptySet())),
                listenRelays = setOf(dvmRelay),
                requestIds = setOf(requestId),
            )

        val filters = filterHomePostsByAlgoFeedIds(set, since = null, defaultSince = null)

        assertEquals(2, filters.size)
        assertTrue(filters.any { it.relay == userRelay && it.filter.ids != null })
        assertTrue(filters.any { it.relay == dvmRelay && it.filter.kinds != null })
    }
}
