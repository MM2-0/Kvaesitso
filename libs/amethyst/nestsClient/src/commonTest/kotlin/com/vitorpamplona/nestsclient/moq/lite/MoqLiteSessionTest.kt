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
package com.vitorpamplona.nestsclient.moq.lite

import com.vitorpamplona.nestsclient.transport.ChannelWriteStream
import com.vitorpamplona.nestsclient.transport.FakeBidiStream
import com.vitorpamplona.nestsclient.transport.FakeReadStream
import com.vitorpamplona.nestsclient.transport.FakeWebTransport
import com.vitorpamplona.quic.Varint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Drives a [MoqLiteSession] from the listener side against a fake peer
 * that plays the relay role. Validates:
 *
 *   - subscribe() writes the right ControlType + body, blocks until the
 *     peer's SubscribeOk arrives, then yields a SubscribeHandle whose
 *     frames flow surfaces every incoming group.
 *   - subscribe() throws MoqLiteSubscribeException on a Drop response.
 *   - announce() writes the AnnouncePlease and surfaces every Announce
 *     update the peer streams back.
 *   - Group uni streams are demuxed by subscribeId — frames for sub A
 *     never leak into sub B.
 *
 * No transport mock magic — these tests use the production codec to
 * encode/decode bytes, so a wire-shape regression on either side fails
 * here.
 */
class MoqLiteSessionTest {
    /** Real-time scope for session pumps. Cancelled in [tearDown] so each
     * test ends with a clean coroutine ledger.
     */
    private val supervisor = SupervisorJob()
    private val pumpScope = CoroutineScope(supervisor)

    @AfterTest
    fun tearDown() =
        runBlocking {
            supervisor.cancelAndJoin()
        }

    @Test
    fun subscribe_writes_request_and_returns_handle_on_ok() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val peerHandlesSubscribe =
                async {
                    val (bidi, req) = nextSubscribeBidi(serverSide)
                    assertEquals("speakerPubkey", req.broadcast)
                    assertEquals("audio/data", req.track)
                    assertEquals(MoqLiteSession.DEFAULT_PRIORITY, req.priority)
                    val ok =
                        MoqLiteSubscribeOk(
                            priority = req.priority,
                            ordered = req.ordered,
                            maxLatencyMillis = req.maxLatencyMillis,
                            startGroup = null,
                            endGroup = null,
                        )
                    bidi.write(MoqLiteCodec.encodeSubscribeOk(ok))
                    req
                }

            val handle = session.subscribe("speakerPubkey", "audio/data")
            val request = peerHandlesSubscribe.await()
            assertEquals(request.id, handle.id)
            assertEquals(0L, handle.id, "first subscribe id is 0")

            // Now push one group with two frames from the server side.
            val uni = serverSide.openUniStream()
            uni.write(Varint.encode(MoqLiteDataType.Group.code))
            uni.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId = handle.id, sequence = 7L)))
            uni.write(framePayload(byteArrayOf(0x10, 0x11)))
            uni.write(framePayload(byteArrayOf(0x20, 0x21)))
            uni.finish()

            val frames =
                withTimeout(2_000) {
                    handle.frames.take(2).toList()
                }
            assertEquals(2, frames.size)
            assertEquals(7L, frames[0].groupSequence)
            assertEquals(7L, frames[1].groupSequence)
            assertContentEquals(byteArrayOf(0x10, 0x11), frames[0].payload)
            assertContentEquals(byteArrayOf(0x20, 0x21), frames[1].payload)

            session.close()
        }

    @Test
    fun subscribe_throws_on_drop_response() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val peer =
                async {
                    val (bidi, _) = nextSubscribeBidi(serverSide)
                    bidi.write(
                        MoqLiteCodec.encodeSubscribeDrop(
                            MoqLiteSubscribeDrop(errorCode = 4L, reasonPhrase = "no such broadcast"),
                        ),
                    )
                }

            assertFailsWith<MoqLiteSubscribeException> {
                session.subscribe("nope", "audio/data")
            }
            peer.await()
            session.close()
        }

    @Test
    fun announce_streams_relay_updates() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val peer =
                async {
                    val bidi = serverSide.peerOpenedBidiStreams().first()
                    val chunks = bidi.incoming().take(2).toList()
                    val controlByte = MoqLiteFrameBuffer().apply { push(chunks[0]) }.readVarint()
                    assertEquals(MoqLiteControlType.Announce.code, controlByte)
                    val plea =
                        MoqLiteFrameBuffer().apply { push(chunks[1]) }.readSizePrefixed()
                            ?: error("AnnouncePlease chunk did not contain a complete size-prefixed payload")
                    assertEquals("nests/30312:abc:room", MoqLiteCodec.decodeAnnouncePlease(plea).prefix)

                    // Send two Announce updates back.
                    bidi.write(
                        MoqLiteCodec.encodeAnnounce(
                            MoqLiteAnnounce(
                                MoqLiteAnnounceStatus.Active,
                                "speakerOne",
                                hops = listOf(0L),
                            ),
                        ),
                    )
                    bidi.write(
                        MoqLiteCodec.encodeAnnounce(
                            MoqLiteAnnounce(
                                MoqLiteAnnounceStatus.Active,
                                "speakerTwo",
                                hops = listOf(0L),
                            ),
                        ),
                    )
                }

            val announces = session.announce("nests/30312:abc:room")
            val updates = withTimeout(2_000) { announces.updates.take(2).toList() }
            peer.await()

            assertEquals(2, updates.size)
            assertEquals("speakerOne", updates[0].suffix)
            assertEquals(MoqLiteAnnounceStatus.Active, updates[0].status)
            assertEquals("speakerTwo", updates[1].suffix)

            announces.close()
            session.close()
        }

    @Test
    fun groups_are_demuxed_by_subscribeId() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            // Set up two parallel subscriptions — peer accepts both, replies
            // with Ok, then pushes one group per subscription out of order.
            // Use [nextSubscribeBidi] (not raw `peerOpenedBidiStreams`)
            // because the session lazy-opens an announce-watch bidi on
            // first subscribe (publisher-disconnect detection); raw
            // `.first()` would race with that and occasionally pick up
            // the announce bidi instead of the subscribe one.
            val subAck =
                async {
                    val (bidiA, _) = nextSubscribeBidi(serverSide)
                    bidiA.write(MoqLiteCodec.encodeSubscribeOk(okFor(0L)))
                    bidiA
                }
            val handleA = session.subscribe("speakerA", "audio/data")
            subAck.await()

            val subAck2 =
                async {
                    val (bidiB, _) = nextSubscribeBidi(serverSide)
                    bidiB.write(MoqLiteCodec.encodeSubscribeOk(okFor(1L)))
                    bidiB
                }
            val handleB = session.subscribe("speakerB", "audio/data")
            subAck2.await()

            assertEquals(0L, handleA.id)
            assertEquals(1L, handleB.id)

            // Push one group for A with payload "a", one for B with payload "b".
            val uniB = serverSide.openUniStream()
            uniB.write(Varint.encode(MoqLiteDataType.Group.code))
            uniB.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId = handleB.id, sequence = 0L)))
            uniB.write(framePayload("b".encodeToByteArray()))
            uniB.finish()

            val uniA = serverSide.openUniStream()
            uniA.write(Varint.encode(MoqLiteDataType.Group.code))
            uniA.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId = handleA.id, sequence = 0L)))
            uniA.write(framePayload("a".encodeToByteArray()))
            uniA.finish()

            val fromA = withTimeout(2_000) { handleA.frames.first() }
            val fromB = withTimeout(2_000) { handleB.frames.first() }
            assertContentEquals("a".encodeToByteArray(), fromA.payload)
            assertContentEquals("b".encodeToByteArray(), fromB.payload)

            handleA.unsubscribe()
            handleB.unsubscribe()
            session.close()
        }

    @Test
    fun publisher_replies_to_announcePlease_with_active_announce() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")

            // Relay (serverSide) opens an Announce bidi to us with
            // AnnouncePlease(prefix="").
            val relayBidi = serverSide.openBidiStream()
            relayBidi.write(Varint.encode(MoqLiteControlType.Announce.code))
            relayBidi.write(MoqLiteCodec.encodeAnnouncePlease(MoqLiteAnnouncePlease(prefix = "")))

            // We reply on the same bidi with Announce(active=true,
            // suffix="speakerPubkey").
            val resp = withTimeout(2_000) { relayBidi.incoming().first() }
            val announce = MoqLiteCodec.decodeAnnounce(MoqLiteFrameBuffer().apply { push(resp) }.readSizePrefixed()!!)
            assertEquals(MoqLiteAnnounceStatus.Active, announce.status)
            assertEquals("speakerPubkey", announce.suffix)

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_acks_subscribe_and_pushes_group_data_on_uni_stream() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")

            // Step 1: relay opens Subscribe bidi.
            val subBidi = serverSide.openBidiStream()
            subBidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 7L,
                        broadcast = "speakerPubkey",
                        track = "audio/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )

            // Step 2: we reply SubscribeOk. moq-lite-03's SubscribeResponse
            // is `[type][size][body]` with the type discriminator OUTSIDE
            // the size prefix — no outer wrap to strip, the chunk itself
            // is the wire form decodeSubscribeResponse expects.
            val ackChunk = withTimeout(2_000) { subBidi.incoming().first() }
            val resp = MoqLiteCodec.decodeSubscribeResponse(ackChunk)
            assertIs<MoqLiteCodec.SubscribeResponse.Ok>(resp)

            // Step 3: publisher pushes one frame, which opens a uni
            // stream with DataType=Group + group header + frame.
            assertEquals(true, publisher.send("opus-1".encodeToByteArray()))
            publisher.endGroup()

            // The uni stream surfaces on the relay side via incomingUniStreams.
            val relayUni = withTimeout(2_000) { serverSide.incomingUniStreams().first() }
            val uniChunks = relayUni.incoming().toList()
            // Concatenate all chunks then parse: type + group header +
            // first frame. The buffer reader handles arbitrary chunk
            // boundaries.
            val buf = MoqLiteFrameBuffer()
            uniChunks.forEach { buf.push(it) }
            assertEquals(MoqLiteDataType.Group.code, buf.readVarint(), "uni stream starts with Group type byte")
            val header =
                MoqLiteCodec.decodeGroupHeader(
                    buf.readSizePrefixed() ?: error("group header missing"),
                )
            assertEquals(7L, header.subscribeId)
            assertEquals(0L, header.sequence, "first group is sequence 0")
            val firstFrame =
                buf.readSizePrefixed()
                    ?: error("first frame missing")
            assertContentEquals("opus-1".encodeToByteArray(), firstFrame)

            publisher.close()
            session.close()
        }

    @Test
    fun lite04_announce_round_trips_full_origin_list_through_session() =
        runBlocking {
            // Lite-03 audit L1: a session running [MoqLiteVersion.LITE_04]
            // routes its codec calls through the Lite-04 branches —
            // verified end-to-end by encoding an Announce on the
            // server side with two origin IDs and confirming the
            // client-side announce-watch flow surfaces them
            // unchanged. Pre-fix the codec was Lite-03-only and
            // would have read the count and discarded the IDs.
            // Drive the negotiated sub-protocol via the fake so the
            // session derives `version = LITE_04` itself (matching the
            // production path, where `MoqLiteSession.version` reads
            // through `transport.negotiatedSubProtocol`).
            val (clientSide, serverSide) =
                FakeWebTransport.pair(negotiatedSubProtocol = MoqLiteAlpn.LITE_04)
            val session = MoqLiteSession.client(clientSide, pumpScope)
            assertEquals(MoqLiteVersion.LITE_04, session.version)

            val peer =
                async {
                    val bidi = serverSide.peerOpenedBidiStreams().first()
                    // Drain control byte + AnnouncePlease. Lite-04
                    // shape: prefix string then excludeHop varint.
                    val chunks = bidi.incoming().take(2).toList()
                    assertEquals(MoqLiteControlType.Announce.code, MoqLiteFrameBuffer().apply { push(chunks[0]) }.readVarint())
                    val pleasePayload =
                        MoqLiteFrameBuffer().apply { push(chunks[1]) }.readSizePrefixed()
                            ?: error("AnnouncePlease body missing")
                    val please = MoqLiteCodec.decodeAnnouncePlease(pleasePayload, MoqLiteVersion.LITE_04)
                    assertEquals("rooms/A", please.prefix)
                    assertEquals(0L, please.excludeHop, "default excludeHop is 0 (no exclusion)")

                    // Push an Announce with full origin list (Lite-04
                    // shape).
                    bidi.write(
                        MoqLiteCodec.encodeAnnounce(
                            MoqLiteAnnounce(
                                status = MoqLiteAnnounceStatus.Active,
                                suffix = "speakerA",
                                hops = listOf(2L, 7L),
                            ),
                            MoqLiteVersion.LITE_04,
                        ),
                    )
                }

            val handle = session.announce("rooms/A")
            val announce = withTimeout(2_000) { handle.updates.first() }
            assertEquals(MoqLiteAnnounceStatus.Active, announce.status)
            assertEquals("speakerA", announce.suffix)
            assertEquals(listOf(2L, 7L), announce.hops, "Lite-04 session preserves origin id list")

            peer.await()
            handle.close()
            session.close()
        }

    @Test
    fun publisher_subscribeOk_narrows_startGroup_to_next_sequence() =
        runBlocking {
            // Lite-03 audit L2: when accepting a SUBSCRIBE, the publisher
            // narrows the SubscribeOk's `startGroup` from the subscriber's
            // request to its own [MoqLitePublisherHandle.nextSequence],
            // communicating the exact sequence the next group will
            // carry. Particularly useful for hot-swap continuations
            // (publisher seeded with a non-zero startSequence).
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher =
                session.publish(
                    broadcastSuffix = "speakerPubkey",
                    track = "audio/data",
                    startSequence = 100L,
                )

            val subBidi = serverSide.openBidiStream()
            subBidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 5L,
                        broadcast = "speakerPubkey",
                        track = "audio/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        // Subscriber asks "from latest" — publisher is
                        // free to narrow.
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )

            val ackChunk = withTimeout(2_000) { subBidi.incoming().first() }
            val resp = MoqLiteCodec.decodeSubscribeResponse(ackChunk)
            val ok = (resp as MoqLiteCodec.SubscribeResponse.Ok).ok
            assertEquals(100L, ok.startGroup, "publisher narrows startGroup to its nextSequence")
            // endGroup stays null — live broadcast has no end in sight.
            assertEquals(null, ok.endGroup)

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_startSequence_seeds_first_group_for_hot_swap_continuation() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            // Mint a publisher with a non-zero startSequence — simulates
            // the hot-swap path's "carry forward old publisher's
            // nextSequence" contract.
            val publisher =
                session.publish(
                    broadcastSuffix = "speakerPubkey",
                    track = "audio/data",
                    startSequence = 42L,
                )
            assertEquals(42L, publisher.nextSequence, "fresh publisher reports startSequence as next")

            // Wire up a subscriber so send() doesn't short-circuit.
            val subBidi = serverSide.openBidiStream()
            subBidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 11L,
                        broadcast = "speakerPubkey",
                        track = "audio/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )
            withTimeout(2_000) { subBidi.incoming().first() }

            assertEquals(true, publisher.send("opus-1".encodeToByteArray()))
            // FIN before draining so toList() terminates rather than
            // blocking indefinitely on the still-open uni stream.
            publisher.endGroup()
            // After the first send, nextSequence should advance to 43.
            assertEquals(43L, publisher.nextSequence)

            // First uni stream's GroupHeader.sequence MUST be 42, not 0.
            val relayUni = withTimeout(2_000) { serverSide.incomingUniStreams().first() }
            val uniChunks = relayUni.incoming().toList()
            val buf = MoqLiteFrameBuffer()
            uniChunks.forEach { buf.push(it) }
            assertEquals(MoqLiteDataType.Group.code, buf.readVarint())
            val header =
                MoqLiteCodec.decodeGroupHeader(
                    buf.readSizePrefixed() ?: error("group header missing"),
                )
            assertEquals(42L, header.sequence, "first group's sequence is the seeded startSequence")

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_packs_trackPriority_and_sequence_into_setPriority_value() =
        runBlocking {
            // Lite-03 audit M1: openGroupStream packs the publisher's
            // trackPriority byte into bits 30..23 and the group sequence
            // into bits 22..0 of the value passed to
            // WebTransportWriteStream.setPriority. Bit 31 stays 0 so
            // the resulting Int is always non-negative — required
            // because [QuicConnectionWriter] sorts via signed
            // `sortedByDescending { it.priority }`. Verified on the
            // *peer* side via FakeReadStream.lastSetPriority, which the
            // in-memory transport uses as a side-channel for priority
            // introspection.
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            // Use a non-default trackPriority so a regression that
            // accidentally drops it back to DEFAULT_TRACK_PRIORITY shows
            // up in the assertion. 0xC0 sits in the upper half of the
            // byte range — pre-fix this would have produced a negative
            // Int (bit 31 set) that mis-sorts.
            val publisher =
                session.publish(
                    broadcastSuffix = "speakerPubkey",
                    track = "audio/data",
                    startSequence = 7L,
                    trackPriority = 0xC0,
                )

            val subBidi = serverSide.openBidiStream()
            subBidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 99L,
                        broadcast = "speakerPubkey",
                        track = "audio/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )
            withTimeout(2_000) { subBidi.incoming().first() }

            assertEquals(true, publisher.send("opus-frame".encodeToByteArray()))
            publisher.endGroup()

            val relayUni = withTimeout(2_000) { serverSide.incomingUniStreams().first() }
            val fakeRead = relayUni as FakeReadStream
            // Expected: (0xC0 shl 23) or (7 and 0x7FFFFF). Compute via
            // the same formula so the test asserts the CONTRACT, not
            // a hex literal.
            val expected = ((0xC0 and 0xFF) shl 23) or (7 and 0x007F_FFFF)
            assertEquals(expected, fakeRead.lastSetPriority, "trackPriority occupies bits 30..23; sequence fills the low 23 bits")
            // Critical guard: the encoded value MUST be non-negative so
            // the QUIC writer's signed `sortedByDescending` orders it
            // correctly relative to lower-trackPriority streams.
            kotlin.test.assertTrue(
                (fakeRead.lastSetPriority ?: -1) >= 0,
                "encoded priority must be non-negative — bit 31 is reserved as the sign bit",
            )

            // Drain the stream so the cleanup path doesn't leak.
            relayUni.incoming().toList()
            publisher.close()
            session.close()
        }

    @Test
    fun publisher_priority_pack_keeps_top_trackPriority_above_lower_trackPriority() =
        runBlocking {
            // Direct guard against the pre-fix sign-extension bug: a
            // higher trackPriority MUST produce a higher encoded
            // priority value. Pre-fix `trackPriority=0xFF` with
            // sequence=0 produced a negative Int that sorted BELOW
            // `trackPriority=0x7F` with the same sequence — exactly
            // the inversion the audit M1 fix was supposed to prevent.
            // We exercise the path by opening a uni stream via a
            // publisher of each priority and comparing.
            val (clientSide1, serverSide1) = FakeWebTransport.pair()
            val (clientSide2, serverSide2) = FakeWebTransport.pair()
            val sessionHigh = MoqLiteSession.client(clientSide1, pumpScope)
            val sessionLow = MoqLiteSession.client(clientSide2, pumpScope)

            val pubHigh =
                sessionHigh.publish(
                    broadcastSuffix = "spk",
                    track = "audio/data",
                    trackPriority = 0xFF,
                )
            val pubLow =
                sessionLow.publish(
                    broadcastSuffix = "spk",
                    track = "audio/data",
                    trackPriority = 0x7F,
                )

            // Wire each up with a subscriber so send() doesn't drop.
            for ((server, _) in listOf(serverSide1 to "high", serverSide2 to "low")) {
                val sub = server.openBidiStream()
                sub.write(Varint.encode(MoqLiteControlType.Subscribe.code))
                sub.write(
                    MoqLiteCodec.encodeSubscribe(
                        MoqLiteSubscribe(
                            id = 1L,
                            broadcast = "spk",
                            track = "audio/data",
                            priority = 0x80,
                            ordered = true,
                            maxLatencyMillis = 0L,
                            startGroup = null,
                            endGroup = null,
                        ),
                    ),
                )
                withTimeout(2_000) { sub.incoming().first() }
            }

            pubHigh.send("h".encodeToByteArray())
            pubHigh.endGroup()
            pubLow.send("l".encodeToByteArray())
            pubLow.endGroup()

            val highUni = withTimeout(2_000) { serverSide1.incomingUniStreams().first() } as FakeReadStream
            val lowUni = withTimeout(2_000) { serverSide2.incomingUniStreams().first() } as FakeReadStream
            val highPriority = highUni.lastSetPriority ?: error("high priority not set")
            val lowPriority = lowUni.lastSetPriority ?: error("low priority not set")

            kotlin.test.assertTrue(highPriority > lowPriority, "0xFF trackPriority must encode higher than 0x7F (was $highPriority vs $lowPriority)")
            kotlin.test.assertTrue(highPriority >= 0, "0xFF trackPriority encoding must stay non-negative (was $highPriority)")

            highUni.incoming().toList()
            lowUni.incoming().toList()
            pubHigh.close()
            pubLow.close()
            sessionHigh.close()
            sessionLow.close()
        }

    @Test
    fun publisher_send_returns_false_when_no_inbound_subscriber() =
        runBlocking {
            val (clientSide, _) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")
            // No relay-opened Subscribe bidi → no subscribers → send is
            // a silent no-op (returns false), matching the listener
            // semantics where the speaker keeps capturing audio even
            // when nobody is listening.
            assertEquals(false, publisher.send("ignored".encodeToByteArray()))

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_setOnNewSubscriber_hook_fires_per_inbound_subscribe() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")
            val hookFireCount =
                java.util.concurrent.atomic
                    .AtomicInteger(0)
            publisher.setOnNewSubscriber {
                hookFireCount.incrementAndGet()
            }

            // First inbound SUBSCRIBE → hook fires once.
            val subBidi1 = serverSide.openBidiStream()
            subBidi1.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi1.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 0L,
                        broadcast = "speakerPubkey",
                        track = "audio/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )
            // Wait for SubscribeOk to drain so we know registerInboundSubscription
            // ran (and therefore the hook had its chance to launch).
            withTimeout(2_000) { subBidi1.incoming().first() }
            // Hook fires asynchronously on the session scope; give it a
            // moment to land. Use a bounded retry rather than a flat
            // delay so the test is fast on the happy path.
            withTimeout(2_000) {
                while (hookFireCount.get() < 1) kotlinx.coroutines.yield()
            }
            assertEquals(1, hookFireCount.get())

            // Second inbound SUBSCRIBE → hook fires again.
            val subBidi2 = serverSide.openBidiStream()
            subBidi2.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi2.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 1L,
                        broadcast = "speakerPubkey",
                        track = "audio/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )
            withTimeout(2_000) { subBidi2.incoming().first() }
            withTimeout(2_000) {
                while (hookFireCount.get() < 2) kotlinx.coroutines.yield()
            }
            assertEquals(2, hookFireCount.get())

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_setOnNewSubscriber_hook_does_not_fire_on_track_mismatch() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            // Publisher serves audio/data only.
            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")
            val hookFireCount =
                java.util.concurrent.atomic
                    .AtomicInteger(0)
            publisher.setOnNewSubscriber {
                hookFireCount.incrementAndGet()
            }

            // Inbound SUBSCRIBE for a different track. Replies
            // SubscribeDrop (covered in another test); hook MUST NOT
            // fire because no subscriber was actually registered on
            // this publisher.
            val subBidi = serverSide.openBidiStream()
            subBidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 7L,
                        broadcast = "speakerPubkey",
                        track = "video/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )
            // Drain the Drop reply.
            withTimeout(2_000) { subBidi.incoming().first() }
            // No way to wait deterministically for "the hook didn't
            // fire"; sleep briefly to let any racing launch surface,
            // then assert. Short delay because the hook would launch
            // on the same scope as registerInboundSubscription's caller.
            kotlinx.coroutines.delay(100)
            assertEquals(0, hookFireCount.get())

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_skips_announce_when_announce_please_prefix_does_not_match() =
        runBlocking {
            // Lite-03 audit M4: when the relay opens an Announce bidi
            // with a non-empty prefix that doesn't match our broadcast
            // suffix, the publisher MUST NOT emit Active under the
            // requested prefix (or under our own suffix) — kixelated's
            // `rs/moq-lite/src/lite/announce.rs::Producer` only emits
            // for matching prefixes. Pre-fix we'd fall through the
            // `null` branch of `MoqLitePath.stripPrefix` and announce
            // ourselves anyway. Verified here by FINing the bidi
            // cleanly without writing any Announce body.
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")

            // Relay opens Announce bidi with a non-matching prefix.
            val relayBidi = serverSide.openBidiStream()
            relayBidi.write(Varint.encode(MoqLiteControlType.Announce.code))
            relayBidi.write(
                MoqLiteCodec.encodeAnnouncePlease(
                    MoqLiteAnnouncePlease(prefix = "differentRoom"),
                ),
            )

            // The bidi MUST end with no body (peer FIN), and
            // `relayBidi.incoming().toList()` therefore returns empty.
            // Use a generous timeout so a slow fake doesn't false-pass
            // by hanging instead of FINing.
            val chunks = withTimeout(2_000) { relayBidi.incoming().toList() }
            assertEquals(emptyList(), chunks, "non-matching prefix must FIN without writing any Announce body")

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_replies_subscribeDrop_when_broadcast_does_not_match() =
        runBlocking {
            // Lite-03 audit M5: when the relay opens a Subscribe bidi
            // whose `broadcast` field doesn't match our publisher's
            // suffix, the publisher MUST reply SubscribeDrop with the
            // BROADCAST_DOES_NOT_EXIST code rather than route OUR audio
            // to the wrong subscriber. Pre-fix we matched on `track`
            // only and would happily send Opus frames to a peer who
            // subscribed under any broadcast string they liked.
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")

            val subBidi = serverSide.openBidiStream()
            subBidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 11L,
                        broadcast = "wrongPubkey",
                        track = "audio/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )

            val ackChunk = withTimeout(2_000) { subBidi.incoming().first() }
            val resp = MoqLiteCodec.decodeSubscribeResponse(ackChunk)
            val dropped = resp as MoqLiteCodec.SubscribeResponse.Dropped
            assertEquals(MoqLiteSubscribeDropCode.BROADCAST_DOES_NOT_EXIST, dropped.drop.errorCode)
            kotlin.test.assertContains(dropped.drop.reasonPhrase, "wrongPubkey")
            kotlin.test.assertContains(dropped.drop.reasonPhrase, "speakerPubkey")

            // Audit M3: in addition to the application-level Drop body,
            // the publisher must also `RESET_STREAM(errorCode)` the bidi
            // so the peer can distinguish a typed rejection from a
            // graceful "publisher gone" FIN. Drain so the read side
            // closes and the cell publishes, then assert the code.
            // toList() will end because reset() closed the channel.
            withTimeout(2_000) { subBidi.incoming().toList() }
            val fakeBidi = subBidi as FakeBidiStream
            assertEquals(
                MoqLiteSubscribeDropCode.BROADCAST_DOES_NOT_EXIST as Long?,
                fakeBidi.lastPeerResetCode,
                "publisher must reset the bidi with BROADCAST_DOES_NOT_EXIST after the Drop body",
            )

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_replies_subscribeDrop_when_track_is_not_published() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            // Publisher serves audio/data only.
            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")

            // Relay opens a Subscribe bidi for a DIFFERENT track. The
            // session must reply with SubscribeDrop carrying the
            // TRACK_DOES_NOT_EXIST code rather than a silent FIN —
            // otherwise the watcher's response wait resolves only when
            // the bidi is FIN'd, with no indication WHY (looks
            // identical to "publisher disappeared mid-subscribe").
            val subBidi = serverSide.openBidiStream()
            subBidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            subBidi.write(
                MoqLiteCodec.encodeSubscribe(
                    MoqLiteSubscribe(
                        id = 99L,
                        broadcast = "speakerPubkey",
                        track = "video/data",
                        priority = 0x80,
                        ordered = true,
                        maxLatencyMillis = 0L,
                        startGroup = null,
                        endGroup = null,
                    ),
                ),
            )

            val ackChunk = withTimeout(2_000) { subBidi.incoming().first() }
            val resp = MoqLiteCodec.decodeSubscribeResponse(ackChunk)
            val dropped = resp as MoqLiteCodec.SubscribeResponse.Dropped
            assertEquals(MoqLiteSubscribeDropCode.TRACK_DOES_NOT_EXIST, dropped.drop.errorCode)
            // Reason phrase is informational; pin substring rather than
            // the exact text so we can keep tweaking the wording.
            kotlin.test.assertContains(dropped.drop.reasonPhrase, "video/data")

            // Audit M3: typed RESET_STREAM follows the Drop body. Same
            // shape as broadcast_does_not_match — the code matches
            // the Drop's errorCode so a peer that only watched the
            // QUIC layer (no body decode) still sees the typed
            // rejection.
            withTimeout(2_000) { subBidi.incoming().toList() }
            val fakeBidi = subBidi as FakeBidiStream
            assertEquals(
                MoqLiteSubscribeDropCode.TRACK_DOES_NOT_EXIST as Long?,
                fakeBidi.lastPeerResetCode,
                "publisher must reset the bidi with TRACK_DOES_NOT_EXIST after the Drop body",
            )

            publisher.close()
            session.close()
        }

    @Test
    fun publisher_close_emits_ended_announce() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val publisher = session.publish(broadcastSuffix = "speakerPubkey", track = "audio/data")

            // Relay opens an announce bidi.
            val relayBidi = serverSide.openBidiStream()
            relayBidi.write(Varint.encode(MoqLiteControlType.Announce.code))
            relayBidi.write(MoqLiteCodec.encodeAnnouncePlease(MoqLiteAnnouncePlease(prefix = "")))
            // Drain the Active announce so the next .first() picks up Ended.
            withTimeout(2_000) { relayBidi.incoming().first() }

            publisher.close()

            val endedChunk = withTimeout(2_000) { relayBidi.incoming().first() }
            val ended =
                MoqLiteCodec.decodeAnnounce(
                    MoqLiteFrameBuffer().apply { push(endedChunk) }.readSizePrefixed()!!,
                )
            assertEquals(MoqLiteAnnounceStatus.Ended, ended.status)
            assertEquals("speakerPubkey", ended.suffix)

            session.close()
        }

    @Test
    fun frames_flow_completes_when_peer_FINs_the_subscribe_bidi() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val peerBidi =
                async {
                    val (bidi, _) = nextSubscribeBidi(serverSide)
                    bidi.write(MoqLiteCodec.encodeSubscribeOk(okFor(0L)))
                    bidi
                }

            val handle = session.subscribe("speakerY", "audio/data")
            val bidi = withTimeout(2_000) { peerBidi.await() }

            // Peer FINs the subscribe bidi — moq-lite Lite-03's signal
            // for "publisher gone, no more data ever". Without the
            // per-subscription bidi-watch, this would leave the
            // consumer-facing frames flow hanging forever; with the
            // watch, the frames Channel closes and `toList()` returns.
            bidi.finish()

            withTimeout(2_000) { handle.frames.toList() }

            session.close()
        }

    @Test
    fun frames_flow_completes_when_peer_FINs_immediately_after_Ok() =
        runBlocking {
            // Race regression: an earlier shape of subscribe() pre-registered
            // the subscription AFTER awaiting the Ok response. If the peer
            // FIN'd between sending Ok and subscribe() resuming, the
            // collector's exit-cleanup ran first against an empty map, and
            // subscribe() then inserted the subscription — leaving the
            // frames channel registered with no live collector to ever close
            // it. This test forces the order by writing Ok and FIN before
            // the listener has a chance to consume them.
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            val peerJob =
                async {
                    val (bidi, _) = nextSubscribeBidi(serverSide)
                    bidi.write(MoqLiteCodec.encodeSubscribeOk(okFor(0L)))
                    bidi.finish()
                }

            val handle = session.subscribe("speakerZ", "audio/data")
            withTimeout(2_000) { peerJob.await() }

            // frames flow must complete naturally regardless of whether
            // the FIN landed before or after subscribe()'s post-await
            // registration.
            withTimeout(2_000) { handle.frames.toList() }

            session.close()
        }

    @Test
    fun listener_stopSending_group_uni_when_subscription_already_canceled() =
        runBlocking {
            // Lite-03 audit M2: when a group's uni stream arrives for a
            // subscribe id that was canceled before the first frame
            // landed, the listener MUST stopSending() the uni stream so
            // the publisher abandons retransmits instead of wasting
            // bandwidth. Pre-fix the listener silently dropped every
            // frame; the publisher kept pushing until natural FIN.
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            // Set up a subscription so the uni-stream pump is active.
            val peer =
                async {
                    val (bidi, _) = nextSubscribeBidi(serverSide)
                    bidi.write(MoqLiteCodec.encodeSubscribeOk(okFor(0L)))
                    bidi
                }
            val handle = session.subscribe("speakerX", "audio/data")
            val subBidi = peer.await()

            // Cancel the subscription before any group flows.
            handle.unsubscribe()
            // Drain the subscribe bidi's FIN so the peer's pump
            // stays clean — not strictly required for the assertion
            // but keeps the test isolated.
            subBidi.incoming().toList()

            // Now the peer (= the relay) opens a uni stream for that
            // dead subscribe id and writes one frame.
            val uni = serverSide.openUniStream() as ChannelWriteStream
            uni.write(Varint.encode(MoqLiteDataType.Group.code))
            uni.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId = handle.id, sequence = 0L)))
            uni.write(framePayload(byteArrayOf(0x01)))

            // Spin until the listener's drainOneGroup has processed
            // the first frame, found no matching subscription, and
            // fired stopSending. We hold the writer side, so reading
            // [peerStopSendingCode] doesn't race the listener's
            // pump for the peer-side [FakeReadStream] reference.
            val deadline = System.currentTimeMillis() + 2_000
            while (uni.peerStopSendingCode == null && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(10)
            }
            assertEquals(
                MoqLiteStreamCancelCode.SUBSCRIPTION_GONE as Long?,
                uni.peerStopSendingCode,
                "listener must stopSending(SUBSCRIPTION_GONE) when a group arrives for a canceled subscription",
            )

            uni.finish()
            session.close()
        }

    @Test
    fun subscriber_probe_writes_control_type_and_decodes_publisher_bitrate() =
        runBlocking {
            // Lite-03 audit L3: the subscriber side opens a Probe bidi
            // by writing ControlType=Probe and reads size-prefixed
            // MoqLiteProbe messages from the publisher. Mirrors
            // kixelated's `Subscriber::run_probe_stream`
            // (`rs/moq-lite/src/lite/subscriber.rs`).
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            // Server side: read the subscriber's control byte, then
            // write two Probe messages.
            val serverPump =
                async {
                    val bidi = serverSide.peerOpenedBidiStreams().first()
                    val buf = MoqLiteFrameBuffer()
                    // Read the leading ControlType varint. The session
                    // writes it as a single chunk via bidi.write.
                    val ctChunk = withTimeout(2_000) { bidi.incoming().first() }
                    buf.push(ctChunk)
                    val ct = buf.readVarint()
                    assertEquals(MoqLiteControlType.Probe.code, ct, "subscriber writes ControlType=Probe")

                    // Push two probes (different bitrates) then FIN.
                    bidi.write(MoqLiteCodec.encodeProbe(MoqLiteProbe(bitrate = 32_000L)))
                    bidi.write(MoqLiteCodec.encodeProbe(MoqLiteProbe(bitrate = 64_000L)))
                    bidi.finish()
                }

            val handle = session.probe()
            // Drain both probes from the cold flow. SharedFlow with
            // replay=8 means we can collect after the publisher has
            // already pushed without losing emissions.
            val received = withTimeout(2_000) { handle.updates.take(2).toList() }
            assertEquals(2, received.size)
            assertEquals(32_000L, received[0].bitrate)
            assertEquals(64_000L, received[1].bitrate)

            serverPump.await()
            handle.close()
            session.close()
        }

    @Test
    fun unsubscribe_FINs_the_subscribe_bidi() =
        runBlocking {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val session = MoqLiteSession.client(clientSide, pumpScope)

            var peerBidi: FakeBidiStream? = null
            val peer =
                async {
                    val (bidi, _) = nextSubscribeBidi(serverSide)
                    bidi.write(MoqLiteCodec.encodeSubscribeOk(okFor(0L)))
                    peerBidi = bidi
                    // Drain whatever the listener writes after Ok — moq-lite
                    // unsubscribe is a FIN, which surfaces here as the
                    // incoming flow completing.
                    bidi.incoming().toList()
                }

            val handle = session.subscribe("speakerX", "audio/data")
            handle.unsubscribe()
            withTimeout(2_000) { peer.await() }
            assertEquals(true, peerBidi != null, "peer received the bidi")

            session.close()
        }

    // ---------- helpers ----------

    /**
     * Read a Subscribe request from the peer side of the bidi. Each
     * `bidi.write` on the session side becomes one channel send (so
     * one chunk on this end), and the session writes ControlType then
     * the encoded body — exactly two chunks. Pull both with a single
     * [take]+[toList] and parse them, no streaming buffer needed.
     */
    private suspend fun readSubscribeRequest(bidi: FakeBidiStream): MoqLiteSubscribe {
        val chunks = bidi.incoming().take(2).toList()
        check(chunks.size == 2) { "expected 2 chunks (control byte + body), got ${chunks.size}" }
        val controlByte = MoqLiteFrameBuffer().apply { push(chunks[0]) }.readVarint()
        assertEquals(MoqLiteControlType.Subscribe.code, controlByte)
        val payload =
            MoqLiteFrameBuffer().apply { push(chunks[1]) }.readSizePrefixed()
                ?: error("subscribe body chunk did not contain a complete size-prefixed payload")
        return MoqLiteCodec.decodeSubscribe(payload)
    }

    /**
     * Pull the next Subscribe bidi the peer's side has accepted,
     * skipping any housekeeping bidis (e.g. the announce-watch
     * bidi that [MoqLiteSession.subscribe] lazy-launches once per
     * session to detect publisher disconnect via `Announce(Ended)`
     * — see `pumpAnnounceWatch`). Each candidate bidi is peeked
     * by reading its first chunk; if the control varint is
     * Subscribe, the bidi + decoded body are returned. Other bidis
     * are simply abandoned — their pump-side `bidi.incoming()`
     * collect just sits idle until the test ends.
     */
    private suspend fun nextSubscribeBidi(serverSide: FakeWebTransport): Pair<FakeBidiStream, MoqLiteSubscribe> =
        serverSide
            .peerOpenedBidiStreams()
            .transformWhile { bidi ->
                val firstChunk = bidi.incoming().firstOrNull() ?: return@transformWhile true
                val code = MoqLiteFrameBuffer().apply { push(firstChunk) }.readVarint()
                if (code != MoqLiteControlType.Subscribe.code) {
                    // Housekeeping bidi (announce watch, etc.) —
                    // drop it on the floor; the session-side
                    // collector will idle indefinitely, which is
                    // fine for unit tests under runBlocking +
                    // pumpScope cleanup.
                    return@transformWhile true
                }
                val bodyChunk =
                    bidi.incoming().firstOrNull()
                        ?: error("subscribe stream FIN before body")
                val payload =
                    MoqLiteFrameBuffer().apply { push(bodyChunk) }.readSizePrefixed()
                        ?: error("subscribe body chunk did not contain a complete size-prefixed payload")
                emit(bidi to MoqLiteCodec.decodeSubscribe(payload))
                // Terminate upstream collection — without this the
                // helper would block waiting for a NEXT bidi that
                // may never come, since `takeWhile` only re-checks
                // its predicate when the next value emits. Tests
                // that open exactly two subscribes (no third bidi
                // to nudge the flow forward) used to hang here.
                false
            }.firstOrNull() ?: error("flow ended without a Subscribe bidi")

    private fun framePayload(bytes: ByteArray): ByteArray = Varint.encode(bytes.size.toLong()) + bytes

    private fun okFor(
        @Suppress("UNUSED_PARAMETER") id: Long,
    ) = MoqLiteSubscribeOk(
        priority = MoqLiteSession.DEFAULT_PRIORITY,
        ordered = true,
        maxLatencyMillis = 0L,
        startGroup = null,
        endGroup = null,
    )
}
