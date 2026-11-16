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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Production-relay diagnostic suite. Drives the EXACT same speaker /
 * listener entry points the Amethyst app uses
 * ([connectNestsSpeaker] + [connectNestsListener] +
 * [OkHttpNestsClient] + [QuicWebTransportFactory]) — pointed at the
 * real `nostrnests.com` infrastructure instead of the local Docker
 * harness — so we can isolate WHERE audio transmission breaks between
 * two real users.
 *
 * The four tests progressively narrow the failure surface:
 *
 *   1. [auth_minting_works_for_publish_and_listen_paths] — does
 *      `POST https://moq-auth.nostrnests.com/auth` accept our NIP-98
 *      signature for both `publish=true` (speaker) and `publish=false`
 *      (audience)? Failures here are auth / DNS / TLS, NOT transport.
 *   2. [same_user_speaker_and_listener_round_trip] — one keypair drives
 *      both sides. Sidesteps the question of how nostrnests gates
 *      `publish=true` cross-user, so a green here proves
 *      QUIC + WebTransport + moq-lite + announce/subscribe all work
 *      against the production cert + ALPN.
 *   3. [two_users_speaker_publishes_listener_subscribes] — user A is
 *      host + speaker, user B (a different keypair) is the audience.
 *      This is the actual app scenario. If #2 passes and this fails,
 *      the bug is in cross-user authorisation / policy, not transport.
 *   4. [sustained_real_time_cadence_two_users] — same pair as #3 but
 *      pushes 100 frames at the production 20 ms Opus cadence (~2 s of
 *      audio). Catches flow-control / late-subscriber / per-track
 *      buffer issues that only show under steady traffic.
 *
 * Reused from the production app (same wire, same code):
 *   - [OkHttpNestsClient] — NIP-98-signed `POST /auth`
 *   - [QuicWebTransportFactory] — pure-Kotlin QUIC v1 + HTTP/3 + WT,
 *     `JdkCertificateValidator` for the real Let's Encrypt-style cert
 *   - [connectNestsSpeaker] / [connectNestsListener] — full state
 *     machine including SUBSCRIBE_OK settle delay, per-frame group
 *     framing, etc.
 *   - [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster] —
 *     the encode-and-publish loop, identical to what the Android UI
 *     wires into `AudioRecordCapture` + `MediaCodecOpusEncoder`.
 *
 * Substitutes (only because the JVM has no `android.media.*`):
 *   - [InteropDriverCapture] for `AudioRecordCapture` — same
 *     [com.vitorpamplona.nestsclient.audio.AudioCapture] interface.
 *   - [InteropStubEncoder] for `MediaCodecOpusEncoder` — emits a
 *     deterministic byte-prefixed payload so we can verify integrity
 *     end-to-end. Real Opus packets are also byte-array opaque from
 *     the moq-lite layer's perspective; the relay never inspects the
 *     payload, so substituting the encoder doesn't change the wire
 *     behaviour we're testing.
 *
 * No Nostr events. Each test mints a synthetic [NestsRoomConfig] with
 * a random `roomId` so tests don't collide; the host pubkey is the
 * speaker's identity. nostrnests's auth sidecar SHOULD accept this
 * (the JWT just authorises a namespace + publish flag) — if it
 * doesn't, that's itself a finding.
 *
 * Skipped by default. Enable with `-DnestsProd=true`. Override URLs
 * with `-DnestsProdEndpoint=...` and `-DnestsProdAuth=...` if you
 * point at a staging deployment.
 */
class NostrnestsProdAudioTransmissionTest {
    @Test
    fun auth_minting_works_for_publish_and_listen_paths() =
        runBlocking {
            assumeProd()
            val scope = "auth-only"
            val signer = NostrSignerInternal(KeyPair())
            val room = freshRoom(hostPubkey = signer.pubKey)
            val httpClient = OkHttpNestsClient { OkHttpClient() }

            InteropDebug.checkpoint(
                scope,
                "auth=${room.authBaseUrl} endpoint=${room.endpoint} ns=${room.moqNamespace()}",
            )

            val publishToken =
                InteropDebug.stepSuspending(scope, "mintToken(publish=true)") {
                    httpClient.mintToken(room = room, publish = true, signer = signer)
                }
            assertTrue(publishToken.isNotBlank(), "publish JWT must be non-empty")
            assertTrue(publishToken.count { it == '.' } == 2, "JWT must have header.payload.signature")

            val listenToken =
                InteropDebug.stepSuspending(scope, "mintToken(publish=false)") {
                    httpClient.mintToken(room = room, publish = false, signer = signer)
                }
            assertTrue(listenToken.isNotBlank(), "listen JWT must be non-empty")
            assertTrue(listenToken.count { it == '.' } == 2, "JWT must have header.payload.signature")

            InteropDebug.checkpoint(scope, "publish JWT=${publishToken.take(24)}…")
            InteropDebug.checkpoint(scope, "listen  JWT=${listenToken.take(24)}…")
            Unit
        }

    @Test
    fun same_user_speaker_and_listener_round_trip() =
        runBlocking {
            assumeProd()
            val scope = "same-user"

            val signer = NostrSignerInternal(KeyPair())
            val room = freshRoom(hostPubkey = signer.pubKey)
            val httpClient = OkHttpNestsClient { OkHttpClient() }
            // Default JdkCertificateValidator — production cert is
            // real, no permissive validator like the Docker harness.
            val transport = QuicWebTransportFactory()

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val capture = InteropDriverCapture()
            val encoder = InteropStubEncoder(prefix = "PROD-A-".encodeToByteArray())

            try {
                val speaker =
                    InteropDebug.stepSuspending(scope, "connectNestsSpeaker") {
                        connectNestsSpeaker(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                            speakerPubkeyHex = signer.pubKey,
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
                    InteropDebug.stepSuspending(scope, "listener.subscribeSpeaker(self)") {
                        listener.subscribeSpeaker(signer.pubKey)
                    }

                // Start collecting BEFORE pushing — moq-lite group frames
                // dropped before .objects.collect() runs are gone.
                val received =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            subscription.objects.take(N_FRAMES).toList()
                        }
                    }

                // Settle delay so SUBSCRIBE_OK is in flight before the
                // publisher's first send (otherwise TrackPublisher.send
                // returns false and the object id rolls back).
                delay(SUBSCRIBE_SETTLE_MS)

                InteropDebug.checkpoint(scope, "pushing $N_FRAMES frames")
                for (i in 0 until N_FRAMES) {
                    capture.push(byteValue = i)
                    delay(FRAME_SPACING_MS)
                }

                val frames =
                    InteropDebug.stepSuspending(scope, "await $N_FRAMES frames") {
                        received.await()
                    }
                if (frames == null) {
                    fail(
                        "[$scope] Did not receive $N_FRAMES moq-lite frames within ${RECEIVE_TIMEOUT_MS}ms — " +
                            "speaker=${InteropDebug.describe(speaker.state.value)}, " +
                            "listener=${InteropDebug.describe(listener.state.value)}",
                    )
                }
                assertEquals(N_FRAMES, frames.size, "expected $N_FRAMES objects")
                frames.forEachIndexed { idx, obj ->
                    assertContentEquals(
                        "PROD-A-".encodeToByteArray() + byteArrayOf(idx.toByte()),
                        obj.payload,
                        "payload at index $idx round-tripped through nostrnests.com",
                    )
                }

                runCatching { subscription.unsubscribe() }
                runCatching { broadcast.close() }
                runCatching { listener.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    @Test
    fun two_users_speaker_publishes_listener_subscribes() =
        runBlocking {
            assumeProd()
            val scope = "two-users"

            val hostSigner = NostrSignerInternal(KeyPair())
            val audienceSigner = NostrSignerInternal(KeyPair())
            // Host pubkey defines the room namespace and the broadcast
            // suffix. Audience uses a DIFFERENT keypair to mint a
            // listen-only JWT for the same namespace — this is the
            // exact app flow when user B joins user A's room.
            val room = freshRoom(hostPubkey = hostSigner.pubKey)

            val httpClient = OkHttpNestsClient { OkHttpClient() }
            val transport = QuicWebTransportFactory()

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val capture = InteropDriverCapture()
            val encoder = InteropStubEncoder(prefix = "PROD-B-".encodeToByteArray())

            InteropDebug.checkpoint(
                scope,
                "host=${hostSigner.pubKey.take(8)}… audience=${audienceSigner.pubKey.take(8)}… " +
                    "ns=${room.moqNamespace()}",
            )

            try {
                val speaker =
                    InteropDebug.stepSuspending(scope, "host: connectNestsSpeaker") {
                        connectNestsSpeaker(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = hostSigner,
                            speakerPubkeyHex = hostSigner.pubKey,
                            captureFactory = { capture },
                            encoderFactory = { encoder },
                            framesPerGroup = 1,
                        )
                    }
                InteropDebug.assertSpeakerReached(scope, "Connected", speaker.state.value)

                val broadcast =
                    InteropDebug.stepSuspending(scope, "host: startBroadcasting") {
                        speaker.startBroadcasting()
                    }
                InteropDebug.assertSpeakerReached(scope, "Broadcasting", speaker.state.value)

                val listener =
                    InteropDebug.stepSuspending(scope, "audience: connectNestsListener") {
                        connectNestsListener(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = audienceSigner,
                        )
                    }
                InteropDebug.assertListenerReached(scope, "Connected", listener.state.value)

                val subscription =
                    InteropDebug.stepSuspending(scope, "audience: subscribeSpeaker(host)") {
                        listener.subscribeSpeaker(hostSigner.pubKey)
                    }

                val received =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                            subscription.objects.take(N_FRAMES).toList()
                        }
                    }
                delay(SUBSCRIBE_SETTLE_MS)

                InteropDebug.checkpoint(scope, "host pushing $N_FRAMES frames")
                for (i in 0 until N_FRAMES) {
                    capture.push(byteValue = i)
                    delay(FRAME_SPACING_MS)
                }

                val frames =
                    InteropDebug.stepSuspending(scope, "audience: await $N_FRAMES frames") {
                        received.await()
                    }
                if (frames == null) {
                    fail(
                        "[$scope] audience did not receive $N_FRAMES frames within " +
                            "${RECEIVE_TIMEOUT_MS}ms — speaker=" +
                            InteropDebug.describe(speaker.state.value) +
                            ", listener=" + InteropDebug.describe(listener.state.value) +
                            ". If same-user round-trip passes but this fails, the bug is " +
                            "cross-user authorisation (nostrnests auth sidecar policy) or " +
                            "broadcast-suffix routing, NOT transport.",
                    )
                }
                assertEquals(N_FRAMES, frames.size, "audience must see exactly $N_FRAMES objects")
                frames.forEachIndexed { idx, obj ->
                    assertContentEquals(
                        "PROD-B-".encodeToByteArray() + byteArrayOf(idx.toByte()),
                        obj.payload,
                        "audience payload at index $idx must match what the host encoded",
                    )
                }

                runCatching { subscription.unsubscribe() }
                runCatching { broadcast.close() }
                runCatching { listener.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    @Test
    fun sustained_real_time_cadence_two_users() =
        runBlocking {
            assumeProd()
            val scope = "two-users-sustained"

            val hostSigner = NostrSignerInternal(KeyPair())
            val audienceSigner = NostrSignerInternal(KeyPair())
            val room = freshRoom(hostPubkey = hostSigner.pubKey)

            val httpClient = OkHttpNestsClient { OkHttpClient() }
            val transport = QuicWebTransportFactory()

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            val capture = InteropDriverCapture()
            // Realistic Opus payload size: ~80 bytes for a 20 ms / 32
            // kbps frame. The relay never inspects the bytes, but the
            // size matters for stream / datagram path choices.
            val payloadPrefix = ByteArray(79) { 0x4F.toByte() } + byteArrayOf(0x00)
            val encoder = InteropStubEncoder(prefix = payloadPrefix)

            InteropDebug.checkpoint(
                scope,
                "host=${hostSigner.pubKey.take(8)}… audience=${audienceSigner.pubKey.take(8)}… " +
                    "ns=${room.moqNamespace()} frames=$REAL_TIME_FRAMES cadence=${REAL_TIME_FRAME_MS}ms " +
                    "(~${REAL_TIME_FRAMES * REAL_TIME_FRAME_MS}ms of audio)",
            )

            try {
                val speaker =
                    InteropDebug.stepSuspending(scope, "host: connectNestsSpeaker") {
                        connectNestsSpeaker(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = hostSigner,
                            speakerPubkeyHex = hostSigner.pubKey,
                            captureFactory = { capture },
                            encoderFactory = { encoder },
                            framesPerGroup = 1,
                        )
                    }
                InteropDebug.assertSpeakerReached(scope, "Connected", speaker.state.value)
                val broadcast = speaker.startBroadcasting()
                InteropDebug.assertSpeakerReached(scope, "Broadcasting", speaker.state.value)

                val listener =
                    connectNestsListener(
                        httpClient = httpClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = audienceSigner,
                    )
                InteropDebug.assertListenerReached(scope, "Connected", listener.state.value)

                val subscription = listener.subscribeSpeaker(hostSigner.pubKey)

                // Drain into a thread-safe list as frames arrive so we
                // can dump partial contents on timeout — the previous
                // .take(N).toList() variant discarded everything on the
                // way to the deadline.
                data class Arrival(
                    val wallMs: Long,
                    val groupId: Long,
                    val objectId: Long,
                    val firstByte: Int,
                )
                val received = java.util.concurrent.CopyOnWriteArrayList<Arrival>()
                val firstArrivalAtomic =
                    java.util.concurrent.atomic
                        .AtomicLong(-1L)
                val collectStart = System.currentTimeMillis()
                // onEach records each arrival into the external list
                // BEFORE take() trims the flow — so even if the
                // surrounding withTimeoutOrNull aborts, the partial
                // contents are preserved for diagnostic dump.
                val collected =
                    async(pumpScope.coroutineContext) {
                        withTimeoutOrNull(REAL_TIME_RECEIVE_TIMEOUT_MS) {
                            subscription.objects
                                .onEach { obj ->
                                    val now = System.currentTimeMillis()
                                    firstArrivalAtomic.compareAndSet(-1L, now)
                                    received +=
                                        Arrival(
                                            wallMs = now - collectStart,
                                            groupId = obj.groupId,
                                            objectId = obj.objectId,
                                            firstByte =
                                                obj.payload
                                                    .firstOrNull()
                                                    ?.toInt()
                                                    ?.and(0xFF) ?: -1,
                                        )
                                }.take(REAL_TIME_FRAMES)
                                .toList()
                        }
                    }
                delay(SUBSCRIBE_SETTLE_MS)

                val started = System.currentTimeMillis()
                for (i in 0 until REAL_TIME_FRAMES) {
                    capture.push(byteValue = i and 0xFF)
                    delay(REAL_TIME_FRAME_MS)
                }
                val pumpDurationMs = System.currentTimeMillis() - started

                collected.await()

                val frames = received.toList()
                val firstArrivalMs = firstArrivalAtomic.get().let { if (it < 0) -1 else it - collectStart }
                val lastArrivalMs = frames.lastOrNull()?.wallMs ?: -1L

                // Identify gaps in groupId. moq-lite is dense per
                // broadcast — a gap at idx N means the relay dropped
                // (or never delivered) the uni stream for that group.
                val seenGroups = frames.map { it.groupId }.toSortedSet()
                val expectedGroups = (0L until REAL_TIME_FRAMES.toLong()).toList()
                val missing = expectedGroups - seenGroups
                val gapRuns =
                    buildString {
                        var run: Pair<Long, Long>? = null
                        for (g in missing) {
                            run =
                                when {
                                    run == null -> {
                                        g to g
                                    }

                                    g == run.second + 1 -> {
                                        run.first to g
                                    }

                                    else -> {
                                        if (isNotEmpty()) append(",")
                                        append(if (run.first == run.second) "${run.first}" else "${run.first}-${run.second}")
                                        g to g
                                    }
                                }
                        }
                        if (run != null) {
                            if (isNotEmpty()) append(",")
                            append(if (run.first == run.second) "${run.first}" else "${run.first}-${run.second}")
                        }
                    }

                InteropDebug.checkpoint(
                    scope,
                    "received=${frames.size}/$REAL_TIME_FRAMES " +
                        "firstArrival=${firstArrivalMs}ms lastArrival=${lastArrivalMs}ms " +
                        "pumpDuration=${pumpDurationMs}ms missingGroups=[$gapRuns]",
                )
                // Print first 20 + last 20 arrivals so the diagnostic
                // captures both the front and tail of the sequence
                // without flooding stdout.
                val sample =
                    if (frames.size <= 40) frames else frames.take(20) + frames.takeLast(20)
                sample.forEach { a ->
                    InteropDebug.checkpoint(
                        scope,
                        "  arrival t=${a.wallMs}ms groupId=${a.groupId} objectId=${a.objectId} firstByte=0x${a.firstByte.toString(16)}",
                    )
                }

                if (frames.size < REAL_TIME_FRAMES) {
                    fail(
                        "[$scope] received ${frames.size}/$REAL_TIME_FRAMES frames " +
                            "within ${REAL_TIME_RECEIVE_TIMEOUT_MS}ms — " +
                            "speaker=" + InteropDebug.describe(speaker.state.value) +
                            ", listener=" + InteropDebug.describe(listener.state.value) +
                            ", pumpDuration=${pumpDurationMs}ms" +
                            ", firstArrival=${firstArrivalMs}ms" +
                            ", lastArrival=${lastArrivalMs}ms" +
                            ", missingGroups=[$gapRuns]. " +
                            "Pattern hints: firstArrival=-1 → SUBSCRIBE never fanned out; " +
                            "lastArrival≪pumpDuration with frames<<N → stream stalled mid-flight; " +
                            "scattered missingGroups → datagram drops or per-subscription buffer overflow.",
                    )
                }
                // Order check on success: groupId must be monotonic and
                // dense; gaps would mean a uni stream was dropped.
                frames.forEachIndexed { idx, a ->
                    assertEquals(
                        idx.toLong(),
                        a.groupId,
                        "groupId gap at index $idx",
                    )
                }

                runCatching { subscription.unsubscribe() }
                runCatching { broadcast.close() }
                runCatching { listener.close() }
                runCatching { speaker.close() }
            } finally {
                capture.stop()
                supervisor.cancelAndJoin()
            }
            Unit
        }

    /**
     * Instrumented variant of [sustained_real_time_cadence_two_users]
     * that BYPASSES [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster]
     * and calls [com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle.send]
     * directly so we can capture per-frame send results.
     *
     * Motivation: the production broadcaster wraps every send in
     * `runCatching { publisher.send(opus); publisher.endGroup() }.onFailure {…}`
     * and IGNORES the boolean. `send` returns false when
     * `publisherClosed` or `inboundSubs.isEmpty()`, AND
     * `runCatching { uni.write(framed) }` swallows write failures while
     * still returning true. Frame loss is structurally invisible to the
     * broadcaster.
     *
     * This test reproduces the speaker side WITHOUT the broadcaster:
     *   - same auth (OkHttpNestsClient.mintToken)
     *   - same transport (QuicWebTransportFactory)
     *   - same MoqLiteSession.client + session.publish() the
     *     broadcaster uses internally
     *   - records [MoqLitePublisherHandle.send]'s Boolean per frame and
     *     prints it alongside the listener's gap pattern
     *
     * Diagnostic table interpretation (sent vs. received per group):
     *   - send=false  → speaker dropped the frame at the moq-lite layer
     *     (no inbound subscribers OR publisher closed)
     *   - send=true, received=false → frame was queued by moq-lite, then
     *     lost downstream (uni-stream write swallowed an exception, OR
     *     QUIC flow control / relay dropped the stream)
     *   - send=true, received=true → frame arrived (control)
     */
    @Test
    fun sustained_per_frame_send_outcomes_two_users() =
        runBlocking {
            assumeProd()
            val scope = "send-trace"

            val hostSigner = NostrSignerInternal(KeyPair())
            val audienceSigner = NostrSignerInternal(KeyPair())
            val room = freshRoom(hostPubkey = hostSigner.pubKey)
            val httpClient = OkHttpNestsClient { OkHttpClient() }
            val transport = QuicWebTransportFactory()

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            InteropDebug.checkpoint(
                scope,
                "host=${hostSigner.pubKey.take(8)}… audience=${audienceSigner.pubKey.take(8)}… " +
                    "ns=${room.moqNamespace()} frames=$REAL_TIME_FRAMES cadence=${REAL_TIME_FRAME_MS}ms",
            )

            // ---- speaker side: build session manually so we can call
            // session.publish() directly and capture per-frame send results.
            val publishToken =
                InteropDebug.stepSuspending(scope, "host: mintToken(publish=true)") {
                    httpClient.mintToken(room = room, publish = true, signer = hostSigner)
                }
            val (authority, path) =
                com.vitorpamplona.nestsclient.buildRelayConnectTarget(
                    endpoint = room.endpoint,
                    namespace = room.moqNamespace(),
                    token = publishToken,
                )
            val speakerWt =
                InteropDebug.stepSuspending(scope, "host: WebTransport.connect") {
                    transport.connect(authority = authority, path = path, bearerToken = null)
                }
            val speakerSession =
                com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
                    .client(speakerWt, pumpScope)
            val publisher =
                InteropDebug.stepSuspending(scope, "host: session.publish(broadcastSuffix=hostPub)") {
                    speakerSession.publish(broadcastSuffix = hostSigner.pubKey, track = "audio/data")
                }

            // ---- listener side: production code path, unchanged.
            val listener =
                InteropDebug.stepSuspending(scope, "audience: connectNestsListener") {
                    connectNestsListener(
                        httpClient = httpClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = audienceSigner,
                    )
                }
            InteropDebug.assertListenerReached(scope, "Connected", listener.state.value)

            val subscription =
                InteropDebug.stepSuspending(scope, "audience: subscribeSpeaker(host)") {
                    listener.subscribeSpeaker(hostSigner.pubKey)
                }

            data class Arrival(
                val wallMs: Long,
                val groupId: Long,
            )
            val received = java.util.concurrent.CopyOnWriteArrayList<Arrival>()
            val sendOutcomes = BooleanArray(REAL_TIME_FRAMES)
            val endGroupErrors = arrayOfNulls<String>(REAL_TIME_FRAMES)
            val collectStart = System.currentTimeMillis()
            val collected =
                async(pumpScope.coroutineContext) {
                    withTimeoutOrNull(REAL_TIME_RECEIVE_TIMEOUT_MS) {
                        subscription.objects
                            .onEach { obj ->
                                received += Arrival(System.currentTimeMillis() - collectStart, obj.groupId)
                            }.take(REAL_TIME_FRAMES)
                            .toList()
                    }
                }

            try {
                delay(SUBSCRIBE_SETTLE_MS)

                val payloadPrefix = ByteArray(79) { 0x4F.toByte() } + byteArrayOf(0x00)
                val started = System.currentTimeMillis()
                for (i in 0 until REAL_TIME_FRAMES) {
                    val payload = payloadPrefix + byteArrayOf(i.toByte())
                    sendOutcomes[i] = publisher.send(payload)
                    runCatching { publisher.endGroup() }
                        .onFailure { endGroupErrors[i] = it::class.simpleName + ": " + it.message }
                    delay(REAL_TIME_FRAME_MS)
                }
                val pumpDurationMs = System.currentTimeMillis() - started

                collected.await()
                val frames = received.toList()
                val receivedGroups = frames.map { it.groupId }.toHashSet()

                val sendCount = sendOutcomes.count { it }
                val firstFalseSend = sendOutcomes.indexOfFirst { !it }
                val sendOnlyMissing = (0 until REAL_TIME_FRAMES).filter { sendOutcomes[it] && it.toLong() !in receivedGroups }
                val firstUnreceivedAfterSend = sendOnlyMissing.firstOrNull() ?: -1

                InteropDebug.checkpoint(
                    scope,
                    "sendTrue=$sendCount/$REAL_TIME_FRAMES received=${frames.size}/$REAL_TIME_FRAMES " +
                        "firstFalseSend=$firstFalseSend " +
                        "firstSentButLost=$firstUnreceivedAfterSend " +
                        "pumpDuration=${pumpDurationMs}ms",
                )
                // Per-frame table: SEND TRUE / FALSE  +  RECV YES / NO
                for (i in 0 until REAL_TIME_FRAMES) {
                    val sentOk = sendOutcomes[i]
                    val recv = i.toLong() in receivedGroups
                    val tag =
                        when {
                            sentOk && recv -> "ok"
                            sentOk && !recv -> "SENT-LOST"
                            !sentOk && !recv -> "send=false"
                            else -> "ghost?"
                        }
                    val err = endGroupErrors[i]?.let { ", endGroup err=$it" } ?: ""
                    InteropDebug.checkpoint(scope, "  i=$i $tag$err")
                }

                if (firstFalseSend >= 0) {
                    fail(
                        "[$scope] publisher.send returned false starting at frame $firstFalseSend — " +
                            "speaker's MoqLiteSession dropped the frame at the source. " +
                            "Likely cause: inboundSubs cleared (relay tore down our SUBSCRIBE bidi) or publisherClosed.",
                    )
                }
                if (firstUnreceivedAfterSend >= 0) {
                    fail(
                        "[$scope] publisher.send returned true for ALL frames, but listener missed " +
                            "${REAL_TIME_FRAMES - frames.size} of them starting at $firstUnreceivedAfterSend. " +
                            "Loss is downstream of moq-lite — uni-stream write threw (swallowed by runCatching), " +
                            "QUIC flow control wedged, or the relay dropped the stream.",
                    )
                }
            } finally {
                runCatching { subscription.unsubscribe() }
                runCatching { publisher.close() }
                runCatching { speakerWt.close(0, "test done") }
                runCatching { listener.close() }
                supervisor.cancelAndJoin()
            }
            Unit
        }

    // ====================================================================
    // Sweep matrix — calls SendTraceScenario with one dimension varied at
    // a time so a single test run on a real network produces a diagnosis
    // table covering cadence, frame count, payload, group packing,
    // late-join, mid-stream pause, fan-out, and slow consumer.
    //
    // Every scenario is wrapped by [withProdSpeakerAndListeners], which
    // mints publish + listen JWTs through the production auth side, opens
    // the QUIC + WebTransport + moq-lite session the same way
    // [connectNestsSpeaker] / [connectNestsListener] do, and tears down
    // in the right order on success / failure.
    //
    // None of these scenarios assert "all frames received" by default —
    // we want the cliff to surface in the report instead of failing fast,
    // so the diagnostic dump is visible for every scenario in one run.
    // The few scenarios where we DO expect success (e.g. very slow
    // cadence) pass `expectAllReceived = true` explicitly.
    // ====================================================================

    @Test
    fun sweep_baseline_100x20ms() = runProdScenarioOrSkip("sweep-baseline", Scenario(frameCount = 100, cadenceMs = 20L))

    @Test fun sweep_cadence_5ms() = runProdScenarioOrSkip("sweep-cad5", Scenario(frameCount = 100, cadenceMs = 5L))

    @Test fun sweep_cadence_10ms() = runProdScenarioOrSkip("sweep-cad10", Scenario(frameCount = 100, cadenceMs = 10L))

    @Test fun sweep_cadence_40ms() = runProdScenarioOrSkip("sweep-cad40", Scenario(frameCount = 100, cadenceMs = 40L))

    @Test fun sweep_cadence_80ms() = runProdScenarioOrSkip("sweep-cad80", Scenario(frameCount = 100, cadenceMs = 80L))

    @Test fun sweep_cadence_200ms() = runProdScenarioOrSkip("sweep-cad200", Scenario(frameCount = 100, cadenceMs = 200L))

    @Test
    fun sweep_burst_no_cadence() =
        runProdScenarioOrSkip(
            "sweep-burst",
            Scenario(frameCount = 100, cadenceMs = 0L, receiveGraceMs = 30_000L),
        )

    @Test
    fun sweep_frames_50() =
        runProdScenarioOrSkip(
            "sweep-frames50",
            Scenario(frameCount = 50, cadenceMs = 20L),
        )

    @Test
    fun sweep_frames_200() =
        runProdScenarioOrSkip(
            "sweep-frames200",
            Scenario(frameCount = 200, cadenceMs = 20L, receiveGraceMs = 45_000L),
        )

    @Test
    fun sweep_frames_400() =
        runProdScenarioOrSkip(
            "sweep-frames400",
            Scenario(frameCount = 400, cadenceMs = 20L, receiveGraceMs = 60_000L),
        )

    @Test
    fun sweep_payload_1kb() =
        runProdScenarioOrSkip(
            "sweep-1kb",
            Scenario(frameCount = 100, cadenceMs = 20L, payloadBytes = 1024),
        )

    @Test
    fun sweep_payload_4kb() =
        runProdScenarioOrSkip(
            "sweep-4kb",
            Scenario(frameCount = 100, cadenceMs = 20L, payloadBytes = 4096),
        )

    @Test
    fun sweep_payload_16kb() =
        runProdScenarioOrSkip(
            "sweep-16kb",
            Scenario(frameCount = 100, cadenceMs = 20L, payloadBytes = 16384),
        )

    @Test
    fun sweep_frames_per_group_5() =
        runProdScenarioOrSkip(
            "sweep-fpg5",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 5),
        )

    @Test
    fun sweep_frames_per_group_10() =
        runProdScenarioOrSkip(
            "sweep-fpg10",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 10),
        )

    @Test
    fun sweep_frames_per_group_20() =
        runProdScenarioOrSkip(
            "sweep-fpg20",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 20),
        )

    // The production default. With a 20 ms cadence this batches a full
    // second of audio into one uni stream — so the e2e-latency line for
    // this scenario is the direct measurement of the send delay the
    // production app exhibits today. Compare its timeToFirstFrame /
    // p50 against sweep_frames_per_group_5 and the framesPerGroup=1
    // baseline to read the batching-vs-latency tradeoff curve.
    @Test
    fun sweep_frames_per_group_50_production_default() =
        runProdScenarioOrSkip(
            "sweep-fpg50",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 50),
        )

    @Test
    fun sweep_frames_per_group_all() =
        runProdScenarioOrSkip(
            "sweep-fpg-all",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 100),
        )

    @Test
    fun sweep_late_subscribe_after_25() =
        runProdScenarioOrSkip(
            "sweep-late25",
            Scenario(frameCount = 100, cadenceMs = 20L, subscribeAtFrame = 25),
        )

    @Test
    fun sweep_late_subscribe_after_50() =
        runProdScenarioOrSkip(
            "sweep-late50",
            Scenario(frameCount = 100, cadenceMs = 20L, subscribeAtFrame = 50),
        )

    @Test
    fun sweep_mid_pause_50_5s() =
        runProdScenarioOrSkip(
            "sweep-pause",
            Scenario(
                frameCount = 100,
                cadenceMs = 20L,
                pauseAfterFrame = 50,
                pauseDurationMs = 5_000L,
                receiveGraceMs = 45_000L,
            ),
        )

    @Test
    fun sweep_two_subscribers() =
        runProdScenarioOrSkip(
            "sweep-2subs",
            Scenario(frameCount = 100, cadenceMs = 20L, parallelSubscriptions = 2),
        )

    @Test
    fun sweep_three_subscribers() =
        runProdScenarioOrSkip(
            "sweep-3subs",
            Scenario(frameCount = 100, cadenceMs = 20L, parallelSubscriptions = 3),
        )

    @Test
    fun sweep_slow_consumer_50ms() =
        runProdScenarioOrSkip(
            "sweep-slowconsumer",
            Scenario(
                frameCount = 100,
                cadenceMs = 20L,
                listenerSlowConsumerMs = 50L,
                receiveGraceMs = 60_000L,
            ),
        )

    @Test
    fun sweep_long_run_30s() =
        runProdScenarioOrSkip(
            "sweep-30s",
            Scenario(
                frameCount = 1500,
                cadenceMs = 20L,
                receiveGraceMs = 60_000L,
                verbosePerFrame = false,
            ),
        )

    @Test
    fun sweep_extreme_long_run_120s() =
        runProdScenarioOrSkip(
            "sweep-120s",
            Scenario(
                frameCount = 6000,
                cadenceMs = 20L,
                receiveGraceMs = 180_000L,
                verbosePerFrame = false,
            ),
        )

    /**
     * Wraps one scenario in `runBlocking { assumeProd(); withProd(…); … }`
     * boilerplate so each `@Test` method is just a single Scenario
     * literal. Skips cleanly when `-DnestsProd=true` isn't set.
     */
    private fun runProdScenarioOrSkip(
        scope: String,
        scenario: Scenario,
        expectAllReceived: Boolean = false,
    ) = runBlocking {
        assumeProd()
        withProdSpeakerAndListeners(scope, scenario.parallelSubscriptions) { publisher, listeners, hostPub, pumpScope, flowControlSnapshot, listenerFlowControlSnapshots ->
            val result =
                SendTraceScenario.run(
                    scope = scope,
                    publisher = publisher,
                    listeners = listeners,
                    speakerPubkeyHex = hostPub,
                    scenario = scenario,
                    pumpScope = pumpScope,
                    flowControlSnapshot = flowControlSnapshot,
                    listenerFlowControlSnapshots = listenerFlowControlSnapshots,
                )
            SendTraceScenario.reportAndAssert(scope, result, expectAllReceived)
        }
        Unit
    }

    /**
     * Build a host publisher (manual MoqLiteSession + session.publish so
     * we bypass [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster]
     * and can call [com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle.send]
     * directly) plus N listener-side [com.vitorpamplona.nestsclient.NestsListener]s,
     * each with their own keypair so the relay sees them as distinct
     * audience members. Tears everything down (in the right order) on
     * success or failure.
     */
    private suspend fun withProdSpeakerAndListeners(
        scope: String,
        listenerCount: Int,
        block: suspend (
            publisher: com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle,
            listeners: List<com.vitorpamplona.nestsclient.NestsListener>,
            speakerPubkeyHex: String,
            pumpScope: CoroutineScope,
            flowControlSnapshot: suspend () -> com.vitorpamplona.quic.connection.QuicFlowControlSnapshot,
            listenerFlowControlSnapshots: List<suspend () -> com.vitorpamplona.quic.connection.QuicFlowControlSnapshot>,
        ) -> Unit,
    ) {
        val hostSigner = NostrSignerInternal(KeyPair())
        val room = freshRoom(hostPubkey = hostSigner.pubKey)
        val httpClient = OkHttpNestsClient { OkHttpClient() }
        val transport = QuicWebTransportFactory()

        val supervisor = SupervisorJob()
        val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

        InteropDebug.checkpoint(scope, "auth=${room.authBaseUrl} endpoint=${room.endpoint} ns=${room.moqNamespace()}")

        val publishToken =
            InteropDebug.stepSuspending(scope, "host: mintToken(publish=true)") {
                httpClient.mintToken(room = room, publish = true, signer = hostSigner)
            }
        val (authority, path) =
            com.vitorpamplona.nestsclient
                .buildRelayConnectTarget(
                    endpoint = room.endpoint,
                    namespace = room.moqNamespace(),
                    token = publishToken,
                )
        val speakerWt =
            InteropDebug.stepSuspending(scope, "host: WebTransport.connect") {
                transport.connect(authority = authority, path = path, bearerToken = null)
            }
        val speakerSession =
            com.vitorpamplona.nestsclient.moq.lite
                .MoqLiteSession
                .client(speakerWt, pumpScope)
        val publisher =
            InteropDebug.stepSuspending(scope, "host: session.publish(broadcastSuffix=hostPub)") {
                speakerSession.publish(broadcastSuffix = hostSigner.pubKey, track = "audio/data")
            }

        val listeners = mutableListOf<com.vitorpamplona.nestsclient.NestsListener>()
        try {
            for (i in 0 until listenerCount) {
                val audienceSigner = NostrSignerInternal(KeyPair())
                val listener =
                    InteropDebug.stepSuspending(scope, "audience[$i]: connectNestsListener") {
                        connectNestsListener(
                            httpClient = httpClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = audienceSigner,
                        )
                    }
                InteropDebug.assertListenerReached(scope, "Connected", listener.state.value)
                listeners += listener
            }

            // Diagnostics passthrough: cast the speaker WT and each
            // listener's underlying WT to the concrete QUIC adapter
            // so the scenario can read flow-control snapshots from
            // both sides. The cliff investigation
            // (`nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`)
            // needs the listener-side numbers because the
            // peer-initiated stream count is what bites us at the
            // audience.
            val quicSpeakerWt =
                speakerWt as com.vitorpamplona.nestsclient.transport.QuicWebTransportSession
            val listenerSnapshots =
                listeners.map { listener ->
                    val listenerWt =
                        (listener as com.vitorpamplona.nestsclient.MoqLiteNestsListener)
                            .transport as com.vitorpamplona.nestsclient.transport.QuicWebTransportSession
                    val supplier: suspend () -> com.vitorpamplona.quic.connection.QuicFlowControlSnapshot =
                        { listenerWt.quicFlowControlSnapshot() }
                    supplier
                }
            block(
                publisher,
                listeners,
                hostSigner.pubKey,
                pumpScope,
                { quicSpeakerWt.quicFlowControlSnapshot() },
                listenerSnapshots,
            )
        } finally {
            for (listener in listeners) {
                runCatching { listener.close() }
            }
            runCatching { publisher.close() }
            runCatching { speakerWt.close(0, "test done") }
            supervisor.cancelAndJoin()
        }
    }

    // ----- helpers -----

    private fun freshRoom(hostPubkey: String): NestsRoomConfig =
        NestsRoomConfig(
            authBaseUrl = System.getProperty(PROD_AUTH_PROPERTY) ?: DEFAULT_AUTH_URL,
            endpoint = System.getProperty(PROD_ENDPOINT_PROPERTY) ?: DEFAULT_ENDPOINT_URL,
            hostPubkey = hostPubkey,
            roomId = "prod-diag-${System.currentTimeMillis()}-${(0..9999).random()}",
        )

    private fun assumeProd() {
        val enabled = System.getProperty(PROD_PROPERTY) == "true"
        assumeTrue(
            "Skipping nostrnests.com production test — set -D$PROD_PROPERTY=true to enable. " +
                "Override URLs with -D$PROD_ENDPOINT_PROPERTY=... and -D$PROD_AUTH_PROPERTY=...",
            enabled,
        )
    }

    companion object {
        // Hardcoded in the Android app's NestsServersScreen — same
        // values the production app ships with.
        private const val DEFAULT_ENDPOINT_URL = "https://moq.nostrnests.com:4443"
        private const val DEFAULT_AUTH_URL = "https://moq-auth.nostrnests.com"

        private const val PROD_PROPERTY = "nestsProd"
        private const val PROD_ENDPOINT_PROPERTY = "nestsProdEndpoint"
        private const val PROD_AUTH_PROPERTY = "nestsProdAuth"

        // Round-trip tunables — same shape as the Docker harness round
        // trip, large enough to detect drops but small enough to keep
        // the test under 30 s wall-clock against a real WAN relay.
        private const val N_FRAMES = 8
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val FRAME_SPACING_MS = 25L
        private const val RECEIVE_TIMEOUT_MS = 20_000L

        // Sustained cadence: ~2 s of audio at the production Opus
        // frame size (20 ms). Same cadence the Android broadcaster
        // hits in real life.
        private const val REAL_TIME_FRAMES = 100
        private const val REAL_TIME_FRAME_MS = 20L
        private const val REAL_TIME_RECEIVE_TIMEOUT_MS = 30_000L
    }
}
