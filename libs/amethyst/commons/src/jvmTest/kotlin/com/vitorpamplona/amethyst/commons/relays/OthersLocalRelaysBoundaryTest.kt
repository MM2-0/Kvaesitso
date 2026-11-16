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
package com.vitorpamplona.amethyst.commons.relays

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip17Dms.DmRelayListState
import com.vitorpamplona.amethyst.commons.model.nip65ListOf
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.commons.relayClient.user.pickRelaysToLoadUsers
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isDialableFromOthers
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Whose data a relay url came from decides whether it may be dialed. The user's own local relay
 * is theirs; another user's names their machine. Asserted at the relay sets the app dials, not
 * only at the accessors.
 */
class OthersLocalRelaysBoundaryTest {
    private fun norm(url: String) = RelayUrlNormalizer.normalizeOrNull(url)!!

    private val public = norm("wss://relay.example")
    private val loopback = norm("ws://127.0.0.1:7777")
    private val tailnet = norm("ws://100.100.10.10:4848")
    private val lan = norm("ws://192.168.1.10:4848")
    private val index = norm("wss://index.example")
    private val home = norm("wss://home.example")

    private val stranger = NostrSignerSync(KeyPair())
    private val context = UserContext { addr -> AddressableNote(addr) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun nip65(
        signer: NostrSignerSync,
        vararg tags: Array<String>,
    ): AdvertisedRelayListEvent = signer.sign(1_000, AdvertisedRelayListEvent.KIND, arrayOf(*tags), "")

    private fun dm(
        signer: NostrSignerSync,
        vararg relays: NormalizedRelayUrl,
    ): DmRelayListEvent = signer.sign(1_000, DmRelayListEvent.KIND, relays.map { arrayOf("relay", it.url) }.toTypedArray(), "")

    private fun strangerWith(vararg events: Event): User {
        val user = User(stranger.pubKey, context)
        events.forEach {
            when (it) {
                is AdvertisedRelayListEvent -> user.nip65RelayListNote.loadEvent(it, user, emptyList())
                is DmRelayListEvent -> user.dmRelayListNote.loadEvent(it, user, emptyList())
            }
        }
        return user
    }

    private fun relaysToLoad(
        user: User,
        hints: HintIndexer = HintIndexer { it.isDialableFromOthers() },
    ) = pickRelaysToLoadUsers(
        users = setOf(user),
        relayHints = hints,
        indexRelays = setOf(index),
        homeRelays = setOf(home),
        searchRelays = emptySet(),
        connected = emptySet(),
        commonRelays = emptySet(),
        cannotConnectRelays = emptySet(),
        hasTried = EOSEAccountFast(),
    )

    @Test
    fun anotherUsersListOfOnlyLocalRelaysIsNeverDialed() {
        val user = strangerWith(nip65(stranger, arrayOf("r", loopback.url), arrayOf("r", tailnet.url)))

        assertNull(user.outboxRelays())
        assertNull(user.inboxRelays())

        // The dial set: neither address, and the existing fallback (index and home relays) applies.
        val dialed = relaysToLoad(user).keys
        assertFalse(loopback in dialed, "dialed $dialed")
        assertFalse(tailnet in dialed, "dialed $dialed")
        assertTrue(index in dialed && home in dialed, "fell back to $dialed")
    }

    @Test
    fun anotherUsersPublicRelaysStillLoadThem() {
        val user = strangerWith(nip65(stranger, arrayOf("r", public.url), arrayOf("r", lan.url, "write")))

        assertEquals(listOf(public), user.outboxRelays())
        assertEquals(setOf(public), relaysToLoad(user).keys)
    }

    @Test
    fun anotherUsersLocalDmInboxIsNotADeliveryTarget() {
        val user = strangerWith(dm(stranger, lan), nip65(stranger, arrayOf("r", public.url)))

        // NIP-17 delivery: no kind:10050 relay we may dial means unreachable, never their LAN.
        assertNull(user.dmInboxRelaysStrict())
        assertEquals(listOf(public), user.dmInboxRelays())
    }

    @Test
    fun anotherUsersLocalHintIsNeverIndexedOrDialed() {
        val cache = EventCache()
        val target = NostrSignerSync(KeyPair()).pubKey
        val withLanHint = stranger.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, arrayOf(arrayOf("p", target, lan.url)), "hi")
        val withPublicHint = stranger.sign<TextNoteEvent>(1_001, TextNoteEvent.KIND, arrayOf(arrayOf("p", target, public.url)), "hi")
        cache.justConsume(withLanHint, null, true)
        cache.justConsume(withPublicHint, null, true)

        assertEquals(listOf(public), cache.relayHints.hintsForKey(target))

        val dialed = relaysToLoad(User(target, context), cache.relayHints).keys
        assertFalse(lan in dialed, "dialed $dialed")
        assertTrue(public in dialed, "dialed $dialed")
    }

    @Test
    fun theHintsWeWriteForAnotherUserAreNeverLocal() {
        val user = User(stranger.pubKey, context)
        // Their most used relay is on their LAN.
        repeat(10) { user.addRelayBeingUsed(lan, 1_000L + it) }
        user.addRelayBeingUsed(public, 900)

        assertEquals(public, user.bestRelayHint())
        assertEquals(listOf(public), user.relayHints())

        val onlyLocal = User(NostrSignerSync(KeyPair()).pubKey, context)
        onlyLocal.addRelayBeingUsed(tailnet, 1_000)
        assertNull(onlyLocal.bestRelayHint())
        assertEquals(emptyList(), onlyLocal.relayHints())

        // With a 10002, both read the outbox, which never carries a local relay either.
        val listed = strangerWith(nip65(stranger, arrayOf("r", lan.url), arrayOf("r", public.url)))
        assertEquals(public, listed.bestRelayHint())
        assertEquals(listOf(public), listed.relayHints())
    }

    @Test
    fun theUsersOwnNip65ListKeepsItsLocalRelaysAndRoundTrips() {
        val keyPair = KeyPair()
        val me = NostrSignerSync(keyPair)
        val own = nip65(me, arrayOf("r", public.url), arrayOf("r", lan.url, "write"), arrayOf("r", tailnet.url, "read"))
        LocalCache.justConsume(own, null, true)

        val state = Nip65RelayListState(NostrSignerInternal(keyPair), LocalCache, scope, AccountSettings(keyPair))

        assertEquals(listOf(public, lan), state.ownWriteRelays())
        assertEquals(listOf(public, tailnet), state.ownReadRelays())
        assertEquals(setOf(public, lan), state.normalizeNIP65WriteRelayListNoDefaults(state.nip65ListNote))
        assertEquals(setOf(public, tailnet), state.normalizeNIP65ReadRelayListNoDefaults(state.nip65ListNote))

        // What the settings screen loads, it saves back unchanged.
        val saved = nip65ListOf(state.ownWriteRelays().toSet(), state.ownReadRelays().toSet())
        assertEquals(own.relays().map { it.relayUrl to it.type }.toSet(), saved.map { it.relayUrl to it.type }.toSet())
    }

    @Test
    fun theUsersOwnLocalDmInboxIsUsedForTheirOwnReads() {
        val keyPair = KeyPair()
        val me = NostrSignerSync(keyPair)
        LocalCache.justConsume(dm(me, lan, public), null, true)

        val state = DmRelayListState(NostrSignerInternal(keyPair), LocalCache, scope, AccountSettings(keyPair))

        assertEquals(setOf(lan, public), state.normalizeDMRelayListWithBackup(state.dmListNote))
    }
}
