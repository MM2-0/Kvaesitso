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
package com.vitorpamplona.amethyst.commons.routstr

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The OkHttp client against a local stand-in for a node (the JDK's own HTTP server, no network),
 * with the requests it sends checked against a live node's openapi.json (routstr-core 0.5.1).
 */
class RoutstrApiTest {
    private class Seen(
        val method: String,
        val path: String,
        val auth: String?,
        val body: String,
    )

    private val seen = ConcurrentLinkedQueue<Seen>()
    private val answers = HashMap<String, Pair<Int, String>>()
    private lateinit var server: HttpServer
    private lateinit var base: String
    private val api = OkHttpRoutstrApi { OkHttpClient() }

    private val openapi = Json.parseToJsonElement(javaClass.getResourceAsStream("/routstr/openapi-0.5.1.json")!!.bufferedReader().readText()).jsonObject

    private fun schema(name: String) = openapi["components"]!!.jsonObject["schemas"]!!.jsonObject[name]!!.jsonObject

    private fun properties(name: String) = schema(name)["properties"]!!.jsonObject.keys

    private fun required(name: String) = (schema(name)["required"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty().toSet()

    /** [body] fits the [name] request schema: every required field, nothing it doesn't declare. */
    private fun assertFits(
        name: String,
        body: String,
    ) {
        val sent = Json.parseToJsonElement(body).jsonObject.keys
        assertTrue(sent.containsAll(required(name)), "$name needs ${required(name)}, sent $sent")
        assertTrue(properties(name).containsAll(sent), "$name declares ${properties(name)}, sent $sent")
    }

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            seen += Seen(exchange.requestMethod, path, exchange.requestHeaders.getFirst("Authorization"), exchange.requestBody.readBytes().decodeToString())
            val (code, body) = answers[path] ?: (404 to """{"detail":"Not Found"}""")
            val bytes = body.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun theOpenApiDeclaresWhatTheClientCalls() {
        val paths = openapi["paths"]!!.jsonObject
        listOf("/v1/info", "/v1/models", "/v1/balance/info", "/v1/balance/create", "/v1/balance/topup", "/v2/lightning/invoice", "/v2/lightning/invoice/{invoice_id}/status", "/v2/lightning/recover")
            .forEach { assertTrue(it in paths, it) }
        // What the client reads is what the node promises to send.
        assertTrue(required("InvoiceCreateResponse").containsAll(setOf("invoice_id", "bolt11", "amount_sats", "expires_at", "payment_hash")))
        assertTrue(properties("InvoiceStatusResponse").containsAll(setOf("status", "api_key", "amount_sats", "paid_at", "expires_at")))
    }

    @Test
    fun lightningCreateTopupStatusAndRecover() =
        runTest {
            answers["/v2/lightning/invoice"] = 200 to """{"invoice_id":"inv_1","bolt11":"lnbc10u1fake","amount_sats":1000,"expires_at":1794480000,"payment_hash":"ab"}"""
            val invoice = api.createInvoice("$base/v1", 1000, RoutstrPurpose.CREATE)
            assertEquals(RoutstrInvoice("inv_1", "lnbc10u1fake", 1000, 1794480000, "ab"), invoice)
            val create = seen.last()
            assertEquals("/v2/lightning/invoice", create.path)
            assertNull(create.auth)
            assertFits("InvoiceCreateRequest", create.body)
            assertEquals(
                "create",
                Json
                    .parseToJsonElement(create.body)
                    .jsonObject["purpose"]!!
                    .jsonPrimitive.content,
            )

            api.createInvoice(base, 500, RoutstrPurpose.TOPUP, apiKey = "sk-topped")
            val topup = seen.last()
            // The key travels in Authorization only: the body's api_key is deprecated.
            assertEquals("Bearer sk-topped", topup.auth)
            assertFits("InvoiceCreateRequest", topup.body)
            assertTrue("api_key" !in Json.parseToJsonElement(topup.body).jsonObject)

            answers["/v2/lightning/invoice/inv_1/status"] = 200 to """{"status":"paid","api_key":"sk-new","amount_sats":1000,"paid_at":1794479000,"created_at":1794478000,"expires_at":1794480000}"""
            val status = api.invoiceStatus(base, "inv_1")
            assertTrue(status.isPaid)
            assertEquals("sk-new", status.apiKey)

            answers["/v2/lightning/recover"] = 200 to """{"api_key":"sk-new","amount_sats":1000}"""
            assertEquals("sk-new", api.recover(base, "lnbc10u1fake").apiKey)
            assertFits("InvoiceRecoverRequest", seen.last().body)

            // An id that would change the path is never sent.
            assertFailsWith<RoutstrException> { api.invoiceStatus(base, "../admin") }
        }

    @Test
    fun cashuCreateAndTopupWithChange() =
        runTest {
            answers["/v1/balance/create"] = 200 to """{"api_key":"sk-cashu","balance":21000}"""
            val created = api.createWithCashu(base, "cashuBtoken")
            assertEquals("sk-cashu", created.apiKey)
            assertEquals(21_000L, created.balanceMsats)
            assertFits("BalanceCreateRequest", seen.last().body)

            answers["/v1/balance/topup"] = 200 to """{"msats":20000,"change_token":"cashuBchange","change_amount":3,"change_unit":"sat"}"""
            val topped = api.topupWithCashu(base, "sk-cashu", "cashuBforeign")
            assertEquals("cashuBchange", topped.changeToken)
            assertEquals(3L, topped.changeAmount)
            assertEquals("Bearer sk-cashu", seen.last().auth)
            assertFits("TopupRequest", seen.last().body)
        }

    @Test
    fun balanceInfoAndModelsAndErrors() =
        runTest {
            answers["/v1/balance/info"] = 200 to """{"api_key":"sk-x","balance":1234567,"reserved":0,"total_requests":3,"total_spent":5000}"""
            val balance = api.balance(base, "sk-x")
            assertEquals(1234L, balance.balanceSats)
            assertEquals("Bearer sk-x", seen.last().auth)

            answers["/v1/models"] = 200 to javaClass.getResourceAsStream("/routstr/models-sample.json")!!.bufferedReader().readText()
            assertEquals(4, api.models(base).size)

            answers["/v1/info"] = 200 to """{"name":"thefux","description":"Thefux Provider","version":"0.5.1+gd980acc","npub":"npub18kpn83drge7x9vz4cuhh7xta79sl4tfq55se4e554yj90s8y3f7qa49nps","mints":["https://mint.minibits.cash/Bitcoin"],"http_url":"https://routstr.otrta.me","onion_url":"http://zdnt2juw6htljsu25ftnbvdicgvr4s3ts4ldlx2myjcuyz4woaf4l7id.onion"}"""
            val info = api.info(base)
            assertTrue(info.isRoutstr)
            assertEquals("3d8333c5a3467c62b055c72f7f197df161faad20a5219ae694a92457c0e48a7c", info.pubKeyHex)

            answers["/v1/balance/info"] = 401 to """{"detail":{"error":{"code":"key_not_found","message":"API key not found"}}}"""
            val error = assertFailsWith<RoutstrException> { api.balance(base, "sk-other") }
            assertEquals(RoutstrFailure.KEY_NOT_FOUND, error.failure)

            server.stop(0)
            assertEquals(RoutstrFailure.UNREACHABLE, assertFailsWith<RoutstrException> { api.info(base) }.failure)
        }
}
