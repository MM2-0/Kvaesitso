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
package com.vitorpamplona.amethyst.commons.wot.onboarding

import com.vitorpamplona.amethyst.commons.wot.onboarding.brainstorm.BrainstormOnboarding
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrainstormOnboardingTest {
    companion object {
        /** api.brainstorm.world/setup/{pubkey}, as answered on 2026-10-08. */
        const val SETUP =
            """[["30382:rank","7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377","wss://scores.brainstorm.world"],""" +
                """["30382:followers","7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377","wss://scores.brainstorm.world"],""" +
                """["30382:reporters","7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377","wss://scores.brainstorm.world"],""" +
                """["30382:muters","7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377","wss://scores.brainstorm.world"],""" +
                """["30382:hops","7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377","wss://scores.brainstorm.world"],""" +
                """["30392","7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377","wss://scores.brainstorm.world"]]"""
    }

    private val signer = NostrSignerInternal(KeyPair())
    private val serviceKey = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"

    /** Answers like brainstorm_server and records what was asked. */
    private class FakeBrainstorm(
        val graperankStatus: Int = 200,
        val calculated: String = "\"2026-10-07T07:13:37\"",
        /** `GET /setup/{pubkey}`'s answer, as api.brainstorm.world gives it; null for a 404. */
        val setup: String? = SETUP,
    ) : TrustProviderHttp {
        val calls = mutableListOf<String>()
        var loginEvent: String? = null
        var authHeader: String? = null

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): TrustProviderHttpResponse {
            calls.add("GET $url")
            return when {
                url.contains("/authChallenge/") -> TrustProviderHttpResponse(200, """{"code":200,"message":null,"data":{"challenge":"abc123"}}""")

                url.contains("/setup/") -> setup?.let { TrustProviderHttpResponse(200, it) } ?: TrustProviderHttpResponse(404, "")

                url.endsWith("/user/history") -> {
                    authHeader = headers["Authorization"]
                    TrustProviderHttpResponse(200, """{"code":200,"data":{"pubkey":"x","ta_pubkey":"7D7FFD720B907FE597A7F454AFE02F2DC1ECA440BAA029E9117B1C3209839377","last_time_calculated_graperank":$calculated}}""")
                }

                else -> TrustProviderHttpResponse(404, "")
            }
        }

        override suspend fun post(
            url: String,
            jsonBody: String?,
            headers: Map<String, String>,
        ): TrustProviderHttpResponse {
            calls.add("POST $url")
            return when {
                url.endsWith("/verify") -> {
                    loginEvent = jsonBody
                    TrustProviderHttpResponse(200, """{"code":200,"data":{"token":"jwt-token"}}""")
                }

                url.endsWith("/user/graperank") -> TrustProviderHttpResponse(graperankStatus, """{"detail":"The last triggered Graperank was too recent"}""")

                else -> TrustProviderHttpResponse(404, "")
            }
        }
    }

    @Test
    fun signsInRequestsScoresAndReturnsTheServiceKey() =
        runBlocking {
            val http = FakeBrainstorm()
            val steps = mutableListOf<TrustProviderOnboardingStep>()
            val registration = BrainstormOnboarding(http, api = "https://api.test").register(signer) { steps.add(it) }

            assertEquals(serviceKey, registration.serviceKey)
            assertEquals("wss://scores.brainstorm.world/", registration.relay.url)
            assertTrue(registration.scoresReady)
            assertEquals(
                listOf(
                    "GET https://api.test/authChallenge/${signer.pubKey}",
                    "POST https://api.test/authChallenge/${signer.pubKey}/verify",
                    "POST https://api.test/user/graperank",
                    "GET https://api.test/user/history",
                    "GET https://api.test/setup/${signer.pubKey}",
                ),
                http.calls,
            )
            // The rows Brainstorm serves, published as it hands them over.
            assertEquals(
                listOf("30382:rank", "30382:followers", "30382:reporters", "30382:muters", "30382:hops", "30392"),
                registration.rows.map { it.name },
            )
            assertTrue(registration.rows.all { it.key == serviceKey && it.relay.url == "wss://scores.brainstorm.world/" })
            assertEquals("Bearer jwt-token", http.authHeader)
            // Sharing the graph runs before the sign-up, in the view model.
            assertEquals(TrustProviderOnboardingStep.entries - TrustProviderOnboardingStep.SHARING_GRAPH, steps.toList())
            assertEquals(setOf("wss://wot.grapevine.network/", "wss://search.brainstorm.world/"), BrainstormOnboarding(http).graphRelays.map { it.url }.toSet())

            // The login is the kind 22242 brainstorm_server checks: author, t tag, challenge tag.
            val event = Json.parseToJsonElement(http.loginEvent!!).jsonObject["signed_event"]!!.jsonObject
            assertEquals(22242, event["kind"]!!.jsonPrimitive.int)
            assertEquals(signer.pubKey, event["pubkey"]!!.jsonPrimitive.content)
            val tags = event["tags"]!!.jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } }
            assertTrue(listOf("t", "brainstorm_login") in tags)
            assertTrue(listOf("challenge", "abc123") in tags)
        }

    @Test
    fun withoutItsRowsTheSiteDefaultsAreUsed() =
        runBlocking {
            for (setup in listOf(null, "not json", """[["30382:followers","$serviceKey","wss://x.com"],["bad"]]""")) {
                val registration = BrainstormOnboarding(FakeBrainstorm(setup = setup), api = "https://api.test").register(signer)
                // What brainstorm.world itself publishes: rank and followers under the service key.
                assertEquals(listOf("30382:rank", "30382:followers"), registration.rows.map { it.name })
                assertEquals(serviceKey, registration.serviceKey)
            }
        }

    @Test
    fun aRecentRunIsNotAnError() =
        runBlocking {
            val http = FakeBrainstorm(graperankStatus = 403, calculated = "null")
            val registration = BrainstormOnboarding(http, api = "https://api.test").register(signer)
            assertEquals(serviceKey, registration.serviceKey)
            assertEquals(false, registration.scoresReady)
            // No scores yet: the assistant's profile is asked for now, so the provider has a name.
            assertTrue("POST https://api.test/user/assistantProfile" in http.calls)
        }

    @Test
    fun aRefusedScoreRunStopsTheSetUp() =
        runBlocking {
            val http = FakeBrainstorm(graperankStatus = 401)
            val error = assertFailsWith<TrustProviderException> { BrainstormOnboarding(http, api = "https://api.test").register(signer) }
            assertEquals(TrustProviderException.Reason.UNEXPECTED_RESPONSE, error.reason)
            assertFalse(http.calls.any { it.endsWith("/user/history") }, "no provider is saved after a refused run")
        }

    @Test
    fun serverErrorsAreReportedAsUnreachable() =
        runBlocking {
            val down =
                object : TrustProviderHttp {
                    override suspend fun get(
                        url: String,
                        headers: Map<String, String>,
                    ) = TrustProviderHttpResponse(503, "")

                    override suspend fun post(
                        url: String,
                        jsonBody: String?,
                        headers: Map<String, String>,
                    ) = TrustProviderHttpResponse(503, "")
                }
            val error = assertFailsWith<TrustProviderException> { BrainstormOnboarding(down).register(signer) }
            assertEquals(TrustProviderException.Reason.UNREACHABLE, error.reason)
        }
}
