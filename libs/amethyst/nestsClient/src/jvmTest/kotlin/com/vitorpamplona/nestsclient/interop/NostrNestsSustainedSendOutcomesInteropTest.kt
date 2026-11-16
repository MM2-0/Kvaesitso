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
import com.vitorpamplona.nestsclient.buildRelayConnectTarget
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
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
import kotlinx.coroutines.flow.onEach
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
 * Local-harness counterpart of
 * [NostrnestsProdAudioTransmissionTest.sustained_per_frame_send_outcomes_two_users].
 *
 * Drives the local Docker-backed reference relay (kixelated/moq) so we
 * can reproduce the production "82/100" frame-loss cliff without needing
 * to talk to nostrnests.com. If the same cliff appears here, the bug is
 * in our client code (`:nestsClient` / `:quic`); if it doesn't, the bug
 * is specific to the production deployment (relay config / network).
 *
 * Bypasses [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster]
 * and calls [com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle.send]
 * directly so we can capture the boolean return per frame, plus any
 * exception thrown by `endGroup()`. This is the only way to tell whether
 * a missing frame was dropped at the moq-lite layer (`send` returned
 * false) or queued and lost downstream (`send` returned true but the
 * uni-stream write threw, swallowed by `runCatching` inside the
 * production `send` itself).
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsSustainedSendOutcomesInteropTest {
    @Test
    fun two_users_sustained_send_outcomes_against_local_relay() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking
            val scope = "send-trace"

            val hostSigner = NostrSignerInternal(KeyPair())
            val audienceSigner = NostrSignerInternal(KeyPair())
            val room =
                NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = hostSigner.pubKey,
                    roomId = "send-trace-${System.currentTimeMillis()}",
                )

            val httpClient = OkHttpNestsClient { OkHttpClient() }
            // Self-signed dev cert on the harness's moq-relay; production
            // tests use the JDK validator. PermissiveCertificateValidator
            // is the same one every other harness test in this package
            // uses, so we're not deviating from the established pattern.
            val transport =
                QuicWebTransportFactory(certificateValidator = PermissiveCertificateValidator())

            val supervisor = SupervisorJob()
            val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

            InteropDebug.checkpoint(
                scope,
                "host=${hostSigner.pubKey.take(8)}… audience=${audienceSigner.pubKey.take(8)}… " +
                    "ns=${room.moqNamespace()} frames=$N_FRAMES cadence=${FRAME_CADENCE_MS}ms",
            )

            // ---- speaker side: build session manually so we can call
            // session.publish() directly and capture per-frame send results.
            val publishToken =
                InteropDebug.stepSuspending(scope, "host: mintToken(publish=true)") {
                    httpClient.mintToken(room = room, publish = true, signer = hostSigner)
                }
            val (authority, path) =
                buildRelayConnectTarget(
                    endpoint = room.endpoint,
                    namespace = room.moqNamespace(),
                    token = publishToken,
                )
            val speakerWt =
                InteropDebug.stepSuspending(scope, "host: WebTransport.connect") {
                    transport.connect(authority = authority, path = path, bearerToken = null)
                }
            val speakerSession = MoqLiteSession.client(speakerWt, pumpScope)
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
            val sendOutcomes = BooleanArray(N_FRAMES)
            val endGroupErrors = arrayOfNulls<String>(N_FRAMES)
            val collectStart = System.currentTimeMillis()
            val collected =
                async(pumpScope.coroutineContext) {
                    withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
                        subscription.objects
                            .onEach { obj ->
                                received += Arrival(System.currentTimeMillis() - collectStart, obj.groupId)
                            }.take(N_FRAMES)
                            .toList()
                    }
                }

            try {
                delay(SUBSCRIBE_SETTLE_MS)

                val payloadPrefix = ByteArray(79) { 0x4F.toByte() } + byteArrayOf(0x00)
                val started = System.currentTimeMillis()
                for (i in 0 until N_FRAMES) {
                    val payload = payloadPrefix + byteArrayOf(i.toByte())
                    sendOutcomes[i] = publisher.send(payload)
                    runCatching { publisher.endGroup() }
                        .onFailure { endGroupErrors[i] = it::class.simpleName + ": " + it.message }
                    delay(FRAME_CADENCE_MS)
                }
                val pumpDurationMs = System.currentTimeMillis() - started

                collected.await()
                val frames = received.toList()
                val receivedGroups = frames.map { it.groupId }.toHashSet()

                val sendCount = sendOutcomes.count { it }
                val firstFalseSend = sendOutcomes.indexOfFirst { !it }
                val sentButLost = (0 until N_FRAMES).filter { sendOutcomes[it] && it.toLong() !in receivedGroups }
                val firstSentButLost = sentButLost.firstOrNull() ?: -1

                InteropDebug.checkpoint(
                    scope,
                    "sendTrue=$sendCount/$N_FRAMES received=${frames.size}/$N_FRAMES " +
                        "firstFalseSend=$firstFalseSend firstSentButLost=$firstSentButLost " +
                        "pumpDuration=${pumpDurationMs}ms",
                )
                for (i in 0 until N_FRAMES) {
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
                if (firstSentButLost >= 0) {
                    fail(
                        "[$scope] publisher.send returned true for ALL sent frames, but listener missed " +
                            "${N_FRAMES - frames.size} of them starting at $firstSentButLost. " +
                            "Loss is downstream of moq-lite — uni-stream write threw (swallowed), " +
                            "QUIC flow control wedged, or relay dropped the stream.",
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
    // Same sweep matrix as [NostrnestsProdAudioTransmissionTest], but
    // wired to the local Docker harness so we can compare prod vs.
    // reference-relay behaviour for each scenario in one run. The
    // [SendTraceScenario] runner is shared.
    // ====================================================================

    @Test
    fun harness_sweep_baseline_100x20ms() = runHarnessScenarioOrSkip("h-baseline", Scenario(frameCount = 100, cadenceMs = 20L))

    @Test fun harness_sweep_cadence_5ms() = runHarnessScenarioOrSkip("h-cad5", Scenario(frameCount = 100, cadenceMs = 5L))

    @Test fun harness_sweep_cadence_10ms() = runHarnessScenarioOrSkip("h-cad10", Scenario(frameCount = 100, cadenceMs = 10L))

    @Test fun harness_sweep_cadence_40ms() = runHarnessScenarioOrSkip("h-cad40", Scenario(frameCount = 100, cadenceMs = 40L))

    @Test fun harness_sweep_cadence_80ms() = runHarnessScenarioOrSkip("h-cad80", Scenario(frameCount = 100, cadenceMs = 80L))

    @Test fun harness_sweep_cadence_200ms() = runHarnessScenarioOrSkip("h-cad200", Scenario(frameCount = 100, cadenceMs = 200L))

    @Test
    fun harness_sweep_burst_no_cadence() =
        runHarnessScenarioOrSkip(
            "h-burst",
            Scenario(frameCount = 100, cadenceMs = 0L, receiveGraceMs = 30_000L),
        )

    @Test fun harness_sweep_frames_50() = runHarnessScenarioOrSkip("h-frames50", Scenario(frameCount = 50, cadenceMs = 20L))

    @Test
    fun harness_sweep_frames_200() =
        runHarnessScenarioOrSkip(
            "h-frames200",
            Scenario(frameCount = 200, cadenceMs = 20L, receiveGraceMs = 45_000L),
        )

    @Test
    fun harness_sweep_frames_400() =
        runHarnessScenarioOrSkip(
            "h-frames400",
            Scenario(frameCount = 400, cadenceMs = 20L, receiveGraceMs = 60_000L),
        )

    @Test
    fun harness_sweep_payload_1kb() =
        runHarnessScenarioOrSkip(
            "h-1kb",
            Scenario(frameCount = 100, cadenceMs = 20L, payloadBytes = 1024),
        )

    @Test
    fun harness_sweep_payload_4kb() =
        runHarnessScenarioOrSkip(
            "h-4kb",
            Scenario(frameCount = 100, cadenceMs = 20L, payloadBytes = 4096),
        )

    @Test
    fun harness_sweep_payload_16kb() =
        runHarnessScenarioOrSkip(
            "h-16kb",
            Scenario(frameCount = 100, cadenceMs = 20L, payloadBytes = 16384),
        )

    @Test
    fun harness_sweep_frames_per_group_5() =
        runHarnessScenarioOrSkip(
            "h-fpg5",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 5),
        )

    @Test
    fun harness_sweep_frames_per_group_10() =
        runHarnessScenarioOrSkip(
            "h-fpg10",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 10),
        )

    @Test
    fun harness_sweep_frames_per_group_20() =
        runHarnessScenarioOrSkip(
            "h-fpg20",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 20),
        )

    // Production default — see the matching note in
    // NostrnestsProdAudioTransmissionTest.sweep_frames_per_group_50.
    @Test
    fun harness_sweep_frames_per_group_50_production_default() =
        runHarnessScenarioOrSkip(
            "h-fpg50",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 50),
        )

    @Test
    fun harness_sweep_frames_per_group_all() =
        runHarnessScenarioOrSkip(
            "h-fpg-all",
            Scenario(frameCount = 100, cadenceMs = 20L, framesPerGroup = 100),
        )

    @Test
    fun harness_sweep_late_subscribe_after_25() =
        runHarnessScenarioOrSkip(
            "h-late25",
            Scenario(frameCount = 100, cadenceMs = 20L, subscribeAtFrame = 25),
        )

    @Test
    fun harness_sweep_late_subscribe_after_50() =
        runHarnessScenarioOrSkip(
            "h-late50",
            Scenario(frameCount = 100, cadenceMs = 20L, subscribeAtFrame = 50),
        )

    @Test
    fun harness_sweep_mid_pause_50_5s() =
        runHarnessScenarioOrSkip(
            "h-pause",
            Scenario(
                frameCount = 100,
                cadenceMs = 20L,
                pauseAfterFrame = 50,
                pauseDurationMs = 5_000L,
                receiveGraceMs = 45_000L,
            ),
        )

    @Test
    fun harness_sweep_two_subscribers() =
        runHarnessScenarioOrSkip(
            "h-2subs",
            Scenario(frameCount = 100, cadenceMs = 20L, parallelSubscriptions = 2),
        )

    @Test
    fun harness_sweep_three_subscribers() =
        runHarnessScenarioOrSkip(
            "h-3subs",
            Scenario(frameCount = 100, cadenceMs = 20L, parallelSubscriptions = 3),
        )

    @Test
    fun harness_sweep_slow_consumer_50ms() =
        runHarnessScenarioOrSkip(
            "h-slowconsumer",
            Scenario(
                frameCount = 100,
                cadenceMs = 20L,
                listenerSlowConsumerMs = 50L,
                receiveGraceMs = 60_000L,
            ),
        )

    @Test
    fun harness_sweep_long_run_30s() =
        runHarnessScenarioOrSkip(
            "h-30s",
            Scenario(
                frameCount = 1500,
                cadenceMs = 20L,
                receiveGraceMs = 60_000L,
                verbosePerFrame = false,
            ),
        )

    private fun runHarnessScenarioOrSkip(
        scope: String,
        scenario: Scenario,
        expectAllReceived: Boolean = false,
    ) = runBlocking {
        NostrNestsHarness.assumeNestsInterop()
        val harness = harnessOrNull ?: return@runBlocking
        withHarnessSpeakerAndListeners(scope, harness, scenario.parallelSubscriptions) { publisher, listeners, hostPub, pumpScope, flowControlSnapshot, listenerFlowControlSnapshots ->
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

    private suspend fun withHarnessSpeakerAndListeners(
        scope: String,
        harness: NostrNestsHarness,
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
        val room =
            com.vitorpamplona.nestsclient
                .NestsRoomConfig(
                    authBaseUrl = harness.authBaseUrl,
                    endpoint = harness.moqEndpoint,
                    hostPubkey = hostSigner.pubKey,
                    roomId = "h-sweep-${System.currentTimeMillis()}-${(0..9999).random()}",
                )

        val httpClient = OkHttpNestsClient { OkHttpClient() }
        val transport =
            QuicWebTransportFactory(certificateValidator = PermissiveCertificateValidator())

        val supervisor = SupervisorJob()
        val pumpScope = CoroutineScope(supervisor + Dispatchers.IO)

        InteropDebug.checkpoint(scope, "auth=${room.authBaseUrl} endpoint=${room.endpoint} ns=${room.moqNamespace()}")

        val publishToken =
            InteropDebug.stepSuspending(scope, "host: mintToken(publish=true)") {
                httpClient.mintToken(room = room, publish = true, signer = hostSigner)
            }
        val (authority, path) =
            buildRelayConnectTarget(
                endpoint = room.endpoint,
                namespace = room.moqNamespace(),
                token = publishToken,
            )
        val speakerWt =
            InteropDebug.stepSuspending(scope, "host: WebTransport.connect") {
                transport.connect(authority = authority, path = path, bearerToken = null)
            }
        val speakerSession = MoqLiteSession.client(speakerWt, pumpScope)
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
            // Diagnostics passthrough mirrors withProdSpeakerAndListeners
            // — see that helper's comment for rationale.
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

    companion object {
        private const val N_FRAMES = 100
        private const val FRAME_CADENCE_MS = 20L
        private const val SUBSCRIBE_SETTLE_MS = 500L
        private const val RECEIVE_TIMEOUT_MS = 30_000L

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
