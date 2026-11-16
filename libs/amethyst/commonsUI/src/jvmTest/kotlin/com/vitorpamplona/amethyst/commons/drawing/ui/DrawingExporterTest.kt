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

import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.drawing.DrawingEditorState
import com.vitorpamplona.amethyst.commons.drawing.DrawingPaper
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay
import com.vitorpamplona.amethyst.commons.drawing.DrawingTool
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The whole export path on the desktop's Skia: offscreen frames, the GIF, the
 * PNG and the hashes. The files are decoded with javax.imageio, a decoder this
 * project did not write, and copies are left in `build/drawing-export/` to look at.
 */
class DrawingExporterTest {
    private val ink = 0xFF1D1B20.toInt()

    /** A sticky note with a written loop, a mirrored spray and an erased gap, drawn over ~2 s. */
    private fun sampleDrawing(): DrawingEditorState {
        val state = DrawingEditorState(DrawingBackground.Paper(DrawingPaper.STICKY_YELLOW))
        var t = 1_000L

        state.tool = DrawingTool.BRUSH
        state.color = ink
        state.width = 0.03f
        state.begin(0.2f, 0.5f, t)
        for (i in 1..60) {
            val a = i / 60f * 4f * Math.PI.toFloat()
            state.extend(0.2f + i * 0.01f, 0.5f + 0.12f * sin(a), t + i * 16L)
        }
        state.end()
        t += 60 * 16L + 400

        state.tool = DrawingTool.SPRAY
        state.color = 0xFFE5484D.toInt()
        state.mirror = true
        state.begin(0.15f, 0.2f, t)
        for (i in 1..20) state.extend(0.15f + i * 0.005f, 0.2f + 0.05f * cos(i / 3f), t + i * 20L)
        state.end()
        t += 20 * 20L + 300

        state.tool = DrawingTool.ERASER
        state.mirror = false
        state.width = 0.04f
        state.begin(0.5f, 0.3f, t)
        state.extend(0.5f, 0.7f, t + 200)
        state.end()
        return state
    }

    @Test
    fun exportsAPlayableReplayAndAStillOfTheFinishedDrawing() {
        val state = sampleDrawing()
        val drawing = runBlocking { DrawingExporter.export(state.background, state.strokes.toList()) }
        try {
            keepCopies(drawing, "sticky")
            val size = DrawingExporter.EXPORT_WIDTH
            assertEquals(size, drawing.width)
            assertEquals(size, drawing.height)
            assertNotNull(drawing.blurhash)
            assertNotNull(drawing.thumbhash)

            val reader = ImageIO.getImageReadersByFormatName("gif").next()
            ImageIO.createImageInputStream(File(drawing.gif.toString())).use { input ->
                reader.input = input
                val frames = reader.getNumImages(true)
                val expected = DrawingReplay(state.strokes.toList()).frameTimes.size
                assertTrue(frames in 2..expected, "the GIF has $frames frames, the replay $expected")

                val first = reader.read(0)
                assertEquals(size, first.width)
                assertEquals(size, first.height)
                // Frame one is the blank note: the ink's spot is still paper yellow.
                assertClose(DrawingPaper.STICKY_YELLOW.background, first.getRGB(xOf(0.3f), xOf(0.5f)), "blank first frame")
            }

            val still = ImageIO.read(File(drawing.still.toString()))
            assertEquals(size, still.width)
            assertEquals(size, still.height)
            // Somewhere along the written loop the ink is down.
            val inked = (0 until size).any { y -> still.getRGB(xOf(0.3f), y) and 0xFFFFFF == ink and 0xFFFFFF }
            assertTrue(inked, "the finished drawing should show the black loop")
            // The eraser ran down the middle, so paper shows there again (below the glue strip).
            assertClose(DrawingPaper.STICKY_YELLOW.background, still.getRGB(xOf(0.5f), xOf(0.5f)), "erased gap")
            // The glue strip sits a shade darker than the rest of the note.
            val glue = still.getRGB(xOf(0.9f), 4)
            val paper = still.getRGB(xOf(0.9f), xOf(0.9f))
            assertTrue(brightness(glue) < brightness(paper), "glue strip should be darker than the note")
        } finally {
            drawing.delete()
        }
    }

    @Test
    fun aSketchIsAPortrait() {
        val state = DrawingEditorState()
        state.begin(0.5f, 0.5f, 0)
        state.extend(0.6f, 0.7f, 100)
        state.end()
        val drawing = runBlocking { DrawingExporter.export(state.background, state.strokes.toList()) }
        try {
            keepCopies(drawing, "sketch")
            assertEquals(DrawingExporter.EXPORT_WIDTH, drawing.width)
            assertEquals((DrawingExporter.EXPORT_WIDTH * 1.25f).toInt(), drawing.height)
            val gif = ImageIO.read(File(drawing.gif.toString()))
            assertEquals(drawing.height, gif.height)
        } finally {
            drawing.delete()
        }
    }

    @Test
    fun theEraserRevealsTheUsersPictureUnderneath() {
        // A picture split red | blue, and a thick black line across it with its middle erased.
        val side = PhotoBackgroundLoader.PHOTO_WIDTH
        val red = 0xFFD03030.toInt()
        val blue = 0xFF3050D0.toInt()
        val photo = DrawingBackground.Photo(IntArray(side * side) { if (it % side < side / 2) red else blue }, side, side)
        val state = DrawingEditorState(photo)
        state.width = 0.06f
        state.begin(0.1f, 0.5f, 0)
        state.extend(0.9f, 0.5f, 300)
        state.end()
        state.tool = DrawingTool.ERASER
        state.begin(0.25f, 0.4f, 600)
        state.extend(0.25f, 0.6f, 700)
        state.end()

        val drawing = runBlocking { DrawingExporter.export(state.background, state.strokes.toList()) }
        try {
            keepCopies(drawing, "photo")
            val still = ImageIO.read(File(drawing.still.toString()))
            assertClose(ink, still.getRGB(xOf(0.75f), xOf(0.5f)), "ink over the blue half")
            assertClose(red, still.getRGB(xOf(0.25f), xOf(0.5f)), "erased ink shows the red picture again")
            assertClose(blue, still.getRGB(xOf(0.75f), xOf(0.2f)), "untouched picture")

            // The GIF's palette cannot hold a photo exactly, but the dither averages back to it.
            val reader = ImageIO.getImageReadersByFormatName("gif").next()
            ImageIO.createImageInputStream(File(drawing.gif.toString())).use { input ->
                reader.input = input
                val first = reader.read(0)
                var r = 0L
                for (y in 0 until 8) for (x in 0 until 8) r += (first.getRGB(xOf(0.75f) + x, xOf(0.2f) + y) shr 16) and 0xFF
                assertTrue(abs(r / 64 - ((blue shr 16) and 0xFF)) <= 8, "the blue half should average back to its own red channel")
            }
        } finally {
            drawing.delete()
        }
    }

    @Test
    fun theNotebookPageIsRuled() {
        val state = DrawingEditorState(DrawingBackground.Paper(DrawingPaper.NOTEBOOK))
        state.begin(0.5f, 0.05f, 0)
        state.end()
        val drawing = runBlocking { DrawingExporter.export(state.background, state.strokes.toList()) }
        try {
            keepCopies(drawing, "notebook")
            val still = ImageIO.read(File(drawing.still.toString()))
            val column = (0 until drawing.height).map { still.getRGB(xOf(0.6f), it) and 0xFFFFFF }.toSet()
            assertTrue(column.size >= 2, "a column of a ruled page crosses the rules")
            assertTrue((0 until drawing.height).any { brightness(still.getRGB(xOf(0.6f), it)) < brightness(DrawingPaper.NOTEBOOK.background) - 30 })
        } finally {
            drawing.delete()
        }
    }

    @Test
    fun theStillForTheDescriberIsTheFinishedDrawing() {
        val state = sampleDrawing()
        val path = runBlocking { DrawingExporter.writeStill(state.background, state.strokes.toList()) }
        try {
            val still = ImageIO.read(File(path.toString()))
            assertEquals(DrawingExporter.EXPORT_WIDTH, still.width)
            assertTrue((0 until still.height).any { y -> still.getRGB(xOf(0.3f), y) and 0xFFFFFF == ink and 0xFFFFFF }, "the still should show the ink")
        } finally {
            File(path.toString()).delete()
        }
    }

    @Test
    fun aCancelledStillLeavesNoFileBehind() {
        val state = sampleDrawing()
        val tempDir = File(System.getProperty("java.io.tmpdir"))

        fun stills() = tempDir.list { _, name -> name.startsWith("amethyst_drawing_still_") }.orEmpty().toSet()
        val before = stills()
        runBlocking {
            // The render runs to the end on Default; the cancellation lands as withContext returns.
            val job = launch { DrawingExporter.writeStill(state.background, state.strokes.toList()) }
            yield()
            job.cancelAndJoin()
        }
        assertEquals(before, stills(), "a cancelled still must be deleted")
    }

    private fun xOf(fraction: Float) = (fraction * DrawingExporter.EXPORT_WIDTH).toInt()

    private fun brightness(rgb: Int) = ((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)

    private fun assertClose(
        expected: Int,
        actual: Int,
        what: String,
    ) {
        val d =
            listOf(16, 8, 0).maxOf { shift ->
                abs(((expected shr shift) and 0xFF) - ((actual shr shift) and 0xFF))
            }
        assertTrue(d <= 8, "$what: expected ${(expected and 0xFFFFFF).toString(16)}, got ${(actual and 0xFFFFFF).toString(16)}")
    }

    private fun keepCopies(
        drawing: RenderedDrawing,
        name: String,
    ) {
        val out = File("build/drawing-export").apply { mkdirs() }
        File(drawing.gif.toString()).copyTo(File(out, "$name.gif"), overwrite = true)
        File(drawing.still.toString()).copyTo(File(out, "$name.png"), overwrite = true)
    }
}
