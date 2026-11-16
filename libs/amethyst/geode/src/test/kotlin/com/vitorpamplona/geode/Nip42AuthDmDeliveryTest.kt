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
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * End-to-end proof that a NIP-17 DM actually delivers to an auth-required relay through the
 * [com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolEventOutbox] retry queue — the exact
 * path Amethyst's auth-permission feature depends on.
 *
 * The scenario mirrors "send a DM to someone whose inbox relay demands NIP-42 auth":
 *  1. The relay runs [FullAuthPolicy], so it rejects every EVENT with `auth-required:` until the
 *     connection authenticates.
 *  2. The client publishes the gift wrap immediately — before AUTH — so the first EVENT is rejected.
 *  3. A [RelayAuthenticator] answers the challenge; the relay's OK-true triggers a filter/outbox
 *     resync and the still-pending wrap is resent and stored.
 *
 * Because the first EVENT is rejected with `auth-required` (not a hard failure), the outbox must
 * NOT burn the wrap's retry budget on it — otherwise the post-AUTH resync would have nothing left
 * to resend. This test is the integration counterpart to PoolEventOutboxAuthTest's unit coverage.
 */
class Nip42AuthDmDeliveryTest {
    private lateinit var hub: InProcessRelays
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient

    private val relayUrl: NormalizedRelayUrl = InProcessRelays.DEFAULT_URL

    @BeforeTest
    fun setup() {
        // Every connection to this relay demands NIP-42 auth before it will accept an EVENT.
        hub = InProcessRelays(defaultPolicy = { FullAuthPolicy(relayUrl) })
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        client = NostrClient(hub, scope)
    }

    @AfterTest
    fun teardown() {
        client.disconnect()
        scope.cancel()
        hub.close()
    }

    /** The recipient's gift wrap (kind 1059) out of a freshly built NIP-17 DM. */
    private suspend fun buildDmWrapTo(
        sender: NostrSigner,
        recipient: String,
    ): GiftWrapEvent {
        val template = ChatMessageEvent.build("gm, this is private", listOf(PTag(recipient)))
        val result = NIP17Factory().createMessageNIP17(template, sender)
        return result.wraps.first { it.recipientPubKey() == recipient }
    }

    private suspend fun storedGiftWrapCount(): Int = hub.get(relayUrl)?.store?.count(Filter(kinds = listOf(GiftWrapEvent.KIND))) ?: 0

    @Test
    fun dmDeliversToAuthRequiredRelayAfterAuth() =
        runBlocking {
            val senderKeys = KeyPair()
            val recipient = NostrSignerInternal(KeyPair()).pubKey
            val wrap = buildDmWrapTo(NostrSignerInternal(senderKeys), recipient)

            // The signer answers the relay's AUTH challenge on this client's behalf (same identity
            // as the DM sender, though FullAuthPolicy would accept any authenticated pubkey).
            val authSigner = NostrSignerSync(senderKeys)
            val authenticator =
                RelayAuthenticator(client = client, scope = scope) { _, template, _ ->
                    listOf(authSigner.sign(template))
                }
            try {
                // Publish goes through the outbox and races ahead of AUTH, so the first EVENT is
                // rejected `auth-required`; the authenticator then unlocks the relay and the outbox
                // resends the still-pending wrap.
                client.publish(wrap, setOf(relayUrl))

                val delivered =
                    withTimeoutOrNull(10_000) {
                        while (storedGiftWrapCount() < 1) delay(50)
                        true
                    }

                assertEquals(true, delivered, "the DM gift wrap must land after AUTH resolves")
                assertEquals(
                    wrap.id,
                    hub
                        .get(relayUrl)
                        ?.store
                        ?.query<Event>(Filter(kinds = listOf(GiftWrapEvent.KIND)))
                        ?.single()
                        ?.id,
                    "the stored event must be exactly the published wrap",
                )
            } finally {
                authenticator.destroy()
            }
        }

    @Test
    fun dmIsRejectedByAuthRequiredRelayWithoutAuth() =
        runBlocking {
            // Control: with no authenticator wired, the relay never unlocks and the wrap is never
            // stored — proving the relay genuinely gates on auth (so the test above isn't vacuous).
            val recipient = NostrSignerInternal(KeyPair()).pubKey
            val wrap = buildDmWrapTo(NostrSignerInternal(KeyPair()), recipient)

            client.publish(wrap, setOf(relayUrl))

            // Give the client ample time to connect, get rejected, and (wrongly) retry.
            delay(2_000)
            assertEquals(0, storedGiftWrapCount(), "an unauthenticated EVENT must never be stored")
            assertNull(
                hub
                    .get(relayUrl)
                    ?.store
                    ?.query<Event>(Filter(kinds = listOf(GiftWrapEvent.KIND)))
                    ?.firstOrNull(),
            )
        }
}
