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
package com.vitorpamplona.amethyst.commons.cordn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.cordn.client.CoordinatorHealth
import com.vitorpamplona.cordn.client.GroupExposure
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the cordn disclosure surface headlessly and looks at the pixels.
 *
 * Compiling a composable proves almost nothing about it. Two failure modes it
 * cannot catch, and this can:
 *
 * - **A missing string resource throws at render, not at build.** The generated
 *   `Res.string.*` accessors compile whether or not `strings.xml` has the entry,
 *   so a typo ships and crashes the screen the first time someone opens it.
 *   This suite opens it.
 * - **A composable that draws nothing** — a zero-height container, a colour that
 *   equals its background — still builds and still "renders".
 *
 * It asserts structure rather than exact pixels, so it does not become a
 * screenshot test that has to be re-blessed on every font or Material bump. It
 * does not write files either; what it checks is what it can check honestly
 * without a human looking. `ImageComposeScene` rasterises in software, so this
 * needs no display and runs in CI.
 */
class CordnExposureRenderTest {
    private val width = 900
    private val height = 1500

    // Inside the card and clear of text: the card starts at the Column's 8dp
    // padding and this sits in the gutter to the right of the title row.
    private val cardInteriorX = width - 40
    private val cardInteriorY = 40

    private fun exposure(linked: Int = 3) =
        GroupExposure(
            coordinator = "cc".repeat(32),
            linkedGroupCount = linked,
            joinedFromShareLink = true,
            publishedKeyPackage = true,
            encryptionPinned = true,
        )

    /** Renders [content] under a light or dark Material theme and decodes it. */
    private fun render(
        dark: Boolean,
        content: @Composable () -> Unit,
    ): BufferedImage {
        val scene =
            ImageComposeScene(width = width, height = height, density = Density(2f)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        content()
                    }
                }
            }
        return try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            ImageIO.read(ByteArrayInputStream(png))
        } finally {
            scene.close()
        }
    }

    /** Every composable on the surface, in one pass. */
    private val wholeSurface: @Composable () -> Unit = {
        CordnExposureCard(exposure())
        CordnGroupBadge()
        CoordinatorHealthRow(CoordinatorHealth.State())
        CoordinatorHealthRow(CoordinatorHealth.State(lastSuccessAt = 1L))
        CoordinatorHealthRow(
            CoordinatorHealth.State(lastFailureAt = 1L, consecutiveFailures = 4, lastFailure = "timeout"),
        )
    }

    private fun BufferedImage.distinctColours(): Int {
        val seen = mutableSetOf<Int>()
        for (x in 0 until width step 3) {
            for (y in 0 until height step 3) {
                seen += getRGB(x, y)
            }
        }
        return seen.size
    }

    @Test
    fun `the whole surface renders in both themes`() {
        // The string-resource check: any missing entry throws here.
        listOf(false, true).forEach { dark ->
            val image = render(dark, wholeSurface)
            assertTrue(
                image.distinctColours() > 20,
                "the ${if (dark) "dark" else "light"} surface drew ${image.distinctColours()} colours, which is not text on a card",
            )
        }
    }

    @Test
    fun `the surface follows the theme rather than hardcoding colours`() {
        // A composable that paints its own background survives a theme switch
        // looking identical, and is unreadable in one of the two.
        val light = render(false, wholeSurface)
        val dark = render(true, wholeSurface)

        val brightness = { rgb: Int -> ((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3 }

        // Two samples, because they catch different mistakes. The page
        // background catches a screen that ignores the theme; a point inside
        // the card, clear of any glyph, catches a *component* that paints its
        // own colour — which the outer sample cannot see at all.
        listOf(
            "page background" to (2 to 2),
            "card surface" to (cardInteriorX to cardInteriorY),
        ).forEach { (what, point) ->
            val (x, y) = point
            val lightPixel = light.getRGB(x, y)
            val darkPixel = dark.getRGB(x, y)
            assertTrue(lightPixel != darkPixel, "the $what ignored the theme")
            assertTrue(brightness(lightPixel) > brightness(darkPixel), "light and dark are swapped on the $what")
        }
    }

    @Test
    fun `an unlinked group draws less than a linked one`() {
        // The §8.2 note is conditional, and a conditional that never fires is
        // indistinguishable from one that is broken. Fewer notes means a
        // shorter card, so the ink below the fold differs.
        val linked = render(false) { CordnExposureCard(exposure(linked = 3)) }
        val alone = render(false) { CordnExposureCard(exposure(linked = 1)) }

        val inkBelow = { image: BufferedImage ->
            var count = 0
            for (x in 0 until width step 3) {
                for (y in height / 2 until height step 3) {
                    if (image.getRGB(x, y) != image.getRGB(2, 2)) count++
                }
            }
            count
        }

        assertTrue(
            inkBelow(linked) > inkBelow(alone),
            "a group linked to others must show the extra disclosure: ${inkBelow(linked)} vs ${inkBelow(alone)}",
        )
    }
}
