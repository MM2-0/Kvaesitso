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

import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.NestsSpeaker
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.moq.MoqObject
import com.vitorpamplona.nestsclient.moq.MoqProtocolException
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
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
 * Phase-4 multi-peer interop tests. Each test brings up a real
 * nostrnests stack, exercises a multi-publisher / multi-subscriber
 * scenario through the production [connectNestsSpeaker] +
 * [connectNestsListener] entry points, and asserts the relay's fan-out
 * + ordering guarantees end-to-end.
 *
 * Coverage:
 *   - **Multi-listener fan-out** — 1 speaker, 2 listeners; each listener
 *     receives the full frame stream.
 *   - **Multi-speaker** — 2 speakers under the same room; one listener
 *     subscribes to each by pubkey hex; both subscriptions deliver
 *     their respective speaker's frames with no cross-talk.
 *   - **Subscribe-before-announce** — listener subscribes to a
 *     pubkey that has not yet announced. moq-lite-03's relay rejects
 *     immediately with `not found` instead of holding the subscribe
 *     pending an announce (older moq-rs queued; Lite-03 doesn't).
 *     This test pins that contract — flipping the assertion vs the
 *     pre-Lite-03 behaviour where pending subscribes resolved later.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsMultiPeerInteropTest {
    @Test
    fun one_speaker_fans_out_to_two_listeners() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val room = freshRoom(harness, hostPubkey = "0".repeat(64))
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val speakerSigner = NostrSignerInternal(KeyPair())
            val speakerRoom = room.copy(hostPubkey = speakerSigner.pubKey)
            val capture = DriverCapture()

            val listenerSignerA = NostrSignerInternal(KeyPair())
            val listenerSignerB = NostrSignerInternal(KeyPair())

            val scope = "fanout"
            InteropDebug.checkpoint(scope, "room=${speakerRoom.moqNamespace()} speaker=${speakerSigner.pubKey.take(8)}")

            try {
                val speaker =
                    connectSpeaker(
                        scope = pumpScope,
                        room = speakerRoom,
                        signer = speakerSigner,
                        capture = capture,
                        debugScope = scope,
                    )
                InteropDebug.stepSuspending(scope, "speaker.startBroadcasting") { speaker.startBroadcasting() }
                InteropDebug.assertSpeakerReached(scope, "Broadcasting", speaker.state.value)

                val listenerA = connectListener(pumpScope, speakerRoom, listenerSignerA, debugScope = "$scope/listenerA")
                val listenerB = connectListener(pumpScope, speakerRoom, listenerSignerB, debugScope = "$scope/listenerB")

                val subA =
                    InteropDebug.stepSuspending("$scope/listenerA", "subscribeSpeaker(${speakerSigner.pubKey.take(8)})") {
                        listenerA.subscribeSpeaker(speakerSigner.pubKey)
                    }
                val subB =
                    InteropDebug.stepSuspending("$scope/listenerB", "subscribeSpeaker(${speakerSigner.pubKey.take(8)})") {
                        listenerB.subscribeSpeaker(speakerSigner.pubKey)
                    }

                val collectedA = collectFrames(pumpScope, subA, FRAMES_FANOUT)
                val collectedB = collectFrames(pumpScope, subB, FRAMES_FANOUT)

                delay(SUBSCRIBE_SETTLE_MS)
                InteropDebug.checkpoint(scope, "pushing $FRAMES_FANOUT frames")
                for (i in 0 until FRAMES_FANOUT) {
                    capture.push(shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }

                InteropDebug.stepSuspending(scope, "await listener A frames") {
                    assertFrameSequence(collectedA.await(), FRAMES_FANOUT, "$scope/listenerA")
                }
                InteropDebug.stepSuspending(scope, "await listener B frames") {
                    assertFrameSequence(collectedB.await(), FRAMES_FANOUT, "$scope/listenerB")
                }

                runCatching { subA.unsubscribe() }
                runCatching { subB.unsubscribe() }
                runCatching { listenerA.close() }
                runCatching { listenerB.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    @Test
    fun two_speakers_in_same_room_deliver_independently_to_one_listener() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            // Two speakers sharing the same room namespace must publish
            // under different sub-paths (relay enforces `put: [pubkey]`).
            // The room's hostPubkey gates `claims.root` — we use signer A's
            // pubkey as the hostPubkey for the namespace, but each speaker
            // mints their own JWT bound to their own pubkey via `put`.
            val signerA = NostrSignerInternal(KeyPair())
            val signerB = NostrSignerInternal(KeyPair())
            val listenerSigner = NostrSignerInternal(KeyPair())
            val room = freshRoom(harness, hostPubkey = signerA.pubKey)

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val captureA = DriverCapture()
            val captureB = DriverCapture()

            val scope = "multispeaker"
            InteropDebug.checkpoint(scope, "room=${room.moqNamespace()} A=${signerA.pubKey.take(8)} B=${signerB.pubKey.take(8)}")

            try {
                val speakerA =
                    connectSpeaker(
                        pumpScope,
                        room,
                        signerA,
                        captureA,
                        encoderPrefix = "A:",
                        debugScope = "$scope/speakerA",
                    )
                val speakerB =
                    connectSpeaker(
                        pumpScope,
                        room,
                        signerB,
                        captureB,
                        encoderPrefix = "B:",
                        debugScope = "$scope/speakerB",
                    )
                InteropDebug.stepSuspending("$scope/speakerA", "startBroadcasting") { speakerA.startBroadcasting() }
                InteropDebug.assertSpeakerReached("$scope/speakerA", "Broadcasting", speakerA.state.value)
                InteropDebug.stepSuspending("$scope/speakerB", "startBroadcasting") { speakerB.startBroadcasting() }
                InteropDebug.assertSpeakerReached("$scope/speakerB", "Broadcasting", speakerB.state.value)

                val listener = connectListener(pumpScope, room, listenerSigner, debugScope = "$scope/listener")
                val subA =
                    InteropDebug.stepSuspending("$scope/listener", "subscribeSpeaker(A=${signerA.pubKey.take(8)})") {
                        listener.subscribeSpeaker(signerA.pubKey)
                    }
                val subB =
                    InteropDebug.stepSuspending("$scope/listener", "subscribeSpeaker(B=${signerB.pubKey.take(8)})") {
                        listener.subscribeSpeaker(signerB.pubKey)
                    }

                val collectedA = collectFrames(pumpScope, subA, FRAMES_MULTI_SPEAKER)
                val collectedB = collectFrames(pumpScope, subB, FRAMES_MULTI_SPEAKER)

                delay(SUBSCRIBE_SETTLE_MS)
                InteropDebug.checkpoint(scope, "pushing $FRAMES_MULTI_SPEAKER frames into each capture")
                for (i in 0 until FRAMES_MULTI_SPEAKER) {
                    captureA.push(shortArrayOf((i + 0).toShort()))
                    captureB.push(shortArrayOf((i + 100).toShort()))
                    delay(FRAME_SPACING_MS)
                }

                val resA =
                    InteropDebug.stepSuspending(scope, "await speaker A frames") { collectedA.await() }
                val resB =
                    InteropDebug.stepSuspending(scope, "await speaker B frames") { collectedB.await() }
                if (resA == null) {
                    fail(
                        "[$scope] listener never received speaker-A frames within ${RECEIVE_TIMEOUT_MS}ms — " +
                            "speakerA=${InteropDebug.describe(speakerA.state.value)}, " +
                            "listener=${InteropDebug.describe(listener.state.value)}",
                    )
                }
                if (resB == null) {
                    fail(
                        "[$scope] listener never received speaker-B frames within ${RECEIVE_TIMEOUT_MS}ms — " +
                            "speakerB=${InteropDebug.describe(speakerB.state.value)}, " +
                            "listener=${InteropDebug.describe(listener.state.value)}",
                    )
                }
                InteropDebug.checkpoint(scope, "received ${resA.size} A-frames, ${resB.size} B-frames")
                assertEquals(FRAMES_MULTI_SPEAKER, resA.size, "[$scope] speaker A count")
                assertEquals(FRAMES_MULTI_SPEAKER, resB.size, "[$scope] speaker B count")

                resA.forEachIndexed { idx, obj ->
                    assertContentEquals(
                        "A:".encodeToByteArray() + byteArrayOf(idx.toByte()),
                        obj.payload,
                        "[$scope] speaker A frame $idx — must not contain B's payload (no cross-talk)",
                    )
                }
                resB.forEachIndexed { idx, obj ->
                    assertContentEquals(
                        "B:".encodeToByteArray() + byteArrayOf((idx + 100).toByte()),
                        obj.payload,
                        "[$scope] speaker B frame $idx — must not contain A's payload (no cross-talk)",
                    )
                }

                runCatching { subA.unsubscribe() }
                runCatching { subB.unsubscribe() }
                runCatching { listener.close() }
                runCatching { speakerA.close() }
                runCatching { speakerB.close() }
            } finally {
                captureA.stop()
                captureB.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    @Test
    fun subscribe_before_announce_fails_with_not_found() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            // moq-lite-03 contract: a SUBSCRIBE to a not-yet-announced
            // broadcast is rejected immediately rather than held pending
            // an announce. The relay logs `subscribed error err=not found`
            // and FINs the subscribe bidi; our session reader surfaces
            // that as a MoqProtocolException with "FIN before reply".
            //
            // Older moq-rs variants queued such subscribes and resolved
            // them once a publisher appeared; this test pins the new
            // contract so a future regression to the queueing behaviour
            // (or a different reject semantic — e.g. a SubscribeDrop
            // reply) shows up here.
            val speakerSigner = NostrSignerInternal(KeyPair())
            val listenerSigner = NostrSignerInternal(KeyPair())
            val room = freshRoom(harness, hostPubkey = speakerSigner.pubKey)

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val scope = "presub"
            InteropDebug.checkpoint(scope, "room=${room.moqNamespace()} speaker=${speakerSigner.pubKey.take(8)} (never announces)")

            try {
                val listener = connectListener(pumpScope, room, listenerSigner, debugScope = "$scope/listener")
                val rejection =
                    InteropDebug.stepSuspending("$scope/listener", "subscribeSpeaker(${speakerSigner.pubKey.take(8)}) — expect rejection") {
                        runCatching { listener.subscribeSpeaker(speakerSigner.pubKey) }.exceptionOrNull()
                    }
                if (rejection == null) {
                    fail("[$scope] expected MoqProtocolException for subscribe to never-announced broadcast, got success")
                }
                InteropDebug.checkpoint(scope, "rejection=${InteropDebug.describe(rejection)}")
                if (rejection !is MoqProtocolException) {
                    fail(
                        "[$scope] expected MoqProtocolException for subscribe to never-announced broadcast, got " +
                            InteropDebug.describe(rejection),
                    )
                }
                // moq-relay 0.10.x signals "broadcast does not exist"
                // by FIN'ing the subscribe bidi without writing a
                // SubscribeDrop body. Our reader translates that into
                // "subscribe stream FIN before reply for id=...".
                // If a future relay version starts sending an explicit
                // SubscribeDrop with reason="not found", the message
                // would change — accept either form.
                val msg = rejection.message ?: ""
                val accepted = msg.contains("FIN before reply") || msg.contains("not found")
                if (!accepted) {
                    fail("[$scope] unexpected rejection message: $msg")
                }

                runCatching { listener.close() }
            } finally {
                supervisor.cancelAndJoin()
            }
            Unit
        }

    // ---------- Helpers ----------

    private suspend fun connectSpeaker(
        scope: CoroutineScope,
        room: NestsRoomConfig,
        signer: NostrSignerInternal,
        capture: AudioCapture,
        encoderPrefix: String = "FRAME-",
        debugScope: String = "speaker(${signer.pubKey.take(8)})",
    ): NestsSpeaker {
        val speaker =
            InteropDebug.stepSuspending(debugScope, "connectNestsSpeaker") {
                connectNestsSpeaker(
                    httpClient = http,
                    transport = transport,
                    scope = scope,
                    room = room,
                    signer = signer,
                    speakerPubkeyHex = signer.pubKey,
                    captureFactory = { capture },
                    encoderFactory = { StubEncoder(encoderPrefix.encodeToByteArray()) },
                    framesPerGroup = 1,
                )
            }
        InteropDebug.assertSpeakerReached(debugScope, "Connected", speaker.state.value)
        return speaker
    }

    private suspend fun connectListener(
        scope: CoroutineScope,
        room: NestsRoomConfig,
        signer: NostrSignerInternal,
        debugScope: String = "listener(${signer.pubKey.take(8)})",
    ): NestsListener {
        val listener =
            InteropDebug.stepSuspending(debugScope, "connectNestsListener") {
                connectNestsListener(
                    httpClient = http,
                    transport = transport,
                    scope = scope,
                    room = room,
                    signer = signer,
                )
            }
        InteropDebug.assertListenerReached(debugScope, "Connected", listener.state.value)
        return listener
    }

    private fun collectFrames(
        scope: CoroutineScope,
        sub: SubscribeHandle,
        count: Int,
    ) = scope.async {
        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
            sub.objects.take(count).toList()
        }
    }

    private fun assertFrameSequence(
        objs: List<MoqObject>?,
        expectedCount: Int,
        who: String,
    ) {
        if (objs == null) {
            fail(
                "$who did not receive $expectedCount frames within ${RECEIVE_TIMEOUT_MS}ms — " +
                    "verify the speaker actually announced + the relay forwarded; " +
                    "look upstream for ✘ checkpoints in the captured stdout",
            )
        }
        InteropDebug.checkpoint(who, "received ${objs.size} frames (expected $expectedCount)")
        assertEquals(expectedCount, objs.size, "$who frame count")
        objs.forEachIndexed { idx, obj ->
            assertEquals(idx.toLong(), obj.objectId, "$who object id at index $idx")
            assertContentEquals(
                "FRAME-".encodeToByteArray() + byteArrayOf(idx.toByte()),
                obj.payload,
                "$who payload at index $idx",
            )
        }
    }

    private class DriverCapture : AudioCapture {
        private val frames = Channel<ShortArray>(capacity = Channel.UNLIMITED)

        @Volatile private var started: Boolean = false

        override fun start() {
            started = true
        }

        override suspend fun readFrame(): ShortArray? {
            if (!started) return null
            return frames.receiveCatching().getOrNull()
        }

        override fun stop() {
            frames.close()
        }

        fun push(pcm: ShortArray) {
            frames.trySend(pcm)
        }
    }

    private class StubEncoder(
        private val prefix: ByteArray,
    ) : OpusEncoder {
        override fun encode(pcm: ShortArray): ByteArray = prefix + byteArrayOf(pcm.first().toByte())

        override fun release() = Unit
    }

    companion object {
        private const val FRAMES_FANOUT = 6
        private const val FRAMES_MULTI_SPEAKER = 6
        private const val FRAMES_PRESUB = 4
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val FRAME_SPACING_MS = 25L
        private const val RECEIVE_TIMEOUT_MS = 15_000L

        private val http = OkHttpNestsClient { OkHttpClient() }
        private val transport =
            QuicWebTransportFactory(certificateValidator = PermissiveCertificateValidator())
        private var harnessOrNull: NostrNestsHarness? = null

        private fun freshRoom(
            harness: NostrNestsHarness,
            hostPubkey: String,
        ) = NestsRoomConfig(
            authBaseUrl = harness.authBaseUrl,
            endpoint = harness.moqEndpoint,
            hostPubkey = hostPubkey,
            roomId = "mp-${System.nanoTime()}",
        )

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
