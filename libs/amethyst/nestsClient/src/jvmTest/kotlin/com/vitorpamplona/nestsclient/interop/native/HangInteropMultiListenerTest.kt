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
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.AudioFormat
import com.vitorpamplona.nestsclient.audio.JvmOpusEncoder
import com.vitorpamplona.nestsclient.audio.PcmAssertions
import com.vitorpamplona.nestsclient.audio.SineWaveAudioCapture
import com.vitorpamplona.nestsclient.connectNestsSpeaker
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
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * I6 — one Amethyst Kotlin speaker, **three** independent
 * `hang-listen` Rust subscribers, all reading from the same
 * broadcast through the same `moq-relay` instance.
 *
 * The speaker broadcasts a 5 s 440 Hz mono sine. Each of the three
 * listeners runs its own `hang-listen` subprocess, decoding to its
 * own PCM tempfile. The test asserts, for every listener
 * independently:
 *   - it received at least 2 s of decoded audio (40 % of the
 *     broadcast wallclock — generous, because the relay's per-
 *     subscriber forward queue gets stressed when N subscribers
 *     all read concurrently from the same publisher),
 *   - the FFT peak of the decoded PCM sits within ±5 Hz of 440 Hz
 *     (the strict spectral assertion — catches any wire-format
 *     regression that mangles a single subscriber while leaving
 *     others unaffected),
 *   - the zero-crossing rate matches the 880/sec expected for a
 *     440 Hz mono tone.
 *
 * Listeners are staggered ~50 ms apart so their handshakes don't
 * pile up on the relay's accept loop simultaneously.
 *
 * Pinned at `framesPerGroup = 5` to interop with `moq-relay 0.10.x`
 * (matches `HangInteropTest`).
 *
 * Gated by `-DnestsHangInterop=true`.
 */
class HangInteropMultiListenerTest {
    @BeforeTest
    fun gate() {
        NativeMoqRelayHarness.assumeHangInterop()
    }

    /**
     * I6 (P1, A→ref): one speaker fans out to three concurrent
     * `hang-listen` subscribers. Each listener's PCM output asserted
     * independently; FFT peak is the strict per-listener invariant,
     * sample count uses a generous 2 s floor.
     */
    @Test
    fun amethyst_speaker_to_three_hang_listeners_static_tone_440() =
        runBlocking {
            val numListeners = 3
            val speakerSeconds = 5
            val listenerLeadInMs = 150L
            val staggerMs = 50L

            val harness = NativeMoqRelayHarness.shared()

            val signer: NostrSigner = NostrSignerInternal(KeyPair())
            val pubkey = signer.pubKey
            val (relayHost, relayPort) = harness.loopbackHostPort()
            val speakerEndpoint = "https://$relayHost:$relayPort"

            val room =
                NestsRoomConfig(
                    authBaseUrl = "<unused-public-relay>",
                    endpoint = speakerEndpoint,
                    hostPubkey = pubkey,
                    roomId = "rt-${UUID.randomUUID()}",
                )
            val moqNamespace = room.moqNamespace()

            val pcmFiles =
                List(numListeners) { idx ->
                    File
                        .createTempFile("hang-listen-pcm-i6-l$idx-", ".bin")
                        .also { it.deleteOnExit() }
                }

            val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val transport =
                QuicWebTransportFactory(
                    parentScope = pumpScope,
                    certificateValidator = PermissiveCertificateValidator(),
                )

            val listenerProcs = mutableListOf<Process>()

            try {
                val speaker =
                    connectNestsSpeaker(
                        httpClient = StaticTokenNestsClientI6,
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

                // Lead-in: let the speaker's announce reach the relay
                // before any listener handshake.
                delay(listenerLeadInMs)

                // Spawn each hang-listen subprocess, staggered so
                // their handshakes don't all hit the relay accept
                // loop in the same QUIC tick.
                for (i in 0 until numListeners) {
                    val cmd =
                        listOf(
                            harness.hangListenBin().toString(),
                            "--relay-url",
                            harness.relayUrl,
                            "--broadcast",
                            moqNamespace,
                            "--duration",
                            "${speakerSeconds + 2}",
                            "--output-pcm",
                            pcmFiles[i].absolutePath,
                        )
                    val proc =
                        ProcessBuilder(cmd)
                            .redirectErrorStream(true)
                            .also { it.environment()["RUST_LOG"] = "info" }
                            .start()
                    listenerProcs += proc
                    if (i < numListeners - 1) delay(staggerMs)
                }

                // Run the speaker for the remainder of the
                // broadcast window. Total elapsed since start of
                // broadcast = listenerLeadInMs + (numListeners-1)*staggerMs
                // by this point.
                val elapsedMs = listenerLeadInMs + (numListeners - 1) * staggerMs
                delay(speakerSeconds * 1_000L - elapsedMs)
                handle.close()
                speaker.close()
            } finally {
                pumpScope.coroutineContext[Job]?.cancel()
            }

            // Reap each listener subprocess. The hang-listen
            // `--duration` was set to speakerSeconds + 2; allow a
            // 15 s wallclock cap as the rest of the suite does.
            val outputs = mutableListOf<String>()
            for ((idx, proc) in listenerProcs.withIndex()) {
                val exited = proc.waitFor(15, TimeUnit.SECONDS)
                val out = proc.inputStream.bufferedReader().readText()
                outputs += out
                assertTrue(
                    exited,
                    "hang-listen #$idx did not exit within 15 s. Output:\n$out",
                )
                assertEquals(
                    0,
                    proc.exitValue(),
                    "hang-listen #$idx exited non-zero. Output:\n$out",
                )
            }

            // Per-listener assertions: each PCM file must contain a
            // recognisable 440 Hz tone. Sample-count threshold is
            // 2 s (40 % of the 5 s broadcast) — generous because the
            // relay's per-subscriber forward queue chokes when N>1
            // subscribers all read the same broadcast and a slow
            // listener can lose its tail. The FFT peak is the
            // strict invariant.
            val minSamples = 2 * AudioFormat.SAMPLE_RATE_HZ
            for (idx in 0 until numListeners) {
                val pcm = readFloat32PcmI6(pcmFiles[idx])
                assertTrue(
                    pcm.size >= minSamples,
                    "listener #$idx received only ${pcm.size} samples " +
                        "(expected ≥ $minSamples = 2 s of audio at " +
                        "${AudioFormat.SAMPLE_RATE_HZ} Hz). " +
                        "hang-listen output:\n${outputs[idx]}",
                )
                // Skip first 40 ms — Opus look-ahead silence (mirror
                // I1 in HangInteropTest).
                val warmup = AudioFormat.SAMPLE_RATE_HZ / 25
                val analysed = pcm.copyOfRange(warmup, pcm.size)
                PcmAssertions.assertFftPeak(
                    analysed,
                    expectedHz = 440.0,
                    halfWindowHz = 5.0,
                )
                PcmAssertions.assertZeroCrossingRate(
                    analysed,
                    expectedPerSecond = 880.0,
                    tolerance = 0.10,
                )
            }
        }
}

/**
 * Same auth bypass as `HangInteropTest` — moq-relay boots with
 * `--auth-public ""`, so any token is accepted.
 */
private object StaticTokenNestsClientI6 : NestsClient {
    override suspend fun mintToken(
        room: NestsRoomConfig,
        publish: Boolean,
        signer: NostrSigner,
    ): String = ""
}

/**
 * Read native-endian little-endian Float32 PCM (the format
 * `hang-listen --output-pcm` writes). Local helper to keep this
 * file self-contained — `HangInteropTest`'s `readFloat32Pcm` is
 * file-private to that file.
 */
private fun readFloat32PcmI6(file: File): FloatArray {
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
