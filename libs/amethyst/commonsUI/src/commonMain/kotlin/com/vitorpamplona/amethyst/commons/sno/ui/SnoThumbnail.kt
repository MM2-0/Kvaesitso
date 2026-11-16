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
package com.vitorpamplona.amethyst.commons.sno.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.sno.SnoObjectToRender
import com.vitorpamplona.amethyst.commons.sno.SnoRasterizer
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload

/**
 * A Simple Nostr Object drawn at a fixed size, for a feed or a thread.
 *
 * Rasterised once per (event, size, view) through Coil, so scrolling past the
 * same object costs a memory-cache hit rather than a re-render.
 */
@Composable
fun SnoThumbnail(
    payload: SnoPayload,
    eventId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    yawDegrees: Float = SnoRasterizer.DEFAULT_YAW_DEGREES,
    pitchDegrees: Float = SnoRasterizer.DEFAULT_PITCH_DEGREES,
    background: Color = Color.Transparent,
) {
    val pixelSize = with(LocalDensity.current) { size.roundToPx() }
    val backgroundArgb = if (background == Color.Transparent) 0 else background.toArgb()

    val request =
        remember(payload, eventId, pixelSize, yawDegrees, pitchDegrees, backgroundArgb) {
            SnoObjectToRender(
                payload = payload,
                eventId = eventId,
                size = pixelSize,
                yawDegrees = yawDegrees,
                pitchDegrees = pitchDegrees,
                background = backgroundArgb,
            )
        }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    }
}
