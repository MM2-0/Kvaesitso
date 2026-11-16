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
package com.vitorpamplona.geode.ratelimit

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Amethyst's relay client (quartz's [NostrClient], on real websockets) against geode's per-client
 * rate limits: whatever the client throws at a limiting relay — a burst of publishes, a burst of
 * subscriptions, a paged download, a reconnect in the middle of a back-off — everything lands,
 * and nothing of a refused type is sent to the relay while a back-off it stated is running.
 */
class RateLimitClientTest {
    private lateinit var relay: RateLimitedGeode
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val http = OkHttpClient.Builder().build()
    private val client = NostrClient(BasicOkHttpWebSocket.Builder { http }, scope)
    private val notes = Notes()
    private val wire = WireLog().also { client.addConnectionListener(it) }

    @AfterTest
    fun tearDown() {
        client.close()
        scope.cancel()
        relay.close()
    }

    /**
     * Every EVENT/REQ the client sent and every back-off the relay stated, with the time each was
     * seen. A send of a type after a refusal of that type and before its back-off ran out is a
     * violation. A send in the first [RACE_MS] after a refusal is not: it may have passed the
     * client's gate while the refusal was still on the wire.
     */
    private class WireLog : RelayConnectionListener {
        class Sent(
            val type: String,
            val atMs: Long,
        )

        class BackOff(
            val type: String,
            val fromMs: Long,
            val untilMs: Long,
        )

        val sent = ConcurrentLinkedQueue<Sent>()
        val backOffs = ConcurrentLinkedQueue<BackOff>()
        val accepted = ConcurrentLinkedQueue<String>()
        private val start = System.currentTimeMillis()
        private val events = ConcurrentLinkedQueue<String>()

        private fun note(line: String) = events.add("${System.currentTimeMillis() - start}ms $line")

        /** What happened, in order, for a failure message. */
        fun timeline() = events.joinToString("\n", prefix = "\n")

        override fun onSent(
            relay: IRelayClient,
            cmdStr: String,
            cmd: Command,
            success: Boolean,
        ) {
            val type =
                when (cmd) {
                    is EventCmd -> "EVENT"
                    is ReqCmd -> "REQ"
                    else -> return
                }
            if (success) sent.add(Sent(type, System.currentTimeMillis()))
            note("-> ${cmdStr.take(90)} sent=$success")
        }

        override suspend fun onIncomingMessage(
            relay: IRelayClient,
            msgStr: String,
            msg: Message,
        ) {
            val now = System.currentTimeMillis()
            if (msg is OkMessage || msg is ClosedMessage || msg.label() == "EOSE") note("<- ${msgStr.take(120)}")
            when (msg) {
                is OkMessage -> {
                    if (msg.success) accepted.add(msg.eventId)
                    msg.retryAfterMs?.let { backOffs.add(BackOff("EVENT", now, now + it)) }
                }

                is ClosedMessage -> {
                    msg.retryAfterMs?.let { backOffs.add(BackOff("REQ", now, now + it)) }
                }

                else -> {}
            }
        }

        fun sends(type: String) = sent.count { it.type == type }

        fun refusals(type: String) = backOffs.count { it.type == type }

        fun assertNothingSentInsideABackOff() {
            val violations =
                backOffs.flatMap { window ->
                    sent.filter { it.type == window.type && it.atMs > window.fromMs + RACE_MS && it.atMs < window.untilMs - SLACK_MS }.map {
                        "${window.type} sent ${it.atMs - window.fromMs}ms into a ${window.untilMs - window.fromMs}ms back-off"
                    }
                }
            assertTrue(violations.isEmpty(), violations.joinToString("\n") + timeline())
        }

        companion object {
            const val RACE_MS = 50L
            const val SLACK_MS = 5L
        }
    }

    private fun subscribe(
        n: Int,
        prefix: String = "s",
    ): List<CompletableDeferred<Unit>> =
        (0 until n).map { i ->
            val done = CompletableDeferred<Unit>()
            client.subscribe(
                "$prefix$i",
                mapOf(relay.url to listOf(Filter(kinds = listOf(1), limit = 1))),
                object : SubscriptionListener {
                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        done.complete(Unit)
                    }
                },
            )
            done
        }

    private suspend fun awaitConnected() =
        withTimeout(5_000) {
            while (relay.url !in client.connectedRelaysFlow().value) delay(10)
        }

    @Test
    fun aBurstOfPublishesAllLandAndNoneIsSentInsideABackOff() =
        runBlocking {
            relay = RateLimitedGeode(rateLimitsToml(eventsPerMinute = 120, eventBurst = 2))
            val events = List(6) { notes.next() }

            val results =
                events
                    .map { event -> async { client.publishAndCollectResults(event, setOf(relay.url), timeoutInSeconds = 20).getValue(relay.url) } }
                    .awaitAll()

            results.forEachIndexed { i, result -> assertTrue(result.accepted, "event #$i: ${result.message}" + wire.timeline()) }
            assertTrue(wire.refusals("EVENT") > 0, "the burst never met the limit, so this proved nothing")
            wire.assertNothingSentInsideABackOff()
            // Each event goes out at most three times: the burst, once early on the accept before
            // it (a token bucket refuses that one), and once paced at the stated back-off. Not
            // once per release.
            assertTrue(wire.sends("EVENT") <= 3 * events.size, "${wire.sends("EVENT")} sends for ${events.size} events" + wire.timeline())
        }

    @Test
    fun aBurstOfSubscriptionsAllReachEoseAndNoneIsSentInsideABackOff() =
        runBlocking {
            relay = RateLimitedGeode(rateLimitsToml(reqsPerMinute = 120, reqBurst = 2))
            val eoses = subscribe(6)

            withTimeout(20_000) { eoses.awaitAll() }

            assertTrue(wire.refusals("REQ") > 0, "the burst never met the limit, so this proved nothing")
            wire.assertNothingSentInsideABackOff()
            // As with events: the burst, once early on the EOSE before it, once paced.
            assertTrue(wire.sends("REQ") <= 18, "${wire.sends("REQ")} REQs for 6 subscriptions" + wire.timeline())
        }

    @Test
    fun aReconnectInsideAReqBackOffWaitsItOut() =
        runBlocking {
            // One REQ every two seconds.
            relay = RateLimitedGeode(rateLimitsToml(reqsPerMinute = 30, reqBurst = 1))
            val (first) = subscribe(1, "first")
            withTimeout(5_000) { first.await() }
            val (second) = subscribe(1, "second")
            withTimeout(5_000) { while (wire.refusals("REQ") == 0) delay(10) }

            // The socket drops and comes back while the relay's back-off runs: the reconnect
            // replays every subscription, and must hold them until the back-off passes.
            // Well inside the back-off, past the window where a send may still be racing the refusal.
            delay(300)
            client.disconnect()
            client.connect()
            awaitConnected()

            withTimeout(15_000) { second.await() }
            wire.assertNothingSentInsideABackOff()
        }

    @Test
    fun aReconnectInsideAnEventBackOffWaitsItOut() =
        runBlocking {
            relay = RateLimitedGeode(rateLimitsToml(eventsPerMinute = 30, eventBurst = 1))
            val first = notes.next()
            val second = notes.next()
            client.publish(first, setOf(relay.url))
            client.publish(second, setOf(relay.url))
            withTimeout(5_000) { while (wire.refusals("EVENT") == 0) delay(10) }

            // Well inside the back-off, past the window where a send may still be racing the refusal.
            delay(300)
            client.disconnect()
            client.connect()
            awaitConnected()

            withTimeout(15_000) { while (first.id !in wire.accepted || second.id !in wire.accepted) delay(20) }
            wire.assertNothingSentInsideABackOff()
        }

    @Test
    fun aClientUnderTheRateIsNeverRefused() =
        runBlocking {
            relay = RateLimitedGeode(rateLimitsToml(eventsPerMinute = 600, eventBurst = 1))
            repeat(8) {
                val event = notes.next()
                assertTrue(client.publishAndCollectResults(event, setOf(relay.url)).getValue(relay.url).accepted)
                delay(150)
            }
            assertEquals(0, wire.refusals("EVENT"))
        }

    @Test
    fun aPagedDownloadWalksThroughTheRateLimit() =
        runBlocking {
            relay = RateLimitedGeode(rateLimitsToml(reqsPerMinute = 120, reqBurst = 1))
            val stored = (1..25).map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = 1_700_000_000L + it) }
            relay.engine.preload(stored)

            val seen = mutableSetOf<String>()
            val result =
                client.fetchAllPages(relay.url, listOf(Filter(kinds = listOf(1))), pageSize = 10) { event: Event ->
                    seen += event.id
                }

            assertEquals(PagedFetchResult.End.DRAINED, result.end, result.message + wire.timeline())
            assertEquals(stored.map { it.id }.toSet(), seen)
            assertTrue(wire.refusals("REQ") > 0, "the walk never met the limit, so this proved nothing")
            wire.assertNothingSentInsideABackOff()
        }
}
