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
package com.vitorpamplona.amethyst.commons.audio.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every playable-media card offscreen (no device, no display) through `ImageComposeScene`, in the
 * dark and light Amethyst themes side by side, and writes each to `commonsUI/build/audio-player/<name>.png`,
 * so the candidate designs for audio inside a post can be compared as screenshots. Fails if a screen throws
 * or draws nothing.
 */
class PlayableMediaCardsRenderTest {
    private val outDir = File("build/audio-player").apply { mkdirs() }

    private fun render(
        name: String,
        widthDp: Int,
        heightDp: Int,
        content: @Composable () -> Unit,
    ) {
        val density = 2f
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val scene = ImageComposeScene(width = width, height = height, density = Density(density), content = content)
        try {
            // Fonts (including the Material Symbols subset) load asynchronously: let a few frames settle.
            var image = scene.render(0)
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode $name")
            File(outDir, "$name.png").writeBytes(png.bytes)

            val pixels = image.peekPixels() ?: error("no pixels for $name")
            val distinct = HashSet<Int>()
            for (y in 0 until height step 7) for (x in 0 until width step 7) distinct += pixels.getColor(x, y)
            assertTrue(distinct.size > MIN_COLOURS, "$name rendered almost nothing (${distinct.size} colours)")
        } finally {
            scene.close()
        }
    }

    @Test fun knownAudio() = render("01-known-audio-waveform", 820, 760) { AudioKnownAudioPreview() }

    @Test fun withArtwork() = render("02-known-audio-cover", 820, 620) { AudioWithArtworkPreview() }

    @Test fun undecided() = render("03-undecided-track-card", 820, 360) { AudioUndecidedPreview() }

    @Test fun pictureInPicture() = render("04-picture-in-picture", 820, 1000) { AudioPictureInPicturePreview() }

    private companion object {
        const val SETTLE_FRAMES = 12
        const val FRAME_MILLIS = 60L
        const val MIN_COLOURS = 20
    }
}
