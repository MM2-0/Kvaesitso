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
package com.vitorpamplona.amethyst.commons.service.images

import okio.BufferedSource

/**
 * Frame delay, in hundredths of a second, below which Coil rewrites a GIF's graphics control
 * blocks. Mirrors `FrameDelayRewritingSource.MINIMUM_FRAME_DELAY` in Coil 3.5.0.
 */
private const val MINIMUM_FRAME_DELAY = 2

/** `00 21 F9 04` — block terminator, extension introducer, graphics control label, block size. */
private const val MARKER_0: Byte = 0x00
private const val MARKER_1: Byte = 0x21
private const val MARKER_2: Byte = 0xF9.toByte()
private const val MARKER_3: Byte = 0x04

/**
 * Bytes one graphics control block occupies from the first marker byte: the 4 marker bytes,
 * the packed field, the two delay bytes, the transparent colour index and the terminator.
 */
private const val BLOCK_SIZE = 9

private const val SCAN_CHUNK = 64 * 1024

/**
 * Streams over a GIF looking for a graphics control block whose frame delay is below
 * [MINIMUM_FRAME_DELAY] — i.e. one that Coil's `FrameDelayRewritingSource` would actually
 * rewrite.
 *
 * Coil wraps every GIF in that rewriting source on API < 34 (the platform clamps sub-threshold
 * delays itself from 34 on). The wrapper is stream-backed, which costs the file fast path in
 * [onSystemFileSystem] and forces the whole encoded animation into RAM twice. The overwhelming
 * majority of GIFs declare a sane delay and come out of the rewriter byte-for-byte identical,
 * so this scan buys the fast path back for them and leaves the rewrite in place only for the
 * files that need it.
 *
 * The condition matches Coil's exactly (verified against Coil 3.5.0): a delay below the
 * threshold in a block whose terminator byte is zero. Reading the whole stream costs one
 * sequential pass over a [SCAN_CHUNK] buffer and allocates nothing else, against the
 * file-sized heap **and** direct-buffer copies the rewrite path would otherwise make.
 */
fun BufferedSource.hasSubThresholdGifFrameDelay(): Boolean {
    // Carries the last BLOCK_SIZE - 1 bytes of a chunk forward so a block that straddles a
    // chunk boundary is still seen whole.
    val window = ByteArray(SCAN_CHUNK + BLOCK_SIZE)
    var carried = 0

    while (true) {
        val read = read(window, carried, SCAN_CHUNK)
        if (read == -1) return false

        val filled = carried + read
        var i = 0
        val last = filled - BLOCK_SIZE
        while (i <= last) {
            if (window[i] == MARKER_0 &&
                window[i + 1] == MARKER_1 &&
                window[i + 2] == MARKER_2 &&
                window[i + 3] == MARKER_3 &&
                window[i + 8] == 0.toByte()
            ) {
                // Delay is two unsigned bytes, least significant first.
                val delay = ((window[i + 6].toInt() and 0xFF) shl 8) or (window[i + 5].toInt() and 0xFF)
                if (delay < MINIMUM_FRAME_DELAY) return true
            }
            i++
        }

        carried = minOf(filled, BLOCK_SIZE - 1)
        window.copyInto(window, 0, filled - carried, filled)
    }
}
