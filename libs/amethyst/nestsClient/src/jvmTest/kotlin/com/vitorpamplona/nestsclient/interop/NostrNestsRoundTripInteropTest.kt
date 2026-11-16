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
import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
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
 * End-to-end interop test. Drives the production [connectNestsSpeaker]
 * + [connectNestsListener] entry points against the real nostrnests
 * Docker stack: the speaker claims a moq-lite broadcast suffix
 * (`MoqLitePublisherHandle`), the listener subscribes by speaker
 * pubkey (`broadcast=pubkey`, `track="audio/data"`), the speaker
 * pushes deterministic Opus-shaped payloads through the
 * [NestMoqLiteBroadcaster] pipeline, and the listener collects
 * them via [SubscribeHandle.objects] (which the moq-lite adapter
 * wraps over `MoqLiteFrame` so existing decoder / player code keeps
 * working).
 *
 * Verifies:
 *   - QuicWebTransportFactory + PermissiveCertificateValidator can speak
 *     to the relay's self-signed cert
 *   - JWT minting + WebTransport CONNECT succeed against the reference
 *     relay (moq-lite Lite-03 has no in-band SETUP — the WT handshake
 *     itself is the handshake)
 *   - The WT path = `/<moqNamespace>` + `?jwt=<token>` matches the
 *     relay's `claims.root` exactly
 *   - moq-lite group uni streams (`DataType=0` + GroupHeader +
 *     `varint(size)+payload` frames) round-trip with payload integrity
 *     and monotonic synthesised object ids on the listener adapter
 *
 * One keypair drives both the speaker and the listener — that sidesteps
 * the question of how nostrnests's auth sidecar gates speaker vs. audience
 * (kind 30312 hostlist, NIP-65, etc.) so this test focuses on the
 * transport + moq-lite protocol, not the authorisation policy. A future
 * dual-keypair test can layer permission checks on top.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsRoundTripInteropTest {
    @Test
    fun production_speaker_broadcasts_to_production_listener_via_real_relay() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val signer = NostrSignerInternal(KeyPair())
            val pubkey = signer.pubKey
            val room =
                NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = pubkey,
                    roomId = "rt-${System.currentTimeMillis()}",
                )

            val httpClient = OkHttpNestsClient { OkHttpClient() }
            // Self-signed dev cert on the relay — production uses
            // JdkCertificateValidator; the type system forces us to pass a
            // permissive validator explicitly here.
            val transport =
                QuicWebTransportFactory(
                    certificateValidator = PermissiveCertificateValidator(),
                )

            // Independent supervisor so a pump failure on one side doesn't
            // tear down the test scope before assertions can run.
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val capture = DriverCapture()
            val encoder = StubEncoder(prefix = "FRAME-".encodeToByteArray())

            val scope = "round-trip"
            InteropDebug.checkpoint(scope, "room=${room.moqNamespace()} authBase=${room.authBaseUrl} endpoint=${room.endpoint}")

            try {
                val speaker =
                    InteropDebug.stepSuspending(scope, "connectNestsSpeaker") {
                        connectNestsSpeaker(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                            speakerPubkeyHex = pubkey,
                            captureFactory = { capture },
                            encoderFactory = { encoder },
                            framesPerGroup = 1,
                        )
                    }
                InteropDebug.assertSpeakerReached(scope, "Connected", speaker.state.value)

                val broadcast =
                    InteropDebug.stepSuspending(scope, "speaker.startBroadcasting") {
                        speaker.startBroadcasting()
                    }
                InteropDebug.assertSpeakerReached(scope, "Broadcasting", speaker.state.value)

                val listener =
                    InteropDebug.stepSuspending(scope, "connectNestsListener") {
                        connectNestsListener(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                        )
                    }
                InteropDebug.assertListenerReached(scope, "Connected", listener.state.value)

                val subscription =
                    InteropDebug.stepSuspending(scope, "listener.subscribeSpeaker(pubkey)") {
                        listener.subscribeSpeaker(pubkey)
                    }

                // Start collecting before the speaker publishes anything —
                // moq-lite group frames that arrive before .objects.collect
                // runs are dropped by the per-subscription buffer.
                val received =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            subscription.objects.take(N_FRAMES).toList()
                        }
                    }

                // Tiny breathing room so the SUBSCRIBE_OK lands and the
                // publisher knows there's an inbound subscriber before the
                // first send (otherwise TrackPublisher.send returns false
                // and rolls back the object id without queuing a datagram).
                delay(SUBSCRIBE_SETTLE_MS)

                InteropDebug.checkpoint(scope, "pushing $N_FRAMES frames into capture")
                for (i in 0 until N_FRAMES) {
                    capture.push(shortArrayOf(i.toShort()))
                    // Pace frames slightly so a transient datagram drop
                    // doesn't cluster the loss window onto a single burst.
                    delay(FRAME_SPACING_MS)
                }

                val datagrams =
                    InteropDebug.stepSuspending(scope, "await $N_FRAMES frames on subscription") {
                        received.await()
                    }
                if (datagrams == null) {
                    fail(
                        "[$scope] Did not receive $N_FRAMES moq-lite frames within ${RECEIVE_TIMEOUT_MS}ms — " +
                            "speaker=${InteropDebug.describe(speaker.state.value)}, " +
                            "listener=${InteropDebug.describe(listener.state.value)}",
                    )
                }
                InteropDebug.checkpoint(scope, "received ${datagrams.size} frames (expected $N_FRAMES)")
                assertEquals(N_FRAMES, datagrams.size, "expected exactly $N_FRAMES objects")

                datagrams.forEachIndexed { idx, obj ->
                    assertEquals(
                        idx.toLong(),
                        obj.objectId,
                        "object id at index $idx — MoqLiteNestsListener uses a session-level counter, monotonic across all groups",
                    )
                    assertEquals(
                        idx.toLong(),
                        obj.groupId,
                        "group id at index $idx — broadcaster emits one moq-lite group per Opus frame (audio-rooms NIP draft) so groupId increments 1:1 with the frame index",
                    )
                    assertContentEquals(
                        "FRAME-".encodeToByteArray() + byteArrayOf(idx.toByte()),
                        obj.payload,
                        "payload at index $idx round-tripped through encoder + relay",
                    )
                }

                // Tear down in reverse order. unsubscribe first so the
                // publisher doesn't fan out into a closed listener.
                runCatching { subscription.unsubscribe() }
                runCatching { broadcast.close() }
                runCatching { listener.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            // Force the runBlocking → method return type to Unit; JUnit 4
            // rejects test methods that return anything else.
            Unit
        }

    /**
     * Capture seam driven directly by the test. [push] hands one frame to
     * the broadcaster's pump; [stop] signals end-of-stream so the pump
     * exits cleanly during teardown.
     */
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

    /**
     * Encoder that emits `<prefix><lo-byte-of-first-pcm-sample>` for each
     * frame. Lets the test assert byte-for-byte payload integrity through
     * the relay without pulling a real Opus encoder into the JVM build.
     */
    private class StubEncoder(
        private val prefix: ByteArray,
    ) : OpusEncoder {
        override fun encode(pcm: ShortArray): ByteArray = prefix + byteArrayOf(pcm.first().toByte())

        override fun release() = Unit
    }

    companion object {
        private const val N_FRAMES = 8
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val FRAME_SPACING_MS = 25L
        private const val RECEIVE_TIMEOUT_MS = 15_000L

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
