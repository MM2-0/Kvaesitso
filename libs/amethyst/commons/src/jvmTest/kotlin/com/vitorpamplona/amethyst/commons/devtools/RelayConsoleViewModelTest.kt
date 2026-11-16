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
package com.vitorpamplona.amethyst.commons.devtools

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RelayConsoleViewModelTest {
    /** Opens as soon as it is dialed, unless it was disconnected first, like the OkHttp socket. */
    private class FakeSocket(
        val listener: WebSocketListener,
    ) : WebSocket {
        @Volatile var closed = false

        override fun needsReconnect() = false

        override fun connect() {
            if (!closed) listener.onOpen(10, false)
        }

        override fun disconnect() {
            closed = true
        }

        override fun send(msg: String) = !closed
    }

    private class FakeBuilder : WebsocketBuilder {
        val sockets = mutableListOf<FakeSocket>()

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = FakeSocket(out).also { sockets.add(it) }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun disconnectRightAfterConnectLeavesNothingConnected() {
        val builder = FakeBuilder()
        val vm = RelayConsoleViewModel(builder, null)
        repeat(50) {
            vm.connect("wss://relay.example.com")
            vm.disconnect()
        }
        // Give any dial still in flight time to land.
        Thread.sleep(200)
        assertEquals(ConsoleConnection.DISCONNECTED, vm.connection.value)
        assertEquals(null, vm.link.value)
        assertEquals(builder.sockets.size, builder.sockets.count { it.closed })
    }

    @Test
    fun framesSentAndReceivedAtOnceKeepUniqueIds() {
        val builder = FakeBuilder()
        val vm = RelayConsoleViewModel(builder, null)
        vm.connect("wss://relay.example.com")
        Thread.sleep(100)
        val listener = builder.sockets.last().listener

        val perThread = RelayConsoleViewModel.MAX_FRAMES / 2
        val start = CountDownLatch(1)
        val sender =
            thread {
                start.await()
                repeat(perThread) { vm.send("[\"REQ\",\"s$it\",{}]") }
            }
        val receiver =
            thread {
                start.await()
                repeat(perThread) { runBlocking { listener.onMessage("[\"EOSE\",\"s$it\"]") } }
            }
        start.countDown()
        sender.join()
        receiver.join()

        val ids = vm.frames.value.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate frame ids would crash the LazyColumn")
    }
}
