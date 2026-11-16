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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Hoisted state for the embedded-surface selection loupe (magnifier #4). The bubble shows a magnified,
 * live slice of the page captured in the `:napplet` provider (host-side `PixelCopy` can't read the sandbox
 * surface — see [EmbeddedMagnifierProbe]). While a caret/selection handle is dragged, [anchorPx] tracks the
 * drag point in this layer's px (so the bubble follows smoothly even between captures) and [image] is the
 * latest decoded frame. Throttle bookkeeping ([lastRequestUptimeMs]/[awaitingFrame]) lives here too but is
 * intentionally NOT Compose state — it must not trigger recomposition.
 */
class MagnifierUiState {
    var visible by mutableStateOf(false)
    var anchorPx by mutableStateOf(Offset.Zero)
    var image by mutableStateOf<ImageBitmap?>(null)

    var lastRequestUptimeMs: Long = 0L
    var awaitingFrame: Boolean = false

    // Request stamp of the frame on screen. Frames decode off the main thread and can finish out of order,
    // so an older one must not replace a newer one.
    var shownFrameStamp: Long = Long.MIN_VALUE

    fun hide() {
        visible = false
        image = null
        awaitingFrame = false
        shownFrameStamp = Long.MIN_VALUE
    }
}

/**
 * The loupe bubble: a rounded, elevated rectangle showing [MagnifierUiState.image], floated above the drag
 * point and centered on it horizontally, clamped inside [layerSize]. Like Android's `Magnifier`, it sits a
 * little above the finger; if there's no room above (drag near the top), it flips below. Nothing is drawn
 * until there's a frame to show.
 */
@Composable
fun Magnifier(
    state: MagnifierUiState,
    layerSize: IntSize,
    bubbleSize: DpSize,
) {
    if (!state.visible) return
    val img = state.image ?: return
    val density = LocalDensity.current
    val wPx = with(density) { bubbleSize.width.toPx() }
    val hPx = with(density) { bubbleSize.height.toPx() }
    val gapPx = with(density) { 28.dp.toPx() }

    val left = (state.anchorPx.x - wPx / 2f).coerceIn(0f, (layerSize.width - wPx).coerceAtLeast(0f))
    val above = state.anchorPx.y - hPx - gapPx
    val top = if (above >= 0f) above else state.anchorPx.y + gapPx

    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(bubbleSize)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        // The captured frame is built so its px size ≈ the bubble's px size (source rect = bubble / zoom,
        // provider scaled by zoom), so FillBounds maps it 1:1 without distortion.
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
