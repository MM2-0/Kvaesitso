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

import com.vitorpamplona.geode.server.HttpCommandSettings
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.RawEvent
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipFERelayOverHttp.HttpRelayClient
import com.vitorpamplona.quartz.nipFERelayOverHttp.OkHttpRelayTransport
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.GzipSource
import okio.buffer
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * NIP-FE end to end: quartz's [HttpRelayClient] and raw OkHttp requests against a real [KtorRelay],
 * covering the answer shape, the status table, the headers each status carries, NIP-98 sign-in
 * on an AUTH-gated relay, and sharing the relay URL with NIP-86.
 */
class NipFEHttpTest {
    private val http = OkHttpClient.Builder().build()
    private val alice = NostrSignerInternal(KeyPair())
    private val running = mutableListOf<Pair<KtorRelay, RelayEngine>>()

    @AfterTest
    fun teardown() {
        running.forEach { (server, relay) ->
            server.stop(0, 1_000)
            relay.close()
        }
    }

    /** A relay whose advertised URL is the one it listens on, so a NIP-98 `u` names a reachable endpoint. */
    private fun start(
        path: String = "/",
        settings: HttpCommandSettings? = HttpCommandSettings(),
        policy: ((NormalizedRelayUrl) -> IRelayPolicy)? = null,
        store: (IEventStore) -> IEventStore = { it },
    ): NormalizedRelayUrl {
        val port = ServerSocket(0).use { it.localPort }
        val url = "ws://127.0.0.1:$port$path".normalizeRelayUrl()
        val events = store(EventStore(dbName = null, relay = url, indexStrategy = RelayIndexingStrategy))
        val relay = RelayEngine(url, events, policyBuilder = { policy?.invoke(url) ?: EmptyPolicy })
        val server = KtorRelay(relay, host = "127.0.0.1", port = port, path = path, httpCommands = settings).start()
        running += server to relay
        return url
    }

    private fun client(signer: NostrSignerInternal? = null) = HttpRelayClient(OkHttpRelayTransport { http }, signer)

    private suspend fun note(text: String): Event = alice.sign(TextNoteEvent.build(text))

    private fun post(
        url: String,
        body: String,
        authorization: String? = null,
        contentType: String = "text/plain",
    ): Response =
        http
            .newCall(
                Request
                    .Builder()
                    .url(url)
                    .post(body.toRequestBody(contentType.toMediaType()))
                    .apply { authorization?.let { header("Authorization", it) } }
                    .build(),
            ).execute()

    private fun Response.lines() = body.string().lines().filter { it.isNotEmpty() }

    @Test
    fun aReqStreamsWhatWasPublishedAndEndsOnEose() =
        runBlocking {
            val relay = start()
            val notes = listOf(note("one"), note("two"), note("three"))
            notes.forEach { assertTrue(assertIs<OkMessage>(client().publish(relay, it).last).success) }

            val got = mutableListOf<Event>()
            val answer = client().req(relay, listOf(Filter(kinds = listOf(TextNoteEvent.KIND))), onEvent = got::add)
            assertEquals(200, answer.status)
            assertTrue(answer.complete)
            assertIs<EoseMessage>(answer.last)
            assertEquals(notes.map { it.id }.toSet(), got.map { it.id }.toSet())
        }

    @Test
    fun theWireIsNdjsonInTheSocketsOwnFrames() =
        runBlocking {
            val relay = start()
            val n = note("hello")
            client().publish(relay, n)
            post(relay.toHttp(), """["REQ","q",{"ids":["${n.id}"]}]""").use { response ->
                assertEquals(200, response.code)
                assertTrue(response.header("Content-Type")!!.startsWith("application/x-ndjson"))
                assertEquals("no", response.header("X-Accel-Buffering"))
                assertEquals("*", response.header("Access-Control-Allow-Origin"))
                val lines = response.lines()
                assertEquals(listOf("""["EVENT","q",${n.toJson()}]""", """["EOSE","q"]"""), lines)
            }
        }

    @Test
    fun aCountIsOneCountLine() =
        runBlocking {
            val relay = start()
            client().publish(relay, note("a"))
            client().publish(relay, note("b"))
            val answer = client().count(relay, listOf(Filter(kinds = listOf(TextNoteEvent.KIND))))
            assertTrue(answer.complete)
            assertEquals(2, assertIs<CountMessage>(answer.last).result.count)
        }

    @Test
    fun aDuplicateIs200AndAForgeryIs400() =
        runBlocking {
            val relay = start(policy = { VerifyPolicy })
            val n = note("once")
            assertEquals(200, client().publish(relay, n).status)
            assertEquals(200, client().publish(relay, n).status)

            val forged = n.toJson().replace("\"once\"", "\"twice\"")
            post(relay.toHttp(), """["EVENT",$forged]""").use { response ->
                assertEquals(400, response.code)
                val line = response.lines().single()
                assertTrue(line.startsWith("""["OK","${n.id}",false,"invalid:"""), line)
            }
        }

    @Test
    fun aBodyThatIsNotACommandIs400AndOneOverTheCapIs413() {
        val relay = start(settings = HttpCommandSettings(maxBodyBytes = 64))
        for (body in listOf("hello", """{"kinds":[1]}""", """["CLOSE","q"]""")) {
            post(relay.toHttp(), body).use { response ->
                assertEquals(400, response.code, body)
                assertTrue(response.lines().single().startsWith("""["NOTICE","invalid:"""))
            }
        }
        post(relay.toHttp(), """["REQ","q",{"authors":["${"a".repeat(64)}"]}]""").use { response ->
            assertEquals(413, response.code)
            assertTrue(response.lines().single().startsWith("""["NOTICE","invalid:"""))
        }
    }

    @Test
    fun aRateLimitedRefusalIs429WithRetryAfter() {
        val relay =
            start(
                settings = HttpCommandSettings(retryAfterSeconds = 7),
                policy = {
                    object : PassThroughPolicy() {
                        override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> = PolicyResult.Rejected("rate-limited: slow down")
                    }
                },
            )
        post(relay.toHttp(), """["REQ","q",{}]""").use { response ->
            assertEquals(429, response.code)
            assertEquals("7", response.header("Retry-After"))
            assertEquals("""["CLOSED","q","rate-limited: slow down"]""", response.lines().single())
        }
    }

    @Test
    fun aPreflightLetsAnyOriginPostWithAuthorization() {
        val relay = start()
        val preflight =
            Request
                .Builder()
                .url(relay.toHttp())
                .method("OPTIONS", null)
                .header("Origin", "https://app.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization")
                .build()
        http.newCall(preflight).execute().use { response ->
            assertEquals(204, response.code)
            assertEquals("*", response.header("Access-Control-Allow-Origin"))
            assertTrue(response.header("Access-Control-Allow-Methods")!!.contains("POST"))
            assertTrue(response.header("Access-Control-Allow-Headers")!!.contains("Authorization"))
        }
    }

    @Test
    fun anAuthGatedRelayAnswers401UntilANip98TokenSignsTheRequestIn() =
        runBlocking {
            val relay = start(policy = ::SignInPolicy)
            post(relay.toHttp(), """["REQ","q",{}]""").use { response ->
                assertEquals(401, response.code)
                assertEquals("Nostr", response.header("WWW-Authenticate"))
                assertTrue(response.lines().single().startsWith("""["CLOSED","q","auth-required:"""))
            }

            val unsigned = client().req(relay, listOf(Filter(kinds = listOf(1)))) {}
            assertEquals(401, unsigned.status)
            assertTrue(unsigned.complete)

            val n = note("signed in")
            val published = client(alice).publish(relay, n)
            assertEquals(200, published.status)
            assertTrue(assertIs<OkMessage>(published.last).success)

            val got = mutableListOf<Event>()
            val read = HttpRelayClient(OkHttpRelayTransport { http }, alice, signFirst = true).req(relay, listOf(Filter(ids = listOf(n.id))), onEvent = got::add)
            assertEquals(200, read.status)
            assertTrue(read.complete)
            assertEquals(listOf(n.id), got.map { it.id })
        }

    @Test
    fun aTokenForAnotherBodyDoesNotSignIn() =
        runBlocking {
            val relay = start(policy = ::SignInPolicy)
            val url = relay.toHttp()
            val signed = """["REQ","q",{"kinds":[1]}]"""
            val token = alice.sign(HTTPAuthorizationEvent.build(url, "POST", signed.encodeToByteArray())).toAuthToken()
            post(url, """["REQ","q",{"kinds":[0]}]""", token).use { response ->
                assertEquals(401, response.code)
                assertTrue(response.lines().single().contains("payload"))
            }
            post(url, signed, token).use { assertEquals(200, it.code) }
            post(url, signed, token).use { assertEquals(200, it.code, "good again for the same body within its window") }
        }

    @Test
    fun aTokenSignedAtTheOnionAddressVerifies() =
        runBlocking {
            val onion = "ws://2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion/".normalizeRelayUrl()
            val relay = start(policy = ::SignInPolicy, settings = HttpCommandSettings(alternateUrls = listOf(onion)))
            val body = """["REQ","q",{"kinds":[1]}]"""
            val token = alice.sign(HTTPAuthorizationEvent.build(onion.toHttp(), "POST", body.encodeToByteArray())).toAuthToken()
            post(relay.toHttp(), body, token).use { assertEquals(200, it.code) }
        }

    @Test
    fun commandsGoToTheRelayUrlPathIncluded() =
        runBlocking {
            val relay = start(path = "/nostr")
            assertTrue(relay.toHttp().trimEnd('/').endsWith("/nostr"))
            assertTrue(client().req(relay, listOf(Filter(kinds = listOf(1)))) {}.complete)
            post(relay.toHttp().replace("/nostr", ""), """["REQ","q",{}]""").use { assertEquals(404, it.code) }
        }

    @Test
    fun nip86CallsKeepTheRelayUrlByTheirContentType() {
        val relay = start()
        post(relay.toHttp(), """{"method":"supportedmethods","params":[]}""", contentType = "application/nostr+json+rpc").use { response ->
            assertEquals(401, response.code, "NIP-86 asks for its own NIP-98 token")
            assertFalse(response.header("Content-Type")!!.startsWith("application/x-ndjson"))
        }
    }

    @Test
    fun turnedOffEveryPostIsNip86Again() {
        val relay = start(settings = null)
        post(relay.toHttp(), """["REQ","q",{}]""").use { response ->
            assertFalse(response.header("Content-Type")!!.startsWith("application/x-ndjson"))
            assertEquals(401, response.code)
        }
    }

    /** Answers a filter naming [HELD_KIND] with what is stored, then holds its EOSE until [release]. */
    private fun holdingEose(release: CompletableDeferred<Unit>): (IEventStore) -> IEventStore =
        { real ->
            object : IEventStore by real {
                override suspend fun rawQuery(
                    filters: List<Filter>,
                    onEach: (RawEvent) -> Unit,
                ) {
                    real.rawQuery(filters, onEach)
                    if (filters.any { it.kinds?.contains(HELD_KIND) == true }) release.await()
                }
            }
        }

    @Test
    fun aGzippedAnswerStillArrivesLineByLine() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val relay = start(store = holdingEose(release))
            val held = alice.sign<Event>(TimeUtils.now(), HELD_KIND, emptyArray(), "held")
            client().publish(relay, held)

            // Asking for gzip by hand turns off OkHttp's own inflating, so the body is read as sent.
            val patient = http.newBuilder().readTimeout(10, TimeUnit.SECONDS).build()
            val request =
                Request
                    .Builder()
                    .url(relay.toHttp())
                    .post("""["REQ","q",{"kinds":[$HELD_KIND]}]""".toRequestBody("text/plain".toMediaType()))
                    .header("Accept-Encoding", "gzip")
                    .build()
            patient.newCall(request).execute().use { response ->
                assertEquals("gzip", response.header("Content-Encoding"))
                val lines = GzipSource(response.body.source()).buffer()
                // The store has not answered EOSE: this line can only be here if the gzip was flushed.
                assertEquals("""["EVENT","q",${held.toJson()}]""", lines.readUtf8Line())
                assertFalse(release.isCompleted)
                release.complete(Unit)
                assertEquals("""["EOSE","q"]""", lines.readUtf8Line())
                assertEquals(null, lines.readUtf8Line())
            }
        }

    @Test
    fun theClientReadsAGzippedAnswerAsItStreams() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val relay = start(store = holdingEose(release))
            val held = alice.sign<Event>(TimeUtils.now(), HELD_KIND, emptyArray(), "held")
            client().publish(relay, held)

            val first = CompletableDeferred<Event>()
            val answer = async(Dispatchers.IO) { client().req(relay, listOf(Filter(kinds = listOf(HELD_KIND))), onEvent = { first.complete(it) }) }
            assertEquals(held.id, withTimeout(10.seconds) { first.await() }.id, "the event arrives before EOSE is written")
            release.complete(Unit)
            assertTrue(answer.await().complete)
        }

    @Test
    fun withoutGzipOrWithItOffTheBodyIsPlain() {
        for ((settings, accept) in listOf(HttpCommandSettings() to "identity", HttpCommandSettings(compress = false) to "gzip")) {
            val relay = start(settings = settings)
            val request =
                Request
                    .Builder()
                    .url(relay.toHttp())
                    .post("""["REQ","q",{"kinds":[1]}]""".toRequestBody("text/plain".toMediaType()))
                    .header("Accept-Encoding", accept)
                    .build()
            http.newCall(request).execute().use { response ->
                assertEquals(null, response.header("Content-Encoding"), accept)
                assertEquals(listOf("""["EOSE","q"]"""), response.lines())
            }
        }
    }

    /** Writes [request] on a plain socket to the relay at [relay] and returns the status line of the answer. */
    private fun rawStatus(
        relay: NormalizedRelayUrl,
        request: String,
    ): String =
        Socket(
            "127.0.0.1",
            relay
                .toHttp()
                .substringAfterLast(':')
                .trimEnd('/')
                .toInt(),
        ).use { socket ->
            socket.soTimeout = 10_000
            socket.getOutputStream().write(request.encodeToByteArray())
            socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader().readLine()
        }

    private fun chunked(body: String) = "${body.length.toString(16)}\r\n$body\r\n0\r\n\r\n"

    @Test
    fun aMalformedContentTypeIsStillACommand() {
        val relay = start()
        val body = """["REQ","q",{"kinds":[1]}]"""
        val status = rawStatus(relay, "POST / HTTP/1.1\r\nHost: x\r\nContent-Type: garbage\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
        assertEquals("HTTP/1.1 200 OK", status)
    }

    @Test
    fun aBodyThatNeverFinishesIsDroppedAndFreesItsSlot() {
        val relay = start(settings = HttpCommandSettings(maxPerClient = 1, bodyTimeout = 500.milliseconds))
        val port =
            relay
                .toHttp()
                .substringAfterLast(':')
                .trimEnd('/')
                .toInt()
        Socket("127.0.0.1", port).use { slow ->
            slow.soTimeout = 10_000
            slow.getOutputStream().write("POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 100\r\n\r\n[\"REQ\"".encodeToByteArray())
            slow.getOutputStream().flush()
            assertEquals("HTTP/1.1 408 Request Timeout", slow.getInputStream().bufferedReader().readLine())
        }
        val body = """["REQ","q",{"kinds":[1]}]"""
        assertEquals("HTTP/1.1 200 OK", rawStatus(relay, "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"))
    }

    @Test
    fun aChunkedBodyGrowsItsBufferAndStopsAtTheCap() {
        val relay = start(settings = HttpCommandSettings(maxBodyBytes = 20_000))
        // No Content-Length: the buffer starts small and grows past it.
        val big = """["REQ","q",{"search":"${"x".repeat(10_000)}"}]"""
        val head = "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
        assertEquals("HTTP/1.1 200 OK", rawStatus(relay, head + chunked(big)))
        val over = """["REQ","q",{"search":"${"x".repeat(30_000)}"}]"""
        assertEquals("HTTP/1.1 413 Payload Too Large", rawStatus(relay, head + chunked(over)))
    }

    @Test
    fun nip11AdvertisesFE() {
        val relay = start()
        val request =
            Request
                .Builder()
                .url(relay.url.replace("ws://", "http://"))
                .header("Accept", "application/nostr+json")
                .build()
        http.newCall(request).execute().use { response ->
            assertTrue(response.body.string().contains("\"FE\""))
        }
    }

    @Test
    fun noFirstFrameWithinTheDeadlineIs503WithRetryAfter() =
        runBlocking {
            val relay = start(settings = HttpCommandSettings(deadline = Duration.ZERO, retryAfterSeconds = 3))
            val answer = client().req(relay, listOf(Filter(kinds = listOf(1)))) {}
            assertEquals(503, answer.status)
            assertEquals("3", answer.retryAfter)
            assertTrue(answer.complete)
            assertFalse(answer.last is EoseMessage)
        }

    private companion object {
        /** A regular kind (stored), not a text note, so no other test reads it. */
        const val HELD_KIND = 7_777
    }
}
