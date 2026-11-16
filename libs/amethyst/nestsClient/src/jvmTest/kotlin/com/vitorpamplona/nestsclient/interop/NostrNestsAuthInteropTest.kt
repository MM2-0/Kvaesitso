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

import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Phase-1 interop smoke test. Brings up a real nostrnests stack (auth
 * sidecar + MoQ relay + strfry) via Docker Compose, then drives the
 * production [OkHttpNestsClient] against the real `/auth` endpoint to
 * mint a JWT. Validates the wire format (POST `<base>/auth` with
 * `{namespace, publish}` body + NIP-98 Authorization header, returning
 * `{token}`) end-to-end.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsAuthInteropTest {
    val client = OkHttpNestsClient { OkHttpClient() }

    @Test
    fun production_OkHttpNestsClient_mints_a_jwt_against_real_moq_auth() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val signer = NostrSignerInternal(KeyPair())

            val room =
                NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = signer.pubKey,
                    roomId = "interop-${System.currentTimeMillis()}",
                )

            val token = client.mintToken(room = room, publish = true, signer = signer)

            // moq-auth signs JWS tokens with three base64url-encoded
            // segments separated by dots.
            assertTrue(token.count { it == '.' } == 2, "Expected JWT (3 segments), got: $token")
            assertTrue(token.isNotBlank(), "JWT must be non-empty")
        }

    companion object {
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
