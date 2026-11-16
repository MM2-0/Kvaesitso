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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** NAP-COMMON in the broker: NIP-19 helpers, reads, and actions published as the events they are. */
class NappletCommonTest {
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("05").hexToByteArray()))
    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "common")
    private val declared = setOf(NappletCapability.COMMON)
    private val alice = "a1".repeat(32)
    private val bob = "b0".repeat(32)

    private class Relay : NappletRelayGateway {
        val published = mutableListOf<Event>()

        override suspend fun publish(event: Event): List<String> {
            published += event
            return listOf("wss://relay.example")
        }

        override suspend fun query(filters: List<Filter>): List<Event> = emptyList()
    }

    // Answers every read with [events].
    private class Reads(
        private val events: List<Event> = emptyList(),
    ) : NappletOutboxGateway {
        override suspend fun getEvent(
            eventId: HexKey,
            author: HexKey?,
            relayHints: List<String>,
            timeoutMs: Long?,
        ) = OutboxRead(events.filter { it.id == eventId }.map { OutboxEvent(it, emptyList()) })

        override suspend fun query(
            filters: List<Filter>,
            authors: List<HexKey>,
            relayHints: List<String>,
            timeoutMs: Long?,
        ) = OutboxRead(events.map { OutboxEvent(it, emptyList()) })

        override suspend fun subscriptionRelays(
            filters: List<Filter>,
            authors: List<HexKey>,
            relayHints: List<String>,
        ) = emptySet<NormalizedRelayUrl>()

        override suspend fun publish(
            event: Event,
            toOutbox: Boolean,
            toInboxes: List<HexKey>,
            relays: List<String>,
        ) = OutboxDelivery(emptyMap())

        override suspend fun plan(
            authors: List<HexKey>,
            write: Boolean,
        ) = OutboxRelayPlan(emptyList(), OutboxRelayPlan.Source.POLICY)
    }

    private fun broker(
        relay: Relay = Relay(),
        follows: ContactListEvent? = null,
        events: List<Event> = emptyList(),
    ) = NappletBroker(
        signer = signer,
        ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
        consentPrompt = { _, _, _ -> GrantState.ALLOW_ALWAYS },
        relay = relay,
        outbox = Reads(events),
        common = NappletCommonGateway { follows },
    )

    private suspend fun followList(vararg keys: HexKey): ContactListEvent = signer.sign<ContactListEvent>(1L, ContactListEvent.KIND, (keys.map { arrayOf("p", it) } + listOf(arrayOf("t", "kept"))).toTypedArray(), "legacy-relays")

    @Test
    fun nip19RoundTripsAndNeverTouchesNsec() {
        val npub = NappletCommonNip19.encode(NappletRequest.CommonEncodeNip19("npub", hex = alice))
        assertIs<NappletResponse.CommonEncoded>(npub)
        assertEquals(NPub.create(alice), npub.value)
        val decoded = NappletCommonNip19.decode(npub.value)
        assertIs<NappletResponse.CommonDecoded>(decoded)
        assertEquals(alice, decoded.text["pubkey"])

        val naddr = NappletCommonNip19.encode(NappletRequest.CommonEncodeNip19("naddr", pubkey = alice, identifier = "feed", kind = 35129, relays = listOf("wss://relay.example")))
        assertIs<NappletResponse.CommonEncoded>(naddr)
        val back = NappletCommonNip19.decode(naddr.value) as NappletResponse.CommonDecoded
        assertEquals("feed", back.text["identifier"])
        assertEquals(35129, back.kind)
        assertEquals(listOf("wss://relay.example/"), back.relays)

        val nrelay = NappletCommonNip19.encode(NappletRequest.CommonEncodeNip19("nrelay", relay = "wss://relay.example")) as NappletResponse.CommonEncoded
        assertEquals("wss://relay.example/", (NappletCommonNip19.decode(nrelay.value) as NappletResponse.CommonDecoded).text["relay"])

        assertEquals(NappletResponse.CommonFailure("unsupported-nip19-type"), NappletCommonNip19.encode(NappletRequest.CommonEncodeNip19("nsec", hex = alice)))
        assertEquals(NappletResponse.CommonFailure("unsupported-nip19-type"), NappletCommonNip19.decode("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"))
        assertEquals(NappletResponse.CommonFailure("invalid-nip19"), NappletCommonNip19.decode("hello ${npub.value}"))
    }

    @Test
    fun followKeepsTheListAndIsIdempotent() =
        runTest {
            val relay = Relay()
            val broker = broker(relay, follows = followList(alice))

            val added = broker.handle(applet, NappletRequest.CommonFollow(listOf(NPub.create(bob)), follow = true), declared)
            assertIs<NappletResponse.Published>(added)
            val list = relay.published.single()
            assertEquals(ContactListEvent.KIND, list.kind)
            assertEquals("legacy-relays", list.content, "the content is kept")
            assertTrue(list.tags.any { it[0] == "t" && it[1] == "kept" }, "unrelated tags are kept")
            assertEquals(
                setOf(alice, bob),
                list.tags
                    .filter { it[0] == "p" }
                    .map { it[1] }
                    .toSet(),
            )

            // Already followed: nothing published.
            assertIs<NappletResponse.CommonUnchanged>(broker.handle(applet, NappletRequest.CommonFollow(listOf(NPub.create(alice)), follow = true), declared))
            assertEquals(1, relay.published.size)

            val removed = broker.handle(applet, NappletRequest.CommonFollow(listOf(NPub.create(alice)), follow = false), declared)
            assertIs<NappletResponse.Published>(removed)
            assertTrue(
                relay.published
                    .last()
                    .tags
                    .none { it[0] == "p" && it[1] == alice },
            )

            assertEquals(NappletResponse.CommonFailure("invalid-pubkey"), broker.handle(applet, NappletRequest.CommonFollow(listOf("nobody"), follow = true), declared))
        }

    @Test
    fun reactAndReportNeedARealTarget() =
        runTest {
            val note = signer.sign(TextNoteEvent.build("hello"))
            val relay = Relay()
            val broker = broker(relay, events = listOf(note))

            assertIs<NappletResponse.Published>(broker.handle(applet, NappletRequest.CommonReact(note.id, "+"), declared))
            val reaction = relay.published.last()
            assertEquals(7, reaction.kind)
            assertTrue(reaction.tags.any { it[0] == "e" && it[1] == note.id })
            assertTrue(reaction.tags.any { it[0] == "p" && it[1] == note.pubKey })

            assertIs<NappletResponse.Published>(broker.handle(applet, NappletRequest.CommonReact(note.id, ":soap:", "https://emoji.example/soap.png"), declared))
            assertTrue(
                relay.published
                    .last()
                    .tags
                    .any { it[0] == "emoji" && it[1] == "soap" },
            )

            assertEquals(NappletResponse.CommonFailure("invalid-reaction"), broker.handle(applet, NappletRequest.CommonReact(note.id, "a whole sentence"), declared))
            assertEquals(NappletResponse.CommonFailure("invalid-reaction"), broker.handle(applet, NappletRequest.CommonReact(note.id, ":soap:"), declared))
            assertEquals(NappletResponse.CommonFailure("invalid-target"), broker.handle(applet, NappletRequest.CommonReact("ff".repeat(32), "+"), declared))

            assertIs<NappletResponse.Published>(broker.handle(applet, NappletRequest.CommonReport(note.id, null, null, "spam", "ads"), declared))
            val report = relay.published.last()
            assertEquals(1984, report.kind)
            assertTrue(report.tags.any { it[0] == "p" && it[1] == note.pubKey }, "an event report names its author")
            assertEquals(NappletResponse.CommonFailure("author-unresolved"), broker.handle(applet, NappletRequest.CommonReport("ff".repeat(32), null, null, "spam", ""), declared))
            assertEquals(NappletResponse.CommonFailure("invalid-report-reason"), broker.handle(applet, NappletRequest.CommonReport(null, bob, null, "boring", ""), declared))
        }

    @Test
    fun anActionNeedsTheCommonDeclaration() =
        runTest {
            val relay = Relay()
            assertIs<NappletResponse.Denied>(broker(relay).handle(applet, NappletRequest.CommonFollow(listOf(NPub.create(bob)), follow = true), setOf(NappletCapability.RELAY)))
            assertTrue(relay.published.isEmpty())
        }
}
