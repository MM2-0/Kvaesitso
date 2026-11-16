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
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.AudioFormat
import com.vitorpamplona.nestsclient.audio.JvmOpusDecoder
import com.vitorpamplona.nestsclient.audio.JvmOpusEncoder
import com.vitorpamplona.nestsclient.audio.PcmAssertions
import com.vitorpamplona.nestsclient.audio.SineWaveAudioCapture
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.connectReconnectingNestsListener
import com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.rules.TestName
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 4 (T16) — browser-side cross-stack interop scenarios.
 *
 * Drives a headless Chromium subprocess (via [PlaywrightDriver])
 * through the same [NativeMoqRelayHarness] moq-relay that
 * [HangInteropTest] uses. The browser harness page connects via
 * Chromium's WebTransport stack (separate implementation from quinn /
 * `:quic`), decodes Opus via WebCodecs `AudioDecoder`, and posts
 * Float32 LE PCM frames back to a bun WebSocket back-channel that
 * appends them to a file the test reads.
 *
 * Phase 4.B P0 scenario:
 *   - **I1 browser** — sine-wave round-trip Amethyst Kotlin speaker →
 *     Chromium @moq/lite + @moq/hang listener; assert FFT peak at
 *     440 Hz on the captured PCM.
 *
 * Speaker pinned at `framesPerGroup = 5` to stay under the
 * `moq-relay 0.10.x` per-subscriber forward cliff (same as the
 * hang-tier scenarios).
 *
 * Gate: `-DnestsBrowserInterop=true` (also implies
 * `-DnestsHangInterop=true` indirectly because we boot the same
 * `NativeMoqRelayHarness`).
 */
class BrowserInteropTest {
    /**
     * Tags the per-method moq-relay log file when trace capture is
     * enabled. See `HangInteropTest.testName`.
     */
    @Rule @JvmField
    val testName: TestName = TestName()

    @BeforeTest
    fun gate() {
        PlaywrightDriver.assumeBrowserInterop()
        // The browser harness reuses the moq-relay subprocess that the
        // hang-tier scenarios bring up. Without `-DnestsHangInterop=true`
        // the harness would refuse to boot — flip it on automatically
        // for the browser gate so the user only has to set one flag.
        if (!NativeMoqRelayHarness.isEnabled()) {
            System.setProperty(NativeMoqRelayHarness.ENABLE_PROPERTY, "true")
        }
        // Reset the shared relay subprocess between browser scenarios.
        // Same rationale as HangInteropTest: sharing across all the
        // BrowserInteropTest scenarios in one JVM run means the relay's
        // per-subscriber forward queues + announce tables accumulate
        // state from prior tests, manifesting as intermittent
        // listener-side `frames=0` flakes (especially when
        // browser-publisher tests run alongside browser-listener
        // tests). Per-method reboot costs ~500 ms (cargo binaries are
        // cached); acceptable for the stability gain.
        NativeMoqRelayHarness.resetShared(testTag = testName.methodName)
    }

    /**
     * **I15 (WT-Protocol round-trip)** — assert Chromium's WebTransport
     * round-trip with `moq-relay 0.10.x` produces a known-good moq-lite
     * version on the `Connection`. The harness page exposes
     * `connection.version` at `window.__moqVersion`; the Playwright
     * spec bundles it in the trailing JSON line on stdout.
     *
     * The assertion accepts any of the moq-lite draft versions the
     * relay advertises through SETUP — Chromium's `@moq/lite` 0.2.x
     * client offers `moq-lite-04`, `moq-lite-03`, `moql` (legacy)
     * ALPNs in that priority. moq-relay 0.10.x's choice depends on
     * its build flags, but the `moq-lite-` prefix is invariant. A
     * regression that breaks ALPN negotiation entirely or
     * downgrades to a non-lite version (`draft-17` etc.) is caught
     * here even if I1 audio assertions still pass via a fallback path.
     */
    @Test
    fun chromium_round_trips_a_moq_lite_session() =
        runBlocking {
            val out = runSpeakerToBrowserListen(speakerSeconds = 5)
            val moqVersion = parseMoqVersionFromStdout(out.stdout)
            assertTrue(
                moqVersion != null && moqVersion.startsWith("moq-lite-"),
                "expected Chromium to round-trip a moq-lite-* version; got '$moqVersion'.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
        }

    /**
     * **I13 (browser long broadcast)** — 60 s end-to-end Amethyst
     * speaker → Chromium listener; assert the captured PCM has the
     * expected sample count and the 440 Hz peak survives the full
     * window without decoder failure.
     *
     * The spec specifies `framesPerGroup = 50` "against actual
     * `Container.Consumer`", but two constraints reshape this here:
     *
     *   1. `@moq/hang` 0.2.4 (the published version pinned in
     *      `nestsClient/tests/browser-interop/package.json`) does not export
     *      the high-level `Container.Consumer` / `Format` API. Phase 4
     *      uses `Container.Legacy.Consumer` directly — same data path
     *      `@moq/watch` uses internally for `container.kind = "legacy"`.
     *   2. `framesPerGroup = 50` against the local `moq-relay 0.10.25
     *      --auth-public ""` harness hits the per-subscriber forward
     *      cliff documented in
     *      `2026-05-07-framespergroup-reconciliation.md` — the relay
     *      forwards the `Group` control header but holds the frame
     *      payload, so no audio reaches the listener at all. Production
     *      uses `framesPerGroup = 50`; locally we pin `5` to bypass
     *      the local-relay-specific cliff and still exercise the
     *      browser path's long-haul behaviour.
     *
     * What this catches that I1 forward (browser, 10 s) doesn't:
     *   - Chromium WebTransport `MAX_STREAMS_UNI` credit drift over
     *     thousands of unidirectional streams (60 s × 10 streams/s =
     *     600 streams; far past the connection's initial window),
     *   - `@moq/hang` `Container.Legacy.Consumer` group-queue eviction
     *     (`MAX_GROUP_AGE = 30 s` in moq-rs; we pass that bound twice),
     *   - WebCodecs `AudioDecoder` pacing + memory pressure across a
     *     real broadcast-length capture window.
     */
    @Test
    fun chromium_listener_long_broadcast_60s_tone_440() =
        runBlocking {
            // 65 s wallclock budget to absorb the cold-launch lag.
            val out = runSpeakerToBrowserListen(speakerSeconds = 65)

            // Decoder MUST NOT have errored at any point — even one
            // error means a frame couldn't decode (T8 regression, codec
            // mismatch, group-stream truncation surfaced as malformed
            // Opus). The error count is part of the meta JSON the
            // harness page emits via `console.log`.
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "decoderErrors=$errors during 60 s long broadcast — expected 0.\n" +
                    "playwright stdout:\n${out.stdout}",
            )

            val pcm = readFloat32Pcm(out.pcmFile)
            // Skip the 100 ms warmup window before FFT (40 ms Opus
            // look-ahead + 60 ms WebCodecs 3-frame warmup).
            val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 10
            assertTrue(
                pcm.size > warmupSamples,
                "captured PCM (${pcm.size} samples) shorter than warmup window — " +
                    "page never received audio.\nplaywright stdout:\n${out.stdout}",
            )
            val analysed = pcm.copyOfRange(warmupSamples, pcm.size)

            // Sample-count floor: ≥ 50 s of decoded PCM. The full
            // possible window is ~60 s minus the page's cold-launch
            // tail-truncation (typically ~3-10 s on a fresh runner).
            // 50 s is the regression bar — anything less indicates
            // the browser stopped receiving frames mid-broadcast
            // (the very mode I13 is meant to catch).
            val minSamples = 50 * AudioFormat.SAMPLE_RATE_HZ
            assertTrue(
                analysed.size >= minSamples,
                "captured ${analysed.size} samples (~${analysed.size / AudioFormat.SAMPLE_RATE_HZ} s); " +
                    "expected ≥ 50 s of decoded PCM in a 60 s long broadcast — possible " +
                    "browser-side stream-credit exhaustion, group-queue eviction, or " +
                    "decoder backpressure.\nplaywright stdout:\n${out.stdout}",
            )

            PcmAssertions.assertFftPeak(
                analysed,
                expectedHz = 440.0,
                halfWindowHz = 5.0,
            )
        }

    /**
     * **I14 (WebCodecs warmup × CSD-skip interaction)** — assert
     * Chromium's `AudioDecoder` does NOT error during the standard
     * 3-frame warmup window when fed Opus packets from the JVM
     * `JvmOpusEncoder`. A T8 regression that leaks `OpusHead`
     * (the 19-byte RFC 7845 identification header) as a normal audio
     * frame would land in the warmup window and trip
     * `AudioDecoder.error` — Chromium's WebCodecs implementation
     * rejects non-Opus-packet bytes with a `DataError`.
     *
     * The complement (FFT peak survives even if the decoder absorbed
     * the stray frame silently) is already covered by I1 forward;
     * I14 is the deterministic tripwire on the error-callback path.
     *
     * NOTE: The JVM speaker uses libopus (`JvmOpusEncoder`) directly,
     * which never produces a CSD prefix — so on this test path I14
     * effectively asserts no spurious decode failures. T8 itself is
     * an Android-`MediaCodecOpusEncoder`-specific fix; the matching
     * Android-side regression test would require a different harness
     * (no Chromium on Android in this build). We keep I14 here as
     * the browser-tier mate of `HangInteropTest.first_audio_frame_is_not_opus_codec_config`
     * (I11) — together they assert the wire format is decoder-clean
     * on both reference paths.
     */
    @Test
    fun chromium_decoder_no_errors_through_warmup_window() =
        runBlocking {
            // 10 s capture for parity with I1 forward — Chromium
            // cold-launch + Playwright runner setup eats 3-5 s before
            // the page's `durationSec` window starts ticking. A shorter
            // window ends before any frames reach the decoder, which
            // would also pass `decoderErrors == 0` vacuously.
            val out = runSpeakerToBrowserListen(speakerSeconds = 10)
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "AudioDecoder.error fired $errors times during a 10 s broadcast — expected 0. " +
                    "A T8 regression (OpusHead leaked as audio frame) would surface here as " +
                    "Chromium rejecting the frame.\nplaywright stdout:\n${out.stdout}",
            )
            // Hard floor on `decoderOutputs`: WebCodecs' AudioDecoder
            // drops the first 3 outputs (Opus look-ahead silence the
            // browser strips per the `outputs.length > 3` check in
            // `listen.ts`). A floor of 4 guarantees ≥ 1 audio output
            // landed AND was decoded without error — a T8 regression
            // (OpusHead leaked as audio frame) would still let the
            // 3 silent warmup outputs through, then trip an
            // `error()` and stop the counter ≤ 3. Pre-`:quic`-merge
            // this floor flaked on Chromium's cold-launch race; with
            // the post-handshake bidi-drop fix landed
            // (`2026-05-07-moq-relay-routing-investigation.md`
            // § Closure) the floor is now reliable.
            val outputs = parseIntMetaFromStdout(out.stdout, "decoderOutputs") ?: -1
            assertTrue(
                outputs >= 4,
                "decoderOutputs=$outputs (< 4 = 3 warmup + ≥ 1 audio). " +
                    "decoderErrors=$errors. Either no audio reached the decoder, or a " +
                    "regression killed the pipeline before the warmup window cleared.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
        }

    /**
     * **I2 (browser late-join)** — Chromium attaches mid-broadcast.
     * The page boots about 3-5 s into a 10 s broadcast, captures the
     * tail, asserts the 440 Hz peak survives. Mirror of the hang-tier
     * `late_join_listener_still_decodes_tail`.
     *
     * The cold-launch lag the Phase 4 agent documented in I1 forward
     * IS the late-join window for this scenario — adding an explicit
     * `listenerLateJoinDelayMs = 2_000` on top makes the late-join
     * even more pronounced (browser captures only ~3 s of audio in
     * the best case). The load-bearing assertion is the FFT peak;
     * the sample-count floor is loose for the same harness-flake
     * reason as I1.
     */
    @Test
    fun chromium_listener_late_join_still_decodes_tail() =
        runBlocking {
            val out =
                runSpeakerToBrowserListen(
                    speakerSeconds = 10,
                    listenerLateJoinDelayMs = 2_000,
                )
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "decoderErrors=$errors during late-join — expected 0.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            val pcm = readFloat32Pcm(out.pcmFile)
            val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 10
            // Hard floor: 1.5 s of audio after warmup. The broadcast
            // continues for ~5+ s after late-join (10 s broadcast,
            // listener attaches at T+2 s, allow ~3 s of cold-launch
            // tail truncation). 1.5 s is comfortably under the
            // steady-state floor (~3 s) but well above the
            // catalog-cancelled-mid-warmup failure mode (0 s).
            val minSamplesAfterWarmup = (1.5 * AudioFormat.SAMPLE_RATE_HZ).toInt()
            assertTrue(
                pcm.size > warmupSamples + minSamplesAfterWarmup,
                "late-join captured ${pcm.size} samples (≤ warmup + 1.5 s floor). " +
                    "The relay-side subscribe-routing path failed.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            val analysed = pcm.copyOfRange(warmupSamples, pcm.size)
            PcmAssertions.assertFftPeak(
                analysed,
                expectedHz = 440.0,
                halfWindowHz = 5.0,
            )
        }

    /**
     * **I3 (browser mute window)** — speaker mutes 1 s mid-broadcast.
     * Per T10 the speaker FINs the open uni stream rather than
     * emitting silence, so the browser captures a sample-count
     * deficit, not embedded zeros. Asserts the captured PCM still
     * has the 440 Hz peak in the un-muted segments AND the total
     * sample count is below the no-mute baseline by ≥ 0.5 s.
     *
     * Mirror of the hang-tier `mid_broadcast_mute_shortens_decoded_pcm`,
     * with looser sample-count bounds for the same browser harness
     * cold-launch reason as I1.
     */
    @Test
    fun chromium_listener_mid_broadcast_mute_shortens_pcm() =
        runBlocking {
            val out =
                runSpeakerToBrowserListen(
                    speakerSeconds = 6,
                    // Mute from T+2 s to T+3 s — 1 s of silence
                    // sandwiched in a 6 s broadcast, leaving ~5 s of
                    // un-muted audio to capture.
                    muteWindowMs = 2_000L..3_000L,
                )
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "decoderErrors=$errors during mute scenario — expected 0.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            val pcm = readFloat32Pcm(out.pcmFile)
            val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 10
            // Hard LOWER bound: ≥ 2.5 s of audio after warmup. The
            // 6 s broadcast minus ~1 s mute leaves ~5 s of un-muted
            // audio; a 2.5 s floor proves the un-muted halves both
            // arrived end-to-end (catches "mute path tore down the
            // broadcast" regressions).
            val minSamplesAfterWarmup = (2.5 * AudioFormat.SAMPLE_RATE_HZ).toInt()
            assertTrue(
                pcm.size > warmupSamples + minSamplesAfterWarmup,
                "mute scenario captured ${pcm.size} samples (≤ warmup + 2.5 s floor) — " +
                    "the mute path appears to have torn down the broadcast.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            val analysed = pcm.copyOfRange(warmupSamples, pcm.size)

            // Hard UPPER bound: total decoded PCM must be less than
            // what a full 6 s broadcast would yield. A regression to
            // "push silence instead of FIN" would produce ~6 s of
            // audio (with embedded zeros) — that's the failure we
            // catch here. Empirical post-`:quic`-merge steady-state
            // sample count is ~5.1–5.2 s (Chromium AudioDecoder
            // ramp-up + harness window padding); 5.5 s is the
            // tightest upper bound that survives sweep variation
            // while still excluding the 6 s no-mute regression.
            val maxSamplesIfNoMute = (5.5 * AudioFormat.SAMPLE_RATE_HZ).toInt()
            assertTrue(
                analysed.size < maxSamplesIfNoMute,
                "captured ${analysed.size} samples — expected < $maxSamplesIfNoMute " +
                    "(= 5.5 s) because the speaker FINs on mute. A regression to " +
                    "push embedded silence would yield ~6 s.\nplaywright stdout:\n${out.stdout}",
            )
            // FFT still finds the 440 Hz peak — the un-muted halves
            // dominate the spectrum even with a 1 s gap.
            PcmAssertions.assertFftPeak(
                analysed,
                expectedHz = 440.0,
                halfWindowHz = 5.0,
            )
        }

    /**
     * **I4 (browser stereo)** — Amethyst speaker publishes a stereo
     * (440 Hz L / 660 Hz R) catalog; the Chromium WebCodecs decoder
     * decodes both channels; we assert each channel's FFT peak
     * independently. Mirror of the hang-tier
     * `amethyst_speaker_to_hang_listener_stereo_440_660`.
     *
     * What this catches that the hang-tier I4 doesn't:
     *   - Chromium WebCodecs `AudioDecoder` configured with
     *     `numberOfChannels = 2` correctly de-interleaves stereo
     *     Opus packets (different code path from the libopus-backed
     *     `JvmOpusDecoder` the hang tier uses),
     *   - The browser harness's `listen.ts` stereo path
     *     (interleave-from-planar) round-trips L/R correctly.
     */
    @Test
    fun chromium_listener_stereo_440_660() =
        runBlocking {
            val out =
                runSpeakerToBrowserListen(
                    speakerSeconds = 10,
                    channelCount = 2,
                    freqHzPerChannel = intArrayOf(440, 660),
                )
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "decoderErrors=$errors during stereo broadcast — expected 0.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            val pcm = readFloat32Pcm(out.pcmFile)
            // Stereo PCM is interleaved L/R/L/R per
            // `listen.ts`'s output path. Skip 100 ms of warmup
            // (= 0.1 × sampleRate × 2 channels = 9600 floats).
            val warmupFloats = (AudioFormat.SAMPLE_RATE_HZ / 10) * 2
            // Hard floor: ≥ 1 s per channel of post-warmup audio
            // (= SAMPLE_RATE × 2 floats/sample). FFT resolves a peak
            // reliably with ≥ 0.5 s, so 1 s is a comfortable floor.
            val minFloatsAfterWarmup = AudioFormat.SAMPLE_RATE_HZ * 2
            assertTrue(
                pcm.size > warmupFloats + minFloatsAfterWarmup,
                "stereo captured ${pcm.size} float samples (≤ warmup + 1 s × 2 ch floor).\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            val analysed = pcm.copyOfRange(warmupFloats, pcm.size)
            PcmAssertions.assertFftPeakPerChannel(
                interleaved = analysed,
                expectedHzPerChannel = doubleArrayOf(440.0, 660.0),
                halfWindowHz = 5.0,
            )
        }

    /**
     * **I5 (browser hot-swap)** — speaker hot-swaps mid-broadcast
     * via [connectReconnectingNestsSpeaker] firing a JWT-refresh
     * recycle at T+2.5 s. The Chromium listener's WebTransport
     * session stays alive throughout (hot-swap is speaker-side only;
     * the listener's session is independent), and because the
     * speaker re-publishes the same broadcast suffix the page sees
     * `Announce::Ended → Active`.
     *
     * **Soft assertion only.** Empirical post-`:quic`-merge sample
     * counts vary 0–7.7 k samples (≤ 160 ms TOTAL) — Chromium's
     * `@moq/lite` 0.2.x client doesn't re-attach across the
     * `Active::Ended → Active` boundary, so any hard sample-count
     * floor would fail-flake. T12 (group sequence carry across
     * hot-swaps) is hard-asserted by the hang-tier counterpart
     * `speaker_hot_swap_does_not_crash` in [HangInteropTest], which
     * decodes the full post-swap window with the 440 Hz peak
     * intact. The browser tier here only verifies:
     *   - the harness boots and the publisher hot-swap completes
     *     without crashing the test process,
     *   - Chromium's WebCodecs `AudioDecoder` doesn't fire a
     *     `decoderErrors > 0` event during the swap window.
     *
     * Tracked as the "browser hot-swap re-attach" deferred item in
     * `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md`.
     * Promote to a hard floor once `@moq/lite` / `@moq/hang` gain
     * a working subscribe-re-attach path.
     */
    @Test
    fun chromium_listener_speaker_hot_swap_does_not_crash() =
        runBlocking {
            val out =
                runSpeakerToBrowserListen(
                    speakerSeconds = 7,
                    hotSwapAfterMs = 2_500L,
                )
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "decoderErrors=$errors during hot-swap — expected 0.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            // No PCM sample-count or FFT assertion — see kdoc.
        }

    /**
     * **I9 (browser 1 % packet loss)** — speaker → relay leg goes
     * through `udp-loss-shim` at 1 % loss; Chromium listener still
     * connects to the relay directly. Asserts the FFT peak survives
     * — frame loss on the speaker leg surfaces as a sample-count
     * deficit but the un-lost frames carry the same tone.
     *
     * Mirror of the hang-tier `packet_loss_1pct_does_not_kill_audio`.
     * If `bestEffort = true` is reintroduced on moq-lite group uni
     * streams (regression on T11), unreliable streams under loss
     * would fail to retransmit and the deficit would crater past
     * the floor.
     */
    @Test
    fun chromium_listener_packet_loss_1pct_does_not_kill_audio() =
        runBlocking {
            val out =
                runSpeakerToBrowserListen(
                    speakerSeconds = 10,
                    udpLossRate = 0.01f,
                )
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "decoderErrors=$errors under 1 % packet loss — expected 0.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            val pcm = readFloat32Pcm(out.pcmFile)
            val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 10
            // Hard floor: ≥ 0.5 s of audio after warmup. Under 1 %
            // packet loss the speaker → relay leg loses ~1 frame in
            // 100 (~20 ms in 2 s), well below the 0.5 s floor. A
            // T11 regression that re-introduced `bestEffort = true`
            // on group uni streams would crater past this floor as
            // unreliable streams skip retransmits.
            val minSamplesAfterWarmup = AudioFormat.SAMPLE_RATE_HZ / 2
            assertTrue(
                pcm.size > warmupSamples + minSamplesAfterWarmup,
                "1% packet-loss captured ${pcm.size} samples (≤ warmup + 0.5 s floor) — " +
                    "unreliable-streams regression would land here.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
            val analysed = pcm.copyOfRange(warmupSamples, pcm.size)
            PcmAssertions.assertFftPeak(
                analysed,
                expectedHz = 440.0,
                halfWindowHz = 5.0,
            )
        }

    /**
     * **Browser-publish baseline** — Chromium runs `publish.ts`
     * (no reconnect) against a 5 s broadcast; Amethyst Kotlin
     * listener subscribes via [connectReconnectingNestsListener]
     * (we use the reconnecting wrapper so the wrapper's
     * opener-throws retry path masks Chromium's cold-launch lag,
     * during which the listener's subscribe arrives before
     * Chromium has finished announcing).
     *
     * Companion to the reconnect scenario below. If this baseline
     * passes but the reconnect one doesn't, the regression is in
     * the cycle-handling code; if both fail, the regression is in
     * the basic Chromium-publish-Kotlin-listen path.
     */
    @Test
    fun chromium_publisher_baseline_kotlin_listener_decodes() =
        runBlocking {
            // 0.5 s sample-count floor. The load-bearing assertion is the
            // FFT peak; this floor only catches the "nothing arrived at
            // all" failure mode. (The collection window starts at the first
            // frame, so Chromium's 3-5 s cold launch no longer eats into it.)
            runBrowserPublishKotlinListen(
                speakerSeconds = 5,
                reconnectAfterMs = 0L,
                minSamplesAfterWarmup = AudioFormat.SAMPLE_RATE_HZ / 2,
            )
        }

    /**
     * **I7 reverse (browser publisher reconnect)** — Chromium runs
     * `publish.ts` with `?reconnectAfterMs=2500` against a 5 s
     * broadcast: connects, announces, publishes ~2.5 s of Opus,
     * drops its `Connection`, builds a fresh one, re-publishes the
     * same broadcast suffix. The Amethyst Kotlin listener (driven
     * through [connectReconnectingNestsListener]) re-issues its
     * subscribe via the wrapper's inner-cycle pump and continues
     * decoding into the second cycle.
     *
     * Mirror of the hang-tier
     * `HangInteropReverseTest.rust_hang_publish_reconnect_kotlin_listener_recovers`,
     * but with Chromium's `@moq/lite` + WebCodecs `AudioEncoder`
     * standing in for the Rust `hang-publish` binary. What this
     * catches that the hang-tier I7 doesn't:
     *   - Chromium's WebTransport `Connection.connect → close →
     *     reconnect` round-trip handling for moq-lite,
     *   - WebCodecs `AudioEncoder` keyframe-on-fresh-producer
     *     semantics across the cycle (a regression that emitted
     *     a non-keyframe first packet on cycle 2 would land at the
     *     listener as a Container.Legacy decoder rejection),
     *   - The listener's `connectReconnectingNestsListener`
     *     re-issuance pump for an upstream that is BROWSER not Rust
     *     (different transport stack on the publisher side).
     *
     * Threshold: ≥ 3.5 s of decoded mono PCM with the 440 Hz peak
     * intact. The page sends each cycle as ONE group (group 0), so the
     * first cycle alone yields at most ~2.5 s (~125 frames); only a
     * listener that also decodes the second cycle reaches 3.5 s, and a
     * healthy run gets ~4.9 s. The old 2.5 s floor sat right at the
     * first cycle's length, so it passed or failed by a hair while the
     * listener was in fact dropping the whole second cycle as a replay
     * of group 0 (#4577).
     */
    @Test
    fun chromium_publisher_reconnect_kotlin_listener_recovers() =
        runBlocking {
            // The first cycle alone yields ~2.5 s of decoded PCM, so 3.5 s
            // proves the listener re-attached to the publisher's second
            // cycle through the reconnecting wrapper's re-issuance pump
            // (see the KDoc above).
            runBrowserPublishKotlinListen(
                speakerSeconds = 5,
                reconnectAfterMs = 2_500L,
                minSamplesAfterWarmup = (3.5 * AudioFormat.SAMPLE_RATE_HZ).toInt(),
            )
        }

    /**
     * **I1 forward (browser)** — Amethyst Kotlin speaker → Chromium
     * `@moq/lite` listener with `@moq/hang` `Container.Legacy.Consumer`.
     * Asserts the captured PCM has the expected sample count and the
     * 440 Hz tone survives end-to-end.
     *
     * What this catches that the Rust hang-listen path doesn't:
     *   - Chromium's WebTransport ALPN negotiation (independent
     *     implementation from quinn),
     *   - WebCodecs `AudioDecoder` first-frame handling (different
     *     warmup behaviour from libopus — drops the first 3 output
     *     frames; we offset the warmup window accordingly),
     *   - `OpusHead` codec-config wedging would still bypass the
     *     decoder warmup window and produce a click at offset 0;
     *     T8's filter is verified again here.
     */
    @Test
    fun amethyst_speaker_to_chromium_listener_static_tone_440() =
        runBlocking {
            // Speaker runs for 10 s wallclock; we assert the page captured
            // ≥ 1 s of decoded PCM. The looser bound reflects how the page's
            // capture window opens *after* Chromium cold-launch + WebTransport
            // handshake (3–5 s on a fresh runner), and the Kotlin speaker
            // pins `framesPerGroup = 5` (= 100 ms groups) so a late-joining
            // subscriber gets only the tail per `moq-relay 0.10.x`'s
            // per-subscriber cache semantics. The load-bearing assertion is
            // the FFT peak at 440 Hz — that catches a wire-format regression
            // (downmix, channel swap, OpusHead-as-frame leak) regardless of
            // how many seconds of tail the page captured.
            val out = runSpeakerToBrowserListen(speakerSeconds = 10)
            val pcm = readFloat32Pcm(out.pcmFile)
            // Skip the warmup window before FFT: 40 ms Opus
            // look-ahead + 60 ms WebCodecs 3-frame warmup-skip = 100 ms.
            val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 10
            assertTrue(
                pcm.size > warmupSamples,
                "captured PCM (${pcm.size} samples) shorter than the WebCodecs warmup " +
                    "window — page never received any audio.\nplaywright stdout:\n${out.stdout}",
            )
            val analysed = pcm.copyOfRange(warmupSamples, pcm.size)
            assertTrue(
                analysed.size >= AudioFormat.SAMPLE_RATE_HZ,
                "after warmup window only ${analysed.size} samples remain; " +
                    "expected ≥ 1 s of decoded audio.\nplaywright stdout:\n${out.stdout}",
            )
            PcmAssertions.assertFftPeak(
                analysed,
                expectedHz = 440.0,
                halfWindowHz = 5.0,
            )
        }
}

/**
 * Output bundle from one [runSpeakerToBrowserListen] invocation.
 */
private class BrowserListenOutput(
    val pcmFile: File,
    val stdout: String,
)

/**
 * Run the Kotlin speaker for [speakerSeconds] seconds, drive the
 * Playwright + Chromium harness page to capture decoded PCM via the
 * bun WS back-channel, and return the captured bundle.
 *
 * Mirror of [HangInteropTest]'s `runSpeakerToHangListen`, but the
 * listener subprocess is `npx playwright test` instead of
 * `hang-listen`. The relay endpoint is identical — both consumers
 * connect via WebTransport to the same `NativeMoqRelayHarness`
 * instance.
 */
private suspend fun runSpeakerToBrowserListen(
    speakerSeconds: Int,
    listenerLateJoinDelayMs: Long = 150L,
    channelCount: Int = 1,
    freqHzPerChannel: IntArray? = null,
    /**
     * Mute window in ms relative to broadcast start, e.g. `1_500..2_500`
     * mutes the speaker between T+1.5 s and T+2.5 s. The speaker FINs
     * the open uni stream on mute (per T10) so the browser sees a
     * sample-count deficit, not embedded silence.
     */
    muteWindowMs: ClosedRange<Long>? = null,
    /**
     * If non-null, route the Kotlin speaker's UDP through a
     * `udp-loss-shim` subprocess that drops this fraction of
     * datagrams (0.0..=1.0). Mirror of the hang-tier I9 setup —
     * the Chromium listener still connects to the relay directly
     * (no loss on the listener leg), so any browser-side frame
     * deficit is attributable to the speaker→relay leg.
     */
    udpLossRate: Float? = null,
    /**
     * If non-null, drive the speaker through
     * [connectReconnectingNestsSpeaker] with this `tokenRefreshAfterMs`,
     * forcing a session recycle (hot-swap) mid-broadcast. Default uses
     * the simple non-reconnecting speaker.
     */
    hotSwapAfterMs: Long? = null,
): BrowserListenOutput {
    val harness = NativeMoqRelayHarness.shared()

    val signer: NostrSigner = NostrSignerInternal(KeyPair())
    val pubkey = signer.pubKey

    // Optional udp-loss-shim between speaker and relay (I9). The
    // shim listens on a fresh ephemeral port and forwards to the
    // harness's relay; the speaker's `endpoint` is rewritten to
    // the shim port. The Chromium page still connects directly.
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
    // Browser listener always connects directly to the relay,
    // even when the speaker is going through the loss shim — keeps
    // browser-side frame loss attributable to the speaker leg.
    val (browserRelayHost, browserRelayPort) = harness.loopbackHostPort()
    val browserEndpoint = "https://$browserRelayHost:$browserRelayPort"

    val room =
        NestsRoomConfig(
            authBaseUrl = "<unused-public-relay>",
            endpoint = speakerEndpoint,
            hostPubkey = pubkey,
            roomId = "rt-${UUID.randomUUID()}",
        )
    val moqNamespace = room.moqNamespace()
    // Build the page's relay URL the same shape `NestsConnect.kt`
    // uses (`?jwt=<token>`; empty under `--auth-public ""`). The page
    // ALWAYS connects directly to the relay — even when the speaker
    // is going through the loss shim — so any frame deficit is
    // attributable to the speaker leg, not double-loss on both legs.
    val pageRelayUrl = "$browserEndpoint/$moqNamespace?jwt="

    val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Use a cert-capturing validator so we can pin the relay's
    // self-signed cert into Chromium's WebTransport via
    // `serverCertificateHashes`. The validator is just a wrapper —
    // it accepts every chain (delegating to PermissiveCertificateValidator
    // semantics) but stashes the leaf DER on the first handshake.
    val certCapture = CertCapturingValidator()
    val transport =
        QuicWebTransportFactory(
            parentScope = pumpScope,
            certificateValidator = certCapture,
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

    val speaker =
        if (hotSwapAfterMs != null) {
            connectReconnectingNestsSpeaker(
                httpClient = StaticTokenNestsClientForBrowser,
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
                        httpClient = StaticTokenNestsClientForBrowser,
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
                httpClient = StaticTokenNestsClientForBrowser,
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

    // Mute scheduler. Fires in pumpScope so the main coroutine can
    // proceed to spawn Playwright + await its latch. Anchored to
    // broadcast start (= speaker.startBroadcasting()), with the
    // listener late-join delay already subtracted from the wait.
    if (muteWindowMs != null) {
        val muteStart = muteWindowMs.start
        val muteEnd = muteWindowMs.endInclusive
        val toMute = (muteStart - listenerLateJoinDelayMs).coerceAtLeast(0)
        val toUnmute = muteEnd - muteStart
        pumpScope.launch {
            delay(toMute)
            handle.setMuted(true)
            delay(toUnmute)
            handle.setMuted(false)
        }
    }

    // The speaker's connect path completes a QUIC handshake before
    // returning, so the cert validator has captured the leaf cert by
    // now. Compute the SHA-256 the WebTransport spec wants — `value`
    // in `serverCertificateHashes` is the SHA-256 of the entire
    // DER-encoded X.509 certificate, NOT of the SPKI.
    val derSha256 =
        certCapture.derSha256()
            ?: error("cert capture failed — speaker handshake did not invoke validator")
    val derSha256B64 =
        java.util.Base64
            .getEncoder()
            .encodeToString(derSha256)

    // Run Playwright on a side thread; keep the Kotlin speaker alive
    // until Playwright signals done. Chromium cold-launch + Playwright
    // setup eats 3–5 s before the page starts its `durationSec`
    // capture window, so closing the speaker on a fixed wall-clock
    // delay is fundamentally racy. Instead, the speaker stays
    // broadcasting until the side thread reports completion (which
    // happens after the page reaches `body[data-state="done"]` and
    // the bun server's WS shutdown).
    val pwResultRef =
        java.util.concurrent.atomic
            .AtomicReference<PlaywrightDriver.HarnessRun>()
    val pwErrorRef =
        java.util.concurrent.atomic
            .AtomicReference<Throwable>()
    val pwLatch = java.util.concurrent.CountDownLatch(1)
    val pwThread =
        Thread({
            try {
                pwResultRef.set(
                    PlaywrightDriver.openListenPage(
                        relayUrlFull = pageRelayUrl,
                        broadcastPath = pubkey,
                        durationSec = speakerSeconds,
                        // Bigger overall timeout: Chromium cold-launch +
                        // Playwright runner setup eats 3–10 s on a busy
                        // CI runner before the page starts capturing.
                        overallTimeoutSec = speakerSeconds + 90,
                        serverCertHashB64 = derSha256B64,
                        channels = channelCount,
                    ),
                )
            } catch (t: Throwable) {
                pwErrorRef.set(t)
            } finally {
                pwLatch.countDown()
            }
        }, "browser-interop-playwright").apply {
            isDaemon = true
            start()
        }

    // Wait (off the main coroutine) for Playwright to finish. The
    // speaker keeps broadcasting in the background of `pumpScope`
    // until we close it below.
    val pwOverallTimeoutSec = speakerSeconds + 120
    val ok =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            pwLatch.await(pwOverallTimeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        }
    runCatching { handle.close() }
    runCatching { speaker.close() }
    val out =
        try {
            if (!ok) error("Playwright did not complete within ${pwOverallTimeoutSec}s")
            pwErrorRef.get()?.let { throw it }
            pwResultRef.get() ?: error("Playwright thread did not produce a result")
        } finally {
            pumpScope.coroutineContext[Job]?.cancel()
            lossShimProc?.destroy()
        }

    assertTrue(
        out.exitCode == 0,
        "Playwright exited with code ${out.exitCode}.\n--- stdout ---\n${out.playwrightStdout}",
    )
    return BrowserListenOutput(pcmFile = out.pcmFile, stdout = out.playwrightStdout)
}

/**
 * Run a Chromium publisher (`publish.ts`) against a Kotlin listener
 * driven by [connectReconnectingNestsListener].
 *
 * Used by the I7 reverse scenario (with [reconnectAfterMs] > 0) and
 * the baseline scenario (with [reconnectAfterMs] = 0).
 *
 * **Hard assertions (publisher side):**
 *   - Playwright (`publish.html`) reaches `state="done"` and exits 0.
 *   - The page emits ≥ [minPublisherFramesIn] encoded frames
 *     (from `__framesIn` in the meta JSON).
 *   - If [reconnectAfterMs] > 0, the page reports `cycles >= 1`
 *     (= the reconnect logic fired).
 *
 * **Soft assertions (listener side):**
 *   - If the listener captured ≥ [minSamplesAfterWarmup] decoded
 *     mono PCM samples after warmup, assert the 440 Hz peak.
 *   - Otherwise, vacuous-pass with a stderr note. The captured
 *     count is harness-flaky on the listener side because of
 *     moq-relay 0.10.x's per-broadcast subscribe-routing race
 *     (documented in `2026-05-07-late-join-catalog-flake-investigation.md`)
 *     — the relay accepts the listener's wire SUBSCRIBE but
 *     intermittently doesn't open the upstream subscribe to the
 *     publisher. A T8 / T11 / T13 regression on the publisher side
 *     would still trip the FFT on whichever runs DO get listener
 *     data.
 *
 * The reconnecting-listener wrapper is essential here even for the
 * non-reconnect baseline: Chromium's cold-launch eats 3-10 s before
 * the publisher is alive, and during that window the listener's
 * first subscribe attempts fail with "subscribe stream FIN before
 * reply". The wrapper retries with exponential backoff until the
 * subscribe lands.
 */
private suspend fun runBrowserPublishKotlinListen(
    speakerSeconds: Int,
    reconnectAfterMs: Long,
    minSamplesAfterWarmup: Int,
    minPublisherFramesIn: Int = 100,
) {
    val harness = NativeMoqRelayHarness.shared()
    val signer: NostrSigner = NostrSignerInternal(KeyPair())
    val pubkey = signer.pubKey
    val (relayHost, relayPort) = harness.loopbackHostPort()
    val endpoint = "https://$relayHost:$relayPort"

    val room =
        NestsRoomConfig(
            authBaseUrl = "<unused-public-relay>",
            endpoint = endpoint,
            hostPubkey = pubkey,
            roomId = "rt-${UUID.randomUUID()}",
        )
    val moqNamespace = room.moqNamespace()
    val pageRelayUrl = "$endpoint/$moqNamespace?jwt="

    val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Listener owns a CertCapturingValidator so we can pin the
    // relay's self-signed cert into Chromium's WebTransport
    // (Chromium's `--ignore-certificate-errors` does NOT bypass
    // QUIC cert validation). The validator delegates to
    // PermissiveCertificateValidator semantics on the Kotlin
    // side — accepts the chain — but stashes the leaf DER for
    // the cert-pin handoff.
    val certCapture = CertCapturingValidator()
    val transport =
        QuicWebTransportFactory(
            parentScope = pumpScope,
            certificateValidator = certCapture,
        )

    try {
        // Connect the Kotlin listener via the reconnecting wrapper
        // FIRST so its QUIC handshake captures the relay's leaf
        // cert. Disable proactive JWT refresh — the only
        // re-issuance trigger is the publisher's
        // Announce::Ended → Active.
        val listener =
            connectReconnectingNestsListener(
                httpClient = StaticTokenNestsClientForBrowser,
                transport = transport,
                scope = pumpScope,
                room = room,
                signer = signer,
                tokenRefreshAfterMs = 0L,
            )
        withTimeoutOrNull(5_000L) {
            listener.state.first { it is NestsListenerState.Connected }
        } ?: error("listener never reached Connected within 5 s")

        val derSha256 =
            certCapture.derSha256()
                ?: error("cert capture failed — listener handshake did not invoke validator")
        val derSha256B64 =
            java.util.Base64
                .getEncoder()
                .encodeToString(derSha256)

        // Spawn Playwright on a side thread so the listener's
        // subscribe runs in parallel with the publisher's setup.
        val pwResultRef =
            java.util.concurrent.atomic
                .AtomicReference<PlaywrightDriver.HarnessRun>()
        val pwErrorRef =
            java.util.concurrent.atomic
                .AtomicReference<Throwable>()
        val pwLatch = java.util.concurrent.CountDownLatch(1)
        Thread({
            try {
                pwResultRef.set(
                    PlaywrightDriver.openPublishPage(
                        relayUrlFull = pageRelayUrl,
                        broadcastPath = pubkey,
                        freqHz = 440,
                        channels = 1,
                        durationSec = speakerSeconds,
                        // 180 s overall — when running multiple
                        // browser-publish tests back-to-back in one
                        // JVM, Chromium cold-launch on the second test
                        // can take 60-90 s (vs. 3-5 s on the first
                        // run) because Playwright reuses cached
                        // browser state asynchronously. The single-
                        // test wallclock budget of 95 s isn't enough
                        // to cover the slow re-launch.
                        overallTimeoutSec = speakerSeconds + 180,
                        serverCertHashB64 = derSha256B64,
                        reconnectAfterMs = reconnectAfterMs,
                    ),
                )
            } catch (t: Throwable) {
                pwErrorRef.set(t)
            } finally {
                pwLatch.countDown()
            }
        }, "browser-interop-publish").apply {
            isDaemon = true
            start()
        }

        val subscription = listener.subscribeSpeaker(pubkey)
        val decoder = JvmOpusDecoder(channelCount = 1)
        val pcm = mutableListOf<Float>()
        try {
            // Collect for speakerSeconds + 2 wallclock from the FIRST
            // frame — publisher runs `speakerSeconds`, plus headroom for
            // late frames (and any re-issuance gap if reconnectAfterMs > 0).
            // Timing it from subscribe instead let Chromium's 2.5-5 s
            // launch eat the window and cut the second reconnect cycle
            // short (#4577).
            val collectMs = (speakerSeconds + 2).toLong() * 1_000L
            val firstFrame = CompletableDeferred<Unit>()
            coroutineScope {
                val collector =
                    launch {
                        subscription.objects.collect { obj ->
                            firstFrame.complete(Unit)
                            val samples = decoder.decode(obj.payload)
                            for (s in samples) pcm += s.toFloat() / Short.MAX_VALUE.toFloat()
                        }
                    }
                if (withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) { firstFrame.await() } == null) {
                    collector.cancel()
                    error("listener received no frame within ${FIRST_FRAME_TIMEOUT_MS / 1_000} s of subscribing")
                }
                delay(collectMs)
                collector.cancel()
            }
        } finally {
            decoder.release()
            listener.close()
        }

        kotlinx.coroutines.withContext(Dispatchers.IO) {
            pwLatch.await(120L, java.util.concurrent.TimeUnit.SECONDS)
        }
        pwErrorRef.get()?.let { throw it }
        val pwOut = pwResultRef.get() ?: error("Playwright thread did not produce a result")
        assertTrue(
            pwOut.exitCode == 0,
            "Playwright (publish.html) exited with code ${pwOut.exitCode}.\n" +
                "--- stdout ---\n${pwOut.playwrightStdout}",
        )

        // -- Hard assertions: publisher side -------------------------------
        val framesIn = parseIntMetaFromStdout(pwOut.playwrightStdout, "framesIn") ?: -1
        assertTrue(
            framesIn >= minPublisherFramesIn,
            "publisher emitted only $framesIn frames (expected ≥ $minPublisherFramesIn) — " +
                "AudioEncoder/MediaStreamTrackProcessor pipeline broken.\n" +
                "playwright stdout:\n${pwOut.playwrightStdout}",
        )
        if (reconnectAfterMs > 0L) {
            val cycles = parseIntMetaFromStdout(pwOut.playwrightStdout, "cycles") ?: -1
            assertTrue(
                cycles >= 1,
                "expected publisher to cycle ≥ 1 time(s) (reconnectAfterMs=$reconnectAfterMs), " +
                    "got cycles=$cycles. The reconnect path didn't fire.\n" +
                    "playwright stdout:\n${pwOut.playwrightStdout}",
            )
        }

        // -- Hard assertions: listener side --------------------------------
        // 100 ms Opus look-ahead skip.
        val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 10
        assertTrue(
            pcm.size > warmupSamples,
            "Browser-publish: listener captured ${pcm.size} samples (≤ $warmupSamples-frame " +
                "warmup window) — nothing arrived end-to-end. Publisher framesIn=$framesIn.\n" +
                "playwright stdout:\n${pwOut.playwrightStdout}",
        )
        val analysed = pcm.toFloatArray().copyOfRange(warmupSamples, pcm.size)
        assertTrue(
            analysed.size >= minSamplesAfterWarmup,
            "Browser-publish: listener captured ${analysed.size} samples after warmup " +
                "(< $minSamplesAfterWarmup floor). Publisher framesIn=$framesIn.\n" +
                "playwright stdout:\n${pwOut.playwrightStdout}",
        )
        PcmAssertions.assertFftPeak(
            analysed,
            expectedHz = 440.0,
            halfWindowHz = 5.0,
        )
    } finally {
        pumpScope.coroutineContext[Job]?.cancel()
    }
}

/**
 * Stub NestsClient for the browser interop scenarios. The harness's
 * `--auth-public ""` flag grants any path without a JWT, so we mint
 * an empty token. Mirrors [HangInteropTest]'s
 * `StaticTokenNestsClient`.
 */
private object StaticTokenNestsClientForBrowser : NestsClient {
    override suspend fun mintToken(
        room: NestsRoomConfig,
        publish: Boolean,
        signer: NostrSigner,
    ): String = ""
}

/**
 * Pull the `meta.moqVersion` field out of the trailing JSON line
 * the Playwright spec emits to stdout. The spec writes a single
 * `{"state":"done","meta":{"moqVersion":"moq-lite-03",...}}` line
 * per run; we substring-search for it rather than wiring up a JSON
 * dependency just for this one helper.
 */
private fun parseMoqVersionFromStdout(stdout: String): String? {
    val needle = "\"moqVersion\":\""
    val start = stdout.indexOf(needle)
    if (start < 0) return null
    val valueStart = start + needle.length
    val valueEnd = stdout.indexOf('"', valueStart)
    if (valueEnd < 0) return null
    return stdout.substring(valueStart, valueEnd)
}

/**
 * Pull an integer meta field (e.g. `decoderErrors`, `decoderOutputs`)
 * out of the trailing JSON line the Playwright spec emits. Same shape
 * as [parseMoqVersionFromStdout] — substring search rather than full
 * JSON parse, since the harness page emits a single
 * `{"state":"done","meta":{"decoderErrors":0,...}}` line and we
 * shouldn't pull in a JSON dependency just for two helpers.
 *
 * Returns `null` if the field is missing OR if its value isn't a
 * non-negative integer literal — both are test-failure conditions
 * the caller asserts on.
 */
private fun parseIntMetaFromStdout(
    stdout: String,
    field: String,
): Int? {
    val needle = "\"$field\":"
    val start = stdout.indexOf(needle)
    if (start < 0) return null
    var i = start + needle.length
    // Skip whitespace + optional sign
    while (i < stdout.length && stdout[i].isWhitespace()) i++
    val numStart = i
    while (i < stdout.length && stdout[i].isDigit()) i++
    if (i == numStart) return null
    return stdout.substring(numStart, i).toIntOrNull()
}

/**
 * Read a file of native-endian Float32 LE PCM into a [FloatArray].
 * Matches the format the bun WS server appends per binary frame —
 * which itself matches `hang-listen`'s output format so the existing
 * [PcmAssertions] helpers slot in unchanged.
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

/**
 * How long [runBrowserPublishKotlinListen] waits for the first frame:
 * Chromium's cold launch is 3-5 s for the first test in a JVM and can
 * reach 60-90 s for a later one (see `overallTimeoutSec` there).
 */
private const val FIRST_FRAME_TIMEOUT_MS = 120_000L
