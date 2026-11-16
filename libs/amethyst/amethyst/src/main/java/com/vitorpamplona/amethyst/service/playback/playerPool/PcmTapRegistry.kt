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
package com.vitorpamplona.amethyst.service.playback.playerPool

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.vitorpamplona.amethyst.commons.audio.AudioWindow
import com.vitorpamplona.amethyst.commons.audio.Fft
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SpectrumTrail
import com.vitorpamplona.amethyst.commons.audio.normalizeToPeakInPlace
import com.vitorpamplona.amethyst.commons.audio.toLogBins
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * A Media3 [TeeAudioProcessor.AudioBufferSink] that turns the decoded 16-bit PCM of the
 * currently-playing track into a live [Spectrum] stream via FFT + log binning, emitting into the
 * per-media-id flow [PcmTapRegistry] has bound it to. Only 16-bit PCM is handled; offload/passthrough
 * bypass the processor chain so the flow simply stays empty (visualizer idles, never stale).
 */
@OptIn(UnstableApi::class)
class SpectrumAudioBufferSink(
    private val fftSize: Int = 1024,
    private val binCount: Int = 48,
) : TeeAudioProcessor.AudioBufferSink {
    /** The per-media-id flow this sink currently feeds; set by [PcmTapRegistry.bind]. */
    @Volatile
    internal var output: MutableSharedFlow<Spectrum>? = null

    /** The media id this sink is currently bound to; protects its flow from eviction. */
    @Volatile
    internal var boundMediaId: String? = null

    private val window = AudioWindow.hann(fftSize)
    private val mono = ShortArray(fftSize)
    private val scratch = FloatArray(fftSize)
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val mags = FloatArray(fftSize / 2 + 1)
    private var filled = 0
    private var channels = 1
    private var encoding = C.ENCODING_PCM_16BIT

    // Audio time one fft frame covers: fftSize samples per channel at the stream's sample rate.
    // Zero until the first flush reports a rate, which leaves the frame unpaced rather than wrong.
    private var frameDurationNanos = 0L

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    override fun flush(
        sampleRateHz: Int,
        channelCount: Int,
        encoding: Int,
    ) {
        this.channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        this.frameDurationNanos = if (sampleRateHz > 0) fftSize * 1_000_000_000L / sampleRateHz else 0L
        filled = 0
        output?.resetReplayCache()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (encoding != C.ENCODING_PCM_16BIT) return
        val pcm = buffer.order(ByteOrder.LITTLE_ENDIAN)
        while (pcm.remaining() >= 2 * channels) {
            val sample = pcm.short // first channel of the frame
            if (channels > 1) pcm.position(pcm.position() + (channels - 1) * 2) // skip remaining channels
            mono[filled++] = sample
            if (filled == fftSize) {
                emitSpectrum()
                filled = 0
            }
        }
    }

    // Reuses pre-allocated working buffers; the only per-frame allocation is the published bins.
    private fun emitSpectrum() {
        AudioWindow.shortsToWindowedInto(mono, window, scratch)
        Fft.magnitudesInto(scratch, re, im, mags)
        // Skip the DC bin (index 0): toLogBins ignores it, so letting a DC/offset component be the
        // peak would scale every audible bin toward zero and wash the spectrum out.
        mags.normalizeToPeakInPlace(fromIndex = 1)
        output?.tryEmit(Spectrum(mags.toLogBins(binCount), frameDurationNanos))
    }
}

/**
 * Routes each pooled player's decoded-PCM spectrum to a stable per-media-id flow the UI subscribes
 * to by media URL. Flows are created on demand (so a UI subscriber can attach before playback binds
 * the sink) and the map is bounded: past [MAX_TRACKED_FLOWS], the least-recently-used flows that no
 * live sink is currently feeding are evicted, so a long feed session can't grow the map without limit.
 */
@OptIn(UnstableApi::class)
object PcmTapRegistry {
    private const val MAX_TRACKED_FLOWS = 64

    private val lock = Any()

    // access-order LinkedHashMap → eldest (least-recently-used) entries iterate first for eviction.
    private val flowsByMediaId = LinkedHashMap<String, MutableSharedFlow<Spectrum>>(16, 0.75f, true)
    private val sinkByPlayer = ConcurrentHashMap<Any, SpectrumAudioBufferSink>()

    fun newSink(): SpectrumAudioBufferSink = SpectrumAudioBufferSink()

    fun registerPlayer(
        playerKey: Any,
        sink: SpectrumAudioBufferSink,
    ) {
        sinkByPlayer[playerKey] = sink
    }

    /** Points [sink]'s output at the flow for [mediaId] (the item it now plays), or detaches it. */
    fun bind(
        mediaId: String?,
        sink: SpectrumAudioBufferSink,
    ) {
        // Mutate the sink's binding under the same lock that guards flow eviction (flowFor is
        // reentrant), so the eviction guard never observes a half-updated boundMediaId.
        synchronized(lock) {
            sink.boundMediaId = mediaId
            sink.output = mediaId?.let { flowFor(it) }
        }
    }

    fun unregisterPlayer(playerKey: Any) {
        synchronized(lock) {
            sinkByPlayer.remove(playerKey)?.let {
                it.output = null
                it.boundMediaId = null
            }
        }
    }

    /** Stable spectrum stream for a media URL; frames arrive once a player binds to it and plays. */
    fun spectrumFor(mediaId: String): Flow<Spectrum> = flowFor(mediaId)

    private fun flowFor(mediaId: String): MutableSharedFlow<Spectrum> =
        synchronized(lock) {
            flowsByMediaId.getOrPut(mediaId) {
                if (flowsByMediaId.size >= MAX_TRACKED_FLOWS) {
                    val iter = flowsByMediaId.entries.iterator()
                    while (iter.hasNext() && flowsByMediaId.size >= MAX_TRACKED_FLOWS) {
                        val entry = iter.next()
                        // Never evict a flow a live sink is currently feeding, nor one a composable is
                        // still collecting — otherwise a later bind() would create a fresh instance the
                        // sink feeds while the old subscriber keeps collecting the dead one (blank viz).
                        val fedByLiveSink = sinkByPlayer.values.any { it.boundMediaId == entry.key }
                        val stillCollected = entry.value.subscriptionCount.value > 0
                        if (!fedByLiveSink && !stillCollected) iter.remove()
                    }
                }
                // Frames arrive in clusters (see SpectrumTrail) emitted within a few ms on the audio
                // thread, while the collector sits on the main dispatcher and cannot run in between;
                // a 2-slot buffer kept ~2 frames of each cluster and dropped the rest. Hold as much as
                // the visualizer's own backlog, which is the real lag bound, dropping the stalest.
                MutableSharedFlow(
                    replay = 1,
                    extraBufferCapacity = SpectrumTrail.MAX_BACKLOG_FRAMES,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }
        }
}
