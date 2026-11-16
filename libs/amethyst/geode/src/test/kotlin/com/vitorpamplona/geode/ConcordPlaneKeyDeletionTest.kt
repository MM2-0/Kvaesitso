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

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeys
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Why a soft-banned member cannot delete a Concord community's history from the relay — and what
 * keeps it that way.
 *
 * The worry is real. CORD-01 inverts NIP-59: every wrap on a plane is signed by the *shared stream
 * key*, not by its author, and the true author only exists inside the encrypted seal. A member
 * banned yesterday still derives `group_key("concord/channel", community_root, channel_id, epoch)`
 * from the root they kept, so they can still sign events *as the channel itself*. If NIP-09 and
 * NIP-62 authorized on the outer `pubkey`, [Nip09DeletionTest]'s "a kind-5 from pubkey X cannot
 * delete pubkey Y's events" would be vacuous inside a plane: one event from any ex-member would
 * erase the whole community's history.
 *
 * What stops it is [com.vitorpamplona.quartz.nip01Core.store.owner]: a kind-1059 gift wrap is
 * controlled by its **p-tag recipient**, not its signer. Concord stamps a *freshly random* p-tag on
 * every wrap ([ConcordStreamEnvelope.wrapSeal]), so each wrap is owned by a one-time key that
 * nobody — attacker, author, or owner — ever holds. The channel is undeletable by construction.
 *
 * Both halves of that are load-bearing and neither was written for this reason, so both are pinned
 * here: [theEphemeralPTagIsWhatMakesTheChannelUndeletable] fails the moment the p-tag becomes a
 * real key, and the first two tests fail the moment ownership goes back to the signer.
 *
 * **Scope.** This is our relay's rule, not the protocol's. A community publishes wherever its
 * metadata points, and a third-party relay that reads NIP-09 the naive way — deletion authorized by
 * matching `pubkey` — hands every ex-member a wipe button for the whole channel. The protocol-level
 * fix is the same one that already exists for everything else: a CORD-06 Refounding rotates the
 * plane address, which protects the future but cannot restore what a relay already dropped.
 */
class ConcordPlaneKeyDeletionTest {
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
        val ch = Channel<Msg>(Channel.UNLIMITED)
        val subId = "sub-${System.nanoTime()}"
        client.subscribe(
            subId,
            mapOf(relayUrl to listOf(filter)),
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    ch.trySend(Msg.Ev(event))
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    ch.trySend(Msg.Eose)
                }
            },
        )
        val events = mutableListOf<Event>()
        withTimeout(5000) {
            while (true) {
                when (val msg = ch.receive()) {
                    is Msg.Ev -> events += msg.event
                    Msg.Eose -> return@withTimeout
                }
            }
        }
        client.unsubscribe(subId)
        return events
    }

    private sealed interface Msg {
        data class Ev(
            val event: Event,
        ) : Msg

        object Eose : Msg
    }

    /** A public channel plane: derived from the community root, so every member holds its secret. */
    private val communityRoot = RandomInstance.bytes(32)
    private val channelId = RandomInstance.bytes(32)
    private val plane = ConcordChannelKeys.publicChannel(communityRoot, channelId, rootEpoch = 0)

    /** The plane's own signer — what the banned member reconstructs from the root they kept. */
    private fun planeSigner() = NostrSignerSync(KeyPair(privKey = plane.secretKey))

    private suspend fun postAs(
        author: NostrSignerInternal,
        text: String,
        createdAt: Long,
    ): Event {
        val rumor = ChannelChat.message(author.pubKey, channelId.toHexKey(), epoch = 0, text = text, createdAt = createdAt)
        return ConcordStreamEnvelope.wrap(rumor, plane, author, encrypted = true, createdAt = createdAt)
    }

    @Test
    fun aPlaneKeyHolderCannotDeleteTheChannelsHistory() =
        runBlocking {
            val now = TimeUtils.now()
            val bob = NostrSignerInternal(KeyPair())
            val carol = NostrSignerInternal(KeyPair())

            val history =
                listOf(
                    postAs(bob, "hello", now),
                    postAs(carol, "hi bob", now + 1),
                    postAs(bob, "how's the project going?", now + 2),
                )
            history.forEach { assertEquals(true, client.publishAndConfirm(it, setOf(relayUrl)), "seed the channel history") }
            assertEquals(3, query(Filter(authors = listOf(plane.publicKeyHex))).size, "three messages on the plane")

            // The banned member still derives `plane`, and every wrap above IS authored by it — so
            // this kind-5 satisfies a same-author check. It must still be refused.
            val deletion = planeSigner().sign(DeletionRequestEvent.build(history, createdAt = now + 10))
            assertEquals(true, client.publishAndConfirm(deletion, setOf(relayUrl)), "the relay accepts the event itself")

            assertEquals(
                3,
                query(Filter(authors = listOf(plane.publicKeyHex), kinds = listOf(ConcordStreamEnvelope.KIND_WRAP))).size,
                "signing as the plane must NOT delete the community's messages",
            )
        }

    @Test
    fun aPlaneKeyHolderCannotVanishTheChannelPlane() =
        runBlocking {
            val now = TimeUtils.now()
            val bob = NostrSignerInternal(KeyPair())

            val history = listOf(postAs(bob, "one", now), postAs(bob, "two", now + 1))
            history.forEach { client.publishAndConfirm(it, setOf(relayUrl)) }
            assertEquals(2, query(Filter(authors = listOf(plane.publicKeyHex))).size)

            // NIP-62 needs no per-event targeting: one event, and everything that pubkey published
            // is gone. The sharpest version of the attack, and the same rule has to stop it.
            val vanish = planeSigner().sign(RequestToVanishEvent.build(relayUrl, "", createdAt = now + 10))
            assertEquals(true, client.publishAndConfirm(vanish, setOf(relayUrl)))

            assertEquals(
                2,
                query(Filter(authors = listOf(plane.publicKeyHex), kinds = listOf(ConcordStreamEnvelope.KIND_WRAP))).size,
                "a kind-62 signed as the plane must not wipe the channel",
            )
        }

    @Test
    fun theEphemeralPTagIsWhatMakesTheChannelUndeletableSoDoNotMakeItMeaningful() =
        runBlocking {
            // The counterfactual, so the invariant is visible rather than incidental: ownership of a
            // 1059 follows the p-tag, so a wrap addressed to a REAL key is deletable by whoever holds
            // that key. Concord is safe only because `wrapSeal` stamps a fresh throwaway pubkey there.
            // If that p-tag ever becomes something a member holds — a recipient, a channel id, a
            // community id — every ex-holder of it can delete the plane's history.
            val now = TimeUtils.now()
            val mallory = NostrSignerInternal(KeyPair())

            val addressedWrap =
                planeSigner().signNormal<Event>(
                    now,
                    ConcordStreamEnvelope.KIND_WRAP,
                    arrayOf(arrayOf("p", mallory.pubKey)),
                    "not-a-real-seal",
                )
            assertEquals(true, client.publishAndConfirm(addressedWrap, setOf(relayUrl)))
            assertEquals(1, query(Filter(ids = listOf(addressedWrap.id))).size)

            val deletion = mallory.sign(DeletionRequestEvent.build(listOf(addressedWrap), createdAt = now + 1))
            assertEquals(true, client.publishAndConfirm(deletion, setOf(relayUrl)))

            assertEquals(
                0,
                query(Filter(ids = listOf(addressedWrap.id))).size,
                "the p-tag recipient owns a 1059 — which is exactly why Concord's p-tag must stay random",
            )
        }
}
