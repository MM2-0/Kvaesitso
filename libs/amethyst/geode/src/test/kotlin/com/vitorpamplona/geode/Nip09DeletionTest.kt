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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies NIP-09 deletion request behavior end-to-end through
 * `NostrClient` → `InProcessRelays`. The relay's
 * [com.vitorpamplona.quartz.nip01Core.store.sqlite.DeletionRequestModule]
 * is responsible for honouring kind-5 events:
 *
 *  1. Existing events targeted by id are removed from the store.
 *  2. A SQL trigger blocks re-insertion of any event that matches a
 *     stored kind-5 deletion (so a malicious relay can't sneak the
 *     deleted event back in via another connection).
 *  3. Cross-author deletion is silently ignored: a kind-5 event from
 *     pubkey X cannot delete pubkey Y's events.
 */
class Nip09DeletionTest {
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

    private suspend fun query(filter: Filter): List<Event> {
        val ch = kotlinx.coroutines.channels.Channel<Either>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val subId = "sub-${System.nanoTime()}"
        client.subscribe(
            subId,
            mapOf(relayUrl to listOf(filter)),
            object : com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    ch.trySend(Either.Ev(event))
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    ch.trySend(Either.Eose)
                }
            },
        )
        val events = mutableListOf<Event>()
        kotlinx.coroutines.withTimeout(5000) {
            while (true) {
                when (val msg = ch.receive()) {
                    is Either.Ev -> events += msg.event
                    Either.Eose -> return@withTimeout
                }
            }
        }
        client.unsubscribe(subId)
        return events
    }

    private sealed interface Either {
        data class Ev(
            val event: Event,
        ) : Either

        object Eose : Either
    }

    @Test
    fun deletionRemovesTargetedEventFromStore() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val note = signer.sign(TextNoteEvent.build("delete me"))
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(note), createdAt = note.createdAt + 1))

            // Publish original, confirm it's stored.
            assertEquals(true, client.publishAndConfirm(note, setOf(relayUrl)))
            assertEquals(1, query(Filter(ids = listOf(note.id))).size)

            // Publish deletion; the store removes the targeted event.
            assertEquals(true, client.publishAndConfirm(deletion, setOf(relayUrl)))
            assertEquals(0, query(Filter(ids = listOf(note.id))).size, "event must be gone")
        }

    @Test
    fun deletionEventItselfIsStoredAndQueryable() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val note = signer.sign(TextNoteEvent.build("a"))
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(note), createdAt = note.createdAt + 1))

            client.publishAndConfirm(note, setOf(relayUrl))
            client.publishAndConfirm(deletion, setOf(relayUrl))

            val results = query(Filter(kinds = listOf(DeletionRequestEvent.KIND), authors = listOf(signer.pubKey)))
            assertEquals(1, results.size)
            assertEquals(deletion.id, results[0].id)
        }

    @Test
    fun reinsertingADeletedEventIsRejected() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val note = signer.sign(TextNoteEvent.build("once-upon-a-time"))
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(note), createdAt = note.createdAt + 1))

            client.publishAndConfirm(note, setOf(relayUrl))
            client.publishAndConfirm(deletion, setOf(relayUrl))

            // Trying to reinsert the same event must fail — relay returns OK false.
            val ok = client.publishAndConfirm(note, setOf(relayUrl))
            assertEquals(false, ok, "reinserting a deleted event must be blocked")
        }

    @Test
    fun crossAuthorDeletionDoesNotRemoveOtherUsersEvents() =
        runBlocking {
            val alice = NostrSignerSync(KeyPair())
            val mallory = NostrSignerSync(KeyPair())
            val aliceNote = alice.sign(TextNoteEvent.build("alice-private"))

            client.publishAndConfirm(aliceNote, setOf(relayUrl))
            assertEquals(1, query(Filter(ids = listOf(aliceNote.id))).size)

            // Mallory tries to delete Alice's event: relay accepts the
            // kind-5 event itself (it's just an event), but the SQL DELETE
            // is owner-scoped, so Alice's event survives.
            val malloryDelete =
                mallory.sign(DeletionRequestEvent.build(listOf(aliceNote), createdAt = aliceNote.createdAt + 1))
            client.publishAndConfirm(malloryDelete, setOf(relayUrl))

            assertEquals(
                1,
                query(Filter(ids = listOf(aliceNote.id))).size,
                "Mallory's deletion must NOT remove Alice's event",
            )
        }
}
