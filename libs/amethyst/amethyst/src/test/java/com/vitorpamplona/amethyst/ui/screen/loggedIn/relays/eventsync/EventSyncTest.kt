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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.eventsync

import com.vitorpamplona.amethyst.commons.relays.eventsync.EventSync
import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives [EventSync] end to end against geode's in-process relays: one
 * "source" relay that already holds the account's history and three empty
 * destination relays (outbox / inbox / DM). No network, no public relay —
 * every relay is a [RelayEngine] inside this JVM, so the assertions are on
 * what actually landed in each destination store, not on "it didn't crash".
 *
 * The second scenario gates the source relay behind NIP-42 ([FullAuthPolicy])
 * to cover the [RelayAuthenticator] wiring the sync screen relies on when a
 * user's relay demands AUTH before serving REQs.
 */
class EventSyncTest : RelayClientTest() {
    private val account = NostrSignerSync(KeyPair())
    private val other = NostrSignerSync(KeyPair())

    private val source: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://source.relay/")
    private val outbox: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://outbox.relay/")
    private val inbox: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://inbox.relay/")
    private val dm: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://dm.relay/")

    /** Separate hub so only the source relay demands AUTH; destinations stay open. */
    private val authHub = InProcessRelays(defaultPolicy = { FullAuthPolicy(source) })

    @After
    fun tearDownAuthHub() {
        authHub.close()
    }

    private fun note(
        author: NostrSignerSync,
        content: String,
        tagged: HexKey? = null,
    ): Event = author.sign(eventTemplate<Event>(1, content) { tagged?.let { pTag(it) } })

    private fun legacyDm(
        author: NostrSignerSync,
        recipient: HexKey,
    ): Event = author.sign(eventTemplate<Event>(4, "ciphertext") { pTag(recipient) })

    private val mine = List(3) { note(account, "mine $it") }
    private val mentions = List(2) { note(other, "hey $it", tagged = account.pubKey) }
    private val dmToMe = legacyDm(other, account.pubKey)
    private val noise = note(other, "unrelated")

    private fun corpus(): List<Event> = mine + mentions + dmToMe + noise

    /** [decorate] runs on every client the sync builds, e.g. to attach an authenticator. */
    private fun eventSync(
        builder: WebsocketBuilder,
        decorate: (NostrClient) -> Unit = {},
    ): EventSync =
        EventSync(
            accountPubKey = account.pubKey,
            relayDb = { listOf(source) },
            outboxTargets = { setOf(outbox) },
            inboxTargets = { setOf(inbox) },
            dmTargets = { setOf(dm) },
            clientBuilder = { NostrClient(builder, scope).also(decorate) },
            scope = scope,
        )

    /**
     * Publishes are fire-and-forget on the client side, so the destination
     * store can lag `runSync` returning by a few ticks. Poll instead of
     * asserting a snapshot.
     */
    private suspend fun RelayEngine.awaitCount(
        filter: Filter,
        expected: Int,
    ): Int =
        withTimeoutOrNull(10_000) {
            while (store.count(filter) < expected) delay(25)
            store.count(filter)
        } ?: store.count(filter)

    private suspend fun assertRouted(hubOfTargets: InProcessRelays) {
        val outboxRelay = hubOfTargets.getOrCreate(outbox)
        val inboxRelay = hubOfTargets.getOrCreate(inbox)
        val dmRelay = hubOfTargets.getOrCreate(dm)

        assertEquals(
            "every event authored by the account lands on the outbox relay",
            mine.size,
            outboxRelay.awaitCount(Filter(authors = listOf(account.pubKey)), mine.size),
        )
        assertEquals(
            "non-DM mentions land on the inbox relay",
            mentions.size,
            inboxRelay.awaitCount(Filter(tags = mapOf("p" to listOf(account.pubKey))), mentions.size),
        )
        assertEquals(
            "the kind-4 DM lands on the DM relay",
            1,
            dmRelay.awaitCount(Filter(kinds = listOf(4)), 1),
        )

        // Routing is exclusive per rule: nothing leaks across destinations and the
        // unrelated note never leaves the source.
        assertEquals("outbox holds only the account's events", mine.size, outboxRelay.store.count(Filter()))
        assertEquals("inbox holds only the mentions", mentions.size, inboxRelay.store.count(Filter()))
        assertEquals("dm relay holds only the DM", 1, dmRelay.store.count(Filter()))
        assertEquals("noise stays on the source", 0, outboxRelay.store.count(Filter(ids = listOf(noise.id))))
    }

    @Test
    fun syncRoutesEventsFromSourceToOutboxInboxAndDmRelays() =
        runBlocking {
            hub.getOrCreate(source).preload(corpus())

            val sync = eventSync(hub)
            withTimeout(30_000) { sync.runSync() }

            val done = sync.syncState.value
            assertTrue("sync should finish in Done, got $done", done is EventSync.SyncState.Done)
            done as EventSync.SyncState.Done
            assertEquals(
                "mine + mentions + dm match a routing rule; noise does not",
                mine.size + mentions.size + 1,
                done.totalEventsReceived,
            )
            // runSync drains the outbox before closing its client, so by the time
            // Done is published every forwarded event has been written to its
            // destination socket — not merely queued.
            assertEquals(
                "every routed event was sent before the client closed",
                mine.size + mentions.size + 1,
                done.totalEventsSent,
            )

            assertRouted(hub)
        }

    @Test
    fun syncReadsFromAuthRequiredSourceOnceAuthenticated() =
        runBlocking {
            authHub.getOrCreate(source).preload(corpus())

            // Source demands NIP-42 before serving REQs; destinations are the open hub.
            val router =
                object : WebsocketBuilder {
                    override fun build(
                        url: NormalizedRelayUrl,
                        out: WebSocketListener,
                    ): WebSocket = if (url == source) authHub.build(url, out) else hub.build(url, out)
                }

            val authSigner = NostrSignerSync(KeyPair())
            var authenticator: RelayAuthenticator? = null
            val sync =
                eventSync(router) { client ->
                    authenticator =
                        RelayAuthenticator(client = client, scope = scope) { _, template, _ ->
                            listOf(authSigner.sign(template))
                        }
                }

            try {
                withTimeout(30_000) { sync.runSync() }
            } finally {
                authenticator?.destroy()
            }

            val done = sync.syncState.value
            assertTrue("sync should finish in Done, got $done", done is EventSync.SyncState.Done)
            assertEquals(
                "the auth-gated source still yields every routed event",
                mine.size + mentions.size + 1,
                (done as EventSync.SyncState.Done).totalEventsReceived,
            )

            assertRouted(hub)
        }
}
