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
package com.vitorpamplona.nestsclient.moq

import com.vitorpamplona.nestsclient.transport.FakeWebTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * End-to-end round-trip tests: an actual `MoqSession.client()` publisher
 * announces, opens a track, and pushes OBJECT_DATAGRAMs which transit a
 * minimal in-test relay and arrive at an actual `MoqSession.client()`
 * subscriber via its `SubscribeHandle.objects` flow.
 *
 * The relay role here is a hand-rolled coroutine that mirrors the wire
 * behavior of a real MoQ relay (e.g. nostrnests):
 *   - reads CLIENT_SETUP from each side, writes SERVER_SETUP back
 *   - matches SUBSCRIBE on the subscriber side to ANNOUNCE on the
 *     publisher side, forwards SUBSCRIBE to the publisher
 *   - matches SUBSCRIBE_OK from the publisher and forwards to the
 *     subscriber
 *   - forwards every OBJECT_DATAGRAM from publisher → subscriber
 *
 * It's intentionally not generic — just enough to drive one
 * (announce, openTrack, send) ↔ (subscribe, collect) round trip per test.
 *
 * What this catches that the per-side tests don't: that what our publisher
 * emits is byte-for-byte what our subscriber expects. If we ever drift on
 * MoQ message layouts between publisher and listener, this fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoqRoundTripTest {
    @Test
    fun publisher_to_subscriber_round_trip_delivers_objects_in_order() =
        runTest(UnconfinedTestDispatcher()) {
            val (pubA, pubB) = FakeWebTransport.pair()
            val (subA, subB) = FakeWebTransport.pair()

            val ns = TrackNamespace.of("nests", "round-trip-room")
            val trackName = "alice-pubkey".encodeToByteArray()
            val publisherSession = MoqSession.client(pubA, backgroundScope)
            val subscriberSession = MoqSession.client(subA, backgroundScope)

            val relayJob = launch { runRelay(pubB, subB, ns, trackName) }

            publisherSession.setup(listOf(MoqVersion.DRAFT_17))
            subscriberSession.setup(listOf(MoqVersion.DRAFT_17))

            val announceHandle = publisherSession.announce(ns)
            val publisher = announceHandle.openTrack(trackName)

            // Subscribe runs on a separate coroutine because it has to stay
            // alive until SUBSCRIBE_OK comes back through the relay path,
            // which requires the publisher's openTrack to be present (it is,
            // by virtue of the line above).
            val handleDeferred =
                async {
                    subscriberSession.subscribe(
                        namespace = ns,
                        trackName = trackName,
                        // Big enough that the test's burst of 100 frames
                        // never trips DROP_OLDEST regardless of how far the
                        // consumer falls behind under UnconfinedTestDispatcher.
                        objectBufferCapacity = 256,
                    )
                }
            val handle = handleDeferred.await()

            // Push N Opus-shaped payloads through the publisher and verify
            // every one arrives on the subscriber's flow with intact
            // group/object ids and payload bytes. Drives the publisher loop
            // on `backgroundScope` so it runs concurrently with the
            // consumer's `take().toList()` collect.
            val frameCount = 100
            backgroundScope.launch {
                repeat(frameCount) { i -> publisher.send(opusPayload(i)) }
            }

            val received = handle.objects.take(frameCount).toList()

            assertEquals(frameCount, received.size)
            received.forEachIndexed { idx, obj ->
                assertEquals(0L, obj.groupId, "frame $idx groupId")
                assertEquals(idx.toLong(), obj.objectId, "frame $idx objectId monotonic")
                assertContentEquals(opusPayload(idx), obj.payload, "frame $idx payload")
            }

            handle.unsubscribe()
            publisher.close()
            announceHandle.unannounce()
            publisherSession.close()
            subscriberSession.close()
            relayJob.cancel()
        }

    @Test
    fun round_trip_unknown_namespace_subscribe_fails_with_protocol_exception() =
        runTest {
            val (pubA, pubB) = FakeWebTransport.pair()
            val (subA, subB) = FakeWebTransport.pair()
            val ns = TrackNamespace.of("nests", "real-room")
            val trackName = "alice".encodeToByteArray()
            val pub = MoqSession.client(pubA, backgroundScope)
            val sub = MoqSession.client(subA, backgroundScope)

            val relayJob = launch { runRelay(pubB, subB, ns, trackName) }

            pub.setup(listOf(MoqVersion.DRAFT_17))
            sub.setup(listOf(MoqVersion.DRAFT_17))

            // Publisher announces "real-room" but does NOT openTrack — the
            // inbound SUBSCRIBE on the publisher side will return
            // SUBSCRIBE_ERROR(TRACK_DOES_NOT_EXIST) because no publisher is
            // registered for that name.
            pub.announce(ns)

            val ex =
                runCatching {
                    sub.subscribe(namespace = ns, trackName = trackName)
                }.exceptionOrNull()

            assertEquals(true, ex is MoqProtocolException, "expected MoqProtocolException, got $ex")

            pub.close()
            sub.close()
            relayJob.cancel()
        }

    /**
     * Minimal MoQ-relay simulator. Mirrors what a nests relay does on the
     * wire so two of our [MoqSession]s can talk through it.
     *
     * Limitations vs a real relay (deliberate, this is a test fixture):
     *   - One namespace, one track, one subscriber per test.
     *   - SubscribeId / trackAlias are forwarded 1:1 across the relay (a
     *     real relay remaps; we don't need to since neither side reuses ids).
     *   - No SUBSCRIBE_DONE relay — the test calls `unsubscribe()` and
     *     checks it doesn't blow up; the publisher's UNSUBSCRIBE goes to
     *     the relay which silently drops it.
     */
    private suspend fun runRelay(
        pubTransport: FakeWebTransport,
        subTransport: FakeWebTransport,
        @Suppress("UNUSED_PARAMETER") expectedNs: TrackNamespace,
        @Suppress("UNUSED_PARAMETER") expectedTrack: ByteArray,
    ) {
        val pubControl = pubTransport.peerOpenedBidiStreams().first()
        val subControl = subTransport.peerOpenedBidiStreams().first()

        // SETUP both sides.
        val pubCs = MoqCodec.decode(pubControl.incoming().first())!!.message as ClientSetup
        pubControl.write(MoqCodec.encode(ServerSetup(pubCs.supportedVersions.first())))

        val subCs = MoqCodec.decode(subControl.incoming().first())!!.message as ClientSetup
        subControl.write(MoqCodec.encode(ServerSetup(subCs.supportedVersions.first())))

        // Read ANNOUNCE from publisher, ack with ANNOUNCE_OK.
        val announce = MoqCodec.decode(pubControl.incoming().first())!!.message as Announce
        pubControl.write(MoqCodec.encode(AnnounceOk(announce.namespace)))

        // Read SUBSCRIBE from subscriber, forward to publisher verbatim.
        val sub = MoqCodec.decode(subControl.incoming().first())!!.message as Subscribe
        pubControl.write(MoqCodec.encode(sub))

        // Wait for the publisher's reply (SUBSCRIBE_OK or SUBSCRIBE_ERROR)
        // and forward to the subscriber.
        val pubReply = MoqCodec.decode(pubControl.incoming().first())!!.message
        when (pubReply) {
            is SubscribeOk -> {
                subControl.write(MoqCodec.encode(pubReply))
            }

            is SubscribeError -> {
                subControl.write(MoqCodec.encode(pubReply))
                return
            }

            else -> {
                error("unexpected publisher reply to SUBSCRIBE: ${pubReply.type}")
            }
        }

        // Forward every OBJECT_DATAGRAM from publisher → subscriber until
        // either side cancels the relay coroutine.
        pubTransport.incomingDatagrams().collect { datagram ->
            subTransport.sendDatagram(datagram)
        }
    }

    private fun opusPayload(seqId: Int): ByteArray {
        // Mimic an Opus packet shape (~80 bytes, varying content).
        val size = 80
        val out = ByteArray(size)
        for (i in 0 until size) {
            out[i] = ((seqId xor i).toByte())
        }
        return out
    }
}
