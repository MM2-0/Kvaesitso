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
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListFragmentSet
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListFragments
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListIncompleteException
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
 * The "leave a Concord community" path: `unfollow` read-modify-writes the Community List (CORD-02 §8).
 * Everything that makes leaving safe lives here — it must remove only the named community, keep the
 * other memberships (and their secrets) intact, leave a tombstone behind (only a tombstone subtracts
 * a membership), and be a pure local list edit that never depends on the community's own (possibly
 * dead) relays.
 */
class ConcordChannelListLeaveTest {
    private val signer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))

    private val alpha = "a".repeat(64)
    private val beta = "b".repeat(64)

    private fun entry(
        id: String,
        name: String,
    ) = ConcordCommunityListEntry(
        id = id,
        owner = signer.pubKey,
        ownerSalt = "1".repeat(64),
        root = "2".repeat(64),
        rootEpoch = 3,
        relays = listOf("ws://127.0.0.1:7777"),
        name = name,
        addedAt = 1000,
    )

    /** Serves the list only from the offline backup — the state a dead-relay community lands in. */
    private class BackupOnlyRepository(
        var saved: ConcordCommunityListEvent?,
        var fragments: List<ConcordCommunityListFragmentEvent> = emptyList(),
    ) : ConcordListRepository {
        override fun concordList() = saved

        override fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?) {
            saved = newConcordList
        }

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

    /** The cached notes are empty (nothing folded from relays), so every read falls back to the offline backup. */
    private suspend fun state(vararg entries: ConcordCommunityListEntry): Pair<ConcordChannelListState, BackupOnlyRepository> {
        val repo = BackupOnlyRepository(null)
        val list = ConcordChannelListState(signer = signer, cache = StubCache(), scope = CoroutineScope(Dispatchers.Unconfined), settings = repo)
        list.markRelaysConfirmed()
        for (e in entries) list.follow(e)
        return list to repo
    }

    private suspend fun readBack(fragments: List<Event>) = ConcordListFragmentSet.resolve(fragments.map { it as ConcordCommunityListFragmentEvent }, signer)

    @Test
    fun leavingDropsOnlyThatCommunityAndTombstonesIt() =
        runTest {
            val (list, repo) = state(entry(alpha, "Alpha"), entry(beta, "Beta"))

            val left = list.unfollow(alpha)
            assertEquals(1, left.size)

            val remaining = list.entries()
            assertEquals(listOf(beta), remaining.map { it.id })
            // The surviving membership keeps its secrets — leaving one community must not damage another.
            assertEquals("2".repeat(64), remaining[0].root)
            assertEquals(3L, remaining[0].rootEpoch)

            val doc = readBack(repo.fragments).doc
            val tombstoned = ConcordListFragments.removals(doc)
            assertTrue(alpha in tombstoned, "a leave must leave a tombstone, or another fragment can re-add it")
        }

    @Test
    fun leavingTheLastCommunityEmptiesTheList() =
        runTest {
            val (list, _) = state(entry(alpha, "Alpha"))
            list.unfollow(alpha)
            assertTrue(list.entries().isEmpty())
        }

    /** Nothing to publish when we weren't a member. */
    @Test
    fun leavingSomethingWeNeverJoinedIsANoOp() =
        runTest {
            val (list, _) = state(entry(alpha, "Alpha"))
            assertTrue(list.unfollow(beta).isEmpty())
        }

    @Test
    fun reJoiningAfterALeaveResurrectsTheMembership() =
        runTest {
            val (list, _) = state(entry(alpha, "Alpha"))
            list.unfollow(alpha)
            val rejoin =
                ConcordCommunityListEntry(
                    id = alpha,
                    owner = signer.pubKey,
                    ownerSalt = "1".repeat(64),
                    root = "2".repeat(64),
                    rootEpoch = 3,
                    name = "Alpha",
                    addedAt = Long.MAX_VALUE / 2,
                )
            list.follow(rejoin)
            assertEquals(listOf(alpha), list.entries().map { it.id })
        }

    @Test
    fun reJoiningInTheSameMillisecondStillOutranksTheLeave() =
        runTest {
            val (list, _) = state(entry(alpha, "Alpha"))
            list.unfollow(alpha)
            // A stale entry (added long before the leave) re-followed: it must come back live.
            list.follow(entry(alpha, "Alpha"))
            assertEquals(listOf(alpha), list.entries().map { it.id })
        }

    @Test
    fun anUnloadedListRefusesToWrite() =
        runTest {
            // Nothing held and the relays not asked yet: writing would replace fragments we never saw.
            val list = ConcordChannelListState(signer = signer, cache = StubCache(), scope = CoroutineScope(Dispatchers.Unconfined), settings = BackupOnlyRepository(null))
            assertFailsWith<ConcordListIncompleteException> { list.follow(entry(alpha, "Alpha")) }
        }

    @Test
    fun aMembershipOnlyTheRetiredListCarriesIsMigratedByTheNextWrite() =
        runTest {
            val repo = BackupOnlyRepository(ConcordCommunityListEvent.create(signer, listOf(entry(alpha, "Alpha"))))
            val list = ConcordChannelListState(signer = signer, cache = StubCache(), scope = CoroutineScope(Dispatchers.Unconfined), settings = repo)
            list.markRelaysConfirmed()
            assertEquals(listOf(alpha), list.entries().map { it.id })

            list.follow(entry(beta, "Beta"))
            val migrated =
                ConcordCommunityList
                    .decodeDocument(readBack(repo.fragments).doc)
                    .entries
                    .map { it.id }
                    .toSet()
            assertEquals(setOf(alpha, beta), migrated)
        }

    /**
     * The list is only readable by its owner, so every fragment must stay self-encrypted — a leave
     * that leaked the remaining memberships in cleartext would be worse than not leaving at all.
     */
    @Test
    fun theRewrittenListStaysSelfEncrypted() =
        runTest {
            val (list, _) = state(entry(alpha, "Alpha"), entry(beta, "Beta"))

            val left = list.unfollow(alpha).single() as ConcordCommunityListFragmentEvent

            assertEquals(ConcordCommunityListFragmentEvent.KIND, left.kind)
            assertEquals("0", left.dTag())
            assertTrue(beta !in left.content)
            assertTrue(ConcordListFragments.hexToB64(beta) !in left.content)
            val stranger = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))
            assertEquals(null, left.decryptPlaintext(stranger))
        }
}
