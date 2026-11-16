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
package com.vitorpamplona.amethyst.commons.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10

/**
 * One frame of frequency-domain magnitudes, ordered low→high Hz and normalized 0f..1f.
 *
 * [durationNanos] is how much audio the frame describes (fft size / sample rate), which is how long
 * it should stay on screen. Zero — the default — means the producer is not pacing to audio (the
 * synthetic preview emits one per display frame), so the frame is shown as soon as it arrives.
 */
class Spectrum(
    val bins: FloatArray,
    val durationNanos: Long = 0L,
)

/**
 * Groups a linear-frequency magnitude array (index 0 = DC, last index = Nyquist)
 * into [binCount] log-spaced buckets so bass/mid get more bars than the treble
 * tail. Output is normalized 0f..1f using [floorDb] as the silence floor.
 * Bin 0 (DC) is always excluded; the Nyquist bin (last) is included.
 * Low bins are guaranteed to map to distinct FFT bins (contiguous, non-aliased).
 */
fun FloatArray.toLogBins(
    binCount: Int,
    floorDb: Float = -60f,
): FloatArray {
    if (isEmpty() || binCount <= 0) return FloatArray(0)
    val out = FloatArray(binCount)
    val logSize = ln(size.toDouble())
    var lo = 1 // skip DC (index 0)
    for (b in 0 until binCount) {
        var hi = exp(logSize * (b + 1) / binCount).toInt()
        if (hi <= lo) hi = lo + 1 // contiguous & distinct buckets: low bins map to distinct FFT bins, not all to bin 1
        if (hi > size) hi = size
        var peak = 0f
        var i = lo
        while (i < hi) {
            if (this[i] > peak) peak = this[i]
            i++
        }
        val db = if (peak > 0f) 20f * log10(peak) else floorDb
        out[b] = ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
        lo = hi
    }
    return out
}

/**
 * Returns a NEW array scaled so the largest value becomes 1f. An all-zero input is returned as a zero-filled copy.
 * Reference/allocating variant — production uses the *Into / in-place form on the audio thread.
 */
fun FloatArray.normalizedToPeak(): FloatArray {
    var peak = 0f
    for (v in this) if (v > peak) peak = v
    if (peak <= 0f) return copyOf()
    val inv = 1f / peak
    return FloatArray(size) { this[it] * inv }
}

/**
 * In-place version of [normalizedToPeak]: scales this array so its largest value becomes 1f.
 * [fromIndex] excludes leading entries from both the peak search and the scaling (e.g. pass 1 to
 * ignore the DC bin of an FFT magnitude array, which downstream log-binning also skips).
 */
fun FloatArray.normalizeToPeakInPlace(fromIndex: Int = 0) {
    var peak = 0f
    for (i in fromIndex until size) if (this[i] > peak) peak = this[i]
    if (peak <= 0f) return
    val inv = 1f / peak
    for (i in fromIndex until size) this[i] *= inv
}

/**
 * Holds back the latest [frames] spectra and emits the one from [frames] hops ago. The PCM tap
 * computes each [Spectrum] inside the audio processor chain, upstream of the AudioTrack output
 * buffer, so it runs ahead of what's audible by roughly that buffer's depth. Delaying the visual by
 * the same number of decoded FFT hops (one [Spectrum] == one hop) realigns it with the speaker, in
 * the same decoded-sample units as the lead, so it stays correct across startup and rate jitter.
 * [frames] <= 0 is identity (e.g. previews, which carry no output latency).
 */
fun Flow<Spectrum>.delayedByFrames(frames: Int): Flow<Spectrum> =
    if (frames <= 0) {
        this
    } else {
        flow {
            val queue = ArrayDeque<Spectrum>(frames + 1)
            collect { frame ->
                queue.addLast(frame)
                if (queue.size > frames) emit(queue.removeFirst())
            }
        }
    }
