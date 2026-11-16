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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.moq.Announce
import com.vitorpamplona.nestsclient.moq.AnnounceOk
import com.vitorpamplona.nestsclient.moq.ClientSetup
import com.vitorpamplona.nestsclient.moq.MoqCodec
import com.vitorpamplona.nestsclient.moq.MoqSession
import com.vitorpamplona.nestsclient.moq.MoqVersion
import com.vitorpamplona.nestsclient.moq.ServerSetup
import com.vitorpamplona.nestsclient.moq.TrackNamespace
import com.vitorpamplona.nestsclient.transport.FakeWebTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NestsSpeakerTest {
    @Test
    fun startBroadcasting_announces_then_state_is_Broadcasting() =
        runTest {
            val (speakerSide, peerSide) = FakeWebTransport.pair()
            val speakerSession = MoqSession.client(speakerSide, backgroundScope)
            val ns = TrackNamespace.of("nests", "test-room")

            val peer =
                async {
                    val ctrl = peerSide.peerOpenedBidiStreams().first()
                    val cs = MoqCodec.decode(ctrl.incoming().first())!!.message as ClientSetup
                    ctrl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                    val ann = MoqCodec.decode(ctrl.incoming().first())!!.message as Announce
                    ctrl.write(MoqCodec.encode(AnnounceOk(ann.namespace)))
                }

            speakerSession.setup(listOf(MoqVersion.DRAFT_17))

            val state =
                MutableStateFlow<NestsSpeakerState>(
                    NestsSpeakerState.Connected(
                        room = TEST_ROOM,
                        negotiatedMoqVersion = speakerSession.selectedVersion!!,
                    ),
                )
            val speaker =
                DefaultNestsSpeaker(
                    session = speakerSession,
                    roomNamespace = ns,
                    speakerTrackName = "test-pubkey".encodeToByteArray(),
                    captureFactory = { ConstantCapture() },
                    encoderFactory = { ConstantEncoder() },
                    scope = backgroundScope,
                    mutableState = state,
                )

            val handle = speaker.startBroadcasting()
            peer.await()

            val now = speaker.state.value
            assertIs<NestsSpeakerState.Broadcasting>(now)
            assertEquals(false, now.isMuted)

            // setMuted reflects through into the public state.
            handle.setMuted(true)
            assertEquals(true, (speaker.state.value as NestsSpeakerState.Broadcasting).isMuted)
            assertEquals(true, handle.isMuted)

            handle.close()
            // Should fall back to Connected after broadcast ends.
            assertIs<NestsSpeakerState.Connected>(speaker.state.value)

            speaker.close()
            assertEquals(NestsSpeakerState.Closed, speaker.state.value)
        }

    @Test
    fun startBroadcasting_twice_throws_IllegalStateException() =
        runTest {
            val (speakerSide, peerSide) = FakeWebTransport.pair()
            val speakerSession = MoqSession.client(speakerSide, backgroundScope)
            val ns = TrackNamespace.of("nests", "test-room")

            val peer =
                async {
                    val ctrl = peerSide.peerOpenedBidiStreams().first()
                    val cs = MoqCodec.decode(ctrl.incoming().first())!!.message as ClientSetup
                    ctrl.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                    val ann = MoqCodec.decode(ctrl.incoming().first())!!.message as Announce
                    ctrl.write(MoqCodec.encode(AnnounceOk(ann.namespace)))
                }

            speakerSession.setup(listOf(MoqVersion.DRAFT_17))
            val state =
                MutableStateFlow<NestsSpeakerState>(
                    NestsSpeakerState.Connected(
                        room = TEST_ROOM,
                        negotiatedMoqVersion = speakerSession.selectedVersion!!,
                    ),
                )
            val speaker =
                DefaultNestsSpeaker(
                    session = speakerSession,
                    roomNamespace = ns,
                    speakerTrackName = "test-pubkey".encodeToByteArray(),
                    captureFactory = { ConstantCapture() },
                    encoderFactory = { ConstantEncoder() },
                    scope = backgroundScope,
                    mutableState = state,
                )

            speaker.startBroadcasting()
            peer.await()

            val ex =
                runCatching { speaker.startBroadcasting() }.exceptionOrNull()
            assertTrue(ex is IllegalStateException, "second startBroadcasting should throw IllegalStateException, got $ex")

            speaker.close()
        }

    // ----- Fakes -----

    private class ConstantCapture : AudioCapture {
        // Yields a single dummy frame then drains; per-instance so tests
        // don't share state through a singleton.
        private val ch = Channel<ShortArray>(capacity = 1)

        init {
            ch.trySend(ShortArray(960) { 0 })
            ch.close()
        }

        override fun start() {}

        override suspend fun readFrame(): ShortArray? = ch.receiveCatching().getOrNull()

        override fun stop() {}
    }

    private class ConstantEncoder : OpusEncoder {
        override fun encode(pcm: ShortArray): ByteArray = byteArrayOf(0xAA.toByte(), 0xBB.toByte())

        override fun release() {}
    }

    companion object {
        private val TEST_ROOM =
            NestsRoomConfig(
                authBaseUrl = "https://relay.example/api/v1/nests",
                endpoint = "https://relay.example/moq",
                hostPubkey = "0".repeat(64),
                roomId = "test-room",
            )
    }
}
