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
package com.vitorpamplona.nestsclient.interop.native

import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.JvmOpusEncoder
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.audio.SineWaveAudioCapture
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.connectReconnectingNestsListener
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import java.net.DatagramSocket
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.BeforeTest
import kotlin.test.assertTrue

/**
 * Side-by-side **pacing + one-way latency** measurement for two
 * moq-lite audio publishers feeding the same relay through the same
 * listener:
 *
 *   - the Amethyst Kotlin speaker (`connectNestsSpeaker` →
 *     `MoqLiteNestsSpeaker` → `NestMoqLiteBroadcaster` → moq-lite uni
 *     streams),
 *   - the upstream reference `hang-publish` Rust binary
 *     (`nestsClient/tests/hang-interop/hang-publish`), invoked with
 *     `--log-send-times` so each frame stamps a wall-clock send time
 *     onto stdout immediately before entering the moq-lite group
 *     producer.
 *
 * Both publish identical-shape tracks (440 Hz mono Opus, 32 kbps,
 * 20 ms frames, **5 frames per group on both sides** — Kotlin's
 * default is 50, overridden here so the per-group QUIC uni-stream
 * open/close cost is matched between the two stacks). The single
 * Kotlin listener subscribes to BOTH speaker pubkeys so the receive
 * path is identical for both sides — the variable under test is the
 * **publisher stack's pacing fidelity** AND, with the matched
 * send-time stamps, **one-way latency** from "frame entered the
 * publisher's outbound buffer" to "frame surfaced through the
 * listener's `objects` flow".
 *
 * Send-time capture, both sides:
 *   - Kotlin: a [TimestampingOpusEncoder] wrapping [JvmOpusEncoder]
 *     records `Instant.now().toMicros()` per `encode(...)` call,
 *     keyed by an internal monotonic frame counter. `encode` is
 *     called once per PCM frame, immediately before the encoded
 *     bytes are queued onto the publisher's outbound channel — that
 *     is the same "moment the frame entered the outbound buffer"
 *     anchor the Rust side uses.
 *   - Rust: `--log-send-times` makes hang-publish print one
 *     `SEND frame=<N> send_t_us=<US>` line per frame to stdout,
 *     captured with `SystemTime::now()` immediately before
 *     `frame.encode(group)`. Both stamps come from
 *     `clock_gettime(CLOCK_REALTIME)` on linux/macOS, so the two
 *     processes share a clock without a sync handshake.
 *
 * Frame-to-frame pairing uses `MoqObject.groupId` /
 * `MoqObject.objectId` to derive the publisher's absolute frame
 * index (`groupId * FRAMES_PER_GROUP + objectId`), so out-of-order
 * group delivery — a real possibility under packet loss / parallel
 * uni streams — doesn't break the matching.
 *
 * What we measure (per side):
 *   - `frames` — delivered objects in the [DURATION_MS] window.
 *     QUIC streams are reliable, so frame *count* should stay
 *     close to the theoretical max even under loss; loss instead
 *     surfaces as a fat **latency tail** from retransmission.
 *   - Inter-arrival jitter at the listener (`p50` / `p95` / `p99`
 *     / `max`) — pacing fidelity, independent of the publisher's
 *     wall clock.
 *   - **One-way latency** (`p50` / `p95` / `p99` / `max`) —
 *     `arrival_t - send_t` per matched frame, in ms. This is the
 *     real-time-audio number; "is our stack as fast as the Rust
 *     reference" answered directly.
 *   - `timeToFirstFrameMs` — wallclock from publisher startup to
 *     first frame at the listener. Informative, includes one-time
 *     handshake / ANNOUNCE / SUBSCRIBE costs; not directly
 *     comparable across stacks (Rust pays a process-spawn the JVM
 *     side doesn't).
 *
 * Two scenarios:
 *   - `clean_path_*` — direct UDP between publisher and relay,
 *     localhost link, no loss.
 *   - `with_5pct_loss_*` — publisher → `udp-loss-shim` → relay.
 *     5 % bidirectional packet loss. The listener stays on the
 *     direct path so any latency growth must come from the
 *     publisher-side retransmit / ack feedback loop the loss
 *     induces.
 *
 * Asserts (loose, host-tolerant):
 *   - each side delivers ≥ 80 % of expected frames on the clean
 *     path, ≥ 60 % under 5 % loss (catches a stalled publisher
 *     without flapping on a loaded CI host),
 *   - each side's median inter-arrival sits in `[15, 35] ms` on
 *     the clean path (catches a no-pacing burst-mode publisher).
 *
 * Tail percentiles + one-way latency are printed for human
 * inspection. The whole point of the test is the COMPARISON: as
 * long as the Kotlin numbers stay close to the Rust numbers in
 * every column, the publisher stack is competitive. A regression
 * — Kotlin's p99 ballooning to 3× Rust's, say — would be
 * immediately visible in the printed output.
 *
 * Gated by `-DnestsHangInterop=true`; needs the cargo-built
 * sidecars (`hang-publish`, `udp-loss-shim`) the
 * `interopBuildSidecars` task produces.
 */
class AudioLatencyComparisonTest {
    /**
     * Skip cleanly when run as part of the default `./gradlew test`
     * suite — every test in this class drives the cargo-built
     * `hang-publish` sidecar and the local moq-relay harness, both
     * of which only exist with `-DnestsHangInterop=true`. Without
     * this, the pre-push hook (which runs the whole suite) fails on
     * `NativeMoqRelayHarness.shared()` with an `IllegalStateException`.
     * Mirrors the pattern in `HangInteropTest`.
     */
    @BeforeTest
    fun assumeHangInteropEnabled() {
        NativeMoqRelayHarness.assumeHangInterop()
    }

    @Test
    fun clean_path_pacing_and_one_way_latency() = runComparison(scenario = "clean", lossRate = 0.0f)

    @Test
    fun under_5pct_packet_loss_pacing_and_one_way_latency() = runComparison(scenario = "loss-5pct", lossRate = 0.05f)

    @Test
    fun under_10pct_packet_loss_pacing_and_one_way_latency() = runComparison(scenario = "loss-10pct", lossRate = 0.10f)

    @Test
    fun under_20pct_packet_loss_pacing_and_one_way_latency() = runComparison(scenario = "loss-20pct", lossRate = 0.20f)

    @Test
    fun under_30pct_packet_loss_pacing_and_one_way_latency() = runComparison(scenario = "loss-30pct", lossRate = 0.30f)

    /**
     * Single-scenario driver. Both `@Test` entry points are thin
     * wrappers so JUnit reports them as separate cases (and the
     * NativeMoqRelayHarness shutdown hook + sidecar reuse stay
     * shared across them).
     */
    private fun runComparison(
        scenario: String,
        lossRate: Float,
    ) = runBlocking {
        val harness = NativeMoqRelayHarness.shared()

        val hostSigner: NostrSigner = NostrSignerInternal(KeyPair())
        val kotlinSpeakerSigner: NostrSigner = NostrSignerInternal(KeyPair())
        val rustSpeakerSigner: NostrSigner = NostrSignerInternal(KeyPair())
        val kotlinPubkey = kotlinSpeakerSigner.pubKey
        val rustPubkey = rustSpeakerSigner.pubKey

        val room =
            NestsRoomConfig(
                authBaseUrl = "<unused-public-relay>",
                endpoint = harness.relayUrl,
                hostPubkey = hostSigner.pubKey,
                roomId = "lat-$scenario-${UUID.randomUUID()}",
            )

        val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val activeShims = mutableListOf<Process>()
        var publishProc: Process? = null
        var rustLogScraper: Job? = null
        var kotlinSpeaker: com.vitorpamplona.nestsclient.NestsSpeaker? = null

        try {
            val transport =
                QuicWebTransportFactory(
                    parentScope = pumpScope,
                    certificateValidator = PermissiveCertificateValidator(),
                )

            // ---- Loss path setup. When [lossRate] > 0, stand up two
            // udp-loss-shim instances — one per publisher — each
            // forwarding 1:1 to the moq-relay's UDP port modulo the
            // configured drop fraction. Two shims (not one) because
            // the shim latches a single client on first datagram, so a
            // shared shim would silently swallow whichever publisher
            // spoke second. The listener stays on the direct relay
            // path: we want loss between publisher and relay only.
            val relayUdpPort = parseUdpPortFrom(harness.relayUrl)
            val (kotlinEndpoint, rustEndpoint) =
                if (lossRate > 0f) {
                    val (kotlinShim, kotlinShimPort) =
                        spawnLossShim(harness, relayUdpPort, lossRate, "kotlin")
                    val (rustShim, rustShimPort) =
                        spawnLossShim(harness, relayUdpPort, lossRate, "rust")
                    activeShims += kotlinShim
                    activeShims += rustShim
                    "https://127.0.0.1:$kotlinShimPort" to "https://127.0.0.1:$rustShimPort"
                } else {
                    harness.relayUrl to harness.relayUrl
                }

            // ---- Listener first. Use the RECONNECTING wrapper so its
            // inner re-issuance pump retries SUBSCRIBE with exponential
            // backoff (100 → 200 → 400 → 800 → 1 000 ms) — without it,
            // a SUBSCRIBE against an unannounced broadcast hits
            // "subscribe stream FIN before reply" and never recovers.
            // With it, subscribing BEFORE either publisher is up is
            // safe; the retry catches whichever ANNOUNCE arrives first.
            val listener =
                connectReconnectingNestsListener(
                    httpClient = LatencyTestNestsClient,
                    transport = transport,
                    scope = pumpScope,
                    room = room,
                    signer = hostSigner,
                    tokenRefreshAfterMs = 0L,
                )
            withTimeoutOrNull(5_000L) {
                listener.state.first { it is NestsListenerState.Connected }
            } ?: error("listener never reached Connected within 5 s")
            val kotlinSub = listener.subscribeSpeaker(kotlinPubkey)
            val rustSub = listener.subscribeSpeaker(rustPubkey)

            val kotlinArrivals = ConcurrentHashMap<Long, FrameArrival>()
            val rustArrivals = ConcurrentHashMap<Long, FrameArrival>()
            // `MoqLiteNestsListener` synthesises [MoqObject.objectId] as
            // a per-SUBSCRIPTION monotonic counter (not group-relative),
            // so it already maps directly to the publisher's absolute
            // frame index — no `groupId * FRAMES_PER_GROUP` arithmetic
            // needed, and doing it anyway would skip every frame past
            // the first group on each side and produce nonsensical
            // negative one-way latencies from misaligned matching.
            val kotlinCollect =
                pumpScope.launch {
                    kotlinSub.objects.collect { obj ->
                        kotlinArrivals[obj.objectId] = FrameArrival(System.nanoTime(), nowEpochMicros())
                    }
                }
            val rustCollect =
                pumpScope.launch {
                    rustSub.objects.collect { obj ->
                        rustArrivals[obj.objectId] = FrameArrival(System.nanoTime(), nowEpochMicros())
                    }
                }

            // Start Rust process FIRST: spawn is the slow setup, and
            // its --log-send-times stdout drives a background scraper
            // that we need running before frames begin to flow.
            val rustT0 = System.nanoTime()
            val rustSendTimesUs = ConcurrentHashMap<Long, Long>()
            publishProc =
                ProcessBuilder(
                    harness.hangPublishBin().toString(),
                    "--relay-url",
                    "$rustEndpoint/${room.moqNamespace()}",
                    "--broadcast",
                    rustPubkey,
                    "--track-name",
                    "audio/data",
                    "--duration",
                    (DURATION_MS / 1_000L).toString(),
                    "--freq-hz",
                    "440",
                    "--log-send-times",
                ).also { it.environment()["RUST_LOG"] = "info" }
                    // Stdout is the SEND-line channel; keep stderr
                    // separate so RUST_LOG messages don't interleave
                    // with the parser's per-frame state.
                    .redirectErrorStream(false)
                    .start()
            rustLogScraper =
                pumpScope.launch(Dispatchers.IO) {
                    publishProc!!.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            val match = SEND_LINE_REGEX.matchEntire(line) ?: return@forEach
                            val frame = match.groupValues[1].toLong()
                            val sendUs = match.groupValues[2].toLong()
                            rustSendTimesUs[frame] = sendUs
                        }
                    }
                }

            // Kotlin speaker. Override framesPerGroup to match the Rust
            // sidecar's default (5) — the test is about publisher-stack
            // fidelity, not about which stack ships which default group
            // size, so matching the knob explicitly makes the
            // comparison apples-to-apples.
            val kotlinT0 = System.nanoTime()
            val kotlinSendTimesUs = ConcurrentHashMap<Long, Long>()
            val frameCounter = AtomicLong(0L)
            val kotlinPublishRoom = room.copy(endpoint = kotlinEndpoint)
            kotlinSpeaker =
                connectNestsSpeaker(
                    httpClient = LatencyTestNestsClient,
                    transport = transport,
                    scope = pumpScope,
                    room = kotlinPublishRoom,
                    signer = kotlinSpeakerSigner,
                    speakerPubkeyHex = kotlinPubkey,
                    captureFactory = { SineWaveAudioCapture(freqHz = 440) },
                    encoderFactory = { TimestampingOpusEncoder(JvmOpusEncoder(), frameCounter, kotlinSendTimesUs) },
                    framesPerGroup = FRAMES_PER_GROUP.toInt(),
                )
            kotlinSpeaker.startBroadcasting()

            delay(DURATION_MS + COLLECTION_HEADROOM_MS)

            // Tear down publishers BEFORE collectors so late frames
            // (sitting in QUIC stream buffers — they arrive a few ms
            // after speaker.close) still land in `kotlinArrivals` /
            // `rustArrivals`. Cancelling the collectors first would
            // truncate the tail of the sample.
            runCatching { kotlinSpeaker.close() }
            runCatching { publishProc.destroy() }
            delay(300)
            kotlinCollect.cancel()
            rustCollect.cancel()
            rustLogScraper.cancel()

            val kotlinStats =
                FrameStats.compute(
                    label = "Kotlin speaker",
                    startNanos = kotlinT0,
                    arrivals = kotlinArrivals,
                    sendTimesUs = kotlinSendTimesUs,
                )
            val rustStats =
                FrameStats.compute(
                    label = "Rust hang-publish",
                    startNanos = rustT0,
                    arrivals = rustArrivals,
                    sendTimesUs = rustSendTimesUs,
                )

            // Print so a CI run captures the comparison even when the
            // test passes — that's the whole point of the test (it's
            // diagnostic, not regression-gating).
            println("===== Audio publisher comparison: $scenario (loss=${"%.0f".format(lossRate * 100)}%) =====")
            println(kotlinStats)
            println(rustStats)
            println("=".repeat(70))

            // Frame-count floor scales with loss. QUIC streams are
            // reliable so loss surfaces mainly as latency, but at high
            // loss rates the retransmit + congestion-control feedback
            // loop can eat into the test window, so the floor relaxes.
            // The intent of the assertion is to catch a CATASTROPHIC
            // failure (publisher never started, transport collapsed,
            // listener subscribe never landed) — NOT to enforce a per-
            // loss-rate delivery SLO. Reporting in the printed stats
            // is the source of truth for delivery quality.
            val frameFloor =
                if (lossRate <= 0f) {
                    MIN_FRAMES_CLEAN_PATH
                } else {
                    maxOf(MIN_FRAMES_HARD_FLOOR, ((1f - 3f * lossRate) * EXPECTED_FRAMES).toInt())
                }
            assertTrue(
                kotlinStats.frames >= frameFloor,
                "Kotlin speaker delivered only ${kotlinStats.frames} frames in the " +
                    "$scenario scenario, expected ≥ $frameFloor of $EXPECTED_FRAMES — " +
                    "publisher stalled or the relay dropped the audio track. " +
                    "Stats:\n$kotlinStats",
            )
            assertTrue(
                rustStats.frames >= frameFloor,
                "Rust hang-publish delivered only ${rustStats.frames} frames in the " +
                    "$scenario scenario, expected ≥ $frameFloor of $EXPECTED_FRAMES — " +
                    "sidecar process never made it to steady state. Stats:\n$rustStats",
            )
            // Inter-arrival pacing is only meaningful on the clean
            // path. Under packet loss, the sender's pacing intent is
            // preserved but retransmissions land in bursts, so the
            // listener sees clusters even though the publisher paced
            // perfectly. Assert pacing on clean only; report it for
            // loss but don't gate on it.
            if (lossRate == 0f) {
                assertTrue(
                    kotlinStats.medianInterArrivalMs in PACING_MEDIAN_OK_MS,
                    "Kotlin speaker median inter-arrival ${kotlinStats.medianInterArrivalMs} ms " +
                        "outside the sane window $PACING_MEDIAN_OK_MS — the publisher's " +
                        "not pacing at all (just bursting frames into the relay).",
                )
                assertTrue(
                    rustStats.medianInterArrivalMs in PACING_MEDIAN_OK_MS,
                    "Rust hang-publish median inter-arrival ${rustStats.medianInterArrivalMs} ms " +
                        "outside the sane window $PACING_MEDIAN_OK_MS — sidecar's pacing " +
                        "loop is broken (or the relay is queueing).",
                )
            }
        } finally {
            // Always best-effort tear down sidecars + scope, even when
            // the test bails partway (assertion / listener-never-
            // Connected / sidecar failed to spawn). pumpScope cancel
            // cascades to the collectors and the rust-log scraper.
            runCatching { kotlinSpeaker?.close() }
            runCatching { publishProc?.destroy() }
            pumpScope.coroutineContext[Job]?.cancel()
            activeShims.forEach { runCatching { it.destroy() } }
        }
    }

    /**
     * Find a free UDP port by binding ephemeral and immediately
     * releasing; the kernel briefly reserves the same port for our
     * caller via [SO_REUSEADDR]-style handoff. Brief race window
     * before the shim binds it back — fine for tests, would NOT be
     * fine for production.
     */
    private fun pickFreeUdpPort(): Int = DatagramSocket(0).use { it.localPort }

    /**
     * Spawn a `udp-loss-shim` instance binding an ephemeral local
     * UDP port, forwarding to [upstreamPort] (moq-relay's UDP
     * listener), with [lossRate] applied independently to each
     * direction. Returns the process AND the bound listen port so
     * the caller can construct the publisher's HTTPS URL.
     *
     * Blocks briefly for the shim to bind; without a delay, the
     * publisher's first packet would race the shim's listen socket
     * and land in the kernel's drop counter.
     */
    private fun spawnLossShim(
        harness: NativeMoqRelayHarness,
        upstreamPort: Int,
        lossRate: Float,
        tag: String,
    ): Pair<Process, Int> {
        val listenPort = pickFreeUdpPort()
        val proc =
            ProcessBuilder(
                harness.udpLossShimBin().toString(),
                "--listen",
                "127.0.0.1:$listenPort",
                "--upstream",
                "127.0.0.1:$upstreamPort",
                "--loss-rate",
                lossRate.toString(),
            ).redirectErrorStream(true)
                .also { it.environment()["RUST_LOG"] = "info" }
                .start()
        // Give the shim ~150 ms to bind. The shim logs "udp-loss-shim ready"
        // at INFO once it's listening; tailing for that line would be
        // more deterministic, but adds a reader thread per shim for a
        // one-shot signal — a fixed sleep is fine for tests.
        Thread.sleep(150)
        check(proc.isAlive) {
            "udp-loss-shim ($tag) exited immediately on listen=$listenPort upstream=$upstreamPort " +
                "loss=$lossRate; stdout=${proc.inputStream.bufferedReader().readText()}"
        }
        return proc to listenPort
    }

    /**
     * Pull the UDP port out of the harness's `https://127.0.0.1:<port>`
     * URL. The harness's moq-relay listens on both TCP (HTTP/WebTransport
     * handshake — irrelevant here) and UDP (QUIC) on the same port, so
     * the URL's port suffix is what udp-loss-shim's `--upstream` needs.
     */
    private fun parseUdpPortFrom(relayUrl: String): Int {
        val match = Regex("https://[^:/]+:(\\d+)").find(relayUrl)
        return checkNotNull(match) { "could not parse port from relay URL '$relayUrl'" }
            .groupValues[1]
            .toInt()
    }

    /**
     * Wall-clock microseconds since `UNIX_EPOCH`, matching what the
     * Rust sidecar's `SystemTime::now().duration_since(UNIX_EPOCH)`
     * emits. `Instant.now()` on the JVM reads `CLOCK_REALTIME`,
     * which is the same source — the two values can be compared
     * directly when both processes run on the same host.
     */
    private fun nowEpochMicros(): Long {
        val i = Instant.now()
        return i.epochSecond * 1_000_000L + i.nano / 1_000L
    }

    /**
     * Captured arrival point for a single frame. `nanoTime` is the
     * JVM-monotonic anchor used for inter-arrival deltas (it doesn't
     * jump on NTP step). `epochMicros` is wall-clock, used to pair
     * with the publisher's send time and compute one-way latency.
     */
    private data class FrameArrival(
        val nanoTime: Long,
        val epochMicros: Long,
    )

    /**
     * Per-publisher pacing + latency summary. Inter-arrival deltas
     * are computed BETWEEN consecutive received frames in
     * publisher-frame order (so out-of-order delivery doesn't skew
     * the result); one-way latency is `arrival_us - send_us` per
     * matched frame, in ms.
     */
    private data class FrameStats(
        val label: String,
        val frames: Int,
        val matchedFrames: Int,
        val timeToFirstFrameMs: Double,
        val medianInterArrivalMs: Double,
        val p95InterArrivalMs: Double,
        val p99InterArrivalMs: Double,
        val maxInterArrivalMs: Double,
        val medianLatencyMs: Double,
        val p95LatencyMs: Double,
        val p99LatencyMs: Double,
        val maxLatencyMs: Double,
    ) {
        override fun toString(): String =
            (
                "%-18s frames=%4d  ttf=%6.1f ms" +
                    "  inter-arrival p50/95/99/max=%5.2f/%5.2f/%5.2f/%6.2f ms" +
                    "  one-way p50/95/99/max=%6.2f/%6.2f/%6.2f/%7.2f ms (n=%d)"
            ).format(
                label,
                frames,
                timeToFirstFrameMs,
                medianInterArrivalMs,
                p95InterArrivalMs,
                p99InterArrivalMs,
                maxInterArrivalMs,
                medianLatencyMs,
                p95LatencyMs,
                p99LatencyMs,
                maxLatencyMs,
                matchedFrames,
            )

        companion object {
            fun compute(
                label: String,
                startNanos: Long,
                arrivals: Map<Long, FrameArrival>,
                sendTimesUs: Map<Long, Long>,
            ): FrameStats {
                if (arrivals.isEmpty()) {
                    return FrameStats(
                        label = label,
                        frames = 0,
                        matchedFrames = 0,
                        timeToFirstFrameMs = Double.NaN,
                        medianInterArrivalMs = Double.NaN,
                        p95InterArrivalMs = Double.NaN,
                        p99InterArrivalMs = Double.NaN,
                        maxInterArrivalMs = Double.NaN,
                        medianLatencyMs = Double.NaN,
                        p95LatencyMs = Double.NaN,
                        p99LatencyMs = Double.NaN,
                        maxLatencyMs = Double.NaN,
                    )
                }
                // Inter-arrival deltas — sort by publisher frame index
                // so out-of-order delivery (parallel uni streams, loss
                // retransmits) doesn't distort the histogram.
                val sortedArrivals =
                    arrivals.entries.sortedBy { it.key }.map { it.value }
                val ttfMs = (sortedArrivals.first().nanoTime - startNanos) / 1_000_000.0
                val deltasMs =
                    if (sortedArrivals.size < 2) {
                        DoubleArray(0)
                    } else {
                        DoubleArray(sortedArrivals.size - 1).also { d ->
                            for (i in 1 until sortedArrivals.size) {
                                d[i - 1] = (sortedArrivals[i].nanoTime - sortedArrivals[i - 1].nanoTime) / 1_000_000.0
                            }
                        }
                    }
                deltasMs.sort()
                // One-way latency — only computable for frames with
                // both a send_t AND an arrival_t. The intersection
                // size is the `matchedFrames` count we report.
                val latenciesMs =
                    arrivals.entries
                        .mapNotNull { (frame, arr) ->
                            val sendUs = sendTimesUs[frame] ?: return@mapNotNull null
                            (arr.epochMicros - sendUs) / 1_000.0
                        }.toDoubleArray()
                latenciesMs.sort()
                return FrameStats(
                    label = label,
                    frames = arrivals.size,
                    matchedFrames = latenciesMs.size,
                    timeToFirstFrameMs = ttfMs,
                    medianInterArrivalMs = percentile(deltasMs, 0.50),
                    p95InterArrivalMs = percentile(deltasMs, 0.95),
                    p99InterArrivalMs = percentile(deltasMs, 0.99),
                    maxInterArrivalMs = if (deltasMs.isEmpty()) Double.NaN else deltasMs.last(),
                    medianLatencyMs = percentile(latenciesMs, 0.50),
                    p95LatencyMs = percentile(latenciesMs, 0.95),
                    p99LatencyMs = percentile(latenciesMs, 0.99),
                    maxLatencyMs = if (latenciesMs.isEmpty()) Double.NaN else latenciesMs.last(),
                )
            }

            /**
             * Linear-interpolated percentile (R-7 / "type 7" — the
             * default in NumPy, R, and Excel's `PERCENTILE`). Input
             * MUST already be sorted ascending; we sort the array
             * before this is called. Returns `NaN` on empty input
             * rather than throwing, so the [FrameStats] dump for a
             * fully-failed publisher still renders.
             */
            private fun percentile(
                sorted: DoubleArray,
                q: Double,
            ): Double {
                if (sorted.isEmpty()) return Double.NaN
                if (sorted.size == 1) return sorted[0]
                val rank = q * (sorted.size - 1)
                val lo = rank.toInt()
                val frac = rank - lo
                val hi = (lo + 1).coerceAtMost(sorted.size - 1)
                return sorted[lo] + frac * (sorted[hi] - sorted[lo])
            }
        }
    }

    /**
     * Delegating [OpusEncoder] that records `Instant.now()` per
     * `encode` call into [sendTimesUs], keyed by an internal
     * monotonic frame counter shared across the broadcaster's
     * lifetime. `encode` is invoked once per PCM frame, immediately
     * before the encoded Opus bytes are queued onto the publisher's
     * outbound channel by `NestMoqLiteBroadcaster` — so the
     * captured timestamp lines up with the "moment the frame
     * entered the publisher's outbound buffer" anchor the Rust
     * sidecar uses.
     */
    private class TimestampingOpusEncoder(
        private val inner: OpusEncoder,
        private val frameCounter: AtomicLong,
        private val sendTimesUs: MutableMap<Long, Long>,
    ) : OpusEncoder {
        override fun encode(pcm: ShortArray): ByteArray {
            val frame = frameCounter.getAndIncrement()
            sendTimesUs[frame] = nowEpochMicrosStatic()
            return inner.encode(pcm)
        }

        override fun release() = inner.release()

        companion object {
            // Static-scope copy so the encoder doesn't pull the
            // enclosing test class in (constructed BY the
            // broadcaster, lifetime independent of the @Test method).
            private fun nowEpochMicrosStatic(): Long {
                val i = Instant.now()
                return i.epochSecond * 1_000_000L + i.nano / 1_000L
            }
        }
    }

    /**
     * Bypass the NIP-98 auth handshake — the harness's moq-relay
     * runs with `--auth-public ""`, which grants any path without a
     * JWT. Same shape as the existing `ReverseStaticTokenNestsClient`
     * in the other native-relay tests; they're file-private so we
     * can't share.
     */
    private object LatencyTestNestsClient : NestsClient {
        override suspend fun mintToken(
            room: NestsRoomConfig,
            publish: Boolean,
            signer: NostrSigner,
        ): String = ""
    }

    companion object {
        /** Matches Rust `hang-publish`'s `--log-send-times` output line. */
        private val SEND_LINE_REGEX = Regex("""^SEND frame=(\d+) send_t_us=(\d+)$""")

        /** moq-lite / Opus frame cadence; matches `hang-publish`'s `FRAME_DURATION_US`. */
        private const val FRAME_MS = 20L

        /**
         * Frames per moq-lite group, matched on both sides.
         * `hang-publish` hard-codes 5; we override Kotlin's default
         * (50) to 5 here so each side opens/closes a uni stream at
         * the same cadence — the per-group transport cost is a
         * publisher-stack property and matching it makes the rest
         * of the comparison meaningful.
         *
         * `Long` so the `groupId * FRAMES_PER_GROUP + objectId`
         * arithmetic in the collector lambdas matches
         * `MoqObject.groupId`'s `Long` type without casts.
         */
        private const val FRAMES_PER_GROUP = 5L

        /** Measurement window. 10 s = 500 frames per side is plenty for stable p95/p99. */
        private const val DURATION_MS = 10_000L

        /** Extra wall-clock after publishers stop, so the listener drains in-flight uni streams. */
        private const val COLLECTION_HEADROOM_MS = 1_500L

        private const val EXPECTED_FRAMES = (DURATION_MS / FRAME_MS).toInt()

        /** Clean-path delivery floor: 80 % of expected. */
        private const val MIN_FRAMES_CLEAN_PATH = (EXPECTED_FRAMES * 0.8).toInt()

        /**
         * Absolute floor on delivered frames regardless of loss
         * rate — anything under this is a catastrophic failure
         * (publisher never started, transport collapsed). 50 frames
         * ≈ 1 s of audio, which we should ALWAYS clear in a 10 s
         * test even with aggressive loss.
         */
        private const val MIN_FRAMES_HARD_FLOOR = 50

        /**
         * Median inter-arrival should sit very close to [FRAME_MS] = 20 ms
         * on the clean path. The window catches a publisher that's
         * just bursting frames (median would collapse) without being
         * so tight it false-fails on a loaded CI host.
         */
        private val PACING_MEDIAN_OK_MS = 15.0..35.0
    }
}
