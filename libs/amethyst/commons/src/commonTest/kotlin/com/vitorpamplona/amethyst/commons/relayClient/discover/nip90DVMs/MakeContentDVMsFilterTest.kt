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
package com.vitorpamplona.amethyst.commons.relayClient.discover.nip90DVMs

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The DVM discovery list renders cached kind-31990 announcements on EVERY top-nav selection, and
 * its freshness gate needs kind-11998 heartbeats to keep flowing. A selection whose per-relay set
 * dispatches to no 31990 filter (a relay whose author set is momentarily empty)
 * must still issue the heartbeat REQ — otherwise cached beats age out and the 60s staleness timer
 * drops every DVM within ~7 minutes (the "DVMs disappear after a while" bug).
 */
class MakeContentDVMsFilterTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example/")!!
    private val otherRelay = RelayUrlNormalizer.normalizeOrNull("wss://other.example/")!!

    @Test
    fun relayVariantAsksThePickedRelayForAnnouncementsAndHeartbeats() {
        val filters = makeContentDVMsFilter(RelayTopNavPerRelayFilterSet(relay), null, null)

        val byKind =
            filters
                .groupBy { (it.filter as? ExplainedFilter)?.kinds }
                .mapValues { entry -> entry.value.map { it.relay } }

        assertEquals(listOf(relay), byKind[listOf(AppDefinitionEvent.KIND)], "the picked relay is asked for its DVMs")
        assertEquals(listOf(relay), byKind[listOf(DvmHeartbeatEvent.KIND)], "heartbeat REQ rides the same relay")
    }

    @Test
    fun globalVariantKeepsAnnouncementFiltersAndGainsTheHeartbeatFilter() {
        val filters =
            makeContentDVMsFilter(
                GlobalTopNavPerRelayFilterSet(mapOf(relay to GlobalTopNavPerRelayFilter)),
                null,
                null,
            )

        val byKind =
            filters
                .groupBy { it.filter.kinds }
                .mapValues { entry -> entry.value.map { it.relay } }

        assertEquals(listOf(relay), byKind[listOf(AppDefinitionEvent.KIND)], "31990 REQ unchanged")
        assertEquals(listOf(relay), byKind[listOf(DvmHeartbeatEvent.KIND)], "heartbeat REQ rides the same relay")
    }

    @Test
    fun authorVariantRelayWithNoAuthorsStillGetsTheHeartbeatFilter() {
        val filters =
            makeContentDVMsFilter(
                AuthorsTopNavPerRelayFilterSet(
                    mapOf(
                        relay to AuthorsTopNavPerRelayFilter(emptySet()),
                        otherRelay to AuthorsTopNavPerRelayFilter(setOf("a".repeat(64))),
                    ),
                ),
                null,
                null,
            )

        val heartbeatRelays =
            filters
                .filter { it.filter.kinds == listOf(DvmHeartbeatEvent.KIND) }
                .map { it.relay }
                .sortedBy { it.url }

        assertEquals(
            listOf(otherRelay, relay).sortedBy { it.url },
            heartbeatRelays,
            "a relay whose author set is empty skips the 31990 REQ but must not skip the heartbeat REQ",
        )
        assertTrue(filters.any { it.filter.kinds == listOf(AppDefinitionEvent.KIND) && it.relay == otherRelay })
    }
}
