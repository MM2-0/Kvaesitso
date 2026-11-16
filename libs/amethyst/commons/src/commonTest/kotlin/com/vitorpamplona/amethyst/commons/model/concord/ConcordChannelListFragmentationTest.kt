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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListFragments
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListTooLargeException
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Community List past one fragment (CORD-02 §8), driven through the app's own
 * [ConcordChannelListState] rather than the fragment math alone: a List that outgrows one event
 * must split into several, every published event must fit a relay's event ceiling, and another
 * device holding only what was published must read back every membership with its secrets.
 */
class ConcordChannelListFragmentationTest {
    private val signer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))

    private fun hex(
        seed: Int,
        salt: Int,
    ): HexKey = (seed * 7919 + salt).toString(16).padStart(8, '0').repeat(8)

    /**
     * A heavy membership: staff secrets, two held roots and a staff member's worth of private
     * channels. Each join re-reads and re-packs the whole List, so the cost of building one grows
     * with (joins x List size); fat entries span several fragments in a few dozen joins instead of
     * hundreds, which keeps the suite inside `runTest`'s timeout on slow targets (the iOS
     * simulator ran 250 lean joins past it).
     */
    private fun entry(n: Int) =
        ConcordCommunityListEntry(
            id = hex(n, 1),
            owner = hex(n, 2),
            ownerSalt = hex(n, 3),
            root = hex(n, 4),
            rootEpoch = 2,
            controlPk = hex(n, 5),
            controlRoot = hex(n, 6),
            heldRoots = listOf(HeldRoot(0, hex(n, 7)), HeldRoot(1, hex(n, 8), hex(n, 9))),
            privateChannels = (0 until PRIVATE_CHANNELS).map { PrivateChannelKey(hex(n, 100 + it), hex(n, 200 + it), 1, "private-$it") },
            relays = listOf("wss://nos.lol/", "wss://nostr.mom/"),
            name = "Community number $n",
            addedAt = 1_000L + n,
        )

    /** Holds the List only as published fragments, the way a relay or the offline backup does. */
    private class WireRepository(
        var fragments: List<ConcordCommunityListFragmentEvent> = emptyList(),
    ) : ConcordListRepository {
        override fun concordList(): ConcordCommunityListEvent? = null

        override fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?) = Unit

        override fun concordListFragments() = fragments

        override fun updateConcordListFragmentTo(fragment: ConcordCommunityListFragmentEvent) {
            fragments = fragments.filterNot { it.index() == fragment.index() } + fragment
        }
    }

    private class StubCache : ICacheProvider {
        override fun getAnyChannel(note: Note): Channel? = null

        override val relayHints = HintIndexer()

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = null

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = null

        override fun getOrCreateAddressableNote(address: Address): AddressableNote = AddressableNote(address)

        override fun getEventStream(): ICacheEventStream = error("not used")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun consumeEmbedded(event: Event) = Unit

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }

    private fun device(wire: List<ConcordCommunityListFragmentEvent> = emptyList()): Pair<ConcordChannelListState, WireRepository> {
        val repo = WireRepository(wire)
        val list = ConcordChannelListState(signer = signer, cache = StubCache(), scope = CoroutineScope(Dispatchers.Unconfined), settings = repo)
        list.markRelaysConfirmed()
        return list to repo
    }

    private suspend fun joinAll(
        list: ConcordChannelListState,
        count: Int,
    ): List<ConcordCommunityListFragmentEvent> {
        val published = ArrayList<ConcordCommunityListFragmentEvent>()
        for (n in 0 until count) published += list.follow(entry(n)).map { it as ConcordCommunityListFragmentEvent }
        return published
    }

    @Test
    fun aLargeListSplitsIntoFragmentsThatEachFitAnEvent() =
        runTest {
            val (list, repo) = device()
            val published = joinAll(list, COUNT)

            val wire = repo.fragments
            assertTrue(wire.size >= 2, "$COUNT heavy memberships must not fit one fragment; got ${wire.size}")
            for (fragment in published) {
                val bytes = fragment.toJson().encodeToByteArray().size
                assertTrue(bytes <= ConcordListFragments.EVENT_CEILING_BYTES, "fragment ${fragment.index()} is $bytes bytes, over the ${ConcordListFragments.EVENT_CEILING_BYTES} ceiling")
            }
            assertEquals(COUNT, list.entries().size)
        }

    @Test
    fun anotherDeviceReadsEveryMembershipFromThePublishedFragments() =
        runTest {
            val (first, repo) = device()
            joinAll(first, COUNT)

            val (second, _) = device(repo.fragments)
            val read = second.entries().associateBy { it.id }

            assertEquals(COUNT, read.size)
            for (n in listOf(0, 1, COUNT / 2, COUNT - 2, COUNT - 1)) {
                val want = entry(n)
                val got = read.getValue(want.id)
                assertEquals(want.root, got.root)
                assertEquals(want.controlRoot, got.controlRoot)
                assertEquals(want.heldRoots.map { it.key }, got.heldRoots.map { it.key })
                assertEquals(want.privateChannels.map { it.key }, got.privateChannels.map { it.key })
                assertEquals(want.name, got.name)
            }
        }

    @Test
    fun editsOnAnotherDeviceSurviveTheRoundTripAcrossFragments() =
        runTest {
            val (first, repo) = device()
            joinAll(first, COUNT)

            // The second device leaves a membership that lives in a later fragment and joins a new one.
            val (second, secondRepo) = device(repo.fragments)
            second.unfollow(entry(COUNT - 3).id)
            second.follow(entry(900))

            // The first device, fed what the second published, converges on the same List.
            val (third, _) = device(secondRepo.fragments)
            val ids = third.entries().map { it.id }.toSet()
            assertEquals(COUNT, ids.size)
            assertTrue(entry(COUNT - 3).id !in ids, "the leave must hold across fragments")
            assertTrue(entry(900).id in ids)
            assertTrue(entry(0).id in ids && entry(COUNT - 1).id in ids)
        }

    @Test
    fun aDeviceMissingFragmentsJoinsIntoAHeldFragmentWithRoom() =
        runTest {
            val (first, repo) = device()
            joinAll(first, COUNT)
            val wire = repo.fragments.sortedBy { it.index() }
            assertTrue(wire.size >= 3)

            // Holding the full fragment 0 and the partly filled last one, but not the middle ones
            // (they sit on a relay this device can't reach): the join goes where it fits.
            val (partial, partialRepo) = device(listOf(wire.first(), wire.last()))
            val written = partial.follow(entry(901)).map { it as ConcordCommunityListFragmentEvent }
            assertEquals(listOf(wire.last().index()), written.map { it.index() }, "the new membership belongs in the held fragment with room")

            // Nothing it never held was rewritten: once the middle fragments arrive, nothing is lost.
            val merged = (partialRepo.fragments + wire).groupBy { it.index() }.map { (_, copies) -> copies.maxBy { it.createdAt } }
            val (reader, _) = device(merged)
            val ids = reader.entries().map { it.id }.toSet()
            assertEquals(COUNT + 1, ids.size, "every membership from every fragment plus the new join")
            assertTrue(entry(901).id in ids)
        }

    @Test
    fun aDeviceHoldingOnlyFullFragmentsRefusesTheJoinRatherThanOverflowing() =
        runTest {
            val (first, repo) = device()
            joinAll(first, COUNT)
            val fragmentZero = repo.fragments.first { it.index() == 0 }

            // Opening a new fragment is a repack, which needs the complete List (CORD-02 §8); an
            // oversized event would be refused by relays. Refusing here is the only safe answer,
            // and the caller must surface it.
            val (partial, partialRepo) = device(listOf(fragmentZero))
            assertFailsWith<ConcordListTooLargeException> { partial.follow(entry(902)) }
            assertEquals(listOf(fragmentZero), partialRepo.fragments, "a refused write publishes nothing")
        }

    companion object {
        /** Private channels per membership: ~6 KB entries, a handful per fragment. */
        private const val PRIVATE_CHANNELS = 30

        /** Joins per test: enough for several fragments with the last one only partly filled. */
        private const val COUNT = 30
    }
}
