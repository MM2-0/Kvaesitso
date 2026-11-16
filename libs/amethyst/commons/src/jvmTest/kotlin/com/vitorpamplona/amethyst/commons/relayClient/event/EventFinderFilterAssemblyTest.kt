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
package com.vitorpamplona.amethyst.commons.relayClient.event

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.filterMissingEvents
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderAccount
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the extracted (Phase 2b) per-note event-finder filter assembly
 * and the [ICacheProvider] seam it relies on.
 */
class EventFinderFilterAssemblyTest {
    private val relay1 = NormalizedRelayUrl("wss://relay1.test/")
    private val relay2 = NormalizedRelayUrl("wss://relay2.test/")

    /**
     * One batched `ids` filter per relay — NOT one filter per event id — and the
     * ids are sorted deterministically so REQs dedup across rebuilds.
     */
    @Test
    fun `filterMissingEvents batches one filter per relay with sorted ids`() {
        val filters =
            filterMissingEvents(
                mapOf(
                    relay1 to setOf("bbbb", "aaaa", "cccc"),
                    relay2 to setOf("dddd"),
                ),
            )

        assertEquals("one batched filter per relay", 2, filters.size)

        val r1 = filters.first { it.relay == relay1 }
        assertEquals(listOf("aaaa", "bbbb", "cccc"), r1.filter.ids)

        val r2 = filters.first { it.relay == relay2 }
        assertEquals(listOf("dddd"), r2.filter.ids)
    }

    @Test
    fun `filterMissingEvents skips relays with no ids and empty input`() {
        assertTrue(filterMissingEvents(emptyMap()).isEmpty())
        assertTrue(filterMissingEvents(mapOf(relay1 to emptySet())).isEmpty())
    }

    /**
     * The per-note event finder fans a missing event out to the account's own
     * search relays ([UserFinderAccount.searchOnlyRelays]) plus the
     * follow/mine/search default — NOT the trusted-relay union that
     * [UserFinderAccount.searchRelays] adds. Regression guard for reusing the
     * wrong getter here (which would silently widen every missing-event REQ to
     * trusted relays).
     */
    @Test
    fun `filterMissingEvents(keys) uses searchOnlyRelays, not the trusted searchRelays union`() {
        val searchOnly = NormalizedRelayUrl("wss://search.test/")
        val trustedExtra = NormalizedRelayUrl("wss://trusted.test/") // only in searchRelays()
        val default = NormalizedRelayUrl("wss://default.test/") // followPlusAllMineWithSearchRelays()

        val account =
            object : StubAccount() {
                override fun searchOnlyRelays() = setOf(searchOnly)

                override fun searchRelays() = setOf(searchOnly, trustedExtra)

                override fun followPlusAllMineWithSearchRelays() = setOf(default)
            }

        // event == null and not addressable → a "missing event"; no author/replies,
        // and the stub cache has empty relay hints, so potentialRelaysToFindEvent is
        // empty and the code falls back to the default relays + searchOnlyRelays.
        val note = Note("a".repeat(64))

        val filters = filterMissingEvents(StubCache(), listOf(EventFinderQueryState(note, account)))
        val relays = filters.map { it.relay }.toSet()

        assertTrue("search-only relay carries the missing-event REQ", searchOnly in relays)
        assertTrue("follow/mine/search default carries it", default in relays)
        assertFalse("trusted relay (only in searchRelays) must NOT be fanned out to", trustedExtra in relays)
        filters.forEach { assertEquals(listOf(note.idHex), it.filter.ids) }
    }

    /**
     * The new default [ICacheProvider.checkGetOrCreateUser] must swallow a
     * malformed-key throw and return null (the event-finder follows pubkey hints
     * parsed out of arbitrary events, some of which are junk).
     */
    @Test
    fun `checkGetOrCreateUser tolerates a throwing getOrCreateUser`() {
        val throwing =
            object : StubCache() {
                override fun getOrCreateUser(pubkey: HexKey): User? = throw IllegalArgumentException("bad key")
            }
        assertNull(throwing.checkGetOrCreateUser("not-a-key"))
    }

    @Test
    fun `checkGetOrCreateUser passes through a null result`() {
        assertNull(StubCache().checkGetOrCreateUser("00"))
    }

    /** Minimal [ICacheProvider] that returns nothing; override per test. */
    private open class StubCache : ICacheProvider {
        override val relayHints = HintIndexer()

        override fun getAnyChannel(note: Note): Channel? = null

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = null

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = null

        override fun getOrCreateAddressableNote(address: Address): AddressableNote = error("unused")

        override fun getEventStream(): ICacheEventStream = error("unused")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun consumeEmbedded(event: Event) = Unit

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }

    /** Minimal [UserFinderAccount] returning nothing; override the relevant getters per test. */
    private open class StubAccount : UserFinderAccount {
        override val userFinderPubkeyHex: HexKey = "00".repeat(32)

        override fun indexRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun outboxHomeRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun searchRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun searchOnlyRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun followPlusAllMineWithSearchRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun commonRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun cardHomeRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun trustProvider(): ServiceProviderTag? = null

        override fun followerCountProvider(): ServiceProviderTag? = null

        override fun declaredFollowsByOutboxRelay(): Map<NormalizedRelayUrl, Set<HexKey>> = emptyMap()
    }
}
