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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip51Lists.tags.RelayTag
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterRepliesAndReactionsToAddressesTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://one.example")!!
    private val authorKey = "b".repeat(64)

    private fun addressable(address: Address) = AddressableNote(address).apply { addRelaySync(relay) }

    private fun listingDeclaringLogRelay(
        address: Address,
        logRelay: NormalizedRelayUrl,
    ) = addressable(address).apply {
        event =
            GeocacheListingEvent(
                id = "c".repeat(64),
                pubKey = authorKey,
                createdAt = 1L,
                tags = arrayOf(arrayOf("d", address.dTag), arrayOf(RelayTag.TAG_NAME, logRelay.url)),
                content = "",
                sig = "d".repeat(64),
            )
    }

    /**
     * A NIP-CC found log is the cache's history. It hangs off the listing by a lowercase `a`, the
     * same tag the engagement watcher already asks about, but it is its own kind — so without
     * 7516 in the kind list the cache screen asks for every reaction to a cache and never for its
     * finds, and a cache with logs on a relay we are connected to still reads as unfound.
     */
    @Test
    fun asksForFoundLogsOfACache() {
        val cache = addressable(Address(GeocacheListingEvent.KIND, authorKey, "a-cache"))

        val filters = filterRepliesAndReactionsToAddresses(listOf(cache), null)!!

        val askingByAddress = filters.filter { it.filter.tags?.containsKey("a") == true }

        assertTrue(
            askingByAddress.any { GeocacheFoundLogEvent.KIND in (it.filter.kinds ?: emptyList()) },
            "no filter asks for kind ${GeocacheFoundLogEvent.KIND} on the cache's address",
        )
    }

    /**
     * NIP-CC lets a cache name the relays its finds belong on, and Amethyst honours that when it
     * publishes one. Asking for them anywhere else splits the write from the read: the logs land
     * exactly where the listing said, and the cache screen — looking only at the owner's inbox and
     * at wherever it happened to see the listing — reports the cache as never found.
     */
    @Test
    fun asksTheRelaysTheCacheNamesForItsLogs() {
        val logRelay = RelayUrlNormalizer.normalizeOrNull("wss://logs.example")!!
        val cache = listingDeclaringLogRelay(Address(GeocacheListingEvent.KIND, authorKey, "a-cache"), logRelay)

        val filters = filterRepliesAndReactionsToAddresses(listOf(cache), null)!!

        assertTrue(
            filters.any { it.relay == logRelay && GeocacheFoundLogEvent.KIND in (it.filter.kinds ?: emptyList()) },
            "no filter asks ${logRelay.url} -- the relay the listing names -- for the cache's logs",
        )
    }

    /** The declared relays are added to the usual ones, not swapped in for them. */
    @Test
    fun stillAsksTheRelayTheListingCameFrom() {
        val logRelay = RelayUrlNormalizer.normalizeOrNull("wss://logs.example")!!
        val cache = listingDeclaringLogRelay(Address(GeocacheListingEvent.KIND, authorKey, "a-cache"), logRelay)

        val filters = filterRepliesAndReactionsToAddresses(listOf(cache), null)!!

        assertEquals(
            setOf(relay, logRelay),
            filters.map { it.relay }.toSet(),
        )
    }

    /**
     * A PressStr magazine lists none of its issues; each points back at it with `a`. The watcher
     * asks for them in a filter of their own, so live-stream chat on the same screen cannot use up
     * a shared limit first, and only from the magazine's author, who alone can publish its issues.
     */
    @Test
    fun asksForAMagazinesIssuesFromItsAuthor() {
        val magazine = addressable(Address(MagazineEvent.KIND, authorKey, "the-mag"))

        val filters = filterRepliesAndReactionsToAddresses(listOf(magazine), null)!!
        val issues = filters.filter { MagazineIssueEvent.KIND in (it.filter.kinds ?: emptyList()) }

        assertEquals(1, issues.size)
        val filter = issues.single().filter
        assertEquals(listOf(MagazineIssueEvent.KIND), filter.kinds)
        assertEquals(listOf(authorKey), filter.authors)
        assertEquals(mapOf("a" to listOf("${MagazineEvent.KIND}:$authorKey:the-mag")), filter.tags)
    }

    /** Any other address on screen has no issues to ask for. */
    @Test
    fun noIssueFilterForOtherAddresses() {
        val cache = addressable(Address(GeocacheListingEvent.KIND, authorKey, "a-cache"))

        val filters = filterRepliesAndReactionsToAddresses(listOf(cache), null)!!

        assertTrue(filters.none { MagazineIssueEvent.KIND in (it.filter.kinds ?: emptyList()) })
    }
}
