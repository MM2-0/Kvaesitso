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
 * One speaker, two listeners. Listener A unsubscribes mid-stream;
 * listener B keeps receiving frames.
 *
 * Pins the relay's "scoped to one subscription id" teardown — a
 * common UI flow ("leave room while others stay") that should NOT
 * tear down the speaker's broadcast or any other subscriber's flow.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsUnsubscribeInteropTest {
    @Test
    fun unsubscribe_one_listener_keeps_other_subscriptions_alive() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val speakerSigner = NostrSignerInternal(KeyPair())
            val listenerSignerA = NostrSignerInternal(KeyPair())
            val listenerSignerB = NostrSignerInternal(KeyPair())
            val room =
                NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = speakerSigner.pubKey,
                    roomId = "unsub-${System.nanoTime()}",
                )
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)
            val capture = InteropDriverCapture()

            val scope = "unsub"
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
                val listenerA =
                    InteropDebug.stepSuspending("$scope/listenerA", "connectNestsListener") {
                        connectNestsListener(httpClient, transport, pumpScope, room, listenerSignerA)
                    }
                val listenerB =
                    InteropDebug.stepSuspending("$scope/listenerB", "connectNestsListener") {
                        connectNestsListener(httpClient, transport, pumpScope, room, listenerSignerB)
                    }
                val subA =
                    InteropDebug.stepSuspending("$scope/listenerA", "subscribeSpeaker") {
                        listenerA.subscribeSpeaker(speakerSigner.pubKey)
                    }
                val subB =
                    InteropDebug.stepSuspending("$scope/listenerB", "subscribeSpeaker") {
                        listenerB.subscribeSpeaker(speakerSigner.pubKey)
                    }

                // A wants only the first 3; B wants all 6.
                val collectedA =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            subA.objects.take(EARLY_FRAMES).toList()
                        }
                    }
                val collectedB =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            subB.objects.take(EARLY_FRAMES + LATE_FRAMES).toList()
                        }
                    }
                delay(SUBSCRIBE_SETTLE_MS)

                InteropDebug.checkpoint(scope, "phase 1: push $EARLY_FRAMES frames (both A + B receive)")
                for (i in 0 until EARLY_FRAMES) {
                    capture.push(i)
                    delay(FRAME_SPACING_MS)
                }

                val resA = collectedA.await()
                if (resA == null) {
                    fail("[$scope] A timed out waiting for $EARLY_FRAMES frames within ${RECEIVE_TIMEOUT_MS}ms")
                }
                InteropDebug.stepSuspending("$scope/listenerA", "unsubscribe") { subA.unsubscribe() }
                delay(UNSUBSCRIBE_SETTLE_MS)

                InteropDebug.checkpoint(scope, "phase 2: push $LATE_FRAMES more frames (only B should receive)")
                for (i in 0 until LATE_FRAMES) {
                    capture.push(EARLY_FRAMES + i)
                    delay(FRAME_SPACING_MS)
                }

                val resB = collectedB.await()
                if (resB == null) {
                    fail(
                        "[$scope] B timed out waiting for ${EARLY_FRAMES + LATE_FRAMES} frames " +
                            "within ${RECEIVE_TIMEOUT_MS}ms — A's unsubscribe should NOT have torn down B's stream",
                    )
                }
                InteropDebug.checkpoint(scope, "A=${resA.size} frames, B=${resB.size} frames")

                runCatching { subB.unsubscribe() }
                runCatching { listenerA.close() }
                runCatching { listenerB.close() }
                runCatching { broadcast.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    companion object {
        private const val EARLY_FRAMES = 3
        private const val LATE_FRAMES = 3
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val UNSUBSCRIBE_SETTLE_MS = 200L
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
