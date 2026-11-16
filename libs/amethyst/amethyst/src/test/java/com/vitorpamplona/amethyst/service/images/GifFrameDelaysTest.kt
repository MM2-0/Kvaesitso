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
package com.vitorpamplona.amethyst.service.images

import com.vitorpamplona.amethyst.commons.service.images.hasSubThresholdGifFrameDelay
import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scan decides whether a GIF still needs Coil's in-RAM frame-delay rewrite on API < 34, so
 * a false negative would silently change how a GIF animates. Its condition therefore has to
 * match `FrameDelayRewritingSource` byte for byte: a delay below 2/100 s in a graphics control
 * block whose terminator is zero.
 */
class GifFrameDelaysTest {
    /**
     * `00 21 F9 04` marker, packed field, delay (low, high), transparent colour index, block
     * terminator — the nine bytes Coil inspects.
     */
    private fun graphicsControlBlock(
        delay: Int,
        terminator: Int = 0,
    ) = byteArrayOf(
        0x00,
        0x21,
        0xF9.toByte(),
        0x04,
        0x00,
        (delay and 0xFF).toByte(),
        ((delay shr 8) and 0xFF).toByte(),
        0x00,
        terminator.toByte(),
    )

    private fun gif(vararg blocks: ByteArray) =
        Buffer().apply {
            write("GIF89a".toByteArray())
            write(ByteArray(7)) // logical screen descriptor
            blocks.forEach { write(it) }
            writeByte(0x3B) // trailer
        }

    @Test
    fun aSaneDelayNeedsNoRewrite() {
        // 5/100 s is what the 69.8 MB GIF from the bug report declares on all 201 frames.
        assertFalse(gif(graphicsControlBlock(delay = 5)).hasSubThresholdGifFrameDelay())
    }

    @Test
    fun zeroAndOneHundredthsAreBelowTheThreshold() {
        assertTrue(gif(graphicsControlBlock(delay = 0)).hasSubThresholdGifFrameDelay())
        assertTrue(gif(graphicsControlBlock(delay = 1)).hasSubThresholdGifFrameDelay())
    }

    @Test
    fun twoHundredthsIsTheFirstAcceptedDelay() {
        assertFalse(gif(graphicsControlBlock(delay = 2)).hasSubThresholdGifFrameDelay())
    }

    @Test
    fun aDelayAboveOneByteIsReadLeastSignificantByteFirst() {
        // 0x0100 = 256/100 s. Reading the bytes in the wrong order would see 1 and rewrite.
        assertFalse(gif(graphicsControlBlock(delay = 256)).hasSubThresholdGifFrameDelay())
    }

    @Test
    fun oneBadBlockAmongGoodOnesIsEnough() {
        val stream =
            gif(
                graphicsControlBlock(delay = 10),
                graphicsControlBlock(delay = 10),
                graphicsControlBlock(delay = 0),
                graphicsControlBlock(delay = 10),
            )

        assertTrue(stream.hasSubThresholdGifFrameDelay())
    }

    @Test
    fun aNonZeroTerminatorIsSkipped() {
        // Coil bails out of the rewrite when the block does not end where it expects, so a
        // marker-shaped run of bytes inside image data must not count as a frame delay.
        assertFalse(gif(graphicsControlBlock(delay = 0, terminator = 0x2C)).hasSubThresholdGifFrameDelay())
    }

    @Test
    fun aBlockStraddlingTheScanChunkBoundaryIsStillFound() {
        // The scan reads in 64 KiB chunks; without the carried-over window a block landing on
        // the seam would be missed and the GIF would animate at the wrong speed.
        for (offset in 0..8) {
            val padding = ByteArray(64 * 1024 - 8 + offset)
            val stream =
                Buffer().apply {
                    write(padding)
                    write(graphicsControlBlock(delay = 0))
                }

            assertTrue("block at 64KiB - 8 + $offset", stream.hasSubThresholdGifFrameDelay())
        }
    }

    @Test
    fun anEmptySourceNeedsNoRewrite() {
        assertFalse(Buffer().hasSubThresholdGifFrameDelay())
    }

    @Test
    fun aTruncatedBlockAtTheEndOfTheStreamNeedsNoRewrite() {
        val stream = Buffer().apply { write(graphicsControlBlock(delay = 0).copyOfRange(0, 8)) }

        assertFalse(stream.hasSubThresholdGifFrameDelay())
    }
}
