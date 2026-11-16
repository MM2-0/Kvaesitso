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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.awt.image.RescaleOp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Deterministic corpus of degraded QR codes, for measuring how well a decoder actually reads the
 * codes people point phones at.
 *
 * Why it lives in the JVM test source set: it is pure `java.awt`, no Android, so the corpus can be
 * generated and the *old* decoder (ZXing-Java) measured on any machine, with no device or
 * emulator. The new decoder is native and Android-only, so it is measured by the instrumented
 * `QrDecodeCorpusTest` against the exact same images, exported from here.
 *
 * Everything is seeded, so two runs produce byte-identical images and the two measurements are
 * comparable.
 */
object QrCorpus {
    /** A throwaway pubkey. Never a real key — these images get committed. */
    private const val PUBKEY = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    /**
     * The payload lengths that matter, shortest to longest. Length drives the QR version, which
     * drives module size at a fixed physical size — and small modules, far more than error
     * correction, are what defeats a camera at arm's length.
     */
    private val PAYLOADS =
        listOf(
            "npub" to "nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqdhpvhq",
            "nprofile" to "nostr:nprofile1qqsrhuxx8l9ex335q7he0f09aej04zpazpl0ne2cgukyawd24mayt8gpp4mhxue69uhkummn9ekx7mqpz4mhxue69uhkummnw3ezummcw3ezuer9wchsz9thwden5te0wfjkccte9ehx7um5wghxyctwvshsz9nhwden5te0wfjkccte9ehx7um5wghxyctwvshszxrhwden5te0wfjkccte9ehx7um5wghxyctwvshsqgxvxz9jkth8dgc6dyckt3jmg5kvthdjtcn6q9lc39ahq5dpjznuwq",
            "nevent" to "nostr:nevent1qqstna2yrezu5wghjvswqqculvvwxsrcvu7uc0f78gan4xqhvz49d9spr3mhxue69uhkummnw3ezuamfdejsygzhuxx8l9ex335q7he0f09aej04zpazpl0ne2cgukyawd24mayt8psgqqqqqqspp4mhxue69uhkummn9ekx7mq",
        )

    /** One generated image plus what it should decode to and which hazard it represents. */
    data class Fixture(
        val name: String,
        val category: String,
        val expected: String,
        val image: BufferedImage,
    )

    /**
     * Every fixture, in a stable order.
     *
     * Categories are the *reasons* scans fail in the field, not arbitrary transforms: a code seen
     * out of focus, off-axis, on a dim or glossy surface, photographed off another screen, or
     * simply too far away to resolve.
     */
    fun all(): List<Fixture> {
        val fixtures = mutableListOf<Fixture>()

        PAYLOADS.forEach { (label, payload) ->
            val (base, modules) = renderWithModuleCount(payload, moduleSize = MODULE_PX)

            fun add(
                category: String,
                image: BufferedImage,
            ) = fixtures.add(Fixture("$category-$label", category, payload, image))

            add("clean", base)
            // A quarter-module and a half-module of blur. Both are expressed in modules so that
            // changing MODULE_PX cannot quietly re-tune the corpus's difficulty -- and they are
            // chosen to straddle the old decoder's limit, because a category it fails outright
            // can only ever show an improvement, never catch a regression.
            add("blur", blur(base, radius = MODULE_PX / 4))
            add("blur-heavy", blur(base, radius = MODULE_PX / 2))
            add("tilt15", rotate(base, degrees = 15.0))
            add("tilt30", rotate(base, degrees = 30.0))
            add("tilt45", rotate(base, degrees = 45.0))
            add("perspective", perspective(base, strength = 0.28))
            add("low-contrast", contrast(base, scale = 0.30f))
            add("very-low-contrast", contrast(base, scale = 0.12f))
            add("inverted", invert(base))
            add("glare", glare(base))
            add("noise", noise(base, sigma = 46.0))
            add("moire", moire(base))
            // Sized by pixels-per-module, not absolute pixels: that ratio is what decides
            // whether a code is resolvable at all, and it is the whole reason a long payload is
            // harder to scan than a short one at the same physical size.
            add("far-3px", shrinkInFrame(base, modules, pxPerModule = 3.0))
            add("far-2px", shrinkInFrame(base, modules, pxPerModule = 2.0))
            add("far-1.5px", shrinkInFrame(base, modules, pxPerModule = 1.5))
        }

        return fixtures
    }

    /** Renders [payload] exactly the way `QrCodeDrawer` does: ECC level Q, 4-module quiet zone. */
    fun render(
        payload: String,
        moduleSize: Int,
    ): BufferedImage = renderWithModuleCount(payload, moduleSize).first

    /** As [render], plus the symbol's module count — what pixels-per-module is measured against. */
    fun renderWithModuleCount(
        payload: String,
        moduleSize: Int,
    ): Pair<BufferedImage, Int> {
        val code =
            Encoder.encode(
                payload,
                ErrorCorrectionLevel.Q,
                mapOf(
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
                ),
            )
        val matrix = code.matrix!!
        val quiet = 4
        val side = (matrix.width + quiet * 2) * moduleSize

        val image = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, side, side)
        g.color = Color.BLACK
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y] == 1.toByte()) {
                    g.fillRect((x + quiet) * moduleSize, (y + quiet) * moduleSize, moduleSize, moduleSize)
                }
            }
        }
        g.dispose()
        return image to matrix.width
    }

    // ------------------------------------------------------------------
    // degradations
    // ------------------------------------------------------------------

    /** Out of focus — the single most common reason a frame fails to decode. */
    private fun blur(
        source: BufferedImage,
        radius: Int,
    ): BufferedImage {
        val size = radius * 2 + 1
        val weight = 1f / (size * size)
        val kernel = Kernel(size, size, FloatArray(size * size) { weight })
        val padded = pad(source, radius)
        val out = BufferedImage(padded.width, padded.height, BufferedImage.TYPE_INT_RGB)
        ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(padded, out)
        return out
    }

    /** Held at an angle. */
    private fun rotate(
        source: BufferedImage,
        degrees: Double,
    ): BufferedImage {
        val radians = Math.toRadians(degrees)
        val cos = abs(cos(radians))
        val sin = abs(sin(radians))
        val w = (source.width * cos + source.height * sin).roundToInt()
        val h = (source.width * sin + source.height * cos).roundToInt()

        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.translate(w / 2.0, h / 2.0)
        g.rotate(radians)
        g.translate(-source.width / 2.0, -source.height / 2.0)
        g.drawImage(source, 0, 0, null)
        g.dispose()
        return out
    }

    /**
     * Seen off-axis — a code on a wall photographed from the side. A true projective warp, which
     * [java.awt.geom.AffineTransform] cannot express, so it is mapped by hand.
     */
    private fun perspective(
        source: BufferedImage,
        strength: Double,
    ): BufferedImage {
        val w = source.width
        val h = source.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)

        for (y in 0 until h) {
            // Rows further "away" sample a narrower slice, which is what makes modules shrink
            // toward one edge exactly as a real off-axis photo does.
            val t = y.toDouble() / (h - 1)
            val squeeze = 1.0 - strength * (1.0 - t)
            for (x in 0 until w) {
                val centered = x - w / 2.0
                val sourceX = (centered / squeeze + w / 2.0).roundToInt()
                val rgb =
                    if (sourceX in 0 until w) source.getRGB(sourceX, y) else WHITE_RGB
                out.setRGB(x, y, rgb)
            }
        }
        return out
    }

    /** A dim screen, or e-ink, or a washed-out print: black and white move toward each other. */
    private fun contrast(
        source: BufferedImage,
        scale: Float,
    ): BufferedImage {
        val offset = 255f * (1f - scale) / 2f
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        RescaleOp(scale, offset, null).filter(source, out)
        return out
    }

    /** Light-on-dark, as a dark-mode client or an inverted print produces. */
    private fun invert(source: BufferedImage): BufferedImage {
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                out.setRGB(x, y, source.getRGB(x, y).inv() and 0xFFFFFF)
            }
        }
        return out
    }

    /** A highlight burning out one corner — glass, gloss, or a ceiling light. */
    private fun glare(source: BufferedImage): BufferedImage {
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val cx = source.width * 0.68
        val cy = source.height * 0.30
        val radius = min(source.width, source.height) * 0.30

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val distance = hypot(x - cx, y - cy)
                val lift = if (distance >= radius) 0.0 else (1.0 - distance / radius) * 235.0
                out.setRGB(x, y, liftPixel(source.getRGB(x, y), lift))
            }
        }
        return out
    }

    /** Sensor noise in poor light. */
    private fun noise(
        source: BufferedImage,
        sigma: Double,
    ): BufferedImage {
        val random = Random(SEED)
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val delta = gaussian(random) * sigma
                out.setRGB(x, y, liftPixel(source.getRGB(x, y), delta))
            }
        }
        return out
    }

    /** Photographed off another screen: a faint scanline beat over the modules. */
    private fun moire(source: BufferedImage): BufferedImage {
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) {
            val band = sin(y * 0.9) * 34.0
            for (x in 0 until source.width) {
                out.setRGB(x, y, liftPixel(source.getRGB(x, y), band))
            }
        }
        return out
    }

    /**
     * Too far away: the symbol is scaled until each module is [pxPerModule] pixels across,
     * then centred in a full-size frame.
     *
     * This is the category the old scanner could do nothing about, because it had no zoom — and
     * the one auto-zoom exists for.
     */
    private fun shrinkInFrame(
        source: BufferedImage,
        modules: Int,
        pxPerModule: Double,
    ): BufferedImage {
        val targetPx = (modules * pxPerModule).roundToInt()
        val frame = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val g = frame.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, frame.width, frame.height)
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        val x = (frame.width - targetPx) / 2
        val y = (frame.height - targetPx) / 2
        g.drawImage(source, x, y, targetPx, targetPx, null)
        g.dispose()
        return frame
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Pixels per module in the source renders.
     *
     * Four is ample to decode from (the hard fixtures shrink from here) and keeps the corpus that
     * has to live in the repository to a few hundred kilobytes rather than a few megabytes.
     */
    private const val MODULE_PX = 4

    private const val SEED = 20260915L
    private const val WHITE_RGB = 0xFFFFFF

    private fun pad(
        source: BufferedImage,
        margin: Int,
    ): BufferedImage {
        val out = BufferedImage(source.width + margin * 2, source.height + margin * 2, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, out.width, out.height)
        g.drawImage(source, margin, margin, null)
        g.dispose()
        return out
    }

    private fun liftPixel(
        rgb: Int,
        delta: Double,
    ): Int {
        fun channel(shift: Int): Int {
            val value = (rgb shr shift) and 0xFF
            return max(0, min(255, (value + delta).roundToInt()))
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Box-Muller, so the noise is normally distributed rather than uniform. */
    private fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }
}
