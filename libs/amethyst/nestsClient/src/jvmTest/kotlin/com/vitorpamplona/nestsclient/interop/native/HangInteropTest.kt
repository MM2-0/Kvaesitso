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

import com.vitorpamplona.nestsclient.AudioBroadcastConfig
import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.AudioFormat
import com.vitorpamplona.nestsclient.audio.JvmOpusDecoder
import com.vitorpamplona.nestsclient.audio.JvmOpusEncoder
import com.vitorpamplona.nestsclient.audio.PcmAssertions
import com.vitorpamplona.nestsclient.audio.SineWaveAudioCapture
import com.vitorpamplona.nestsclient.buildRelayConnectTarget
import com.vitorpamplona.nestsclient.connectNestsListener
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSubscribeException
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.rules.TestName
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cross-stack interop scenarios driving the reference `kixelated/moq`
 * `hang-listen` Rust binary against an Amethyst Kotlin speaker
 * through [NativeMoqRelayHarness].
 *
 * **Phase 2 P0 scenarios:**
 *   - **I1** — sine-wave round-trip ([amethyst_speaker_to_hang_listener_static_tone_440]).
 *   - **I11** — wire-byte capture: assert the first audio frame
 *     payload isn't `OpusHead\1\1...` Codec-Specific-Data
 *     ([first_audio_frame_is_not_opus_codec_config]).
 *   - **I2** — late-join: listener attaches at T+2 s of a 5 s
 *     broadcast, still receives ~3 s of decoded audio
 *     ([late_join_listener_still_decodes_tail]).
 *   - **I3** — mute-window: speaker mutes for 1 s mid-broadcast,
 *     listener observes a corresponding silence window
 *     ([mid_broadcast_mute_produces_silence_window]).
 *   - **Rust↔Rust** round-trip: pure-Rust through our harness
 *     ([rust_hang_publish_to_rust_hang_listener_round_trip_440]).
 *
 * All scenarios pin the Kotlin speaker at `framesPerGroup = 5`
 * to stay under the moq-relay 0.10.x per-subscriber forward
 * cliff (see
 * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`).
 *
 * Gated by `-DnestsHangInterop=true`.
 */
class HangInteropTest {
    /**
     * JUnit 4 rule that exposes the running test method's name. Used
     * to tag the per-method moq-relay log file when trace capture is
     * enabled (`-DnestsHangInteropTraceRelay=true`) — see
     * `NativeMoqRelayHarness.RELAY_LOG_DIR_PROPERTY`.
     */
    @Rule @JvmField
    val testName: TestName = TestName()

    @BeforeTest
    fun gate() {
        NativeMoqRelayHarness.assumeHangInterop()
        // Reset the shared relay subprocess between scenarios.
        // Sharing across all 11 methods in one JVM run means the
        // relay's per-subscriber forward queues + announce
        // tables accumulate state from prior tests, manifesting
        // as intermittent catalog-cancel + sample-count flakes
        // that don't reproduce in isolation. Per-method reboot
        // costs ~500 ms (cargo binaries are cached) — acceptable
        // for the stability gain.
        NativeMoqRelayHarness.resetShared(testTag = testName.methodName)
    }

    /** I1: 5 s 440 Hz mono sine, asserted via FFT peak + ZCR. */
    @Test
    fun amethyst_speaker_to_hang_listener_static_tone_440() =
        runBlocking {
            val out =
                runSpeakerToHangListen(
                    speakerSeconds = 5,
                    captureFirstFrame = false,
                )
            val pcm = readFloat32Pcm(out.pcmFile)
            PcmAssertions.assertSampleCount(pcm, expectedDurationSec = 5.0, tolerance = 0.20)
            // Skip first 40 ms — Opus look-ahead silence.
            val warmup = AudioFormat.SAMPLE_RATE_HZ / 25
            val analysed = pcm.copyOfRange(warmup, pcm.size)
            PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
            PcmAssertions.assertZeroCrossingRate(
                analysed,
                expectedPerSecond = 880.0,
                tolerance = 0.05,
            )
        }

    /**
     * I11: assert the first audio frame's payload isn't
     * `OpusHead\1\1...` codec-config bytes.
     *
     * Catches the T8 regression where Android's
     * `MediaCodecOpusEncoder` would emit OPUS_INITIAL_OUTPUT
     * config-data as the first uni-stream frame; downstream watchers
     * (hang.js, our `JvmOpusDecoder`) decode that to a few ms of
     * white-noise click before catching up. The fix in T8 filters
     * BUFFER_FLAG_CODEC_CONFIG before the publisher pushes; this
     * test is the cross-stack regression.
     *
     * The hang-listen `--dump-first-frame` flag writes the
     * post-Container-Legacy-strip codec payload (i.e. the bytes the
     * Opus decoder will be fed) to a file. We assert the leading
     * bytes aren't the literal `OpusHead` magic.
     */
    @Test
    fun first_audio_frame_is_not_opus_codec_config() =
        runBlocking {
            val out =
                runSpeakerToHangListen(
                    speakerSeconds = 2,
                    captureFirstFrame = true,
                )
            val firstFrame = out.firstFrameFile?.readBytes()
            checkNotNull(firstFrame) { "hang-listen --dump-first-frame produced no file" }
            assertTrue(
                firstFrame.size >= 8,
                "first frame is suspiciously short (${firstFrame.size} bytes); " +
                    "expected an Opus packet",
            )
            val opusHeadMagic = "OpusHead".encodeToByteArray()
            val startsWithOpusHead =
                firstFrame.size >= opusHeadMagic.size &&
                    opusHeadMagic.indices.all { firstFrame[it] == opusHeadMagic[it] }
            assertFalse(
                startsWithOpusHead,
                "first audio frame begins with `OpusHead` magic (${firstFrame.take(16)} → " +
                    "byte 0x${firstFrame[0].toUByte().toString(16)})—the speaker is " +
                    "leaking codec-specific-data as a regular audio frame.",
            )
        }

    /**
     * I2 — late-join: listener attaches 2 s into a 5 s broadcast
     * and still gets the tail. Threshold is 0.5 s — tight enough
     * to catch a regression that cancels the broadcast on
     * subscriber-after-start, loose enough to absorb full-suite
     * timing jitter (relay state accumulates across the 11
     * scenarios in one JVM run; the late-join catalog hook
     * occasionally arrives after hang-listen's catalog-read
     * timeout).
     */
    @Test
    fun late_join_listener_still_decodes_tail() =
        runBlocking {
            val out =
                runSpeakerToHangListen(
                    speakerSeconds = 5,
                    listenerLateJoinDelayMs = 2_000,
                    captureFirstFrame = false,
                )
            val pcm = readFloat32Pcm(out.pcmFile)
            assertTrue(
                pcm.size >= AudioFormat.SAMPLE_RATE_HZ / 2,
                "late-join listener decoded only ${pcm.size} samples — " +
                    "expected at least 0.5 s of audio after the late-join window",
            )
            val warmup = AudioFormat.SAMPLE_RATE_HZ / 25
            val analysed = pcm.copyOfRange(warmup, pcm.size)
            PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
        }

    /**
     * I3 — mute window: speaker mutes for 1 s mid-broadcast. The
     * Amethyst broadcaster doesn't push Opus frames while muted
     * (it FINs the open uni stream so web watchers don't park on
     * `await readFrame`), so the decoded PCM ends up ~1 s shorter
     * than the wallclock broadcast — the mute gap manifests as a
     * sample-count deficit, not as embedded silence samples.
     *
     * The test asserts the deficit is in the right ballpark (so a
     * regression that, e.g., kept pushing zeros instead of FINning
     * would be caught — that'd produce a normal-length PCM with
     * zero RMS in the middle, NOT a short PCM).
     */
    @Test
    fun mid_broadcast_mute_shortens_decoded_pcm() =
        runBlocking {
            val out =
                runSpeakerToHangListen(
                    speakerSeconds = 4,
                    muteWindowMs = 1_000L..2_000L,
                    captureFirstFrame = false,
                )
            val pcm = readFloat32Pcm(out.pcmFile)
            val durationSec = pcm.size.toDouble() / AudioFormat.SAMPLE_RATE_HZ
            // Wallclock 4 s minus 1 s mute = ~3 s ideal. Real
            // budget is loose because hang-listen's consumer
            // skips groups older than its 500 ms latency window
            // and full-suite mode accumulates relay-side state
            // that occasionally truncates the post-mute tail
            // by another 0.5–1 s. The lower bound catches a
            // wholesale failure (no audio at all, or zero-length
            // window); the upper bound catches a regression
            // that pushes zeros instead of FINning the uni
            // stream during mute (would produce ~4 s of audio
            // including silence).
            assertTrue(
                durationSec in 1.8..3.5,
                "expected 1.8–3.5 s of decoded PCM (4 s broadcast − 1 s mute), " +
                    "got ${"%.2f".format(durationSec)} s",
            )
            // Sanity: the unmuted halves still carry a 440 Hz tone.
            val warmup = AudioFormat.SAMPLE_RATE_HZ / 25
            val analysed = pcm.copyOfRange(warmup, pcm.size)
            PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
        }

    /**
     * I8 — SubscribeDrop on unknown track. Subscribes to a track
     * the publisher hasn't claimed (`audio/data-not-here`); the
     * publisher's `MoqLiteSession` replies with a SubscribeDrop
     * (`TRACK_DOES_NOT_EXIST`).
     *
     * `moq-relay 0.10.x` forwards the SUBSCRIBE to the publisher
     * but ALSO acknowledges the listener's bidi with `SubscribeOk`
     * upfront — relay-level optimistic ack — so the listener-side
     * `subscribe()` call returns a handle rather than throwing.
     * The publisher's Drop arrives on the same bidi shortly after,
     * the relay forwards a stream-FIN, and the handle's `frames`
     * flow completes empty.
     *
     * The test's assertion: subscribing returns a handle (relay
     * ack), the bidi closes within a short timeout (publisher's
     * Drop reaches us), and **no frames** are emitted on the
     * subscription. A regression that returned an "OK" but kept
     * the bidi open forever would hang at `take(1)` past the
     * deadline.
     */
    @Test
    fun subscribe_drop_for_unknown_track() =
        runBlocking {
            val harness = NativeMoqRelayHarness.shared()

            val signer: NostrSigner = NostrSignerInternal(KeyPair())
            val pubkey = signer.pubKey
            val room =
                NestsRoomConfig(
                    authBaseUrl = "<unused-public-relay>",
                    endpoint = harness.relayUrl,
                    hostPubkey = pubkey,
                    roomId = "rt-${UUID.randomUUID()}",
                )

            val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val transport =
                QuicWebTransportFactory(
                    parentScope = pumpScope,
                    certificateValidator = PermissiveCertificateValidator(),
                )

            try {
                // Speaker side: claim "audio/data" + "catalog.json".
                val speaker =
                    connectNestsSpeaker(
                        httpClient = StaticTokenNestsClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = pubkey,
                        captureFactory = { SineWaveAudioCapture(freqHz = 440) },
                        encoderFactory = { JvmOpusEncoder() },
                        framesPerGroup = 5,
                    )
                val handle = speaker.startBroadcasting()
                delay(150)

                // Listener side: open a raw moq-lite session and
                // SUBSCRIBE to a track the publisher never claimed.
                val (authority, path) =
                    buildRelayConnectTarget(
                        endpoint = harness.relayUrl,
                        namespace = room.moqNamespace(),
                        token = "",
                    )
                val listenerWt =
                    transport.connect(
                        authority = authority,
                        path = path,
                        bearerToken = null,
                    )
                val listenerSession = MoqLiteSession.client(listenerWt, pumpScope)
                try {
                    val sub =
                        try {
                            listenerSession.subscribe(
                                broadcast = pubkey,
                                track = "audio/data-not-here",
                            )
                        } catch (t: MoqLiteSubscribeException) {
                            // Tolerate either path: relay sends Drop
                            // pre-Ok (test passes here), or ack-then-
                            // Drop (test handles below).
                            null
                        }
                    if (sub != null) {
                        val frames =
                            withTimeoutOrNull(2_000L) {
                                sub.frames.take(1).toList()
                            }
                        // Either:
                        //   - withTimeoutOrNull returned null (flow
                        //     stayed open with no frames for 2 s):
                        //     publisher Drop arrived but didn't
                        //     surface — regression.
                        //   - Empty list (flow closed empty before
                        //     timeout): expected — Drop closed the
                        //     bidi, no frames.
                        assertTrue(
                            frames != null && frames.isEmpty(),
                            "subscribe to a non-existent track should close empty " +
                                "(SubscribeDrop FINs the bidi), but received frames=$frames",
                        )
                    }
                } finally {
                    listenerSession.close()
                }

                handle.close()
                speaker.close()
            } finally {
                pumpScope.coroutineContext[Job]?.cancel()
            }
        }

    /**
     * I10 — long broadcast. Sustained 60 s 440 Hz tone Kotlin
     * speaker → hang-listen, asserts the decoded PCM has ≥ 95 %
     * of the expected sample count.
     *
     * Catches relay-side queue overflow and listener-side stream-
     * count cliff (`MAX_STREAMS_UNI` extension) regressions over
     * minute-scale broadcasts. With `framesPerGroup = 5` the speaker
     * opens ~10 uni streams/s = 600 streams across the run, well
     * past the default 100-stream initial cap that the
     * `:quic` `MaxStreamsFrame` fix in commit `d391ae1d` widened.
     *
     * Tagged so `gradle --tests` filters can include / exclude it
     * (the 60 s wallclock is significant compared to the rest of
     * the suite).
     */
    @Test
    fun long_broadcast_60s_tone_round_trips() =
        runBlocking {
            val out =
                runSpeakerToHangListen(
                    speakerSeconds = 60,
                    captureFirstFrame = false,
                )
            val pcm = readFloat32Pcm(out.pcmFile)
            // ≥ 95 % of 60 s × 48 kHz, with the same skip-warmup
            // window as the I1 scenario so an Opus look-ahead
            // gap doesn't trip the threshold.
            val expectedSamples = 60.0 * AudioFormat.SAMPLE_RATE_HZ
            assertTrue(
                pcm.size >= expectedSamples * 0.95,
                "expected ≥ 95% of $expectedSamples samples in a 60 s broadcast, " +
                    "got ${pcm.size} (${"%.1f".format(pcm.size / expectedSamples * 100)} %)",
            )
            // Spectral content still matches the tone at the tail —
            // catches a silent regression where the relay forwards
            // gibberish bytes after a stream-count limit hit.
            val tailWindow =
                pcm.copyOfRange(
                    (pcm.size - AudioFormat.SAMPLE_RATE_HZ * 5).coerceAtLeast(0),
                    pcm.size,
                )
            PcmAssertions.assertFftPeak(tailWindow, expectedHz = 440.0, halfWindowHz = 5.0)
        }

    /**
     * I9 — 1% packet loss via the `udp-loss-shim` between the
     * Kotlin speaker's UDP socket and the relay. Asserts the
     * decoded PCM still has at least half the expected sample
     * count and the 440 Hz peak survives — moq-lite groups are
     * reliable streams (`bestEffort=false`) so lost bytes get
     * retransmitted, but hang-listen's `Container::Consumer`
     * runs with a 500 ms latency window and aggressively skips
     * groups that arrive late. Random 1 % loss can land on
     * back-to-back packets that push a single group past the
     * window, so the deficit is non-deterministic. The
     * threshold is tuned to catch a wholesale failure (catalog
     * never arrives, all groups dropped) without flaking on
     * normal jitter.
     */
    @Test
    fun packet_loss_1pct_does_not_kill_audio() =
        runBlocking {
            val out =
                runSpeakerToHangListen(
                    speakerSeconds = 5,
                    captureFirstFrame = false,
                    udpLossRate = 0.01f,
                )
            val pcm = readFloat32Pcm(out.pcmFile)
            val expected = 5.0 * AudioFormat.SAMPLE_RATE_HZ
            assertTrue(
                pcm.size >= expected * 0.50,
                "expected ≥ 50% of $expected samples under 1% packet loss, " +
                    "got ${pcm.size} (${"%.1f".format(pcm.size / expected * 100)} %)",
            )
            val warmup = AudioFormat.SAMPLE_RATE_HZ / 25
            val analysed = pcm.copyOfRange(warmup, pcm.size)
            PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
        }

    /**
     * I5 — speaker hot-swap mid-broadcast (proactive JWT refresh).
     * Drives `connectReconnectingNestsSpeaker` with a 2.5 s
     * `tokenRefreshAfterMs` so the speaker recycles its session
     * mid-broadcast.
     *
     * **Limitation:** the reference `hang-listen` is single-shot
     * — it reads the catalog once and subscribes once. When the
     * speaker hot-swaps, the relay unannounces the old broadcast
     * and announces a new one; hang-listen's audio subscription
     * dies and it doesn't re-subscribe. So the listener captures
     * only the pre-hot-swap chunk (~2.5 s of 5 s).
     *
     * The Amethyst production listener handles re-announce
     * transparently (`ReconnectingNestsListener.kt`), so the
     * "no audio gap" assertion the spec calls for is a property
     * of the Amethyst path, not the hang-listen path. This test
     * therefore asserts only:
     *
     *   - the speaker survives the hot-swap (no crash, got some
     *     audio),
     *   - the FFT peak on the captured pre-swap chunk is still
     *     at 440 Hz (the swap doesn't corrupt active uni
     *     streams).
     *
     * The "no audible gap" assertion belongs to a Phase 3
     * follow-up that exercises this scenario through the
     * Amethyst Kotlin LISTENER instead of `hang-listen`.
     */
    @Test
    fun speaker_hot_swap_does_not_crash() =
        runBlocking {
            val out =
                runSpeakerToHangListen(
                    speakerSeconds = 5,
                    captureFirstFrame = false,
                    hotSwapAfterMs = 2_500L,
                )
            val pcm = readFloat32Pcm(out.pcmFile)
            // Got SOMETHING (≥ 1 s of audio) — speaker didn't
            // crash on the hot-swap.
            assertTrue(
                pcm.size >= AudioFormat.SAMPLE_RATE_HZ,
                "expected ≥ 1 s of audio across the hot-swap, got ${pcm.size} samples",
            )
            // The captured chunk's tone is still 440 Hz —
            // spectral integrity intact.
            val warmup = AudioFormat.SAMPLE_RATE_HZ / 25
            val analysed = pcm.copyOfRange(warmup, pcm.size)
            PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
        }

    /**
     * I4 forward — Amethyst Kotlin speaker broadcasts stereo Opus
     * (L=440 Hz, R=660 Hz) to `hang-listen`. Asserts each channel's
     * peak frequency independently, so a regression that downmixes
     * to mono or swaps channels is caught.
     *
     * Validates the speaker-side stereo path end-to-end:
     *   - `AudioBroadcastConfig(channelCount = 2)` plumbs through
     *     `connectNestsSpeaker` → `MoqLiteNestsSpeaker` → catalog,
     *   - `MoqLiteHangCatalog.opus48k(name, 2)` emits
     *     `numberOfChannels: 2` in the published JSON,
     *   - `JvmOpusEncoder(channelCount = 2)` encodes interleaved
     *     L/R PCM into stereo Opus packets,
     *   - hang-listen's catalog reader picks up `channels = 2` and
     *     constructs an `opus::Decoder` for stereo,
     *   - the resulting Float32 PCM file is interleaved L/R/L/R/...
     *     with the per-channel tones intact.
     */
    @Test
    fun amethyst_speaker_to_hang_listener_stereo_440_660() =
        runBlocking {
            val out =
                runSpeakerToHangListen(
                    speakerSeconds = 5,
                    captureFirstFrame = false,
                    channelCount = 2,
                    freqHzPerChannel = intArrayOf(440, 660),
                )
            val pcm = readFloat32Pcm(out.pcmFile)
            // Skip first 80 ms (40 ms × 2 channels = 80 ms of
            // interleaved silence prefix). Opus look-ahead.
            val warmup = AudioFormat.SAMPLE_RATE_HZ / 25 * 2
            val analysed = pcm.copyOfRange(warmup, pcm.size)
            PcmAssertions.assertFftPeakPerChannel(
                analysed,
                expectedHzPerChannel = doubleArrayOf(440.0, 660.0),
                halfWindowHz = 5.0,
            )
        }

    /**
     * I4 reverse — `hang-publish` broadcasts stereo Opus
     * (L=440 Hz, R=660 Hz); the Amethyst Kotlin listener
     * decodes via `JvmOpusDecoder(channelCount = 2)` and
     * asserts the per-channel FFT peaks. Mirror of the I4
     * forward scenario for the listener-side stereo path.
     *
     * Validates:
     *   - hang-publish's `--freq-hz-l` / `--freq-hz-r` /
     *     `--channels 2` produce a real stereo Opus stream,
     *   - the Kotlin listener's `subscribeSpeaker` flow
     *     delivers stereo Opus packets (after timestamp
     *     strip),
     *   - `JvmOpusDecoder(channelCount = 2)` correctly
     *     interleaves L/R PCM,
     *   - per-channel FFT peaks are intact end-to-end.
     */
    @Test
    fun rust_hang_publish_stereo_to_kotlin_listener_440_660() =
        runBlocking {
            val harness = NativeMoqRelayHarness.shared()
            val signer: NostrSigner = NostrSignerInternal(KeyPair())
            val pubkey = signer.pubKey
            val room =
                NestsRoomConfig(
                    authBaseUrl = "<unused-public-relay>",
                    endpoint = harness.relayUrl,
                    hostPubkey = pubkey,
                    roomId = "rt-${UUID.randomUUID()}",
                )
            val moqNamespace = room.moqNamespace()

            val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val transport =
                QuicWebTransportFactory(
                    parentScope = pumpScope,
                    certificateValidator = PermissiveCertificateValidator(),
                )

            // Spawn hang-publish under URL=<moqNamespace>, broadcast
            // suffix=<pubkey>. The Kotlin listener subscribes to
            // exactly that path via `subscribeSpeaker(pubkey)`.
            val publishProc =
                ProcessBuilder(
                    harness.hangPublishBin().toString(),
                    "--relay-url",
                    "${harness.relayUrl}/$moqNamespace",
                    "--broadcast",
                    pubkey,
                    "--track-name",
                    "audio/data",
                    "--channels",
                    "2",
                    "--freq-hz-l",
                    "440",
                    "--freq-hz-r",
                    "660",
                    "--duration",
                    "5",
                ).redirectErrorStream(true)
                    .also { it.environment()["RUST_LOG"] = "info" }
                    .start()

            // Tiny breathing room so the publisher's ANNOUNCE
            // Active is on the relay before we subscribe.
            Thread.sleep(300)

            try {
                val listener =
                    connectNestsListener(
                        httpClient = StaticTokenNestsClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = signer,
                    )
                val subscription = listener.subscribeSpeaker(pubkey)
                val decoder = JvmOpusDecoder(channelCount = 2)
                val pcm = mutableListOf<Float>()
                try {
                    // Collect for ~4 s wallclock (publisher runs
                    // 5 s; allow tail buffer). The flow doesn't
                    // necessarily yield exactly N items — collect
                    // by time, not count.
                    withTimeoutOrNull(4_000L) {
                        subscription.objects.collect { obj ->
                            val samples = decoder.decode(obj.payload)
                            for (s in samples) pcm += s.toFloat() / Short.MAX_VALUE.toFloat()
                        }
                    }
                } finally {
                    decoder.release()
                    listener.close()
                }

                // Read publisher output BEFORE destroying so the
                // stream is still open. Stops at EOF when the
                // publisher exits naturally (5 s --duration), or
                // returns whatever's been written so far if it's
                // still running.
                val published =
                    runCatching {
                        publishProc.inputStream.bufferedReader().readText()
                    }.getOrDefault("(stdout unavailable)")
                publishProc.destroy()
                // Threshold is 0.5 s of stereo (= 0.5 s × 48 kHz
                // × 2 channels = 48 000 floats). Smaller window
                // because under full-suite load (11 tests in one
                // JVM run) the relay's accumulated state can
                // truncate the tail. Per-channel FFT below still
                // asserts the spectral content, so a wire-format
                // regression that mixes / downmixes channels
                // trips even if only 0.5 s arrived.
                assertTrue(
                    pcm.size >= AudioFormat.SAMPLE_RATE_HZ,
                    "expected ≥ 0.5 s of stereo PCM (= ${AudioFormat.SAMPLE_RATE_HZ} floats), " +
                        "got ${pcm.size} floats. hang-publish stderr:\n$published",
                )

                val pcmArr = pcm.toFloatArray()
                // Skip first 80 ms (40 ms × 2 channels) — Opus look-
                // ahead silence.
                val warmup = AudioFormat.SAMPLE_RATE_HZ / 25 * 2
                val analysed = pcmArr.copyOfRange(warmup, pcmArr.size)
                PcmAssertions.assertFftPeakPerChannel(
                    analysed,
                    expectedHzPerChannel = doubleArrayOf(440.0, 660.0),
                    halfWindowHz = 5.0,
                )
            } finally {
                pumpScope.coroutineContext[Job]?.cancel()
                publishProc.destroy()
            }
        }

    /**
     * Rust↔Rust round-trip: pure-Rust through our harness.
     * Validates the cargo workspace + relay config + `moq-lite-03`
     * ALPN end-to-end without any Kotlin in the loop.
     */
    @Test
    fun rust_hang_publish_to_rust_hang_listener_round_trip_440() {
        val harness = NativeMoqRelayHarness.shared()
        val broadcast = "test/${UUID.randomUUID()}"
        val pcmFile = File.createTempFile("hang-listen-pcm", ".bin").also { it.deleteOnExit() }

        val publishProc =
            ProcessBuilder(
                harness.hangPublishBin().toString(),
                "--relay-url",
                "${harness.relayUrl}/$broadcast",
                "--broadcast",
                broadcast,
                "--track-name",
                "audio",
                "--duration",
                "5",
                "--freq-hz",
                "440",
            ).redirectErrorStream(true)
                .also { it.environment()["RUST_LOG"] = "info" }
                .start()
        Thread.sleep(300)
        val listenProc =
            ProcessBuilder(
                harness.hangListenBin().toString(),
                "--relay-url",
                harness.relayUrl,
                "--broadcast",
                broadcast,
                "--duration",
                "6",
                "--output-pcm",
                pcmFile.absolutePath,
            ).redirectErrorStream(true)
                .also { it.environment()["RUST_LOG"] = "info" }
                .start()

        val pubExit = publishProc.waitFor(15, TimeUnit.SECONDS)
        val listenExit = listenProc.waitFor(15, TimeUnit.SECONDS)
        val pubOut = publishProc.inputStream.bufferedReader().readText()
        val listenOut = listenProc.inputStream.bufferedReader().readText()
        assertTrue(pubExit, "hang-publish did not exit. Output:\n$pubOut")
        assertTrue(listenExit, "hang-listen did not exit. Output:\n$listenOut")
        assertEquals(0, publishProc.exitValue(), "hang-publish exited non-zero. Output:\n$pubOut")
        assertEquals(0, listenProc.exitValue(), "hang-listen exited non-zero. Output:\n$listenOut")

        val pcm = readFloat32Pcm(pcmFile)
        PcmAssertions.assertSampleCount(pcm, expectedDurationSec = 5.0, tolerance = 0.20)
        val warmup = AudioFormat.SAMPLE_RATE_HZ / 25
        val analysed = pcm.copyOfRange(warmup, pcm.size)
        PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
        PcmAssertions.assertZeroCrossingRate(
            analysed,
            expectedPerSecond = 880.0,
            tolerance = 0.05,
        )
    }
}

/**
 * Container for files [runSpeakerToHangListen] hands back to the test.
 */
private class HangListenOutput(
    val pcmFile: File,
    val firstFrameFile: File?,
)

/**
 * Run the Kotlin speaker for [speakerSeconds] seconds, drive a
 * [hang-listen] subprocess to capture decoded PCM, optionally
 * applying mute / late-join / first-frame-dump tweaks. Returns
 * the captured files to the caller for assertion.
 *
 *   - [listenerLateJoinDelayMs]: spawn `hang-listen` after this
 *     many ms of broadcast (default 150 ms — just enough for the
 *     speaker's announce to reach the relay before the
 *     subscriber connects, sidesteps the catalog-hook race in
 *     `MoqLiteNestsSpeaker`).
 *   - [muteWindowMs]: if non-null, mute the speaker for this
 *     [start, end] window (in ms relative to broadcast start).
 *   - [captureFirstFrame]: if true, pass `--dump-first-frame
 *     <path>` so the test can read the first frame's raw bytes.
 */
private suspend fun runSpeakerToHangListen(
    speakerSeconds: Int,
    listenerLateJoinDelayMs: Long = 150L,
    muteWindowMs: ClosedRange<Long>? = null,
    captureFirstFrame: Boolean,
    /**
     * If non-null, route the Kotlin speaker's UDP through a
     * `udp-loss-shim` subprocess that drops this fraction of
     * datagrams (0.0..=1.0). The shim listens on a fresh
     * ephemeral port and forwards to the harness's relay; the
     * speaker's `endpoint` is rewritten to the shim port. The
     * hang-listen subprocess still connects directly to the
     * relay (no loss), so any frame deficit is attributable to
     * the speaker→relay leg.
     */
    udpLossRate: Float? = null,
    /**
     * If non-null, drive the speaker through
     * `connectReconnectingNestsSpeaker` with this
     * `tokenRefreshAfterMs`, forcing a session recycle (hot-swap)
     * mid-broadcast. Default uses the simple non-reconnecting
     * speaker — the I1/I2/I3/I8/I10/I11 scenarios don't need
     * the reconnect orchestrator.
     */
    hotSwapAfterMs: Long? = null,
    /**
     * Per-channel sine-wave config for the speaker. Default mono
     * 440 Hz; I4 stereo passes `(channels=2, freqHzPerChannel=[440, 660])`.
     */
    channelCount: Int = 1,
    freqHzPerChannel: IntArray? = null,
): HangListenOutput {
    val harness = NativeMoqRelayHarness.shared()

    val signer: NostrSigner = NostrSignerInternal(KeyPair())
    val pubkey = signer.pubKey

    // If a loss rate is requested, spin up a udp-loss-shim
    // between the speaker and the relay. The speaker connects
    // through the shim (lossy); hang-listen still connects to
    // the relay directly so any frame deficit is attributable
    // to the lossy leg.
    val (relayHostForSpeaker, relayPortForSpeaker, lossShimProc) =
        if (udpLossRate != null) {
            val shimPort = java.net.ServerSocket(0).use { it.localPort }
            val (relayHost, relayPort) = harness.loopbackHostPort()
            val proc =
                ProcessBuilder(
                    harness.udpLossShimBin().toString(),
                    "--listen",
                    "127.0.0.1:$shimPort",
                    "--upstream",
                    "$relayHost:$relayPort",
                    "--loss-rate",
                    udpLossRate.toString(),
                ).redirectErrorStream(true)
                    .also { it.environment()["RUST_LOG"] = "info" }
                    .start()
            // Tiny breathing room for the shim's listen socket
            // to bind before the speaker's QUIC handshake hits.
            Thread.sleep(200)
            Triple("127.0.0.1", shimPort, proc)
        } else {
            val (h, p) = harness.loopbackHostPort()
            Triple(h, p, null)
        }
    val speakerEndpoint = "https://$relayHostForSpeaker:$relayPortForSpeaker"

    val room =
        NestsRoomConfig(
            authBaseUrl = "<unused-public-relay>",
            endpoint = speakerEndpoint,
            hostPubkey = pubkey,
            roomId = "rt-${UUID.randomUUID()}",
        )
    val moqNamespace = room.moqNamespace()

    val pcmFile =
        File.createTempFile("hang-listen-pcm", ".bin").also { it.deleteOnExit() }
    val firstFrameFile =
        if (captureFirstFrame) {
            File.createTempFile("hang-listen-first-frame", ".bin").also { it.deleteOnExit() }
        } else {
            null
        }

    val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val transport =
        QuicWebTransportFactory(
            // Pin the transport's coroutine scope to the per-test
            // pumpScope so cancelling pumpScope in `finally` also
            // tears down the transport's UDP socket + QuicConnection
            // pumps. Without this, each scenario leaks a UDP socket
            // and a coroutine tree, and KotlinSpeakerKotlinListenerThroughNativeRelayTest
            // (which runs last in the alphabetical order) flakes
            // under the accumulated relay load.
            parentScope = pumpScope,
            certificateValidator = PermissiveCertificateValidator(),
        )

    val captureFactory: () -> SineWaveAudioCapture = {
        SineWaveAudioCapture(
            freqHz = 440,
            channelCount = channelCount,
            freqHzPerChannel = freqHzPerChannel,
        )
    }
    val encoderFactory: () -> JvmOpusEncoder = {
        JvmOpusEncoder(channelCount = channelCount)
    }
    val broadcastConfig = AudioBroadcastConfig(channelCount = channelCount)

    lateinit var listenProc: Process
    try {
        val speaker =
            if (hotSwapAfterMs != null) {
                connectReconnectingNestsSpeaker(
                    httpClient = StaticTokenNestsClient,
                    transport = transport,
                    scope = pumpScope,
                    room = room,
                    signer = signer,
                    speakerPubkeyHex = pubkey,
                    captureFactory = captureFactory,
                    encoderFactory = encoderFactory,
                    broadcastConfig = broadcastConfig,
                    tokenRefreshAfterMs = hotSwapAfterMs,
                    connector = {
                        connectNestsSpeaker(
                            httpClient = StaticTokenNestsClient,
                            transport = transport,
                            scope = pumpScope,
                            room = room,
                            signer = signer,
                            speakerPubkeyHex = pubkey,
                            captureFactory = captureFactory,
                            encoderFactory = encoderFactory,
                            broadcastConfig = broadcastConfig,
                            framesPerGroup = 5,
                        )
                    },
                )
            } else {
                connectNestsSpeaker(
                    httpClient = StaticTokenNestsClient,
                    transport = transport,
                    scope = pumpScope,
                    room = room,
                    signer = signer,
                    speakerPubkeyHex = pubkey,
                    captureFactory = captureFactory,
                    encoderFactory = encoderFactory,
                    broadcastConfig = broadcastConfig,
                    framesPerGroup = 5,
                )
            }
        val handle = speaker.startBroadcasting()
        delay(listenerLateJoinDelayMs)

        val cmd =
            mutableListOf(
                harness.hangListenBin().toString(),
                "--relay-url",
                harness.relayUrl,
                "--broadcast",
                moqNamespace,
                "--duration",
                "${speakerSeconds + 2}",
                "--output-pcm",
                pcmFile.absolutePath,
            )
        firstFrameFile?.let {
            cmd += listOf("--dump-first-frame", it.absolutePath)
        }
        listenProc =
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .also { it.environment()["RUST_LOG"] = "info" }
                .start()

        if (muteWindowMs != null) {
            val muteStart = muteWindowMs.start
            val muteEnd = muteWindowMs.endInclusive
            // listenerLateJoinDelayMs has already been waited
            // before this point. Subtract it so the mute schedule
            // is anchored to broadcast start.
            val toMute = (muteStart - listenerLateJoinDelayMs).coerceAtLeast(0)
            val toUnmute = muteEnd - muteStart
            val toEnd = speakerSeconds * 1_000L - muteEnd

            delay(toMute)
            handle.setMuted(true)
            delay(toUnmute)
            handle.setMuted(false)
            delay(toEnd)
        } else {
            delay(speakerSeconds * 1_000L - listenerLateJoinDelayMs)
        }
        handle.close()
        speaker.close()
    } finally {
        pumpScope.coroutineContext[Job]?.cancel()
        lossShimProc?.destroy()
    }

    val exited = listenProc.waitFor(15, TimeUnit.SECONDS)
    val output = listenProc.inputStream.bufferedReader().readText()
    assertTrue(exited, "hang-listen did not exit within 15 s. Output:\n$output")
    assertEquals(
        0,
        listenProc.exitValue(),
        "hang-listen exited non-zero. Output:\n$output",
    )
    return HangListenOutput(pcmFile = pcmFile, firstFrameFile = firstFrameFile)
}

/**
 * Bypass the NIP-98 auth handshake — the harness boots moq-relay
 * with `--auth-public ""`, which grants any path without a JWT.
 */
private object StaticTokenNestsClient : NestsClient {
    override suspend fun mintToken(
        room: NestsRoomConfig,
        publish: Boolean,
        signer: NostrSigner,
    ): String = ""
}

/**
 * Read a file of native-endian Float32 little-endian PCM into a
 * [FloatArray]. The hang-listen binary writes LE Float32, no header.
 */
private fun readFloat32Pcm(file: File): FloatArray {
    val bytes = file.readBytes()
    require(bytes.size % 4 == 0) {
        "PCM file size ${bytes.size} is not a multiple of 4 (Float32)"
    }
    val n = bytes.size / 4
    val out = FloatArray(n)
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until n) out[i] = buf.float
    return out
}
