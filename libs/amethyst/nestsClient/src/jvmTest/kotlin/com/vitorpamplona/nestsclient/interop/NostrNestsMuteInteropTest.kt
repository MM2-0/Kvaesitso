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
package com.vitorpamplona.nestsclient.interop

import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pin the mute / unmute mid-broadcast contract end-to-end:
 *
 *   - the broadcaster keeps the capture pump running while muted (so
 *     unmute is sample-accurate)
 *   - frames pushed during mute do NOT reach subscribers (`if (muted)
 *     continue` in [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster])
 *   - frames pushed before / after the muted window DO reach
 *     subscribers, with the muted ones missing entirely (no silent
 *     placeholder frames)
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsMuteInteropTest {
    @Test
    fun mute_drops_frames_unmute_resumes() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val speakerSigner = NostrSignerInternal(KeyPair())
            val listenerSigner = NostrSignerInternal(KeyPair())
            val room =
                NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = speakerSigner.pubKey,
                    roomId = "mute-${System.nanoTime()}",
                )
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)
            val capture = InteropDriverCapture()

            val scope = "mute"
            InteropDebug.checkpoint(scope, "room=${room.moqNamespace()} speaker=${speakerSigner.pubKey.take(8)}")

            try {
                val speaker =
                    InteropDebug.stepSuspending("$scope/speaker", "connectNestsSpeaker") {
                        connectNestsSpeaker(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = speakerSigner,
                            speakerPubkeyHex = speakerSigner.pubKey,
                            captureFactory = { capture },
                            encoderFactory = { InteropStubEncoder("FRAME-".encodeToByteArray()) },
                            framesPerGroup = 1,
                        )
                    }
                val broadcast =
                    InteropDebug.stepSuspending("$scope/speaker", "startBroadcasting") {
                        speaker.startBroadcasting()
                    }
                val listener =
                    InteropDebug.stepSuspending("$scope/listener", "connectNestsListener") {
                        connectNestsListener(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = listenerSigner,
                        )
                    }
                val sub =
                    InteropDebug.stepSuspending("$scope/listener", "subscribeSpeaker") {
                        listener.subscribeSpeaker(speakerSigner.pubKey)
                    }

                // Expect exactly 4 unmuted frames; the 2 muted frames in
                // the middle should never reach the wire.
                val collected =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            sub.objects.take(4).toList()
                        }
                    }
                delay(SUBSCRIBE_SETTLE_MS)

                InteropDebug.checkpoint(scope, "phase 1: unmuted, push 0,1")
                capture.push(0)
                delay(FRAME_SPACING_MS)
                capture.push(1)
                delay(MUTE_BOUNDARY_MS)

                InteropDebug.stepSuspending(scope, "setMuted(true)") { broadcast.setMuted(true) }
                InteropDebug.checkpoint(scope, "phase 2: muted, push 50,51 (expect dropped)")
                capture.push(50)
                delay(FRAME_SPACING_MS)
                capture.push(51)
                delay(MUTE_BOUNDARY_MS)

                InteropDebug.stepSuspending(scope, "setMuted(false)") { broadcast.setMuted(false) }
                InteropDebug.checkpoint(scope, "phase 3: unmuted, push 2,3")
                capture.push(2)
                delay(FRAME_SPACING_MS)
                capture.push(3)

                val frames = collected.await()
                if (frames == null) {
                    fail("[$scope] timed out waiting for 4 unmuted frames within ${RECEIVE_TIMEOUT_MS}ms")
                }
                assertEquals(4, frames.size, "[$scope] expected exactly 4 unmuted frames")
                val expected = listOf<Byte>(0, 1, 2, 3)
                frames.forEachIndexed { idx, obj ->
                    assertContentEquals(
                        "FRAME-".encodeToByteArray() + byteArrayOf(expected[idx]),
                        obj.payload,
                        "[$scope] frame $idx — muted frames must not appear in the stream",
                    )
                }

                runCatching { sub.unsubscribe() }
                runCatching { listener.close() }
                runCatching { broadcast.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    companion object {
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val FRAME_SPACING_MS = 25L
        private const val MUTE_BOUNDARY_MS = 200L
        private const val RECEIVE_TIMEOUT_MS = 10_000L

        private val httpClient = OkHttpNestsClient { OkHttpClient() }
        private val transport =
            QuicWebTransportFactory(certificateValidator = PermissiveCertificateValidator())
        private var harnessOrNull: NostrNestsHarness? = null

        @BeforeClass
        @JvmStatic
        fun setUpHarness() {
            if (NostrNestsHarness.isEnabled()) {
                harnessOrNull = NostrNestsHarness.shared()
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownHarness() {
            harnessOrNull = null
        }
    }
}
