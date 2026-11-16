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
package com.vitorpamplona.amethyst.commons.sno

import androidx.compose.ui.graphics.ImageBitmap
import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.service.image.toComposeImageBitmap
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The path a drawn object actually takes on this platform: rasteriser to
 * [PlatformImage] to a Compose image. Compiling proves the expect/actual lines
 * up; this proves the pixels survive the trip.
 */
class SnoComposeImageBridgeTest {
    private val json =
        """{"v":2,"name":"tetra","unit":0,"mode":"solid","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":[238,238,238],"faces":[[0,1,2]]}"""

    @Test
    fun aRasterSurvivesTheTripToCompose() {
        val payload = SnoParser.parse(json).payloadOrNull()
        assertNotNull(payload)

        val size = 48
        val pixels = SnoRasterizer.render(payload, size, size, yawDegrees = 0f, pitchDegrees = 0f, background = 0xFF000000.toInt())
        assertEquals(0xFFFF0000.toInt(), pixels[(size / 2) * size + size / 2], "the raster itself should be red in the middle")

        val bitmap: ImageBitmap = PlatformImage.create(pixels, size, size).toComposeImageBitmap()
        assertEquals(size, bitmap.width)
        assertEquals(size, bitmap.height)

        val readBack = IntArray(size * size)
        bitmap.readPixels(readBack)
        assertEquals(0xFFFF0000.toInt(), readBack[(size / 2) * size + size / 2], "and still red after the conversion")
        assertTrue(readBack.any { it == 0xFF000000.toInt() }, "the background should survive too")
    }
}
