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
package com.vitorpamplona.amethyst.commons.drawing

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The encoder checked by decoding what it writes.
 *
 * [GifReader] below is an independent, minimal GIF decoder (LZW, sub-blocks,
 * transparency, "do not dispose" compositing). Each test plays the file back
 * and compares the pictures, so a wrong code width, a missed clear code or an
 * off-by-one patch rectangle shows up as a wrong pixel rather than passing.
 */
class AnimatedGifEncoderTest {
    private val paper = 0xFFFFE46B.toInt()
    private val ink = 0xFF1F1A14.toInt()
    private val red = 0xFFD22F27.toInt()

    @Test
    fun replayFramesDecodeBackToThePicturesThatWentIn() {
        val w = 40
        val h = 30
        val frames = mutableListOf<IntArray>()
        var picture = IntArray(w * h) { paper }
        frames.add(picture.copyOf())
        // A line grows to the right, then a red dot appears, like a drawing being made.
        for (x in 5 until 35) {
            picture = picture.copyOf()
            for (y in 10..12) picture[y * w + x] = ink
            frames.add(picture)
        }
        picture = picture.copyOf()
        for (y in 20..24) for (x in 18..22) picture[y * w + x] = red
        frames.add(picture)

        val palette = GifPalette.from(frames.last())
        val encoder = AnimatedGifEncoder(w, h, palette)
        frames.forEachIndexed { i, f -> encoder.addFrame(f, if (i == 0) 40 else 5) }
        val gif = GifReader(encoder.finish())

        assertEquals(w, gif.width)
        assertEquals(h, gif.height)
        assertTrue(gif.loopsForever, "the replay should loop")
        assertEquals(frames.size, gif.frames.size)
        frames.forEachIndexed { i, expected ->
            assertContentEquals(expected.map { it and 0xFFFFFF }, gif.frames[i].toList(), "frame $i")
        }
        assertEquals(40 + 5 * (frames.size - 1), gif.delaysCs.sum())
    }

    @Test
    fun laterFramesOnlyStoreTheRectangleThatChanged() {
        val w = 64
        val h = 64
        val first = IntArray(w * h) { paper }
        val second = first.copyOf().also { it[10 * w + 20] = ink }
        val encoder = AnimatedGifEncoder(w, h, GifPalette.from(second))
        encoder.addFrame(first, 5)
        encoder.addFrame(second, 5)
        val gif = GifReader(encoder.finish())

        assertEquals(listOf(Rect(0, 0, w, h), Rect(20, 10, 1, 1)), gif.rects)
    }

    @Test
    fun anUnchangedFrameLengthensThePreviousOneInsteadOfAddingAFrame() {
        val w = 8
        val h = 8
        val first = IntArray(w * h) { paper }
        val second = first.copyOf().also { it[3] = ink }
        val encoder = AnimatedGifEncoder(w, h, GifPalette.from(second))
        encoder.addFrame(first, 40)
        encoder.addFrame(second, 5)
        encoder.addFrame(second, 5)
        encoder.addFrame(second, 250)
        val gif = GifReader(encoder.finish())

        assertEquals(listOf(40, 260), gif.delaysCs)
    }

    @Test
    fun bigNoisyFramesSurviveTheCodeTableFillingUpAndResetting() {
        // Random indices fill the 4096-entry table many times over, forcing clear codes
        // and every code width from 9 to 12 bits.
        val w = 200
        val h = 150
        val random = Random(42)
        val colors = IntArray(200) { (0xFF000000.toInt()) or (it * 0x010307) }
        val noise = IntArray(w * h) { colors[random.nextInt(colors.size)] }
        val palette = GifPalette.from(noise)
        val encoder = AnimatedGifEncoder(w, h, palette)
        encoder.addFrame(noise, 10)
        val gif = GifReader(encoder.finish())

        val expected = noise.map { palette.colorOf(it) }
        assertContentEquals(expected, gif.frames[0].toList())
    }

    @Test
    fun longRunsOfOneColourRoundTrip() {
        val w = 300
        val h = 300
        val flat = IntArray(w * h) { paper }
        val encoder = AnimatedGifEncoder(w, h, GifPalette.from(flat))
        encoder.addFrame(flat, 10)
        val bytes = encoder.finish()
        val gif = GifReader(bytes)

        assertContentEquals(flat.map { it and 0xFFFFFF }, gif.frames[0].toList())
        assertTrue(bytes.size < 4000, "a flat 300x300 frame should compress to almost nothing, was ${bytes.size} bytes")
    }

    @Test
    fun inkThatIsLaterErasedKeepsItsColourInTheFramesThatShowIt() {
        val w = 20
        val h = 20
        val blank = IntArray(w * h) { paper }
        val withRed = blank.copyOf().also { for (x in 2..17) it[10 * w + x] = red }
        val erased = blank.copyOf().also { it[3 * w + 3] = ink }
        val frames = listOf(blank, withRed, erased)

        // Built from the last frame alone, red would be missing and frame 1 would show it as ink or paper.
        val palette = GifPalette.Builder().apply { frames.forEach { add(it) } }.build()
        val encoder = AnimatedGifEncoder(w, h, palette)
        frames.forEach { encoder.addFrame(it, 5) }
        val gif = GifReader(encoder.finish())

        frames.forEachIndexed { i, expected ->
            assertContentEquals(expected.map { it and 0xFFFFFF }, gif.frames[i].toList(), "frame $i")
        }
    }

    @Test
    fun inkOnPaperKeepsItsExactColoursAndIsNotDithered() {
        val picture =
            IntArray(100) {
                if (it % 3 == 0) {
                    ink
                } else if (it % 3 == 1) {
                    red
                } else {
                    paper
                }
            }
        val palette = GifPalette.from(picture)
        assertEquals(3, palette.size)
        assertTrue(!palette.dithers)
        assertContentEquals(picture.map { it and 0xFFFFFF }, picture.map { palette.colorOf(it) })
    }

    @Test
    fun aPhotoIsCutTo255ColoursThatStayCloseToIt() {
        // A smooth two-axis gradient, like a sky behind a drawing: far more than 255 colours.
        val w = 256
        val h = 128
        val photo = IntArray(w * h) { p -> 0xFF000000.toInt() or ((p % w) shl 16) or ((p / w * 2) shl 8) or (255 - p % w) }
        val palette = GifPalette.from(photo)
        assertEquals(255, palette.size)
        assertTrue(palette.dithers)

        var error = 0L
        for (pixel in photo) {
            val c = palette.colorOf(pixel)
            for (shift in listOf(16, 8, 0)) error += abs(((c shr shift) and 0xFF) - ((pixel shr shift) and 0xFF))
        }
        val meanPerChannel = error.toDouble() / (photo.size * 3)
        assertTrue(meanPerChannel < 6.0, "median cut should stay within a few levels per channel, was $meanPerChannel")
    }

    @Test
    fun ditheringIsTheSameForAnUnchangedPixelInEveryFrame() {
        val w = 64
        val h = 64
        val photo = IntArray(w * h) { p -> 0xFF000000.toInt() or ((p % w * 4) shl 16) or ((p / w * 4) shl 8) or 0x80 }
        val withInk = photo.copyOf().also { it[10 * w + 10] = ink }
        val palette = GifPalette.from(withInk)
        val a = palette.indicesOf(photo, w)
        val b = palette.indicesOf(withInk, w)
        val changed = a.indices.count { a[it] != b[it] }
        assertEquals(1, changed, "only the inked pixel should differ")

        // And the encoder therefore stores a one-pixel patch for that frame.
        val encoder = AnimatedGifEncoder(w, h, palette)
        encoder.addFrame(photo, 5)
        encoder.addFrame(withInk, 5)
        assertEquals(Rect(10, 10, 1, 1), GifReader(encoder.finish()).rects[1])
    }

    private fun GifPalette.colorOf(argb: Int): Int {
        val i = indexOf(argb)
        return ((rgb[i * 3].toInt() and 0xFF) shl 16) or ((rgb[i * 3 + 1].toInt() and 0xFF) shl 8) or (rgb[i * 3 + 2].toInt() and 0xFF)
    }

    data class Rect(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
    )

    /** A minimal GIF89a decoder: global palette, transparency, disposal "leave in place". */
    class GifReader(
        bytes: ByteArray,
    ) {
        private val data = bytes
        private var pos = 0
        val width: Int
        val height: Int
        var loopsForever = false
        val frames = mutableListOf<IntArray>()
        val delaysCs = mutableListOf<Int>()
        val rects = mutableListOf<Rect>()

        private fun u8() = data[pos++].toInt() and 0xFF

        private fun u16() = u8() or (u8() shl 8)

        private fun subBlocks(): ByteArray {
            val out = mutableListOf<Byte>()
            while (true) {
                val n = u8()
                if (n == 0) return out.toByteArray()
                repeat(n) { out.add(data[pos++]) }
            }
        }

        init {
            assertEquals("GIF89a", data.copyOfRange(0, 6).decodeToString())
            pos = 6
            width = u16()
            height = u16()
            val packed = u8()
            u8()
            u8()
            val tableSize = 2 shl (packed and 7)
            val palette = IntArray(tableSize) { (u8() shl 16) or (u8() shl 8) or u8() }
            val canvas = IntArray(width * height)
            var transparent = -1
            var delay = 0
            loop@ while (true) {
                when (u8()) {
                    0x21 -> {
                        when (u8()) {
                            0xF9 -> {
                                u8()
                                val flags = u8()
                                delay = u16()
                                val index = u8()
                                transparent = if (flags and 1 != 0) index else -1
                                u8()
                            }

                            0xFF -> {
                                val app = subBlocks()
                                if (app.decodeToString().startsWith("NETSCAPE2.0")) loopsForever = true
                            }

                            else -> {
                                subBlocks()
                            }
                        }
                    }

                    0x2C -> {
                        val x = u16()
                        val y = u16()
                        val w = u16()
                        val h = u16()
                        u8()
                        val minCodeSize = u8()
                        val indices = lzwDecode(subBlocks(), minCodeSize, w * h)
                        for (row in 0 until h) {
                            for (col in 0 until w) {
                                val index = indices[row * w + col].toInt() and 0xFF
                                if (index != transparent) canvas[(y + row) * width + x + col] = palette[index]
                            }
                        }
                        frames.add(canvas.copyOf())
                        delaysCs.add(delay)
                        rects.add(Rect(x, y, w, h))
                    }

                    0x3B -> {
                        break@loop
                    }

                    else -> {
                        error("unexpected block at $pos")
                    }
                }
            }
        }

        private fun lzwDecode(
            bytes: ByteArray,
            minCodeSize: Int,
            pixels: Int,
        ): ByteArray {
            val clear = 1 shl minCodeSize
            val eoi = clear + 1
            val prefix = IntArray(4096)
            val suffix = ByteArray(4096)
            val first = ByteArray(4096)
            for (i in 0 until clear) {
                prefix[i] = -1
                suffix[i] = i.toByte()
                first[i] = i.toByte()
            }
            val out = ByteArray(pixels)
            var written = 0
            var codeSize = minCodeSize + 1
            var next = eoi + 1
            var old = -1
            var bitPos = 0
            val stack = ByteArray(4097)

            fun read(): Int {
                var code = 0
                for (b in 0 until codeSize) {
                    val byte = bytes[(bitPos + b) / 8].toInt()
                    code = code or (((byte shr ((bitPos + b) % 8)) and 1) shl b)
                }
                bitPos += codeSize
                return code
            }

            fun emit(code: Int) {
                var depth = 0
                var c = code
                while (c >= 0) {
                    stack[depth++] = suffix[c]
                    c = prefix[c]
                }
                while (depth > 0) out[written++] = stack[--depth]
            }

            while (bitPos + codeSize <= bytes.size * 8) {
                val code = read()
                if (code == clear) {
                    codeSize = minCodeSize + 1
                    next = eoi + 1
                    old = -1
                    continue
                }
                if (code == eoi) break
                if (old == -1) {
                    emit(code)
                    old = code
                    continue
                }
                val firstByte = if (code < next) first[code] else first[old]
                if (next < 4096) {
                    prefix[next] = old
                    suffix[next] = firstByte
                    first[next] = first[old]
                    next++
                    if (next == (1 shl codeSize) && codeSize < 12) codeSize++
                }
                emit(code)
                old = code
            }
            assertEquals(pixels, written, "LZW stream decoded to the wrong number of pixels")
            return out
        }
    }
}
