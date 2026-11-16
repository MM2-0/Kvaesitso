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
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletIncRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletIntentRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The host-agnostic routing brain. Verifies that one raw envelope maps to the right [Outcome] for
 * each host to act on — independent of any transport (Messenger / IPC / in-process).
 */
class NappletRequestRouterTest {
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("07").hexToByteArray()))
    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "demo")
    private val allDeclared = NappletCapability.entries.toSet()

    private class Prompt(
        private val answer: GrantState,
    ) : NappletConsentPrompt {
        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState = answer
    }

    private class FakeRelay : NappletRelayGateway {
        override suspend fun publish(event: Event): List<String> = emptyList()

        override suspend fun query(filters: List<Filter>): List<Event> = emptyList()
    }

    private fun broker(answer: GrantState = GrantState.ALLOW_ONCE) =
        NappletBroker(
            signer,
            NappletPermissionLedger(InMemoryNappletPermissionStore()),
            Prompt(answer),
            relay = FakeRelay(),
        )

    private suspend fun route(
        payload: String,
        answer: GrantState = GrantState.ALLOW_ONCE,
    ) = NappletRequestRouter.route(broker(answer), applet, allDeclared, payload)

    @Test
    fun relayCloseBecomesCloseSubscription() =
        runTest {
            assertEquals(
                NappletRequestRouter.Outcome.CloseSubscription("s1"),
                route("""{"type":"relay.close","subId":"s1"}"""),
            )
            // No subId → nothing to do.
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("""{"type":"relay.close"}"""))
        }

    @Test
    fun resourceCancelIsSilentlyHandled() =
        runTest {
            assertEquals(
                NappletRequestRouter.Outcome.Ignore,
                route("""{"type":"resource.cancel"}"""),
            )
        }

    @Test
    fun incRequestsGoToTheBus() =
        runTest {
            assertEquals(
                NappletRequestRouter.Outcome.Inc(NappletIncRequest.Subscribe("a1", "news")),
                route("""{"type":"inc.subscribe","id":"a1","topic":"news"}"""),
            )
            assertEquals(
                NappletRequestRouter.Outcome.Inc(NappletIncRequest.Unsubscribe("news")),
                route("""{"type":"inc.unsubscribe","topic":"news"}"""),
            )
            assertEquals(
                NappletRequestRouter.Outcome.Inc(NappletIncRequest.Emit("news", """{"text":"hi"}""")),
                route("""{"type":"inc.emit","topic":"news","payload":{"text":"hi"}}"""),
            )
            assertEquals(
                NappletRequestRouter.Outcome.Inc(NappletIncRequest.ChannelOpen("c1", "peer")),
                route("""{"type":"inc.channel.open","id":"c1","target":"peer"}"""),
            )
            // A subscribe without an id still subscribes; there is just no id to answer.
            assertEquals(
                NappletRequestRouter.Outcome.Inc(NappletIncRequest.Subscribe("", "news")),
                route("""{"type":"inc.subscribe","topic":"news"}"""),
            )
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("""{"type":"inc.emit","topic":7}"""))
        }

    @Test
    fun intentRequestsGoToTheDispatcher() =
        runTest {
            val invoke = route("""{"type":"intent.invoke","id":"i1","request":{"archetype":"feed","action":"open","convention":"napplet:feed/open","payload":{"a":"b"}}}""")
            assertIs<NappletRequestRouter.Outcome.Intent>(invoke)
            assertEquals(NappletIntentRequest.Ready, (route("""{"type":"intent.ready"}""") as NappletRequestRouter.Outcome.Intent).request)
            // Without the declaration, an invoke is refused as IntentResult{ok:false}.
            val refused = NappletRequestRouter.routeIntent(emptySet(), """{"type":"intent.invoke","id":"i2","request":{"archetype":"feed","action":"open","convention":"napplet:feed/open"}}""")
            assertIs<NappletRequestRouter.Outcome.Reply>(refused)
            assertTrue(refused.payload.contains("\"ok\":false"))
            assertEquals(NappletRequestRouter.Outcome.Ignore, NappletRequestRouter.routeIntent(emptySet(), """{"type":"intent.ready"}"""))
        }

    @Test
    fun aMalformedIncRequestWithAnIdIsAnswered() =
        runTest {
            val malformed = route("""{"type":"inc.channel.open","id":"r1","target":123}""")
            assertIs<NappletRequestRouter.Outcome.Reply>(malformed)
            assertTrue(malformed.payload.contains("inc.channel.open.result"))
            assertTrue(malformed.payload.contains("\"error\""))
            // An unknown inc type stays silent, like any unknown type.
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("""{"type":"inc.channel.info","id":"r2"}"""))
        }

    @Test
    fun incWithoutTheDeclarationIsRefused() {
        assertEquals(NappletRequestRouter.Outcome.Ignore, NappletRequestRouter.routeInc(emptySet(), """{"type":"inc.emit","topic":"t","payload":1}"""))
        val subscribe = NappletRequestRouter.routeInc(emptySet(), """{"type":"inc.subscribe","id":"a1","topic":"t"}""")
        assertIs<NappletRequestRouter.Outcome.Reply>(subscribe)
        assertTrue(subscribe.payload.contains("\"error\""))
        // Anything else is not inc's business.
        assertEquals(null, NappletRequestRouter.routeInc(emptySet(), """{"type":"relay.query","id":"q"}"""))
    }

    @Test
    fun unknownAndMalformedRequestsAreSilentlyIgnored() =
        runTest {
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("""{"type":"totally.unknown"}"""))
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("not json"))
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("""{"type":"relay.publish"}"""))
        }

    @Test
    fun keyedUnknownIsSilentButMalformedKnownRequestRepliesWithFailure() =
        runTest {
            val unknown = route("""{"type":"totally.unknown","id":"r1"}""")
            assertEquals(NappletRequestRouter.Outcome.Ignore, unknown)

            val malformed = route("""{"type":"relay.publish","id":"r2"}""")
            assertIs<NappletRequestRouter.Outcome.Reply>(malformed)
            assertTrue(malformed.payload.contains("relay.publish.result"))
            assertTrue(malformed.payload.contains("Malformed request."))
        }

    @Test
    fun queryRepliesWithItsResult() =
        runTest {
            val outcome = route("""{"type":"relay.query","filters":[{"kinds":[1]}]}""")
            assertIs<NappletRequestRouter.Outcome.Reply>(outcome)
            assertTrue(outcome.payload.contains("relay.query.result"))
        }

    @Test
    fun malformedResourcePayloadsReturnInvalidRequest() =
        runTest {
            for (payload in listOf(
                """{"type":"resource.bytes","id":"r1"}""",
                """{"type":"resource.bytes","id":"r1","url":null}""",
                """{"type":"resource.bytesMany","id":"r1","urls":[42]}""",
            )) {
                val outcome = assertIs<NappletRequestRouter.Outcome.Reply>(route(payload))
                assertTrue(outcome.payload.contains(".error"))
                assertTrue(outcome.payload.contains("invalid-request"))
            }
        }

    @Test
    fun authorizedSubscribeOpensALiveSubscriptionWithAllFilters() =
        runTest {
            val outcome = route("""{"type":"relay.subscribe","subId":"s1","filters":[{"kinds":[1]},{"authors":["aa"]}]}""")
            assertIs<NappletRequestRouter.Outcome.OpenSubscription>(outcome)
            assertEquals("s1", outcome.subId)
            assertEquals(2, outcome.filters.size)
            assertEquals(listOf(1), outcome.filters[0].kinds)
            assertEquals(listOf("aa"), outcome.filters[1].authors)
        }

    @Test
    fun refusedSubscribePushesAnEoseToCloseIt() =
        runTest {
            val outcome = route("""{"type":"relay.subscribe","subId":"s1","filters":[{"kinds":[1]}]}""", answer = GrantState.DENY)
            assertIs<NappletRequestRouter.Outcome.Push>(outcome)
            assertEquals(1, outcome.payloads.size)
            assertTrue(outcome.payloads.first().contains("relay.eose"))
        }

    @Test
    fun removedIdentityWatchMessagesAreIgnored() =
        runTest {
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("""{"type":"identity.watch"}"""))
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("""{"type":"identity.unwatch"}"""))
        }

    private class Outbox(
        private val events: List<OutboxEvent> = emptyList(),
    ) : NappletOutboxGateway {
        var lastQuery: Triple<List<Filter>, List<String>, List<String>>? = null
        var lastPublish: Triple<Boolean, List<String>, List<String>>? = null

        override suspend fun getEvent(
            eventId: String,
            author: String?,
            relayHints: List<String>,
            timeoutMs: Long?,
        ) = OutboxRead(events)

        override suspend fun query(
            filters: List<Filter>,
            authors: List<String>,
            relayHints: List<String>,
            timeoutMs: Long?,
        ): OutboxRead {
            lastQuery = Triple(filters, authors, relayHints)
            return OutboxRead(events)
        }

        override suspend fun subscriptionRelays(
            filters: List<Filter>,
            authors: List<String>,
            relayHints: List<String>,
        ) = setOf(RelayUrlNormalizer.normalize("wss://author.example.com"))

        override suspend fun publish(
            event: Event,
            toOutbox: Boolean,
            toInboxes: List<String>,
            relays: List<String>,
        ): OutboxDelivery {
            lastPublish = Triple(toOutbox, toInboxes, relays)
            return OutboxDelivery(mapOf(RelayUrlNormalizer.normalize("wss://mine.example.com") to true, RelayUrlNormalizer.normalize("wss://slow.example.com") to false))
        }

        override suspend fun plan(
            authors: List<String>,
            write: Boolean,
        ) = OutboxRelayPlan(listOf("wss://author.example.com/"), OutboxRelayPlan.Source.NIP65, listOf("cc".repeat(32)))
    }

    private suspend fun routeOutbox(
        payload: String,
        outbox: Outbox = Outbox(),
        answer: GrantState = GrantState.ALLOW_ONCE,
    ) = NappletRequestRouter.route(
        NappletBroker(signer, NappletPermissionLedger(InMemoryNappletPermissionStore()), Prompt(answer), outbox = outbox),
        applet,
        allDeclared,
        payload,
    )

    private fun json(payload: String) = Json.parseToJsonElement(payload).jsonObject

    @Test
    fun anOutboxQueryTakesOneFilterAndRepliesWithRelayEventResults() =
        runTest {
            val note = signer.sign<Event>(1, 1, emptyArray(), "hi")
            val outbox = Outbox(listOf(OutboxEvent(note, listOf("wss://author.example.com/"))))
            val outcome = routeOutbox("""{"type":"outbox.query","id":"q1","filters":{"authors":["ab"],"kinds":[1]},"options":{"authors":["cd"],"relays":["wss://hint.example.com"],"timeoutMs":3000}}""", outbox)

            assertEquals(Triple(listOf("ab"), listOf("cd"), listOf("wss://hint.example.com")), outbox.lastQuery?.let { Triple(it.first.single().authors, it.second, it.third) })
            assertIs<NappletRequestRouter.Outcome.Reply>(outcome)
            val reply = json(outcome.payload)
            assertEquals("outbox.query.result", reply["type"]!!.jsonPrimitive.content)
            val result = reply["events"]!!.jsonArray.single().jsonObject
            assertEquals(note.id, result["event"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            assertEquals(
                "wss://author.example.com/",
                result["sidecar"]!!
                    .jsonObject["relayHints"]!!
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun anOutboxPublishReportsEachRelay() =
        runTest {
            val outbox = Outbox()
            val outcome = routeOutbox("""{"type":"outbox.publish","id":"p1","event":{"kind":1111,"content":"gg","tags":[["p","ab"]]},"options":{"toOutbox":false,"toInboxes":["ab"],"relays":["wss://x.example.com"]}}""", outbox)

            assertEquals(Triple(false, listOf("ab"), listOf("wss://x.example.com")), outbox.lastPublish)
            assertIs<NappletRequestRouter.Outcome.Reply>(outcome)
            val reply = json(outcome.payload)
            assertEquals(true, reply["ok"]!!.jsonPrimitive.boolean)
            assertEquals(
                1111,
                reply["event"]!!
                    .jsonObject["kind"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
            assertEquals(reply["event"]!!.jsonObject["id"], reply["eventId"])
            val relays = reply["relays"]!!.jsonObject
            assertEquals(true, relays["wss://mine.example.com/"]!!.jsonPrimitive.boolean)
            assertEquals(false, relays["wss://slow.example.com/"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun aRefusedOutboxRequestStillAnswersInItsOwnShape() =
        runTest {
            val publish = routeOutbox("""{"type":"outbox.publish","id":"p1","event":{"kind":1,"content":"x","tags":[]}}""", answer = GrantState.DENY)
            assertIs<NappletRequestRouter.Outcome.Reply>(publish)
            assertEquals(false, json(publish.payload)["ok"]!!.jsonPrimitive.boolean)
            // NAP-OUTBOX's word for a refused publish.
            assertEquals("publish denied", json(publish.payload)["error"]!!.jsonPrimitive.content)

            val query = routeOutbox("""{"type":"outbox.query","id":"q1","filters":[{"kinds":[1]}]}""", answer = GrantState.DENY)
            assertIs<NappletRequestRouter.Outcome.Reply>(query)
            assertEquals(0, json(query.payload)["events"]!!.jsonArray.size)
            assertEquals("policy denied", json(query.payload)["error"]!!.jsonPrimitive.content)
        }

    @Test
    fun outboxSubscriptionsOpenOnTheRoutedRelaysAndClose() =
        runTest {
            val opened = routeOutbox("""{"type":"outbox.subscribe","id":"s","subId":"o1","filters":[{"authors":["ab"]}]}""")
            assertIs<NappletRequestRouter.Outcome.OpenSubscription>(opened)
            assertEquals("o1", opened.subId)
            assertTrue(opened.outbox)
            assertEquals(setOf(RelayUrlNormalizer.normalize("wss://author.example.com")), opened.relays)

            val refused = routeOutbox("""{"type":"outbox.subscribe","id":"s","subId":"o1","filters":[{"authors":["ab"]}]}""", answer = GrantState.DENY)
            assertIs<NappletRequestRouter.Outcome.Push>(refused)
            assertEquals("outbox.closed", json(refused.payloads.single())["type"]!!.jsonPrimitive.content)

            assertEquals(NappletRequestRouter.Outcome.CloseSubscription("o1"), routeOutbox("""{"type":"outbox.close","id":"c","subId":"o1"}"""))
        }

    @Test
    fun resolveRelaysReturnsThePlan() =
        runTest {
            val outcome = routeOutbox("""{"type":"outbox.resolveRelays","id":"r1","target":{"pubkey":"ab","direction":"read"}}""")
            assertIs<NappletRequestRouter.Outcome.Reply>(outcome)
            val plan = json(outcome.payload)["plan"]!!.jsonObject
            assertEquals("nip65", plan["source"]!!.jsonPrimitive.content)
            assertEquals(
                "wss://author.example.com/",
                plan["relays"]!!
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
            assertEquals(
                "cc".repeat(32),
                plan["missingAuthors"]!!
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
        }
}
