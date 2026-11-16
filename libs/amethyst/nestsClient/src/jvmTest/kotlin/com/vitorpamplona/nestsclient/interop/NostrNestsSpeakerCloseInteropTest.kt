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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.fail

/**
 * Speaker calls `close()` (and `BroadcastHandle.close()`) mid-stream.
 *
 * Pins the **current** behaviour: the listener
 *   1. receives every pre-close frame the speaker pushed, and
 *   2. its `subscription.objects` flow does NOT auto-terminate when
 *      the speaker disconnects — the relay's `subscribed complete`
 *      signal isn't yet propagated to a `Channel.close()` on the
 *      listener side. UI code is currently expected to drive its
 *      own teardown (timer, listener.close, etc).
 *
 * If a future change wires the relay's done-signal through to a
 * clean flow completion (the more user-friendly behaviour), this
 * test will start failing on the "still open after close" assertion
 * — at which point flip the assertion and rename the test.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsSpeakerCloseInteropTest {
    @Test
    fun speaker_close_does_not_yet_terminate_listener_flow() =
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
                    roomId = "speakerClose-${System.nanoTime()}",
                )
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)
            val capture = InteropDriverCapture()

            val scope = "speaker-close"
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
                        connectNestsListener(httpClient, transport, pumpScope, room, listenerSigner)
                    }
                val sub =
                    InteropDebug.stepSuspending("$scope/listener", "subscribeSpeaker") {
                        listener.subscribeSpeaker(speakerSigner.pubKey)
                    }

                // Collect into a list AND track how the flow ended:
                // * caught == null + completed + receivedCount > 0  → clean FIN
                // * caught != null                                  → leak
                // * !completed within timeout                       → hang
                val received = mutableListOf<Int>()
                val caught = CompletableDeferred<Throwable?>()
                val collectorJob =
                    pumpScope.launch {
                        try {
                            sub.objects.collect { obj ->
                                received += obj.payload.last().toInt() and 0xFF
                            }
                            caught.complete(null)
                        } catch (t: Throwable) {
                            caught.complete(t)
                            throw t
                        }
                    }

                delay(SUBSCRIBE_SETTLE_MS)
                InteropDebug.checkpoint(scope, "phase 1: push $FRAMES_BEFORE_CLOSE frames")
                for (i in 0 until FRAMES_BEFORE_CLOSE) {
                    capture.push(i)
                    delay(FRAME_SPACING_MS)
                }
                delay(SETTLE_MS)

                InteropDebug.stepSuspending("$scope/speaker", "broadcast.close") { broadcast.close() }
                InteropDebug.stepSuspending("$scope/speaker", "speaker.close") { speaker.close() }

                // Current behaviour: the listener's flow stays open
                // (no SUBSCRIBE_DONE → Channel.close path). Confirm
                // the flow does NOT complete within the timeout. If
                // this starts failing because the flow now completes,
                // flip the assertion and rename the test.
                val terminatedEarly =
                    InteropDebug.stepSuspending(scope, "verify listener flow stays open after speaker.close") {
                        withTimeoutOrNull(CLOSE_TIMEOUT_MS) { caught.await() }
                    }
                if (terminatedEarly != null) {
                    fail(
                        "[$scope] listener.objects flow now terminates on speaker.close — " +
                            "this would be the user-friendly behaviour, but it's not the contract this test " +
                            "currently pins. Flip the assertion and rename to " +
                            "speaker_close_terminates_listener_flow_cleanly. End signal: " +
                            InteropDebug.describe(terminatedEarly),
                    )
                }
                if (received.isEmpty()) {
                    fail("[$scope] listener never received any frames before speaker close")
                }
                if (received.size != FRAMES_BEFORE_CLOSE) {
                    fail("[$scope] expected exactly $FRAMES_BEFORE_CLOSE frames, got $received")
                }
                InteropDebug.checkpoint(scope, "received ${received.size} frames; flow still open: $received")

                // Listener-driven teardown: cancel the collector and
                // close the listener; that path IS expected to clean
                // up without leaking an exception.
                collectorJob.cancel()
                runCatching { sub.unsubscribe() }
                runCatching { listener.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    companion object {
        private const val FRAMES_BEFORE_CLOSE = 4
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val SETTLE_MS = 300L
        private const val FRAME_SPACING_MS = 25L
        private const val CLOSE_TIMEOUT_MS = 5_000L

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
