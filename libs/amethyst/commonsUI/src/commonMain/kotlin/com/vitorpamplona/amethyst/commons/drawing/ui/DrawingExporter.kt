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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.blurhash.toBlurhash
import com.vitorpamplona.amethyst.commons.drawing.AnimatedGifEncoder
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay
import com.vitorpamplona.amethyst.commons.drawing.DrawingStroke
import com.vitorpamplona.amethyst.commons.drawing.GifPalette
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.service.image.encodeArgbAsPng
import com.vitorpamplona.amethyst.commons.thumbhash.toThumbhash
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.platformTempDirectory
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.math.roundToInt

/**
 * Turns a drawing into the two files a post needs: the replay as a looping GIF
 * and the finished drawing as a PNG, written to the temp directory.
 *
 * Every frame is drawn by the same [drawDrawing] the live canvas uses, onto an
 * offscreen [ImageBitmap], and read back as pixels for [AnimatedGifEncoder].
 */
object DrawingExporter {
    /** Wide enough to stay crisp in a feed, small enough that a replay stays a light GIF. */
    const val EXPORT_WIDTH = 540

    /**
     * Writes just the finished drawing as a PNG in the temp directory, for an
     * on-device model to describe while the user is still looking at the
     * preview. The caller deletes it.
     */
    suspend fun writeStill(
        background: DrawingBackground,
        strokes: List<DrawingStroke>,
    ): Path {
        val path = platformTempDirectory / ("amethyst_drawing_still_" + RandomInstance.randomChars(12) + ".png")
        try {
            return withContext(Dispatchers.Default) {
                val height = (EXPORT_WIDTH * background.aspect).roundToInt()
                val png = encodeArgbAsPng(renderFinished(background, strokes, EXPORT_WIDTH, height), EXPORT_WIDTH, height)
                platformFileSystem.write(path) { write(png) }
                path
            }
        } catch (e: Throwable) {
            // Cancelled (withContext throws even after the block finished) or a write that failed
            // half way: either way the caller never gets the path, so nothing may be left there.
            platformFileSystem.delete(path, mustExist = false)
            throw e
        }
    }

    /** The finished drawing at [width] x [height], as row-major ARGB pixels. */
    private fun renderFinished(
        background: DrawingBackground,
        strokes: List<DrawingStroke>,
        width: Int,
        height: Int,
    ): IntArray {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawDrawing(BackgroundPainter(background), strokes)
        }
        return IntArray(width * height).also { bitmap.readPixels(it) }
    }

    suspend fun export(
        background: DrawingBackground,
        strokes: List<DrawingStroke>,
        onProgress: (Float) -> Unit = {},
    ): RenderedDrawing {
        // Set once the files are on disk. withContext throws if the caller was cancelled
        // while it worked, even after the block finished: the files must not outlive that.
        var written: RenderedDrawing? = null
        try {
            return withContext(Dispatchers.Default) { renderFiles(background, strokes, onProgress).also { written = it } }
        } catch (e: CancellationException) {
            written?.delete()
            throw e
        }
    }

    private fun CoroutineScope.renderFiles(
        background: DrawingBackground,
        strokes: List<DrawingStroke>,
        onProgress: (Float) -> Unit,
    ): RenderedDrawing {
        val width = EXPORT_WIDTH
        val height = (EXPORT_WIDTH * background.aspect).roundToInt()
        val painter = BackgroundPainter(background)
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val size = Size(width.toFloat(), height.toFloat())
        val pixels = IntArray(width * height)
        val replay = DrawingReplay(strokes)
        val renderer = ReplayFrameRenderer(painter, strokes, replay)

        fun render(timeMs: Long) {
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                with(renderer) { drawFrame(timeMs) }
            }
            bitmap.readPixels(pixels)
        }

        val frames = replay.frameTimes

        // The palette sees the finished drawing and a sample of the replay: ink that is
        // erased or painted over never reaches the last frame but must keep its colour.
        val palette = GifPalette.Builder()
        val sampleEvery = maxOf(1, frames.size / PALETTE_SAMPLES)
        for (k in frames.indices step sampleEvery) {
            ensureActive()
            render(frames[k])
            palette.add(pixels)
        }
        render(Long.MAX_VALUE)
        val finished = pixels.copyOf()
        palette.add(finished)

        val encoder = AnimatedGifEncoder(width, height, palette.build())
        for (k in frames.indices) {
            ensureActive()
            render(frames[k])
            encoder.addFrame(pixels, replay.frameDelayCs(k))
            onProgress((k + 1f) / frames.size)
        }
        val gifBytes = encoder.finish()
        val pngBytes = encodeArgbAsPng(finished, width, height)

        val image = PlatformImage.create(finished, width, height)
        val blurhash = runCatching { image.toBlurhash() }.onFailure { Log.w(TAG, "Could not blurhash the drawing", it) }.getOrNull()
        val thumbhash = runCatching { image.toThumbhash() }.onFailure { Log.w(TAG, "Could not thumbhash the drawing", it) }.getOrNull()

        val name = "amethyst_drawing_" + RandomInstance.randomChars(12)
        val gif = platformTempDirectory / "$name.gif"
        val still = platformTempDirectory / "$name.png"
        val drawing = RenderedDrawing(gif, still, width, height, blurhash, thumbhash)
        try {
            platformFileSystem.write(gif) { write(gifBytes) }
            platformFileSystem.write(still) { write(pngBytes) }
        } catch (e: Exception) {
            // A full disk after the GIF was written must not leave it behind.
            drawing.delete()
            throw e
        }

        Log.d(TAG) { "Drawing exported: ${frames.size} frames, GIF ${gifBytes.size} B, PNG ${pngBytes.size} B" }

        return drawing
    }

    /** About how many replay frames, besides the last, the palette is built from. */
    private const val PALETTE_SAMPLES = 12

    private const val TAG = "DrawingExporter"
}
