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
package com.vitorpamplona.nestsclient.interop

import com.vitorpamplona.nestsclient.NestsAuth
import com.vitorpamplona.nestsclient.NestsException
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.nestsAuthUrl
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Phase-4 negative-auth interop tests. Locks in the wire-level rejection
 * paths exposed by the moq-auth sidecar so a regression in our request
 * shape (header format, NIP-98 signing, body schema, namespace regex)
 * surfaces as a fast unit-of-work failure rather than a confused
 * production stack trace.
 *
 * Mirrors the audit findings in moq-auth `index.ts` — rejection responses
 * are JSON `{"error": "..."}` with HTTP 400 / 401 / 429 depending on the
 * failure mode. We assert HTTP status only (the human-readable error text
 * isn't part of the contract), but include the message in the failure
 * message for debuggability.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsAuthFailureInteropTest {
    @Test
    fun missing_authorization_header_is_rejected_401() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val url = nestsAuthUrl(harness.authBaseUrl)
            val body = """{"namespace":"${validNamespace()}","publish":false}"""
            assertStatus(
                expected = 401,
                description = "missing Authorization header",
                request =
                    Request
                        .Builder()
                        .url(url)
                        .post(body.toRequestBody(JSON))
                        .build(),
            )
        }

    @Test
    fun authorization_header_with_wrong_scheme_is_rejected_401() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val url = nestsAuthUrl(harness.authBaseUrl)
            val body = """{"namespace":"${validNamespace()}","publish":false}"""
            assertStatus(
                expected = 401,
                description = "Authorization header with non-Nostr scheme",
                request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Authorization", "Bearer not-a-nostr-event")
                        .post(body.toRequestBody(JSON))
                        .build(),
            )
        }

    @Test
    fun nip98_event_signed_for_a_different_url_is_rejected_401() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val signer = NostrSignerInternal(KeyPair())
            val realUrl = nestsAuthUrl(harness.authBaseUrl)
            val body = """{"namespace":"${validNamespace()}","publish":false}"""
            val bodyBytes = body.encodeToByteArray()

            // NIP-98 binds the signed event to a `u` tag that the server
            // re-checks against the request URL. Sign for a *different*
            // URL so the host/path mismatch fires.
            val authHeader =
                NestsAuth.header(
                    signer = signer,
                    url = "https://other.example.test/auth",
                    method = "POST",
                    payload = bodyBytes,
                )

            assertStatus(
                expected = 401,
                description = "NIP-98 event signed for the wrong URL",
                request =
                    Request
                        .Builder()
                        .url(realUrl)
                        .header("Authorization", authHeader)
                        .post(body.toRequestBody(JSON))
                        .build(),
            )
        }

    @Test
    fun malformed_namespace_is_rejected_400() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            // Per moq-auth's regex /^nests\/\d+:[0-9a-f]{64}:[a-zA-Z0-9._-]+$/
            // — uppercase pubkey hex doesn't match the `[0-9a-f]` class.
            val signer = NostrSignerInternal(KeyPair())
            val client = OkHttpNestsClient(httpClient = { http })
            val room =
                NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = "A".repeat(64),
                    roomId = "regex-fuzz",
                )
            try {
                client.mintToken(room = room, publish = false, signer = signer)
                fail("expected NestsException for invalid namespace, none thrown")
            } catch (e: NestsException) {
                assertEquals(
                    400,
                    e.status,
                    "expected 400 for namespace regex mismatch — got status=${e.status} message='${e.message}'",
                )
            }
        }

    @Test
    fun publish_true_grants_a_token_for_any_caller() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            // The sidecar does NOT gate publish-vs-audience by NIP-53
            // hostlist; any user requesting `publish: true` gets a JWT
            // with `put: [<their-pubkey>]`. Lock that in so a future
            // hostlist gate doesn't sneak in unnoticed.
            val signer = NostrSignerInternal(KeyPair())
            val client = OkHttpNestsClient(httpClient = { http })
            val room =
                NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = signer.pubKey,
                    roomId = "publish-grant-${System.currentTimeMillis()}",
                )
            val token = client.mintToken(room = room, publish = true, signer = signer)
            assertTrue(token.count { it == '.' } == 2, "expected JWT with 3 segments, got: $token")
        }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val http = OkHttpClient()

        private var harnessOrNull: NostrNestsHarness? = null

        private fun validNamespace(): String = "nests/30312:" + "0".repeat(64) + ":sample-room"

        /**
         * Issue [request] and assert the response status equals [expected].
         * Reads the body once for the failure message — okhttp's `Response`
         * body is single-shot, so we have to materialise the string before
         * the `.use {}` block exits.
         */
        private fun assertStatus(
            expected: Int,
            description: String,
            request: Request,
        ) {
            http.newCall(request).execute().use { response ->
                val code = response.code
                val bodyText = response.body.string()
                assertEquals(
                    expected,
                    code,
                    "$description — server returned $code with body: $bodyText",
                )
            }
        }

        @BeforeClass
        @JvmStatic
        fun setUpHarness() {
            if (NostrNestsHarness.isEnabled()) {
                harnessOrNull = NostrNestsHarness.shared()
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownHarness() {
            harnessOrNull = null
        }
    }
}
