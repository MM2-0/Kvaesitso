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
package com.vitorpamplona.amethyst.commons.relays.health

/**
 * Fixed-capacity ring of `Int` millisecond samples. New pushes overwrite the oldest sample
 * once full. Thread-safe via `synchronized(this)` — push is called from relay-network threads,
 * [snapshotMedian] and [snapshotSamples] are called from the [RelayHealthStore] classifier
 * coroutine on the 60 s tick.
 *
 * Median is computed by copying the live region into a fresh array and sorting — the buffer
 * itself is not reordered. For [DEFAULT_CAPACITY] = 50 the per-snapshot sort is sub-millisecond
 * and the snapshot happens only on the classifier tick, so allocation cost is irrelevant.
 */
class LatencyRingBuffer(
    val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be > 0; got $capacity" }
    }

    private val data = IntArray(capacity)
    private var nextIndex = 0
    private var filled = 0

    /** Number of valid samples currently in the buffer (0..capacity). */
    val size: Int
        @Synchronized get() = filled

    /** Append a sample. If the buffer is full the oldest sample is overwritten. */
    @Synchronized
    fun push(ms: Int) {
        data[nextIndex] = ms
        nextIndex = (nextIndex + 1) % capacity
        if (filled < capacity) filled++
    }

    /** Median of the current samples in ms, or `null` if empty. */
    @Synchronized
    fun snapshotMedian(): Int? {
        if (filled == 0) return null
        val copy = IntArray(filled)
        if (filled < capacity) {
            // Buffer not yet full: valid samples are at [0, filled).
            System.arraycopy(data, 0, copy, 0, filled)
        } else {
            // Buffer full: nextIndex points at oldest, wrap-copy in order.
            val tailLen = capacity - nextIndex
            System.arraycopy(data, nextIndex, copy, 0, tailLen)
            if (nextIndex > 0) System.arraycopy(data, 0, copy, tailLen, nextIndex)
        }
        copy.sort()
        val n = copy.size
        return if (n % 2 == 1) copy[n / 2] else copy[n / 2 - 1]
    }

    /**
     * Returns the live samples as a fresh `IntArray` (chronological order from oldest to
     * newest). Used by the persistence layer to encode the buffer.
     */
    @Synchronized
    fun snapshotSamples(): IntArray {
        if (filled == 0) return EMPTY
        val out = IntArray(filled)
        if (filled < capacity) {
            System.arraycopy(data, 0, out, 0, filled)
        } else {
            val tailLen = capacity - nextIndex
            System.arraycopy(data, nextIndex, out, 0, tailLen)
            if (nextIndex > 0) System.arraycopy(data, 0, out, tailLen, nextIndex)
        }
        return out
    }

    /**
     * Replaces the current contents with [samples] (used to restore from persisted state).
     * Only the most recent [capacity] entries of [samples] are kept.
     */
    @Synchronized
    fun restore(samples: IntArray) {
        val src = if (samples.size > capacity) samples.copyOfRange(samples.size - capacity, samples.size) else samples
        for (i in src.indices) data[i] = src[i]
        filled = src.size
        nextIndex = src.size % capacity
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 50
        private val EMPTY = IntArray(0)
    }
}
