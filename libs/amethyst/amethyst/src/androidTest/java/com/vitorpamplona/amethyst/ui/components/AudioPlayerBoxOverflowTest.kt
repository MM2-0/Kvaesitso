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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.commons.ui.playback.audioSquare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The inline audio player sizes itself as a square so the visualizer and controls get room, which is
 * taller than the 16:9 box [ZoomableContentView] builds for a video of unknown dimensions. That box
 * is centre-aligned and nothing between it and NoteComposeLayout clips, so caging the square in it
 * makes the player paint over the note header above and the reactions row below.
 *
 * These tests recreate that enclosure — the real [mediaSizingModifier] over the real
 * [unknownMediaAspectRatio], holding the real [audioSquare] — and pin both halves of the contract:
 * audio must not be given a ratio, and video must keep the 16:9 assumption that stops live streams
 * from letterboxing on first play.
 */
@RunWith(AndroidJUnit4::class)
class AudioPlayerBoxOverflowTest {
    @get:Rule
    val rule = createComposeRule()

    private class Bounds {
        var top = 0f
        var bottom = 0f
        var width = 0
        var height = 0
    }

    private fun Modifier.trackBounds(bounds: Bounds) =
        onGloballyPositioned {
            bounds.top = it.positionInRoot().y
            bounds.width = it.size.width
            bounds.height = it.size.height
            bounds.bottom = bounds.top + bounds.height
        }

    private fun measure(
        url: String,
        square: Boolean,
    ): Pair<Bounds, Bounds> {
        val box = Bounds()
        val player = Bounds()

        rule.setContent {
            Box(Modifier.size(width = 400.dp, height = 1200.dp)) {
                Box(
                    modifier = mediaSizingModifier(unknownMediaAspectRatio(null, url), ContentScale.Fit).trackBounds(box),
                    contentAlignment = Alignment.Center,
                ) {
                    Box((if (square) Modifier.audioSquare() else Modifier).trackBounds(player))
                }
            }
        }
        rule.waitForIdle()

        return box to player
    }

    @Test
    fun squareAudioPlayerStaysInsideItsMediaBox() {
        val (box, player) = measure("https://haven.sdbitcoiners.com/f28a5a2e.mp3", square = true)

        assertTrue(
            "player overflows above the media box by ${box.top - player.top}px",
            player.top >= box.top,
        )
        assertTrue(
            "player overflows below the media box by ${player.bottom - box.bottom}px",
            player.bottom <= box.bottom,
        )
        assertEquals("the box should wrap the square", player.height, box.height)
    }

    @Test
    fun videoOfUnknownSizeKeeps16by9() {
        val (box, _) = measure("https://example.com/a.mp4", square = false)

        assertEquals(16f / 9f, box.width.toFloat() / box.height, 0.01f)
    }
}
