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

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase-4 ancillary endpoint smoke tests for the moq-auth sidecar. Cheap
 * sanity checks for the routes we don't drive through the production
 * client, so a regression in the sidecar's deployment (a port flip, a
 * missing route, a busted CORS config) surfaces immediately rather than
 * silently when the next /auth flow fires.
 *
 * Coverage:
 *   - `GET /health` returns `{"status":"ok"}` (used by Docker health
 *     probes; we read it for a test-time port-readiness check too)
 *   - `GET /.well-known/jwks.json` returns a JWK set with at least one
 *     ES256 verification key — moq-relay polls this every 30 s
 *     (`MOQ_AUTH_REFRESH_INTERVAL`) so a malformed JWKS would brick
 *     authentication
 *   - Unknown route returns 404 (locks down the surface)
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsAuthEndpointsInteropTest {
    @Test
    fun health_endpoint_returns_status_ok() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            http.newCall(Request.Builder().url("${harness.authBaseUrl}/health").build()).execute().use { resp ->
                val body = resp.body.string()
                assertEquals(200, resp.code, "/health should be 200, got ${resp.code}: $body")
                assertTrue("\"status\"" in body && "\"ok\"" in body, "expected {\"status\":\"ok\"} JSON, got: $body")
            }
        }

    @Test
    fun jwks_endpoint_returns_at_least_one_es256_signing_key() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            http.newCall(Request.Builder().url("${harness.authBaseUrl}/.well-known/jwks.json").build()).execute().use { resp ->
                val body = resp.body.string()
                assertEquals(200, resp.code, "/.well-known/jwks.json should be 200, got ${resp.code}: $body")
                // Don't pull a JWK parser into tests — substring sniffing is
                // enough to assert the shape moq-relay actually consumes.
                assertTrue("\"keys\"" in body, "JWKS body must contain a `keys` array, got: $body")
                assertTrue(
                    "\"kty\"" in body,
                    "JWKS entry must have a `kty` (key type) field per RFC 7517, got: $body",
                )
                assertTrue(
                    "\"alg\":\"ES256\"" in body || "\"crv\":\"P-256\"" in body,
                    "expected ES256 / P-256 signing key (matches moq-rs claim verification), got: $body",
                )
            }
        }

    @Test
    fun unknown_route_returns_404() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            http.newCall(Request.Builder().url("${harness.authBaseUrl}/does-not-exist").build()).execute().use { resp ->
                assertEquals(404, resp.code, "unknown route should be 404")
            }
        }

    companion object {
        private val http = OkHttpClient()
        private var harnessOrNull: NostrNestsHarness? = null

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
