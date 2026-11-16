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
package com.vitorpamplona.geode.policies

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.KindAllowDenyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PubkeyAllowDenyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RejectFutureEventsPolicy
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
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
 * End-to-end through `NostrClient → InProcessRelays → Relay` with the policies
 * actually wired into the relay. Proves an EVENT command sent on the
 * wire surfaces an OK false response when the policy rejects.
 */
class PoliciesIntegrationTest {
    private val relayUrl: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/")
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @AfterTest
    fun teardown() {
        scope.cancel()
    }

    /** Spin up a hub whose only relay uses the supplied policy factory. */
    private fun hubWith(policyFactory: () -> IRelayPolicy): Pair<NostrClient, InProcessRelays> {
        val hub = InProcessRelays(defaultPolicy = policyFactory)
        // Materialise the relay so the URL resolves in the hub.
        hub.getOrCreate(relayUrl)
        return NostrClient(hub, scope) to hub
    }

    @Test
    fun kindBlacklistRejectsKind4OverWire() =
        runBlocking {
            val (client, hub) = hubWith { KindAllowDenyPolicy(deny = setOf(4)) }
            try {
                val signer = NostrSignerSync(KeyPair())
                val ok = client.publishAndConfirm(signer.sign(TextNoteEvent.build("ok")), setOf(relayUrl))
                assertEquals(true, ok, "kind 1 must pass")

                // Synthetic kind-4 event — the relay's deny list rejects it.
                val kind4 = SyntheticEvents.fakeEvent(idSeed = 999, kind = 4, pubKey = signer.pubKey)
                val rejected = client.publishAndConfirm(kind4, setOf(relayUrl))
                assertEquals(false, rejected, "kind 4 must be rejected")
            } finally {
                client.disconnect()
                hub.close()
            }
        }

    @Test
    fun pubkeyAllowListRejectsForeignAuthorOverWire() =
        runBlocking {
            val alice = NostrSignerSync(KeyPair())
            val mallory = NostrSignerSync(KeyPair())
            val (client, hub) = hubWith { PubkeyAllowDenyPolicy(allow = setOf(alice.pubKey)) }
            try {
                val accepted = client.publishAndConfirm(alice.sign(TextNoteEvent.build("hi")), setOf(relayUrl))
                assertEquals(true, accepted)
                val denied = client.publishAndConfirm(mallory.sign(TextNoteEvent.build("nope")), setOf(relayUrl))
                assertEquals(false, denied)
            } finally {
                client.disconnect()
                hub.close()
            }
        }

    @Test
    fun rejectFutureEventsBlocksFarFutureCreatedAtOverWire() =
        runBlocking {
            // Use a fixed clock so the policy decision is deterministic.
            val frozen = 1_000_000L
            val (client, hub) =
                hubWith { RejectFutureEventsPolicy(maxFutureSeconds = 60, now = { frozen }) }
            try {
                val signer = NostrSignerSync(KeyPair())
                val nearby = signer.sign(TextNoteEvent.build("ok", createdAt = frozen + 30))
                assertEquals(true, client.publishAndConfirm(nearby, setOf(relayUrl)))
                val tooFar = signer.sign(TextNoteEvent.build("nope", createdAt = frozen + 3600))
                assertEquals(false, client.publishAndConfirm(tooFar, setOf(relayUrl)))
            } finally {
                client.disconnect()
                hub.close()
            }
        }
}
