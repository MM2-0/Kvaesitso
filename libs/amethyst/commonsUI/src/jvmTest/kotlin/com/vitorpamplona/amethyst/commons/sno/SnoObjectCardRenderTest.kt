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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import com.vitorpamplona.amethyst.commons.sno.ui.SnoObjectViewer
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectCard
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoParser
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The card actually draws, in real Compose.
 *
 * Everything under this — the parser, the rasterizer, the image bridge — has
 * its own tests, and all of them passed while two defects sat in the Compose
 * layer above: a detail line that clipped mid-word because its column had no
 * width to work with, and a viewer that rasterised on the composition thread.
 * Compiling proved nothing about either. This renders the card offscreen
 * through `ImageComposeScene` (no display, no device) and asserts the object
 * reached the screen through Coil, the fetcher and the platform image bridge.
 */
@OptIn(DelicateCoilApi::class)
class SnoObjectCardRenderTest {
    /** Coil's singleton outlives the class; leave none behind for the next render test. */
    @AfterTest
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    /** A flat gold triangle: one colour, so it is unmistakable in the pixels. */
    private val gold =
        """{"v":2,"name":"Gold","unit":0,"mode":"solid","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":[249,249,249],"faces":[[0,1,2]]}"""

    @Test
    fun theCardDrawsTheObject() {
        val payload = SnoParser.parse(gold).payloadOrNull()
        assertTrue(payload != null, "fixture did not parse")

        val width = 700
        val height = 260
        val scene = ImageComposeScene(width = width, height = height, density = Density(2f))
        try {
            scene.setContent {
                setSingletonImageLoaderFactory { ctx: PlatformContext ->
                    ImageLoader
                        .Builder(ctx)
                        .components {
                            add(SnoFetcher.Factory)
                            add(SnoFetcher.SKeyer)
                        }.build()
                }
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxSize()) {
                            SnoObjectCard(payload = payload, eventId = "render-test", onClick = {})
                        }
                    }
                }
            }

            // Coil resolves off the composition, so the first frames are the
            // placeholder. Poll rather than sleeping a fixed amount.
            // `return@repeat` would be a continue, not a break, and the
            // assertion would then read whatever the last frame happened to be.
            var goldPixels = 0
            for (poll in 0 until POLLS) {
                val pixels = scene.render().toPixels(width, height)
                goldPixels = pixels.count { it.isGold() }
                if (goldPixels > 0) break
                Thread.sleep(POLL_MILLIS)
            }

            assertTrue(
                goldPixels > 200,
                "the object should have reached the card through Coil and the image bridge; found $goldPixels gold pixels",
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun theViewerDrawsTheObject() {
        // The viewer has its own composition path — BoxWithConstraints sizing,
        // an off-thread raster held across angles, and a graphicsLayer for zoom
        // and pan. None of that is exercised by the card's path.
        val payload = SnoParser.parse(gold).payloadOrNull()
        assertTrue(payload != null, "fixture did not parse")

        val side = 400
        val scene = ImageComposeScene(width = side, height = side, density = Density(2f))
        try {
            scene.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        SnoObjectViewer(
                            payload = payload,
                            eventId = "viewer-test",
                            modifier = Modifier.size(200.dp),
                        )
                    }
                }
            }

            var goldPixels = 0
            for (poll in 0 until POLLS) {
                val pixels = scene.render().toPixels(side, side)
                goldPixels = pixels.count { it.isGold() }
                if (goldPixels > 0) break
                Thread.sleep(POLL_MILLIS)
            }

            assertTrue(goldPixels > 200, "the viewer should have drawn the object; found $goldPixels gold pixels")
        } finally {
            scene.close()
        }
    }

    /** Palette index 249 is `#ffd300`, which nothing else on the card is. */
    private fun Int.isGold(): Boolean {
        val r = (this shr 16) and 0xFF
        val g = (this shr 8) and 0xFF
        val b = this and 0xFF
        return r > 0xE0 && g in 0xB0..0xF0 && b < 0x40
    }

    /** The rendered frame as ARGB, through PNG so no Skia pixel layout is assumed. */
    private fun Image.toPixels(
        width: Int,
        height: Int,
    ): IntArray {
        val png = encodeToData(EncodedImageFormat.PNG)!!.bytes
        val decoded = ImageIO.read(ByteArrayInputStream(png))
        val out = IntArray(width * height)
        decoded.getRGB(0, 0, width, height, out, 0, width)
        return out
    }

    companion object {
        private const val POLLS = 40
        private const val POLL_MILLIS = 50L
    }
}
