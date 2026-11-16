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

/**
 * The per-displayed-frame step of the spectrum visualizer: queue incoming frames, release each as
 * its audio time comes due, apply the decay trail against what is already on screen, and return a
 * fresh array to draw.
 *
 * Why pacing is needed at all: the audio pipeline fills its output buffer in chunks, so frames reach
 * the UI in clusters — ~15 frames roughly every 330 ms, measured on a Pixel 9a. Drawn on arrival a
 * cluster collapses into one frame; released one per display refresh it plays out in ~110 ms at
 * 120 Hz and then freezes (161 stalls averaging 211 ms over 52.8 s). Each frame instead stays up for
 * the [Spectrum.durationNanos] of audio it describes, so a cluster spreads across the gap to the
 * next. Time lost while starved is not owed back: after a gap, frames resume from the current frame
 * time rather than being dumped at once to catch up.
 *
 * The backlog is capped at [capacity], evicting the stalest frame, so faster-than-real-time playback
 * or a stalled UI cannot leave the picture drifting ever further behind the sound.
 *
 * The decay gives each bin an instant attack and a gradual release, so bars snap up to a transient
 * and fall back smoothly instead of flickering.
 *
 * This lives here, rather than inline in the Compose collector, so the pacing, starvation and decay
 * rules are unit-testable without a Compose harness; the composable is left as plumbing.
 *
 * Not thread-safe by design: both ends run on the UI dispatcher.
 */
class SpectrumTrail(
    private val decay: Float,
    private val capacity: Int = MAX_BACKLOG_FRAMES,
) {
    private val queue = ArrayDeque<Spectrum>(capacity)
    private var drawn = FloatArray(0)

    // When the frame at the head of the queue may be drawn, in the caller's frame-clock nanos.
    private var dueNanos = Long.MIN_VALUE
    private var starved = true

    /** Queues a freshly decoded frame, evicting the stalest if the backlog is at [capacity]. */
    fun offer(frame: Spectrum) {
        while (queue.size >= capacity) queue.removeFirst()
        queue.addLast(frame)
    }

    /** True when nothing is queued, so the caller can stop polling until the next [offer]. */
    fun isEmpty(): Boolean = queue.isEmpty()

    /**
     * The array to draw at [frameTimeNanos], or null when nothing new is due and the current one
     * should persist.
     *
     * A fresh array each time is intentional: `mutableStateOf` compares by reference, so a new
     * instance is what signals Compose to redraw. Do NOT switch to in-place mutation.
     */
    fun nextOrNull(frameTimeNanos: Long): FloatArray? {
        if (queue.isEmpty()) {
            starved = true
            return null
        }
        if (starved) {
            // Resume from now, but never earlier than the last drawn frame's audio time runs out.
            dueNanos = maxOf(dueNanos, frameTimeNanos)
            starved = false
        }

        var result: FloatArray? = null
        while (dueNanos <= frameTimeNanos) {
            val frame = queue.removeFirstOrNull() ?: break
            result = decayedFrom(frame)
            dueNanos += frame.durationNanos
        }
        return result
    }

    private fun decayedFrom(frame: Spectrum): FloatArray {
        val prev = drawn
        val next =
            FloatArray(frame.bins.size) { i ->
                val prior = if (i < prev.size) prev[i] * decay else 0f
                if (frame.bins[i] > prior) frame.bins[i] else prior
            }
        drawn = next
        return next
    }

    companion object {
        // A cluster is ~15 frames, so 24 (~0.5 s of audio) never trims ordinary playback, while
        // bounding how far the picture can lag the sound (e.g. at 2x playback speed).
        const val MAX_BACKLOG_FRAMES = 24
    }
}
