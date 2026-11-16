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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.qrcode.ScanBounds
import com.vitorpamplona.amethyst.commons.qrcode.ScanFrame
import com.vitorpamplona.amethyst.commons.qrcode.ScanPoint
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_dark_hint
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_paste
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_pick_one
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_scan_image
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_sequence_progress
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_torch_off
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_torch_on
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_zoom_reset
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlin.math.max

/**
 * Maps the decoder's image space onto the preview.
 *
 * The preview is FILL_CENTER and the analysis frame shares its field of view — both use cases are
 * bound under one [androidx.camera.core.ViewPort] — so one uniform scale plus a centring offset
 * is exact. [max] rather than `min` because FILL_CENTER overfills and crops.
 */
class ScanViewMapping private constructor(
    private val scale: Float,
    private val dx: Float,
    private val dy: Float,
) {
    fun map(point: ScanPoint) = Offset(point.x * scale + dx, point.y * scale + dy)

    companion object {
        fun of(
            frame: ScanFrame,
            viewSize: IntSize,
        ): ScanViewMapping? {
            if (frame.width <= 0 || frame.height <= 0 || viewSize.width <= 0 || viewSize.height <= 0) return null

            val scale =
                max(
                    viewSize.width.toFloat() / frame.width,
                    viewSize.height.toFloat() / frame.height,
                )
            return ScanViewMapping(
                scale = scale,
                dx = (viewSize.width - frame.width * scale) / 2f,
                dy = (viewSize.height - frame.height * scale) / 2f,
            )
        }
    }
}

fun ScanBounds.toViewPath(mapping: ScanViewMapping): Path =
    Path().apply {
        val start = mapping.map(topLeft)
        moveTo(start.x, start.y)
        mapping.map(topRight).let { lineTo(it.x, it.y) }
        mapping.map(bottomRight).let { lineTo(it.x, it.y) }
        mapping.map(bottomLeft).let { lineTo(it.x, it.y) }
        close()
    }

fun ScanBounds.centerInView(mapping: ScanViewMapping): Offset = mapping.map(ScanPoint(centerX, centerY))

/**
 * [longestSide] expressed in view pixels.
 *
 * Needed because [longestSide] is measured in the decoder's image space: comparing it directly
 * against a distance in view space silently shrinks or grows the hit area by the preview's scale
 * factor, which on a 1280x720 analysis frame shown on a 1080p-wide screen is off by nearly 2x.
 */
fun ScanBounds.longestSideInView(mapping: ScanViewMapping): Float {
    val a = mapping.map(topLeft)
    val b = mapping.map(topRight)
    val c = mapping.map(bottomRight)
    val d = mapping.map(bottomLeft)
    return maxOf((a - b).getDistance(), (b - c).getDistance(), (c - d).getDistance(), (d - a).getDistance())
}

/** Four corner brackets marking where to aim. Purely decorative — we decode the whole frame. */
fun DrawScope.drawViewfinderBrackets(color: Color) {
    val side = minOf(size.width, size.height) * 0.68f
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f
    val arm = side * 0.12f
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

    fun bracket(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
    ) {
        drawPath(
            Path().apply {
                moveTo(x, y + dy * arm)
                lineTo(x, y)
                lineTo(x + dx * arm, y)
            },
            color = color,
            style = stroke,
        )
    }

    bracket(left, top, 1f, 1f)
    bracket(left + side, top, -1f, 1f)
    bracket(left, top + side, 1f, -1f)
    bracket(left + side, top + side, -1f, -1f)
}

/**
 * The chrome over the camera feed: close, torch, import, hints and progress.
 *
 * Deliberately a slot-free, single-purpose component — there is exactly one scanner screen, and
 * pulling these five controls out into parameters would be ceremony without a second caller.
 */
@Composable
fun QrScannerControls(
    state: QrScannerState,
    onClose: () -> Unit,
    onToggleTorch: () -> Unit,
    onPickImage: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Derived, so a pinch -- a new zoom ratio on every touch event -- recomposes only the chip
    // that prints it, not every control on the screen.
    val zoomed by remember(state) { derivedStateOf { state.zoomRatio > 1.05f } }

    Box(modifier.fillMaxSize()) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Close,
                contentDescription = stringRes(Res.string.close),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        if (zoomed) {
            ZoomChip(
                state = state,
                onReset = { state.resetZoom() },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            )
        }

        HintStack(
            state = state,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
        )

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            if (state.torchAvailable) {
                OverlayButton(
                    symbol = if (state.torchOn) MaterialSymbols.FlashlightOff else MaterialSymbols.FlashlightOn,
                    description =
                        if (state.torchOn) {
                            stringRes(Res.string.qr_scanner_torch_off)
                        } else {
                            stringRes(Res.string.qr_scanner_torch_on)
                        },
                    highlighted = state.torchOn,
                    onClick = onToggleTorch,
                )
            }
            OverlayButton(
                symbol = MaterialSymbols.PhotoLibrary,
                description = stringRes(Res.string.qr_scanner_scan_image),
                onClick = onPickImage,
            )
            OverlayButton(
                symbol = MaterialSymbols.ContentPaste,
                description = stringRes(Res.string.qr_scanner_paste),
                onClick = onPaste,
            )
        }
    }
}

/**
 * The one line of text under the viewfinder.
 *
 * Only ever shows one message, most urgent first: a notice the user asked for ("no code in that
 * picture") beats sequence progress, which beats "pick one of these", which beats the dark hint.
 */
@Composable
private fun HintStack(
    state: QrScannerState,
    modifier: Modifier = Modifier,
) {
    // `candidates` is a new list on every frame while codes are in view (their outlines move), so
    // reading its size directly recomposed this at the camera's frame rate.
    val severalCodes by remember(state) { derivedStateOf { state.candidates.size > 1 } }
    val progress = state.sequenceProgress
    val message =
        when {
            state.notice != null -> state.notice
            progress != null -> pluralStringRes(Res.plurals.qr_scanner_sequence_progress, progress.second, progress.first, progress.second)
            severalCodes -> stringRes(Res.string.qr_scanner_pick_one)
            state.isDark && !state.torchOn -> stringRes(Res.string.qr_scanner_dark_hint)
            else -> null
        } ?: return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.65f),
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ZoomChip(
    state: QrScannerState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onReset),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Text(
            text = "%.1f×  %s".format(state.zoomRatio, stringRes(Res.string.qr_scanner_zoom_reset)),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun OverlayButton(
    symbol: MaterialSymbol,
    description: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    FilledIconButton(
        onClick = onClick,
        colors =
            if (highlighted) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                )
            },
        modifier = Modifier.size(54.dp),
    ) {
        Icon(symbol = symbol, contentDescription = description, modifier = Modifier.size(26.dp))
    }
}
