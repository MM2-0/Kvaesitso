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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.DataInputStream
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ktor allocates a frame's whole buffer as soon as the header names its length, so without a cap
 * one header claiming 2 GB costs the relay 2 GB before a byte of payload arrives.
 *
 * The oversized frame goes over a raw socket as a header alone. A client that also sends the payload
 * races the relay's close: the relay closes with that payload unread, the reset can reach the client
 * before the 1009 frame does, and the client then reports a broken pipe instead of a close code.
 */
class KtorRelayFrameLimitTest {
    private lateinit var relay: RelayEngine
    private lateinit var server: KtorRelay
    private val httpClient = OkHttpClient.Builder().build()

    @BeforeTest
    fun setup() {
        relay = RelayEngine(url = "ws://127.0.0.1:7771/".normalizeRelayUrl())
        server = KtorRelay(relay, host = "127.0.0.1", port = 0, maxFrameSize = 64 * 1024).start()
    }

    @AfterTest
    fun teardown() {
        server.stop()
        relay.close()
    }

    private class Recorder : WebSocketListener() {
        val messages = mutableListOf<String>()
        val firstMessage = CountDownLatch(1)

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            synchronized(messages) { messages.add(text) }
            firstMessage.countDown()
        }
    }

    private fun open(listener: Recorder): WebSocket = httpClient.newWebSocket(Request.Builder().url(server.url.replace("ws://", "http://")).build(), listener)

    @Test
    fun aFrameOverTheLimitClosesTheConnection() {
        val uri = URI(server.url)
        Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 10_000
            val out = socket.getOutputStream()
            val input = DataInputStream(socket.getInputStream())

            out.write(
                (
                    "GET ${uri.path.ifEmpty { "/" }} HTTP/1.1\r\nHost: ${uri.host}:${uri.port}\r\n" +
                        "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
                ).toByteArray(),
            )
            out.flush()
            assertTrue(readHttpHead(input).startsWith("HTTP/1.1 101"), "the relay must accept the upgrade")

            // FIN + text, then masked with the 64-bit length form: 2 GB, then the mask key. No payload.
            out.write(byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0x80.toByte(), 0, 0, 0, 1, 2, 3, 4))
            out.flush()

            assertEquals(0x88, input.readUnsignedByte(), "the relay must answer with a close frame")
            assertTrue((input.readUnsignedByte() and 0x7F) >= 2, "the close frame must carry a code")
            assertEquals(1009, input.readUnsignedShort(), "1009 is the close code for a message too big to process")
        }
    }

    private fun readHttpHead(input: DataInputStream): String {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) head.append(input.readUnsignedByte().toChar())
        return head.toString()
    }

    @Test
    fun aFrameUnderTheLimitIsServed() {
        val listener = Recorder()
        val ws = open(listener)
        ws.send("[\"REQ\",\"small\",{\"kinds\":[1],\"limit\":1}]")

        assertTrue(listener.firstMessage.await(10, TimeUnit.SECONDS))
        assertTrue(synchronized(listener.messages) { listener.messages.first() }.contains("EOSE"))
        ws.close(1000, null)
    }
}
