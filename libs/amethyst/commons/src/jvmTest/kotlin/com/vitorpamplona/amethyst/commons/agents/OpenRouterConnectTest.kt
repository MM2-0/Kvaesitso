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
package com.vitorpamplona.amethyst.commons.agents

import com.vitorpamplona.amethyst.commons.service.http.RoleBasedOkHttpClients
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.net.HttpURLConnection
import java.net.URI
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The loopback flow end to end: a "browser" follows the redirect, OpenRouter's exchange is faked. */
class OpenRouterConnectTest {
    private var exchangedBody: String? = null

    private val clients =
        object : RoleBasedOkHttpClients {
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            val request = chain.request()
                            exchangedBody = Buffer().also { request.body?.writeTo(it) }.readUtf8()
                            Response
                                .Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body("""{"key":"sk-or-v1-from-oauth"}""".toResponseBody("application/json".toMediaType()))
                                .build()
                        },
                    ).build()

            override fun proxyPortForVideo(url: String): Int? = null

            override fun okHttpClientForNip05(url: String) = client

            override fun okHttpClientForUploads(url: String) = client

            override fun okHttpClientForImage(url: String) = client

            override fun okHttpClientForVideo(url: String) = client

            override fun okHttpClientForMoney(url: String) = client

            override fun okHttpClientForPreview(url: String) = client

            override fun okHttpClientForPushRegistration(url: String) = client
        }

    /** Plays the user's browser: approves on "OpenRouter", which redirects to the callback. */
    private fun browser(answer: (callbackUrl: String, state: String) -> String): (String) -> Unit =
        { authUrl ->
            val query = OpenRouterOAuth.parseQuery(authUrl.substringAfter('?'))
            thread {
                val target = answer(query.getValue("callback_url"), query.getValue("state"))
                // A stray request first, as browsers ask for a favicon: it must not end the wait.
                listOf(target.substringBefore("/callback") + "/favicon.ico", target).forEach { url ->
                    (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                        responseCode
                        disconnect()
                    }
                }
            }
        }

    @Test
    fun approvedFlowReturnsTheExchangedKey() =
        runBlocking {
            val result =
                clients.connectOpenRouter("done", timeoutMs = 10_000, openBrowser = browser { cb, state -> "$cb?code=the-code&state=$state" })

            assertEquals(OpenRouterConnectResult.Connected("sk-or-v1-from-oauth"), result)
            assertTrue(exchangedBody!!.contains("\"code\":\"the-code\""))
            assertTrue(exchangedBody!!.contains("\"code_verifier\":"))
        }

    @Test
    fun deniedOrForgedCallbacksGetNoKey() =
        runBlocking {
            assertEquals(OpenRouterConnectResult.Denied, clients.connectOpenRouter("done", 10_000, browser { cb, _ -> cb }))
            // A forged answer is ignored, not taken as the end: nothing is exchanged, the wait runs out.
            assertEquals(
                OpenRouterConnectResult.TimedOut,
                clients.connectOpenRouter("done", 2_000, browser { cb, _ -> "$cb?code=x&state=forged" }),
            )
            assertEquals(null, exchangedBody)
        }

    @Test
    fun aStrayCallbackDoesNotEndTheRealOne() =
        runBlocking {
            val result =
                clients.connectOpenRouter("done", timeoutMs = 10_000) { authUrl ->
                    val query = OpenRouterOAuth.parseQuery(authUrl.substringAfter('?'))
                    val cb = query.getValue("callback_url")
                    thread {
                        listOf("$cb?code=forged&state=other", "$cb?code=the-code&state=${query.getValue("state")}").forEach { url ->
                            (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                                responseCode
                                disconnect()
                            }
                        }
                    }
                }
            assertEquals(OpenRouterConnectResult.Connected("sk-or-v1-from-oauth"), result)
            assertTrue(exchangedBody!!.contains("\"code\":\"the-code\""))
        }

    @Test
    fun noBrowserIsAFailureNotACrash() =
        runBlocking {
            assertEquals(OpenRouterConnectResult.Failed, clients.connectOpenRouter("done", 10_000) { throw UnsupportedOperationException("no browser") })
        }

    @Test
    fun noAnswerTimesOut() =
        runBlocking {
            assertEquals(OpenRouterConnectResult.TimedOut, clients.connectOpenRouter("done", 1_500) { })
        }
}
