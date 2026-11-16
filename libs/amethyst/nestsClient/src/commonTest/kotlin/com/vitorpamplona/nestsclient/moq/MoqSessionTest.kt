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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoqSessionTest {
    @Test
    fun client_and_server_complete_setup_handshake_over_fake_transport() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()

            val clientJob =
                async {
                    val session = MoqSession.client(clientSide, backgroundScope)
                    session.setup(listOf(MoqVersion.DRAFT_17))
                    session.selectedVersion
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control, backgroundScope)
                    session.setup(
                        supportedVersions = listOf(MoqVersion.DRAFT_17, MoqVersion.DRAFT_11),
                        clientParameters =
                            listOf(SetupParameter(SetupParameter.KEY_MAX_SUBSCRIBE_ID, byteArrayOf(0x10))),
                    )
                    session.selectedVersion
                }

            assertEquals(MoqVersion.DRAFT_17, clientJob.await())
            assertEquals(MoqVersion.DRAFT_17, serverJob.await())
        }

    @Test
    fun server_picks_first_mutually_supported_version_from_its_own_list() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()

            val clientJob =
                async {
                    val session = MoqSession.client(clientSide, backgroundScope)
                    session.setup(listOf(MoqVersion.DRAFT_17, MoqVersion.DRAFT_11))
                    session.selectedVersion
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control, backgroundScope)
                    session.setup(supportedVersions = listOf(MoqVersion.DRAFT_11))
                    session.selectedVersion
                }

            assertEquals(MoqVersion.DRAFT_11, clientJob.await())
            assertEquals(MoqVersion.DRAFT_11, serverJob.await())
        }

    @Test
    fun server_rejects_when_no_version_overlap() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()

            val clientJob =
                async {
                    val session = MoqSession.client(clientSide, backgroundScope)
                    runCatching { session.setup(listOf(MoqVersion.DRAFT_17)) }
                }

            val serverJob =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val session = MoqSession.server(serverSide, control, backgroundScope)
                    assertFailsWith<MoqProtocolException> {
                        session.setup(supportedVersions = listOf(MoqVersion.DRAFT_11))
                    }
                }

            serverJob.await()
            val clientOutcome = clientJob.await()
            assert(clientOutcome.isFailure) { "client setup should have failed, got: $clientOutcome" }
        }

    /**
     * End-to-end: client subscribes; a *raw* server peer (no MoqSession on
     * the server side, since that would compete with the test's manual reads
     * for control-stream items) responds with SUBSCRIBE_OK and three
     * OBJECT_DATAGRAMs. The client's flow should yield them in order.
     */
    @Test
    fun subscribe_completes_on_subscribe_ok_then_delivers_datagrams_in_order() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val clientSession = MoqSession.client(clientSide, backgroundScope)

            // Server-side raw peer: handshake + serve one subscription manually.
            val serverWork =
                async {
                    val serverControl = serverSide.peerOpenedBidiStreams().first()
                    // Handshake: read CLIENT_SETUP, write SERVER_SETUP.
                    val clientSetupFrame = serverControl.incoming().first()
                    val clientSetup = MoqCodec.decode(clientSetupFrame)!!.message as ClientSetup
                    val version = clientSetup.supportedVersions.first()
                    serverControl.write(MoqCodec.encode(ServerSetup(selectedVersion = version)))

                    // Wait for SUBSCRIBE, reply with SUBSCRIBE_OK + three datagrams.
                    val sub = MoqCodec.decode(serverControl.incoming().first())!!.message as Subscribe
                    serverControl.write(
                        MoqCodec.encode(
                            SubscribeOk(
                                subscribeId = sub.subscribeId,
                                expiresMs = 60_000,
                                groupOrder = 0,
                                contentExists = false,
                            ),
                        ),
                    )
                    repeat(3) { i ->
                        val obj =
                            MoqObject(
                                trackAlias = sub.trackAlias,
                                groupId = 0,
                                objectId = i.toLong(),
                                publisherPriority = 0x80,
                                payload = byteArrayOf(i.toByte()),
                            )
                        serverSide.sendDatagram(MoqObjectDatagram.encode(obj))
                    }
                    sub
                }

            clientSession.setup(listOf(MoqVersion.DRAFT_17))

            val handle =
                clientSession.subscribe(
                    namespace = TrackNamespace.of("nests", "test-room"),
                    trackName = "speaker-1".encodeToByteArray(),
                )
            val sub = serverWork.await()

            assertEquals(sub.subscribeId, handle.subscribeId)
            assertEquals(sub.trackAlias, handle.trackAlias)
            assertEquals(60_000L, handle.ok.expiresMs)

            val received = handle.objects.take(3).toList()
            assertEquals(listOf(0L, 1L, 2L), received.map { it.objectId })
            assertContentEquals(byteArrayOf(0), received[0].payload)
            assertContentEquals(byteArrayOf(1), received[1].payload)
            assertContentEquals(byteArrayOf(2), received[2].payload)

            clientSession.close()
        }

    @Test
    fun subscribe_throws_MoqProtocolException_when_publisher_replies_with_subscribe_error() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val clientSession = MoqSession.client(clientSide, backgroundScope)

            val rejector =
                async {
                    val serverControl = serverSide.peerOpenedBidiStreams().first()
                    val clientSetup =
                        MoqCodec.decode(serverControl.incoming().first())!!.message as ClientSetup
                    serverControl.write(MoqCodec.encode(ServerSetup(clientSetup.supportedVersions.first())))

                    val sub =
                        MoqCodec.decode(serverControl.incoming().first())!!.message as Subscribe
                    serverControl.write(
                        MoqCodec.encode(
                            SubscribeError(
                                subscribeId = sub.subscribeId,
                                errorCode = 404,
                                reasonPhrase = "track not found",
                                trackAlias = sub.trackAlias,
                            ),
                        ),
                    )
                }

            clientSession.setup(listOf(MoqVersion.DRAFT_17))

            val ex =
                assertFailsWith<MoqProtocolException> {
                    clientSession.subscribe(
                        namespace = TrackNamespace.of("nests", "test-room"),
                        trackName = "missing".encodeToByteArray(),
                    )
                }
            rejector.await()
            assert("404" in ex.message!!) { "error message should mention the code: ${ex.message}" }

            clientSession.close()
        }

    @Test
    fun datagrams_for_unknown_track_alias_are_dropped_silently() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val clientSession = MoqSession.client(clientSide, backgroundScope)

            val handshake =
                async {
                    val serverControl = serverSide.peerOpenedBidiStreams().first()
                    val cs = MoqCodec.decode(serverControl.incoming().first())!!.message as ClientSetup
                    serverControl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                }
            clientSession.setup(listOf(MoqVersion.DRAFT_17))
            handshake.await()

            // Send a datagram for a track no one subscribed to. The pump should
            // silently drop it; the session stays usable + closes cleanly.
            serverSide.sendDatagram(
                MoqObjectDatagram.encode(
                    MoqObject(
                        trackAlias = 9_999,
                        groupId = 0,
                        objectId = 0,
                        publisherPriority = 0x80,
                        payload = byteArrayOf(0xAA.toByte()),
                    ),
                ),
            )
            testScheduler.runCurrent()
            clientSession.close()
        }

    /**
     * Existing FakeWebTransport bidi-stream test still passes after the
     * receiveAsFlow refactor: write + finish + collect should still surface
     * the chunk and complete cleanly.
     */
    @Test
    fun fake_bidi_stream_finish_completes_the_consumer_flow() =
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

    // ----- Publisher path (M5) -------------------------------------------

    @Test
    fun announce_completes_on_announce_ok_then_unannounce_sends_unannounce_frame() =
        runTest {
            val (publisherSide, peerSide) = FakeWebTransport.pair()
            val publisherSession = MoqSession.client(publisherSide, backgroundScope)

            val ns = TrackNamespace.of("nests", "test-room")

            val peer =
                async {
                    val ctrl = peerSide.peerOpenedBidiStreams().first()
                    // Setup
                    val cs = MoqCodec.decode(ctrl.incoming().first())!!.message as ClientSetup
                    ctrl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                    // ANNOUNCE
                    val announce = MoqCodec.decode(ctrl.incoming().first())!!.message as Announce
                    assertEquals(ns, announce.namespace)
                    ctrl.write(MoqCodec.encode(AnnounceOk(announce.namespace)))
                    // Read UNANNOUNCE after publisher tears down
                    val unannounce = MoqCodec.decode(ctrl.incoming().first())!!.message as Unannounce
                    assertEquals(ns, unannounce.namespace)
                }

            publisherSession.setup(listOf(MoqVersion.DRAFT_17))
            val handle = publisherSession.announce(ns)
            assertEquals(ns, handle.namespace)
            handle.unannounce()
            peer.await()

            publisherSession.close()
        }

    @Test
    fun announce_throws_MoqProtocolException_when_peer_replies_announce_error() =
        runTest {
            val (publisherSide, peerSide) = FakeWebTransport.pair()
            val publisherSession = MoqSession.client(publisherSide, backgroundScope)
            val ns = TrackNamespace.of("nests", "no-perms")

            val peer =
                async {
                    val ctrl = peerSide.peerOpenedBidiStreams().first()
                    val cs = MoqCodec.decode(ctrl.incoming().first())!!.message as ClientSetup
                    ctrl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                    val ann = MoqCodec.decode(ctrl.incoming().first())!!.message as Announce
                    ctrl.write(
                        MoqCodec.encode(
                            AnnounceError(
                                namespace = ann.namespace,
                                errorCode = 0x10,
                                reasonPhrase = "no permission",
                            ),
                        ),
                    )
                }

            publisherSession.setup(listOf(MoqVersion.DRAFT_17))
            val ex = assertFailsWith<MoqProtocolException> { publisherSession.announce(ns) }
            assert("0x10" in ex.message!! || "no permission" in ex.message!!)
            peer.await()
            publisherSession.close()
        }

    @Test
    fun publisher_routes_inbound_subscribe_and_emits_object_datagrams() =
        runTest {
            val (publisherSide, peerSide) = FakeWebTransport.pair()
            val publisherSession = MoqSession.client(publisherSide, backgroundScope)
            val ns = TrackNamespace.of("nests", "test-room")
            val trackName = "speaker-pub-1".encodeToByteArray()

            // Real nests servers wait for a viewer's UI to subscribe; they don't
            // race ANNOUNCE_OK. The test models that with an explicit gate so
            // openTrack runs before we forge an inbound SUBSCRIBE.
            val publisherReady = CompletableDeferred<Unit>()

            val peerJob =
                async {
                    val ctrl = peerSide.peerOpenedBidiStreams().first()
                    // Setup
                    val cs = MoqCodec.decode(ctrl.incoming().first())!!.message as ClientSetup
                    ctrl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                    // ANNOUNCE
                    val announce = MoqCodec.decode(ctrl.incoming().first())!!.message as Announce
                    ctrl.write(MoqCodec.encode(AnnounceOk(announce.namespace)))
                    publisherReady.await()
                    // Send a SUBSCRIBE for the publisher's track
                    val subscribeId = 42L
                    val trackAlias = 7L
                    ctrl.write(
                        MoqCodec.encode(
                            Subscribe(
                                subscribeId = subscribeId,
                                trackAlias = trackAlias,
                                namespace = announce.namespace,
                                trackName = trackName,
                            ),
                        ),
                    )
                    // Expect a SUBSCRIBE_OK reply
                    val ok = MoqCodec.decode(ctrl.incoming().first())!!.message as SubscribeOk
                    assertEquals(subscribeId, ok.subscribeId)
                    Triple(subscribeId, trackAlias, ok)
                }

            publisherSession.setup(listOf(MoqVersion.DRAFT_17))
            val handle = publisherSession.announce(ns)
            val publisher = handle.openTrack(trackName)
            publisherReady.complete(Unit)

            val (subscribeId, trackAlias, _) = peerJob.await()

            // Now push 3 objects through the publisher; they should arrive on
            // the peer's incoming-datagram channel.
            repeat(3) { i ->
                publisher.send(byteArrayOf(i.toByte()))
            }

            val received = peerSide.incomingDatagrams().take(3).toList()
            val decoded = received.map { MoqObjectDatagram.decode(it) }
            assertEquals(listOf(0L, 1L, 2L), decoded.map { it.objectId })
            assertEquals(List(3) { trackAlias }, decoded.map { it.trackAlias })
            assertContentEquals(byteArrayOf(0), decoded[0].payload)
            assertContentEquals(byteArrayOf(1), decoded[1].payload)
            assertContentEquals(byteArrayOf(2), decoded[2].payload)

            // subscribeId is intentionally read so the test fails if the order
            // ever flips silently.
            assertEquals(42L, subscribeId)

            publisher.close()
            handle.unannounce()
            publisherSession.close()
        }

    @Test
    fun publisher_send_returns_false_when_no_subscribers_attached() =
        runTest {
            val (publisherSide, peerSide) = FakeWebTransport.pair()
            val publisherSession = MoqSession.client(publisherSide, backgroundScope)
            val ns = TrackNamespace.of("nests", "lonely")

            val peer =
                async {
                    val ctrl = peerSide.peerOpenedBidiStreams().first()
                    val cs = MoqCodec.decode(ctrl.incoming().first())!!.message as ClientSetup
                    ctrl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                    val a = MoqCodec.decode(ctrl.incoming().first())!!.message as Announce
                    ctrl.write(MoqCodec.encode(AnnounceOk(a.namespace)))
                }

            publisherSession.setup(listOf(MoqVersion.DRAFT_17))
            val handle = publisherSession.announce(ns)
            peer.await()
            val publisher = handle.openTrack("nobody-listens".encodeToByteArray())

            assertEquals(false, publisher.send(byteArrayOf(1, 2, 3)))

            publisher.close()
            handle.unannounce()
            publisherSession.close()
        }

    @Test
    fun publisher_replies_subscribe_error_for_unknown_track_under_announced_namespace() =
        runTest {
            val (publisherSide, peerSide) = FakeWebTransport.pair()
            val publisherSession = MoqSession.client(publisherSide, backgroundScope)
            val ns = TrackNamespace.of("nests", "test-room")

            val peer =
                async {
                    val ctrl = peerSide.peerOpenedBidiStreams().first()
                    val cs = MoqCodec.decode(ctrl.incoming().first())!!.message as ClientSetup
                    ctrl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                    val a = MoqCodec.decode(ctrl.incoming().first())!!.message as Announce
                    ctrl.write(MoqCodec.encode(AnnounceOk(a.namespace)))
                    // Send SUBSCRIBE for a track we never opened
                    ctrl.write(
                        MoqCodec.encode(
                            Subscribe(
                                subscribeId = 1L,
                                trackAlias = 1L,
                                namespace = a.namespace,
                                trackName = "ghost".encodeToByteArray(),
                            ),
                        ),
                    )
                    val err = MoqCodec.decode(ctrl.incoming().first())!!.message as SubscribeError
                    assertEquals(1L, err.subscribeId)
                    assertEquals(ErrorCode.TRACK_DOES_NOT_EXIST, err.errorCode)
                }

            publisherSession.setup(listOf(MoqVersion.DRAFT_17))
            val handle = publisherSession.announce(ns)
            peer.await()

            handle.unannounce()
            publisherSession.close()
        }
}
