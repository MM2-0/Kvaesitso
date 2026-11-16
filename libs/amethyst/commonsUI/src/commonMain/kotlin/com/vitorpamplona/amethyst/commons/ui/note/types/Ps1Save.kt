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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.service.image.argbPixelsToImageBitmap
import com.vitorpamplona.amethyst.commons.ui.note.Ps1SaveCard
import com.vitorpamplona.amethyst.commons.ui.theme.Size24dp
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveEvent
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveIcon

/** How many hex characters of the block to preview (the full block is ~16K chars). */
private const val HEX_PREVIEW_CHARS = 192

/** Milliseconds per animation frame, roughly the BIOS memory-card screen cadence. */
private const val ICON_FRAME_MILLIS = 250L

/**
 * Entry for a PS1 memory-card save block: decodes the [Note] and hands the parsed
 * values to the shared commons [Ps1SaveCard]. The save's 16×16 icon needs Android
 * bitmap decoding, so it is supplied here as the card's icon slot.
 */
@Composable
fun RenderPs1Save(baseNote: Note) {
    val noteEvent = baseNote.event as? Ps1SaveEvent ?: return

    val save =
        remember(noteEvent) {
            Ps1SaveInfo(
                title = noteEvent.saveTitle(),
                filename = noteEvent.filename(),
                region = noteEvent.region(),
                blockNumber = noteEvent.blockNumber(),
                icon = noteEvent.icon(),
                isBlank = noteEvent.isBlankBlock(),
                hexPreview =
                    noteEvent.content
                        .take(HEX_PREVIEW_CHARS)
                        .chunked(4)
                        .joinToString(" ")
                        .ifBlank { null },
            )
        }

    Ps1SaveCard(
        title = save.title,
        filename = save.filename,
        region = save.region,
        blockNumber = save.blockNumber,
        isBlank = save.isBlank,
        hexPreview = save.hexPreview,
        icon = save.icon?.let { ic -> { Ps1SaveIconImage(ic) } },
    )
}

/**
 * The save's 16×16 icon, cycling its animation frames like the PS1 BIOS
 * memory-card screen. Scaled with nearest-neighbor so the pixels stay crisp.
 */
@Composable
private fun Ps1SaveIconImage(icon: Ps1SaveIcon) {
    val frames =
        remember(icon) {
            icon.frames.map { pixels ->
                argbPixelsToImageBitmap(pixels, Ps1SaveIcon.SIZE, Ps1SaveIcon.SIZE)
            }
        }

    var frameIndex by remember(icon) { mutableIntStateOf(0) }

    if (frames.size > 1) {
        // Driven by the frame clock (not delay) so the ticker suspends whenever
        // the composition stops drawing (backgrounded, behind another screen).
        LaunchedEffect(icon) {
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    frameIndex = (((now - start) / ICON_FRAME_MILLIS) % frames.size).toInt()
                }
            }
        }
    }

    Image(
        bitmap = frames[frameIndex],
        contentDescription = null,
        modifier = Modifier.size(Size24dp),
        filterQuality = FilterQuality.None,
    )
}

/** Tag values parsed once per event for the save card. */
private class Ps1SaveInfo(
    val title: String?,
    val filename: String?,
    val region: String?,
    val blockNumber: Int?,
    val icon: Ps1SaveIcon?,
    val isBlank: Boolean,
    val hexPreview: String?,
)
