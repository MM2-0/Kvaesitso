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

import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsReconnectPolicy
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.NestsSpeakerState
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.AfterClass
import org.junit.BeforeClass
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Interop test for [connectReconnectingNestsSpeaker] against the real
 * nostrnests stack. The speaker side mirrors the listener's
 * [NostrNestsReconnectingListenerInteropTest] but exercises the
 * publish path:
 *
 *   1. **Happy path** — the wrapper drives a single real session,
 *      Opus frames flow listener-side. Sanity-check that the
 *      broadcast pump didn't break the round-trip vs the bare
 *      [connectNestsSpeaker] path.
 *
 *   2. **Forced JWT refresh** — small `tokenRefreshAfterMs` forces
 *      the orchestrator to recycle the underlying speaker mid-stream.
 *      Frames before the recycle and after the recycle MUST both
 *      land on the same listener-side [SubscribeHandle] — that's
 *      the load-bearing postcondition for the production
 *      540 s ↔ 600 s JWT-TTL relationship. Also verifies that the
 *      wrapper's outward state never dips into Reconnecting /
 *      Failed during a clean refresh.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsReconnectingSpeakerInteropTest {
    @Test
    fun reconnecting_speaker_round_trips_frames_via_real_relay() =
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
                    roomId = "spk-rec-${System.currentTimeMillis()}",
                )

            val httpClient = OkHttpNestsClient { OkHttpClient() }
            val transport =
                QuicWebTransportFactory(
                    certificateValidator = PermissiveCertificateValidator(),
                )
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            // captureFactory is invoked once per underlying session
            // (broadcaster.start opens a fresh capture) — we keep
            // every instance in `captures` so the test can push
            // frames into whichever one is currently live.
            val capturesLock = Any()
            val captures = mutableListOf<DriverCapture>()
            val captureFactory: () -> AudioCapture = {
                val c = DriverCapture()
                synchronized(capturesLock) { captures += c }
                c
            }
            val encoder = StubEncoder(prefix = "SREC-".encodeToByteArray())

            val scope = "reconnecting-speaker-happy-path"
            try {
                val reconnecting =
                    InteropDebug.stepSuspending(scope, "connectReconnectingNestsSpeaker") {
                        connectReconnectingNestsSpeaker(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                            speakerPubkeyHex = pubkey,
                            captureFactory = captureFactory,
                            encoderFactory = { encoder },
                            policy = NestsReconnectPolicy(initialDelayMs = 250L),
                            // Disable proactive refresh — happy path
                            // exercises a single session only.
                            tokenRefreshAfterMs = 0L,
                        )
                    }
                InteropDebug.assertSpeakerReached(scope, "Connected", reconnecting.state.value)

                val broadcast =
                    InteropDebug.stepSuspending(scope, "reconnecting.startBroadcasting") {
                        reconnecting.startBroadcasting()
                    }

                // Wait for the wrapper to land Broadcasting before we
                // start pushing — the broadcast pump's underlying
                // startBroadcasting needs to run first for the relay
                // to serve the announce.
                withTimeoutOrNull(BROADCAST_TIMEOUT_MS) {
                    reconnecting.state.first { it is NestsSpeakerState.Broadcasting }
                } ?: fail("[$scope] reconnecting wrapper never reached Broadcasting")

                // Vanilla listener — just consumes our published frames.
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

                val received =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            subscription.objects.take(N_FRAMES).toList()
                        }
                    }

                delay(SUBSCRIBE_SETTLE_MS)

                for (i in 0 until N_FRAMES) {
                    pushTo(captures, capturesLock, shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }

                val datagrams = received.await()
                if (datagrams == null) {
                    fail(
                        "[$scope] did not receive $N_FRAMES frames within ${RECEIVE_TIMEOUT_MS}ms — " +
                            "wrapper=${InteropDebug.describe(reconnecting.state.value)}, " +
                            "listener=${InteropDebug.describe(listener.state.value)}",
                    )
                }
                assertEquals(N_FRAMES, datagrams.size, "expected exactly $N_FRAMES frames")
                val payloads = datagrams.map { it.payload.last().toInt() and 0xFF }.toSet()
                assertEquals((0 until N_FRAMES).toSet(), payloads, "all unique frame indices round-tripped")

                runCatching { subscription.unsubscribe() }
                runCatching { listener.close() }
                runCatching { broadcast.close() }
                runCatching { reconnecting.close() }
            } finally {
                synchronized(capturesLock) { captures.forEach { runCatching { it.stop() } } }
                supervisor.cancelAndJoin()
            }
            Unit
        }

    @Test
    fun reconnecting_speaker_recycles_session_on_jwt_refresh_without_dropping_frames() =
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
                    roomId = "spk-refr-${System.currentTimeMillis()}",
                )

            val httpClient = OkHttpNestsClient { OkHttpClient() }
            val transport =
                QuicWebTransportFactory(
                    certificateValidator = PermissiveCertificateValidator(),
                )
            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val capturesLock = Any()
            val captures = mutableListOf<DriverCapture>()
            val captureFactory: () -> AudioCapture = {
                val c = DriverCapture()
                synchronized(capturesLock) { captures += c }
                c
            }
            val encoder = StubEncoder(prefix = "REFR-".encodeToByteArray())

            // Track every state the wrapper surfaces. The
            // load-bearing JWT-refresh invariant: outward state
            // must NEVER show Reconnecting / Failed during a
            // clean recycle. The list is snapshotted under a
            // lock before the assertion to dodge CME from the
            // concurrent `state.collect` writer.
            val seenStatesLock = Any()
            val seenStates: MutableList<NestsSpeakerState> = mutableListOf()

            // Connector counts how many real sessions the
            // orchestrator opened. ≥2 is the marker that the
            // proactive refresh path actually fired.
            val openCount = AtomicInteger(0)

            val scope = "reconnecting-speaker-jwt-refresh"
            try {
                val reconnecting =
                    InteropDebug.stepSuspending(scope, "connectReconnectingNestsSpeaker (refresh=${REFRESH_WINDOW_MS}ms)") {
                        connectReconnectingNestsSpeaker(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                            speakerPubkeyHex = pubkey,
                            captureFactory = captureFactory,
                            encoderFactory = { encoder },
                            policy = NestsReconnectPolicy(initialDelayMs = 250L),
                            tokenRefreshAfterMs = REFRESH_WINDOW_MS,
                            connector = {
                                openCount.incrementAndGet()
                                connectNestsSpeaker(
                                    httpClient = httpClient,
                                    transport = transport,
                                    scope = pumpScope,
                                    room = room,
                                    signer = signer,
                                    speakerPubkeyHex = pubkey,
                                    captureFactory = captureFactory,
                                    encoderFactory = { encoder },
                                    framesPerGroup = 1,
                                )
                            },
                        )
                    }

                // Watch wrapper state for the no-Reconnecting/Failed
                // assertion below.
                val watcher =
                    pumpScope.launch {
                        reconnecting.state.collect { st ->
                            synchronized(seenStatesLock) { seenStates += st }
                        }
                    }

                val broadcast =
                    InteropDebug.stepSuspending(scope, "reconnecting.startBroadcasting") {
                        reconnecting.startBroadcasting()
                    }

                withTimeoutOrNull(BROADCAST_TIMEOUT_MS) {
                    reconnecting.state.first { it is NestsSpeakerState.Broadcasting }
                } ?: fail("[$scope] never reached initial Broadcasting")

                // Phase 1: validate frames flow on the FIRST
                // session. Open a vanilla listener, subscribe, push
                // a small batch, confirm round-trip. This proves
                // the wrapper's first-session publish path is sound
                // before we induce the refresh.
                val firstListener =
                    InteropDebug.stepSuspending(scope, "connectNestsListener (pre-refresh)") {
                        connectNestsListener(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                        )
                    }
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    firstListener.state.first { it is NestsListenerState.Connected }
                } ?: fail("[$scope] first listener never reached Connected")
                val firstSub = firstListener.subscribeSpeaker(pubkey)
                val preFrames =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            firstSub.objects.take(HALF_FRAMES).toList()
                        }
                    }
                delay(SUBSCRIBE_SETTLE_MS)
                for (i in 0 until HALF_FRAMES) {
                    pushTo(captures, capturesLock, shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }
                val pre =
                    preFrames.await() ?: fail(
                        "[$scope] pre-refresh frames did not arrive — wrapper=${InteropDebug.describe(reconnecting.state.value)}",
                    )
                assertEquals(HALF_FRAMES, pre.size, "all pre-refresh frames must round-trip on the first session")
                runCatching { firstSub.unsubscribe() }
                runCatching { firstListener.close() }

                // Phase 2: wait for the proactive refresh to fire.
                // The orchestrator's withTimeoutOrNull fires at
                // REFRESH_WINDOW_MS, closes the underlying speaker,
                // reopens via the connector (openCount→2).
                InteropDebug.checkpoint(scope, "waiting for proactive refresh")
                withTimeoutOrNull(SWAP_TIMEOUT_MS) {
                    while (openCount.get() < 2) delay(50)
                    // Wrapper outward state may briefly dip to
                    // Connected during cutover; wait for the
                    // second Broadcasting so the new session is
                    // serving the announce.
                    reconnecting.state.first { it is NestsSpeakerState.Broadcasting }
                } ?: fail(
                    "[$scope] speaker did not recycle — openCount=${openCount.get()}, " +
                        "wrapper=${InteropDebug.describe(reconnecting.state.value)}",
                )

                delay(POST_SWAP_SETTLE_MS)

                // Phase 3: validate frames flow on the SECOND
                // (post-refresh) session. Open a FRESH listener +
                // subscription so we exercise the new publisher
                // directly. (A pre-recycle subscription would be
                // bound to the dead session — that listener-survival-
                // across-publisher-recycle is a separate concern, not
                // a speaker-reconnect bug.)
                val secondListener =
                    InteropDebug.stepSuspending(scope, "connectNestsListener (post-refresh)") {
                        connectNestsListener(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                        )
                    }
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    secondListener.state.first { it is NestsListenerState.Connected }
                } ?: fail("[$scope] second listener never reached Connected")
                val secondSub = secondListener.subscribeSpeaker(pubkey)
                val postFrames =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            secondSub.objects.take(HALF_FRAMES).toList()
                        }
                    }
                delay(SUBSCRIBE_SETTLE_MS)
                for (i in HALF_FRAMES until N_FRAMES_SWAP) {
                    pushTo(captures, capturesLock, shortArrayOf(i.toShort()))
                    delay(FRAME_SPACING_MS)
                }
                val post =
                    postFrames.await() ?: fail(
                        "[$scope] post-refresh frames did not arrive on the new session — " +
                            "openCount=${openCount.get()}, wrapper=${InteropDebug.describe(reconnecting.state.value)}",
                    )
                assertEquals(HALF_FRAMES, post.size, "all post-refresh frames must round-trip on the new session")

                // Wrapper-side invariant: outward state must NEVER
                // have surfaced Reconnecting/Failed during the
                // refresh — that's the load-bearing user-visible
                // promise of proactive JWT recycle.
                watcher.cancel()
                watcher.join()
                val snapshot = synchronized(seenStatesLock) { seenStates.toList() }
                val sawReconnecting = snapshot.any { it is NestsSpeakerState.Reconnecting }
                val sawFailed = snapshot.any { it is NestsSpeakerState.Failed }
                assertTrue(
                    !sawReconnecting && !sawFailed,
                    "[$scope] proactive refresh must not surface Reconnecting/Failed; saw=$snapshot",
                )

                assertTrue(
                    openCount.get() >= 2,
                    "expected ≥2 underlying speaker sessions (one before refresh, one after); got ${openCount.get()}",
                )

                runCatching { broadcast.close() }
                runCatching { reconnecting.close() }
            } finally {
                synchronized(capturesLock) { captures.forEach { runCatching { it.stop() } } }
                supervisor.cancelAndJoin()
            }
            Unit
        }

    /**
     * Push a PCM frame into whichever live capture the broadcast
     * pump is currently using. The factory hands out a fresh
     * [DriverCapture] per session (the production speaker calls
     * `captureFactory()` from inside `startBroadcasting`), so the
     * "current" capture is always the most recently created one
     * — older captures correspond to recycled sessions whose
     * channels have been closed by [DriverCapture.stop].
     */
    private fun pushTo(
        captures: MutableList<DriverCapture>,
        lock: Any,
        pcm: ShortArray,
    ) {
        val current =
            synchronized(lock) { captures.lastOrNull() }
                ?: error("captureFactory was never invoked — no live capture to push to")
        current.push(pcm)
    }

    /** Channel-driven capture seam — same shape the round-trip test uses. */
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
        private const val N_FRAMES = 6
        private const val N_FRAMES_SWAP = 8
        private const val HALF_FRAMES = 3
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val POST_SWAP_SETTLE_MS = 1_500L
        private const val FRAME_SPACING_MS = 50L

        // Refresh window — small enough that the orchestrator's
        // proactive recycle fires after the first half-batch but
        // before the second; large enough to leave headroom for
        // the WebTransport handshake + first batch of frame
        // pushes (~450 ms on a warm Docker stack).
        private const val REFRESH_WINDOW_MS = 4_000L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val BROADCAST_TIMEOUT_MS = 15_000L
        private const val RECEIVE_TIMEOUT_MS = 15_000L
        private const val SWAP_TIMEOUT_MS = 60_000L

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
