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
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListFragments
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The account joined a community on another client but has NO offline backup here yet; the
 * kind-13302 list only shows up later, delivered by a relay into the addressable cache note.
 * `liveCommunities` must then decrypt it and surface the entry so the bottom-bar tab and the
 * Concord Channels screen fill in.
 */
class ConcordListLateArrivalTest {
    private val signer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))

    private val alpha = "a".repeat(64)

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

    /** No offline backup at all — this account never saved a list locally. */
    private class NoBackupRepository : ConcordListRepository {
        override fun concordList(): ConcordCommunityListEvent? = null

        override fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?) {}
    }

    /** Hands back one stable addressable note per address so the state and the "relay" share it. */
    private class StubCache : ICacheProvider {
        private val notes = HashMap<String, AddressableNote>()

        override fun getAnyChannel(note: Note): Channel? = null

        override val relayHints = HintIndexer()

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = null

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = null

        override fun getOrCreateAddressableNote(address: Address): AddressableNote = notes.getOrPut(address.toValue()) { AddressableNote(address) }

        override fun getEventStream(): ICacheEventStream = error("not used")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun consumeEmbedded(event: Event) = Unit

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }

    @Test
    fun lateArrivingListDecryptsAndSurfaces() =
        runTest {
            val state =
                ConcordChannelListState(
                    signer = signer,
                    cache = StubCache(),
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    settings = NoBackupRepository(),
                )

            // Nothing is known yet: no backup, empty cache note.
            assertEquals(emptyList(), state.liveCommunities.value)

            // A relay delivers the kind-13302 into the SAME addressable note the state observes.
            val event = ConcordCommunityListEvent.create(signer, listOf(entry(alpha, "Alpha")))
            val author = User(signer.pubKey) { addr -> Note(addr.toValue()) }
            state.concordListNote.loadEvent(event, author, emptyList())

            // liveCommunities decrypts on Dispatchers.IO, so give it a beat to settle.
            withTimeout(5000) {
                while (state.liveCommunities.value.isEmpty()) yield()
            }

            assertEquals(1, state.liveCommunities.value.size)
            assertEquals(alpha, state.liveCommunities.value[0].id)
        }

    @Test
    fun lateArrivingFragmentDecryptsAndSurfaces() =
        runTest {
            val cache = StubCache()
            val state =
                ConcordChannelListState(
                    signer = signer,
                    cache = cache,
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    settings = NoBackupRepository(),
                )
            assertEquals(emptyList(), state.liveCommunities.value)

            // A relay delivers fragment 0 of the kind-33302 List into the note the state watches.
            val internal = ConcordCommunityList.encodeInternal(listOf(entry(alpha, "Alpha")))
            val fragment = ConcordCommunityListFragmentEvent.create(signer, 0, ConcordListFragments.pack(internal).single())
            val author = User(signer.pubKey) { addr -> Note(addr.toValue()) }
            cache.getOrCreateAddressableNote(fragment.address()).loadEvent(fragment, author, emptyList())

            withTimeout(5000) {
                while (state.liveCommunities.value.isEmpty()) yield()
            }
            assertEquals(listOf(alpha), state.liveCommunities.value.map { it.id })
        }
}
