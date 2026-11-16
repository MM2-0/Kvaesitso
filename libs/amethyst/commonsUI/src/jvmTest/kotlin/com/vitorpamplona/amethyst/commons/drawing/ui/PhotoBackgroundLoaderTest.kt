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
package com.vitorpamplona.amethyst.commons.drawing.ui

import coil3.PlatformContext
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.service.image.encodeArgbAsPng
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A picked picture through the real Coil decoder: cropped to a postable shape, centred, scaled to drawing width. */
class PhotoBackgroundLoaderTest {
    private val red = 0xFFD03030.toInt()
    private val green = 0xFF30B050.toInt()
    private val blue = 0xFF3050D0.toInt()

    @Test
    fun aVeryTallPictureIsCroppedFromTheMiddleToTwoByThree() {
        // 400 x 1000, in three bands. Cropped to 2:3 from the centre, the top edge lands in the
        // red band and the bottom edge in the blue one, with green across the middle.
        val w = 400
        val h = 1000
        val pixels =
            IntArray(w * h) { p ->
                if (p / w < h / 3) {
                    red
                } else if (p / w < 2 * h / 3) {
                    green
                } else {
                    blue
                }
            }
        val photo = runBlocking { PhotoBackgroundLoader.load(PlatformContext.INSTANCE, encodeArgbAsPng(pixels, w, h)) }

        assertNotNull(photo)
        assertEquals(PhotoBackgroundLoader.PHOTO_WIDTH, photo.width)
        assertEquals((PhotoBackgroundLoader.PHOTO_WIDTH * DrawingBackground.Photo.MAX_ASPECT).toInt(), photo.height)
        assertClose(red, photo.pixelAt(0.5f, 0.02f), "top")
        assertClose(green, photo.pixelAt(0.5f, 0.5f), "middle")
        assertClose(blue, photo.pixelAt(0.5f, 0.98f), "bottom")
    }

    @Test
    fun aSquarePictureKeepsItsShape() {
        val pixels = IntArray(300 * 300) { green }
        val photo = runBlocking { PhotoBackgroundLoader.load(PlatformContext.INSTANCE, encodeArgbAsPng(pixels, 300, 300)) }
        assertNotNull(photo)
        assertEquals(photo.width, photo.height)
    }

    @Test
    fun bytesThatAreNotAPictureGiveNothing() {
        val photo = runBlocking { PhotoBackgroundLoader.load(PlatformContext.INSTANCE, "not a picture".encodeToByteArray()) }
        assertNull(photo)
    }

    private fun DrawingBackground.Photo.pixelAt(
        fx: Float,
        fy: Float,
    ) = pixels[(fy * (height - 1)).toInt() * width + (fx * (width - 1)).toInt()]

    private fun assertClose(
        expected: Int,
        actual: Int,
        what: String,
    ) {
        val d = listOf(16, 8, 0).maxOf { abs(((expected shr it) and 0xFF) - ((actual shr it) and 0xFF)) }
        assertTrue(d <= 8, "$what: expected ${(expected and 0xFFFFFF).toString(16)}, got ${(actual and 0xFFFFFF).toString(16)}")
    }
}
