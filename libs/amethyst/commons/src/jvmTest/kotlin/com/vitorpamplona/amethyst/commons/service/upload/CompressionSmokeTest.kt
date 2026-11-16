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
package com.vitorpamplona.amethyst.commons.service.upload

import net.coobird.thumbnailator.Thumbnails
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 0 smoke: confirms the Thumbnailator dep is on the classpath, AWT
 * is headless during tests, and a basic re-encode produces a readable
 * JPEG. Deeper compression behavior is verified by ImageReencoderTest in
 * Phase 1.
 */
class CompressionSmokeTest {
    @Test
    fun awtHeadlessIsEnabled() {
        // Without -Djava.awt.headless=true, this is false on macOS dev
        // machines and macOS would happily spin up a Dock icon during
        // tests. Pin it here so a regression in commons/build.gradle.kts
        // is caught fast.
        assertEquals(
            "true",
            System.getProperty("java.awt.headless"),
            "java.awt.headless must be true during commons jvmTest",
        )
    }

    @Test
    fun thumbnailatorReencodesJpeg() {
        val src = makeSyntheticImage(640, 480)
        val out = File.createTempFile("amethyst_smoke_", ".jpg")
        try {
            Thumbnails
                .of(src)
                .size(320, 320)
                .outputFormat("jpg")
                .outputQuality(0.9f)
                .toFile(out)

            assertTrue(out.length() > 0, "output file must be non-empty")

            val decoded = ImageIO.read(out)
            assertTrue(decoded != null, "output must decode as a valid image")
            assertEquals(320, decoded.width, "downscale must hit target width")
            assertEquals(
                240,
                decoded.height,
                "aspect ratio preserved → 480 × (320/640) = 240",
            )
        } finally {
            out.delete()
        }
    }

    @Test
    fun thumbnailatorUpscalesByDefault() {
        // Documents the trap: Thumbnailator's .size(w, h) WILL upscale a
        // smaller source up to the box. ImageReencoder must gate the
        // resize itself — see thumbnailatorNeverUpscalesWhenGated below
        // for the pattern.
        val src = makeSyntheticImage(200, 150)
        val out = File.createTempFile("amethyst_smoke_upscale_", ".jpg")
        try {
            Thumbnails
                .of(src)
                .size(1920, 1920)
                .outputFormat("jpg")
                .outputQuality(0.9f)
                .toFile(out)

            val decoded = ImageIO.read(out)
            // 200×150 was upscaled to fit the 1920×1920 box (aspect kept).
            assertEquals(1920, decoded.width)
            assertEquals(1440, decoded.height)
        } finally {
            out.delete()
        }
    }

    @Test
    fun thumbnailatorNeverUpscalesWhenGated() {
        // The pattern ImageReencoder will use: skip the .size() call
        // entirely when the source is already within the target box.
        val src = makeSyntheticImage(200, 150)
        val out = File.createTempFile("amethyst_smoke_no_upscale_", ".jpg")
        try {
            val targetMax = 1920
            val builder = Thumbnails.of(src).outputFormat("jpg").outputQuality(0.9f)
            if (src.width > targetMax || src.height > targetMax) {
                builder.size(targetMax, targetMax)
            } else {
                // Re-encode only — no resize.
                builder.scale(1.0)
            }
            builder.toFile(out)

            val decoded = ImageIO.read(out)
            assertEquals(200, decoded.width, "source within box → no resize")
            assertEquals(150, decoded.height, "source within box → no resize")
        } finally {
            out.delete()
        }
    }

    private fun makeSyntheticImage(
        width: Int,
        height: Int,
    ): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            // Diagonal gradient so the encoder produces realistic
            // entropy (a flat-color image compresses suspiciously well).
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = (x * 255 / width)
                    val g0 = (y * 255 / height)
                    val b = ((x + y) * 255 / (width + height))
                    img.setRGB(x, y, Color(r, g0, b).rgb)
                }
            }
        } finally {
            g.dispose()
        }
        return img
    }
}
