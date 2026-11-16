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

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

private const val TAG = "OpenRouterConnect"

actual suspend fun IRoleBasedHttpClientBuilder.connectOpenRouter(
    donePageText: String,
    timeoutMs: Long,
    openBrowser: (String) -> Unit,
): OpenRouterConnectResult {
    // Loopback only: the code is useless without the verifier, but nobody else needs to see it.
    val server =
        withContext(Dispatchers.IO) {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = 1000 }
        }
    return server.use {
        val request = OpenRouterOAuth.newRequest("http://localhost:${server.localPort}/callback")
        // On the caller's dispatcher: the UI opens the browser. A device with no browser throws
        // (ActivityNotFoundException on Android, UnsupportedOperationException on a bare JVM).
        try {
            openBrowser(request.authUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open the browser", e)
            return@use OpenRouterConnectResult.Failed
        }

        val query = withContext(Dispatchers.IO) { awaitCallback(server, donePageText, timeoutMs) { OpenRouterOAuth.readCallback(request, it) != OpenRouterCallback.StateMismatch } }
        if (query == null) return@use OpenRouterConnectResult.TimedOut

        when (val callback = OpenRouterOAuth.readCallback(request, query)) {
            OpenRouterCallback.Denied -> OpenRouterConnectResult.Denied
            OpenRouterCallback.StateMismatch -> OpenRouterConnectResult.Failed
            is OpenRouterCallback.Code -> exchange(callback.code, request.verifier)
        }
    }
}

/**
 * Accepts connections until one asks for `/callback` with a query [isOurs] accepts (the state
 * this flow sent); returns that query, or null on timeout. A stray request (another local
 * program, a prefetch, a stale tab) gets a 400 and the wait goes on: it can't end the flow.
 */
private suspend fun awaitCallback(
    server: ServerSocket,
    donePageText: String,
    timeoutMs: Long,
    isOurs: (Map<String, String>) -> Boolean,
): Map<String, String>? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        currentCoroutineContext().ensureActive()
        val socket =
            try {
                server.accept()
            } catch (e: SocketTimeoutException) {
                continue
            }
        socket.use {
            val target = readRequestTarget(it) ?: return@use
            val path = target.substringBefore('?')
            if (path != "/callback") {
                respond(it, 404, "")
                return@use
            }
            val query = OpenRouterOAuth.parseQuery(target.substringAfter('?', ""))
            if (!isOurs(query)) {
                respond(it, 400, "")
                return@use
            }
            respond(it, 200, donePageText)
            return query
        }
    }
    return null
}

/** The target of `GET <target> HTTP/1.1`, or null for anything else. */
private fun readRequestTarget(socket: Socket): String? =
    try {
        socket.soTimeout = 5000
        val line = socket.getInputStream().bufferedReader().readLine() ?: return null
        val parts = line.split(' ')
        if (parts.size >= 2 && parts[0] == "GET") parts[1] else null
    } catch (e: IOException) {
        Log.w(TAG, "Unreadable callback request", e)
        null
    }

private fun respond(
    socket: Socket,
    code: Int,
    text: String,
) {
    val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val body =
        "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">" +
            "<title>Amethyst</title></head><body style=\"font-family:sans-serif;padding:2em\"><p>$escaped</p></body></html>"
    val bytes = body.encodeToByteArray()
    val head =
        "HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
    try {
        socket.getOutputStream().apply {
            write(head.encodeToByteArray())
            write(bytes)
            flush()
        }
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't answer the browser", e)
    }
}

private suspend fun IRoleBasedHttpClientBuilder.exchange(
    code: String,
    verifier: String,
): OpenRouterConnectResult =
    withContext(Dispatchers.IO) {
        val request =
            Request
                .Builder()
                .url(OpenRouterOAuth.EXCHANGE_URL)
                .post(OpenRouterOAuth.exchangeBody(code, verifier).toRequestBody("application/json".toMediaType()))
                .build()
        try {
            okHttpClientForMoney(OpenRouterOAuth.EXCHANGE_URL).newCall(request).executeAsync().use { response ->
                val key = if (response.isSuccessful) OpenRouterOAuth.parseKey(response.body.string()) else null
                if (key != null) {
                    OpenRouterConnectResult.Connected(key)
                } else {
                    Log.w(TAG) { "Code exchange failed: HTTP ${response.code}" }
                    OpenRouterConnectResult.Failed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Code exchange failed", e)
            OpenRouterConnectResult.Failed
        }
    }
