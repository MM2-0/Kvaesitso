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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * NIP-FE (commands over HTTP) under the same per-client limits as the websocket: a refused
 * command answers 429 with the refusing frame — its NIP-01 back-off included — and a
 * `Retry-After` that is that back-off in whole seconds, rounded up.
 */
class RateLimitHttpTest {
    private val http = OkHttpClient.Builder().build()
    private val notes = Notes()
    private lateinit var relay: RateLimitedGeode

    @AfterTest
    fun tearDown() = relay.close()

    private class Answer(
        val status: Int,
        val retryAfter: String?,
        val body: String,
    )

    private fun post(body: String): Answer =
        http
            .newCall(
                Request
                    .Builder()
                    .url(relay.httpUrl)
                    .post(body.toRequestBody("text/plain".toMediaType()))
                    .build(),
            ).execute()
            .use { Answer(it.code, it.header("Retry-After"), it.body.string().trim()) }

    @Test
    fun aRefusedEventIs429WithTheBackOffInTheFrameAndTheHeader() {
        relay = RateLimitedGeode(rateLimitsToml(eventsPerMinute = 6, eventBurst = 1))

        assertEquals(200, post(notes.eventFrame()).status)

        val refused = post(notes.eventFrame())
        assertEquals(429, refused.status)
        val ok = Message.fromJson(refused.body) as OkMessage
        assertEquals(false, ok.success)
        val backOff = assertNotNull(ok.retryAfterMs, refused.body)
        assertTrue(backOff in 9_000..10_000, "six a minute is one every ten seconds: $backOff")
        assertEquals(((backOff + 999) / 1000).toString(), refused.retryAfter)
    }

    @Test
    fun aRefusedReqIs429WithAClosedThatCarriesTheBackOff() {
        relay = RateLimitedGeode(rateLimitsToml(reqsPerMinute = 6, reqBurst = 1))
        assertEquals(200, post("""["REQ","a",{"kinds":[1]}]""").status)

        val refused = post("""["REQ","b",{"kinds":[1]}]""")
        assertEquals(429, refused.status)
        val closed = Message.fromJson(refused.body) as ClosedMessage
        assertEquals("b", closed.subId)
        val backOff = assertNotNull(closed.retryAfterMs)
        assertEquals(((backOff + 999) / 1000).toString(), refused.retryAfter)
    }

    @Test
    fun httpAndWebsocketCommandsCountAgainstTheSameClient() {
        relay = RateLimitedGeode(rateLimitsToml(eventsPerMinute = 6, eventBurst = 2))
        RawSocket(relay.server.url, http).use { ws ->
            repeat(2) {
                ws.send(notes.eventFrame())
                assertTrue((Message.fromJson(ws.next("OK")) as OkMessage).success)
            }
        }
        assertEquals(429, post(notes.eventFrame()).status, "the websocket already spent this client's allowance")
    }
}
