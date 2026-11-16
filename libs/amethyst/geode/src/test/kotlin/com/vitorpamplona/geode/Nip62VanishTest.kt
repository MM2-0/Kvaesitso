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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * NIP-62 right-to-vanish:
 *  - A kind-62 event scoped to a relay URL cascades-deletes ALL of the
 *    author's earlier events on that relay.
 *  - After the vanish, attempts to insert OLDER events from that author
 *    are rejected (the SQL `reject_events_on_event_vanish` trigger).
 *  - Newer events from the same author (createdAt > vanish.createdAt)
 *    can still be published — the user is asking the relay to forget
 *    their past, not to ban them.
 *  - A vanish from author A does not affect author B's events.
 */
class Nip62VanishTest {
    private lateinit var hub: InProcessRelays
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient
    private val relayUrl: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/")

    @BeforeTest
    fun setup() {
        hub = InProcessRelays()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        client = NostrClient(hub, scope)
    }

    @AfterTest
    fun teardown() {
        client.disconnect()
        scope.cancel()
        hub.close()
    }

    @Test
    fun vanishCascadesPriorEventsFromSameAuthor() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val now = TimeUtils.now()

            val a = signer.sign(TextNoteEvent.build("first", createdAt = now - 100))
            val b = signer.sign(TextNoteEvent.build("second", createdAt = now - 50))

            client.publishAndConfirm(a, setOf(relayUrl))
            client.publishAndConfirm(b, setOf(relayUrl))
            assertNotNull(client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(a.id))))
            assertNotNull(client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(b.id))))

            val vanish =
                signer.sign(
                    RequestToVanishEvent.build(
                        relay = relayUrl,
                        reason = "GDPR cleanup",
                        createdAt = now,
                    ),
                )
            assertEquals(true, client.publishAndConfirm(vanish, setOf(relayUrl)))

            assertNull(client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(a.id))))
            assertNull(client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(b.id))))
        }

    @Test
    fun vanishBlocksReinsertionOfOlderEvents() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val now = TimeUtils.now()

            val vanish =
                signer.sign(
                    RequestToVanishEvent.build(relay = relayUrl, createdAt = now),
                )
            client.publishAndConfirm(vanish, setOf(relayUrl))

            // Older event from the same author must be rejected.
            val older = signer.sign(TextNoteEvent.build("comeback", createdAt = now - 100))
            val ok = client.publishAndConfirm(older, setOf(relayUrl))
            assertEquals(false, ok, "events older than the vanish must be rejected")
        }

    @Test
    fun vanishDoesNotAffectOtherAuthors() =
        runBlocking {
            val alice = NostrSignerSync(KeyPair())
            val bob = NostrSignerSync(KeyPair())
            val now = TimeUtils.now()

            val aliceNote = alice.sign(TextNoteEvent.build("alice", createdAt = now - 100))
            val bobNote = bob.sign(TextNoteEvent.build("bob", createdAt = now - 100))
            client.publishAndConfirm(aliceNote, setOf(relayUrl))
            client.publishAndConfirm(bobNote, setOf(relayUrl))

            val aliceVanish =
                alice.sign(RequestToVanishEvent.build(relay = relayUrl, createdAt = now))
            client.publishAndConfirm(aliceVanish, setOf(relayUrl))

            assertNull(client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(aliceNote.id))))
            val bobStillThere =
                client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(bobNote.id)))
            assertEquals(bobNote.id, bobStillThere?.id, "bob's events must survive alice's vanish")
        }
}
