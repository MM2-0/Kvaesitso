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
package com.vitorpamplona.nestsclient.transport

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeWebTransportTest {
    @Test
    fun pair_starts_open() {
        val (a, b) = FakeWebTransport.pair()
        assertTrue(a.isOpen)
        assertTrue(b.isOpen)
    }

    @Test
    fun datagrams_from_a_arrive_on_b() =
        runTest {
            val (a, b) = FakeWebTransport.pair()
            assertTrue(a.sendDatagram(byteArrayOf(1, 2, 3)))
            assertContentEquals(byteArrayOf(1, 2, 3), b.incomingDatagrams().first())
        }

    @Test
    fun datagrams_from_b_arrive_on_a() =
        runTest {
            val (a, b) = FakeWebTransport.pair()
            assertTrue(b.sendDatagram(byteArrayOf(4, 5, 6)))
            assertContentEquals(byteArrayOf(4, 5, 6), a.incomingDatagrams().first())
        }

    @Test
    fun bidi_stream_write_is_visible_on_the_peer_side() =
        runTest {
            val (a, b) = FakeWebTransport.pair()

            val clientStream = a.openBidiStream()
            clientStream.write(byteArrayOf(0xAA.toByte()))
            clientStream.finish()

            val serverStream = b.peerOpenedBidiStreams().first()
            val received = serverStream.incoming().toList()
            assertEquals(1, received.size)
            assertContentEquals(byteArrayOf(0xAA.toByte()), received.single())
        }

    @Test
    fun close_flips_isOpen_and_drops_further_datagrams() =
        runTest {
            val (a, _) = FakeWebTransport.pair()
            a.close()
            assertFalse(a.isOpen)
            assertFalse(a.sendDatagram(byteArrayOf(9)))
        }
}
