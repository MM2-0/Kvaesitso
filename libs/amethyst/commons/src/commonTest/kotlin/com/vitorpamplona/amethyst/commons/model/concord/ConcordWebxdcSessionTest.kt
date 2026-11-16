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

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordWebxdc
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WebXDC signals (kind 3310) on a Chat Plane (F10): the session accepts them under the plane's strict
 * binding and holds them for a WebXDC host, but never hands them to the chat store — so they never
 * become feed rows, previews or unread messages — while ordinary messages on the same plane still do.
 */
class ConcordWebxdcSessionTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val alice = NostrSignerInternal(KeyPair())
    private val topic = "A".repeat(26) + "234567".repeat(4) + "BC"

    @Test
    fun webxdcSignalsAreHeldApartFromChatRows() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val stored = mutableListOf<Event>()
            val session = ConcordCommunitySession(entry, owner.pubKey) { _, _, rumor, _ -> stored += rumor }
            community.genesisWraps.forEach { session.ingest(it) }
            val general = community.generalChannelIdHex
            val plane = assertNotNull(session.currentChannelPlane(general))

            val update = ConcordWebxdc.stateUpdate(alice.pubKey, general, plane.epoch, "uuid-1", "{\"move\":1}", createdAt = 10L)
            val ad = ConcordWebxdc.peerSignal(alice.pubKey, general, plane.epoch, topic, "node-addr", createdAt = 11L)
            // Bound to another epoch: the plane's strict binding still refuses it.
            val misbound = ConcordWebxdc.stateUpdate(alice.pubKey, general, plane.epoch + 1, "uuid-1", "{}", createdAt = 12L)
            for (rumor in listOf(update, ad, misbound)) {
                session.ingest(ConcordStreamEnvelope.wrap(rumor, plane.key, alice, encrypted = true, createdAt = rumor.createdAt))
            }
            val message = ConcordActions.buildChannelMessage(alice, plane.key, general, plane.epoch, "hello", 13L)
            session.ingest(message)

            // Only the chat message reached the store (feeds, previews, unread counts read from there).
            assertEquals(listOf("hello"), stored.map { it.content })
            assertTrue(stored.none { it.kind == ConcordWebxdc.KIND })

            // The signals are held for a WebXDC host, oldest first; the misbound one is not.
            val held = session.webxdcSignals(general)
            assertEquals(listOf(update.id, ad.id), held.map { it.id })
            assertEquals("uuid-1", ConcordWebxdc.sessionOf(held.first()))
            assertEquals("node-addr", ConcordWebxdc.parsePeerSignal(held.last().content)?.addr)
            assertEquals(2L, session.webxdcRevision.value)

            // A duplicate delivery holds nothing new; the author counts as observed either way.
            session.ingest(ConcordStreamEnvelope.wrap(update, plane.key, alice, encrypted = true, createdAt = 10L))
            assertEquals(2, session.webxdcSignals(general).size)
            assertTrue(alice.pubKey.lowercase() in session.observedAuthors.value)
        }

    // Any member can send signals, and nothing consumes them yet: what is held stays bounded in bytes.
    @Test
    fun aFloodOfBigSignalsIsBoundedInBytes() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val session = ConcordCommunitySession(entry, owner.pubKey) { _, _, _, _ -> }
            community.genesisWraps.forEach { session.ingest(it) }
            val general = community.generalChannelIdHex
            val plane = assertNotNull(session.currentChannelPlane(general))
            // A wrap carries at most NIP-44's 64 KiB, so a flood is many signals near that size.
            val payload = "x".repeat(40_000)

            repeat(150) { i ->
                val update = ConcordWebxdc.stateUpdate(alice.pubKey, general, plane.epoch, "uuid-$i", payload, createdAt = 10L + i)
                session.ingest(ConcordStreamEnvelope.wrap(update, plane.key, alice, encrypted = true, createdAt = update.createdAt))
            }

            val held = session.webxdcSignals(general)
            assertTrue(held.sumOf { ConcordCommunitySession.webxdcWeight(it) } <= ConcordCommunitySession.MAX_WEBXDC_BYTES_PER_CHANNEL)
            assertTrue(held.size < 150, "the oldest were let go: ${held.size}")
            assertEquals("uuid-149", ConcordWebxdc.sessionOf(held.last()))
        }
}
