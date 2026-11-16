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
package com.vitorpamplona.geode.admin

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives a real `KtorRelay` over HTTP and proves the NIP-86
 * admin RPC flow works end-to-end: NIP-98 auth, admin allow-list
 * gate, ban mutation, and the resulting policy effect on a follow-up
 * EVENT publish.
 */
class Nip86EndToEndTest {
    private lateinit var relay: RelayEngine
    private lateinit var server: KtorRelay
    private lateinit var scope: CoroutineScope
    private lateinit var nostrClient: NostrClient

    private val httpClient = OkHttpClient.Builder().build()

    private val admin = NostrSignerSync(KeyPair())
    private val outsider = NostrSignerSync(KeyPair())
    private val targetUser = NostrSignerSync(KeyPair())

    @BeforeTest
    fun setup() {
        // KtorRelay derives the NIP-98 admin URL from relay.url (per
        // NIP-86: same URI as the WebSocket, just http(s)://). We must
        // therefore pre-allocate the port and set relay.url to match
        // — placeholder + OS-assigned port would cause the URL the
        // server expects to differ from where it actually listens.
        val freePort = java.net.ServerSocket(0).use { it.localPort }
        val url = "ws://127.0.0.1:$freePort/".normalizeRelayUrl()
        relay = RelayEngine(url = url, adminPubkeys = setOf(admin.pubKey))
        server =
            KtorRelay(
                relay = relay,
                host = "127.0.0.1",
                port = freePort,
            ).start()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val builder = BasicOkHttpWebSocket.Builder { _ -> httpClient }
        nostrClient = NostrClient(builder, scope)
    }

    @AfterTest
    fun teardown() {
        nostrClient.disconnect()
        scope.cancel()
        server.stop(gracePeriodMillis = 200, timeoutMillis = 500)
        relay.close()
    }

    private val httpUrl get() = server.url.replace("ws://", "http://")

    /** Sends a NIP-86 RPC request signed by [signer] and returns the raw HTTP response. */
    private fun rpc(
        request: Nip86Request,
        signer: NostrSignerSync,
    ): okhttp3.Response {
        val body = JsonMapper.toJson(request).encodeToByteArray()
        val authTemplate =
            HTTPAuthorizationEvent.build(url = httpUrl, method = "POST", file = body)
        val authToken = signer.sign(authTemplate).toAuthToken()
        return httpClient
            .newCall(
                Request
                    .Builder()
                    .url(httpUrl)
                    .post(body.toRequestBody("application/nostr+json+rpc".toMediaType()))
                    .header("Authorization", authToken)
                    .build(),
            ).execute()
    }

    @Test
    fun supportedMethodsListsTheServersMethods() {
        rpc(Nip86Request.supportedMethods(), admin).use {
            assertEquals(200, it.code)
            val json = JsonMapper.fromJson<Nip86Response>(it.body.string())
            val arr = json.result as JsonArray
            val names = arr.map { e -> e.jsonPrimitive.content }
            assertTrue(names.contains("supportedmethods"))
            assertTrue(names.contains("banpubkey"))
        }
    }

    @Test
    fun foreignSignerReturns403() {
        rpc(Nip86Request.supportedMethods(), outsider).use {
            assertEquals(403, it.code)
        }
    }

    @Test
    fun missingAuthHeaderReturns401() {
        val body = JsonMapper.toJson(Nip86Request.supportedMethods()).encodeToByteArray()
        httpClient
            .newCall(
                Request
                    .Builder()
                    .url(httpUrl)
                    .post(body.toRequestBody("application/nostr+json+rpc".toMediaType()))
                    .build(),
            ).execute()
            .use {
                assertEquals(401, it.code)
                assertTrue(it.headers["WWW-Authenticate"]?.startsWith("Nostr") == true)
            }
    }

    @Test
    fun banPubkeyBlocksSubsequentEventsFromThatAuthor() =
        runBlocking {
            val relayUrl = server.url.normalizeRelayUrl()

            // Baseline: targetUser can publish.
            val before = nostrClient.publishAndConfirm(targetUser.sign(TextNoteEvent.build("first")), setOf(relayUrl))
            assertEquals(true, before)

            // Admin bans them.
            rpc(Nip86Request.banPubkey(targetUser.pubKey, "spam"), admin).use {
                assertEquals(200, it.code)
                val resp = JsonMapper.fromJson<Nip86Response>(it.body.string())
                assertEquals(true, (resp.result as JsonPrimitive).boolean)
            }

            // Subsequent EVENT from the banned author is rejected.
            val after = nostrClient.publishAndConfirm(targetUser.sign(TextNoteEvent.build("second")), setOf(relayUrl))
            assertEquals(false, after, "BanListPolicy must reject events from banned pubkeys")
        }

    @Test
    fun allowEventLetsOneEventPastABanUntilUnallowed() =
        runBlocking {
            val relayUrl = server.url.normalizeRelayUrl()
            rpc(Nip86Request.banPubkey(targetUser.pubKey, "spam"), admin).use { assertEquals(200, it.code) }

            // Admin approves one specific event from the banned author.
            val approved = targetUser.sign(TextNoteEvent.build("approved"))
            rpc(Nip86Request.allowEvent(approved.id, "reviewed"), admin).use { assertEquals(200, it.code) }
            assertEquals(true, nostrClient.publishAndConfirm(approved, setOf(relayUrl)), "allow-listed event bypasses the pubkey ban")

            // Any other event from that author is still blocked.
            val other = targetUser.sign(TextNoteEvent.build("other"))
            assertEquals(false, nostrClient.publishAndConfirm(other, setOf(relayUrl)))

            rpc(Nip86Request.listAllowedEvents(), admin).use {
                val resp = JsonMapper.fromJson<Nip86Response>(it.body.string())
                val ids = (resp.result as JsonArray).map { e -> (e as JsonObject)["id"]!!.jsonPrimitive.content }
                assertEquals(listOf(approved.id), ids)
            }

            // unallowevent drops the exemption without banning the event.
            rpc(Nip86Request.unallowEvent(approved.id), admin).use { assertEquals(200, it.code) }
            assertTrue(!relay.banStore.isAllowedEvent(approved.id))
            assertTrue(!relay.banStore.isBannedEvent(approved.id))
        }

    @Test
    fun changeRelayNameFlowsToNip11Endpoint() {
        rpc(Nip86Request.changeRelayName("renamed-by-admin"), admin).use {
            assertEquals(200, it.code)
        }

        // Read the NIP-11 endpoint and confirm the new name is live.
        val response =
            httpClient
                .newCall(
                    Request
                        .Builder()
                        .url(httpUrl)
                        .header("Accept", "application/nostr+json")
                        .build(),
                ).execute()
        response.use {
            val info = Nip11RelayInformation.fromJson(it.body.string())
            assertEquals("renamed-by-admin", info.name)
        }
    }

    @Test
    fun adminEndpointRejectsValidTokenAsNotAdminWhenNoPubkeysConfigured() =
        runBlocking {
            // Spin up a *separate* server with no admin pubkeys. The
            // POST route is still mounted (uniform code path); every
            // request just fails the empty allow-list check → 403.
            val freePort = java.net.ServerSocket(0).use { it.localPort }
            val url = "ws://127.0.0.1:$freePort/".normalizeRelayUrl()
            val openRelay = RelayEngine(url = url)
            val openServer =
                KtorRelay(openRelay, host = "127.0.0.1", port = freePort).start()
            try {
                val openHttpUrl = openServer.url.replace("ws://", "http://")
                val body =
                    JsonMapper.toJson(Nip86Request.supportedMethods()).encodeToByteArray()
                val authToken =
                    admin
                        .sign(
                            HTTPAuthorizationEvent.build(url = openHttpUrl, method = "POST", file = body),
                        ).toAuthToken()
                httpClient
                    .newCall(
                        Request
                            .Builder()
                            .url(openHttpUrl)
                            .post(body.toRequestBody("application/nostr+json+rpc".toMediaType()))
                            .header("Authorization", authToken)
                            .build(),
                    ).execute()
                    .use {
                        assertEquals(403, it.code)
                    }
            } finally {
                openServer.stop(gracePeriodMillis = 100, timeoutMillis = 500)
                openRelay.close()
            }
        }
}
