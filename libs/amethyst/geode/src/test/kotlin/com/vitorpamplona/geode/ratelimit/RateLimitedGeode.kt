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

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.config.StaticConfig
import com.vitorpamplona.geode.server.ClientAddresses
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.fail

/**
 * Geode booted the way `Main` boots it, from a TOML config: the `[rate_limits]` section builds the
 * shared limiters, every connection's policy uses them, and `[http]`'s proxy settings decide who a
 * socket counts against. Served by [KtorRelay] on a real loopback port, so clients go through real
 * websockets and real HTTP.
 */
class RateLimitedGeode(
    toml: String,
) : AutoCloseable {
    val config: StaticConfig = StaticConfig.fromToml(toml).also { it.validate() }
    private val rateLimits = config.rate_limits.toRateLimits()

    val engine =
        RelayEngine(
            url = "ws://127.0.0.1:7777/".normalizeRelayUrl(),
            policyBuilder = { rateLimits?.policy() ?: EmptyPolicy },
        )

    val server =
        KtorRelay(
            engine,
            host = "127.0.0.1",
            port = 0,
            httpCommands = config.http.toSettings(),
            clientAddresses = ClientAddresses(config.http.trusted_proxies.toSet(), config.http.client_address_header),
        ).start()

    val url: NormalizedRelayUrl = server.url.normalizeRelayUrl()
    val httpUrl: String = server.url.replace("ws://", "http://")

    override fun close() {
        // No grace: a test's idle HTTP keep-alive connection would otherwise hold the stop for 5 s.
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        engine.close()
    }
}

/** A websocket that keeps every frame it receives, for assertions on the exact wire. */
class RawSocket(
    url: String,
    http: OkHttpClient,
    headers: Map<String, String> = emptyMap(),
) : AutoCloseable {
    private val frames = LinkedBlockingQueue<String>()
    private val opened = CountDownLatch(1)

    private val socket: WebSocket =
        http.newWebSocket(
            Request
                .Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build(),
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) = opened.countDown()

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    frames.add(text)
                }
            },
        )

    init {
        if (!opened.await(5, TimeUnit.SECONDS)) fail("could not open $url")
    }

    fun send(json: String) {
        check(socket.send(json)) { "socket closed" }
    }

    /** The next frame whose label is one of [labels], skipping others (EVENTs, NOTICEs), as raw text. */
    fun next(
        vararg labels: String,
        timeoutMs: Long = 5_000,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) fail("no ${labels.joinToString("/")} within ${timeoutMs}ms")
            val frame = frames.poll(left, TimeUnit.MILLISECONDS) ?: continue
            if (labels.isEmpty() || wire(frame)[0].toString().trim('"') in labels) return frame
        }
    }

    fun <T : Message> nextMessage(
        vararg labels: String,
        timeoutMs: Long = 5_000,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return Message.fromJson(next(*labels, timeoutMs = timeoutMs)) as T
    }

    override fun close() {
        socket.close(1000, null)
    }

    companion object {
        fun wire(frame: String): JsonArray = Json.parseToJsonElement(frame).jsonArray
    }
}

/** Signed kind-1 notes from one throwaway key, each distinct. */
class Notes {
    private val signer = NostrSignerSync(KeyPair())
    private val counter = AtomicInteger()

    fun next(): Event = signer.sign(TextNoteEvent.build("rate limit probe ${counter.incrementAndGet()}"))

    fun eventFrame(event: Event = next()) = """["EVENT",${event.toJson()}]"""
}

fun rateLimitsToml(
    eventsPerMinute: Int = 0,
    eventBurst: Int = 0,
    reqsPerMinute: Int = 0,
    reqBurst: Int = 0,
    extra: String = "",
) = """
    [rate_limits]
    events_per_minute = $eventsPerMinute
    event_burst = $eventBurst
    reqs_per_minute = $reqsPerMinute
    req_burst = $reqBurst
    $extra
    """.trimIndent()
