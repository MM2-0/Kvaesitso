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
import kotlin.test.fail

/**
 * Pin moq-lite-03's "from latest" subscribe semantics: a listener
 * that joins after the speaker has been broadcasting for a while
 * gets new frames as they arrive — but does NOT replay history.
 *
 * The bytes the speaker pushed before the listener subscribed
 * (encoded as `FRAME-<00..04>`) must NOT appear in the listener's
 * stream; only the post-subscribe batch (`FRAME-<64..68>`) should.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsLateJoinInteropTest {
    @Test
    fun late_listener_does_not_replay_history() =
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
                    roomId = "late-${System.nanoTime()}",
                )
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)
            val capture = InteropDriverCapture()

            val scope = "late-join"
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

                // Phase 1: speaker pushes early frames with NO listener
                // subscribed. Per moq-lite-03 the relay does not buffer
                // these for future subscribers.
                InteropDebug.checkpoint(scope, "phase 1: $EARLY_FRAMES early frames (pre-subscribe)")
                delay(SETTLE_MS)
                for (i in 0 until EARLY_FRAMES) {
                    capture.push(i)
                    delay(FRAME_SPACING_MS)
                }
                delay(SETTLE_MS)

                // Phase 2: late listener subscribes.
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
                    InteropDebug.stepSuspending("$scope/listener", "subscribeSpeaker (late)") {
                        listener.subscribeSpeaker(speakerSigner.pubKey)
                    }

                // Take exactly LATE_FRAMES — if the relay accidentally
                // replayed any historical frame, take(N) would pick it
                // up and the byte-range check below catches it.
                val collected =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            sub.objects.take(LATE_FRAMES).toList()
                        }
                    }
                delay(SUBSCRIBE_SETTLE_MS)

                // Phase 3: speaker pushes late frames with the listener
                // subscribed. Use byte values 100+ so any contamination
                // from phase 1 is trivial to spot.
                InteropDebug.checkpoint(scope, "phase 3: $LATE_FRAMES late frames (post-subscribe)")
                for (i in 0 until LATE_FRAMES) {
                    capture.push(LATE_BYTE_OFFSET + i)
                    delay(FRAME_SPACING_MS)
                }

                val frames = collected.await()
                if (frames == null) {
                    fail("[$scope] timed out waiting for $LATE_FRAMES late frames within ${RECEIVE_TIMEOUT_MS}ms")
                }

                val bytes = frames.map { it.payload.last().toInt() and 0xFF }
                InteropDebug.checkpoint(scope, "received bytes=$bytes")
                val replayed = bytes.filter { it < LATE_BYTE_OFFSET }
                if (replayed.isNotEmpty()) {
                    fail(
                        "[$scope] late listener saw history-replayed frames (bytes < $LATE_BYTE_OFFSET): $replayed " +
                            "— moq-lite-03 'from latest' subscribe must not replay pre-subscribe groups",
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
        private const val EARLY_FRAMES = 5
        private const val LATE_FRAMES = 5
        private const val LATE_BYTE_OFFSET = 100
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val SETTLE_MS = 300L
        private const val FRAME_SPACING_MS = 25L
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
