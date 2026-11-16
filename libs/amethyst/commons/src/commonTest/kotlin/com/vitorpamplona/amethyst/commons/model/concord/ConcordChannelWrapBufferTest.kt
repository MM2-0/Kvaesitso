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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A channel's wrap buffer keeps its most recently delivered wraps only: every wrap a busy channel
 * delivered used to stay for the session's whole life, with an index entry per message beside it.
 */
class ConcordChannelWrapBufferTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun aBusyChannelKeepsOnlyItsNewestWrapsAndTheirIndex() =
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
            val emitted = mutableListOf<Event>()
            val session = ConcordCommunitySession(entry, owner.pubKey, maxChannelWraps = 10) { _, _, rumor, _ -> emitted += rumor }
            community.genesisWraps.forEach { session.ingest(it) }

            val general = community.generalChannelIdHex
            val plane = assertNotNull(session.currentChannelPlane(general))
            val wraps = (0 until 30).map { ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "m$it", 1_000_000L + it) }
            wraps.forEach { session.ingest(it) }

            // Every message still reached the store; the buffer only stopped holding the old wraps.
            val messages = emitted.filter { it.content.startsWith("m") }
            assertEquals(30, messages.size)
            val held = wraps.filter { session.isBuffered(general, it.id) }
            assertEquals(wraps.takeLast(10), held, "the ten most recently delivered stay")
            assertTrue(session.isBuffered(general, wraps.last().id), "the newest wrap stays")
            assertFalse(session.isBuffered(general, wraps.first().id), "the oldest wrap goes")

            // The rumor index follows the buffer: an evicted message is no longer held for pins.
            assertFalse(session.holdsRumor(messages.first().id))
            assertNull(session.pinSource(general, messages.first().id))
            assertTrue(session.holdsRumor(messages.last().id))
            assertNotNull(session.pinSource(general, messages.last().id))

            // An evicted wrap delivered again (a page scrolled back to) is opened again, and is now
            // among the most recent, so its message can be pinned; the oldest held one makes room.
            session.ingest(wraps.first())
            assertEquals(31, emitted.count { it.content.startsWith("m") })
            assertTrue(session.isBuffered(general, wraps.first().id))
            assertNotNull(session.pinSource(general, messages.first().id))
            assertFalse(session.isBuffered(general, wraps[20].id))
        }
}
