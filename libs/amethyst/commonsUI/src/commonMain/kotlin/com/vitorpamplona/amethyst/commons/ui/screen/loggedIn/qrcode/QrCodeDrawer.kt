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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder

/**
 * The quiet zone around the code, in **modules** — the QR spec's minimum of 4.
 *
 * It was a fixed 100px per side, which does not scale: at a small draw size those 200px ate most of
 * the canvas, so a long payload (a Concord invite link, an nprofile) rendered as a postage stamp
 * floating in white. Expressed in modules the zone stays proportional, so the code fills whatever
 * box it is given at every size while remaining scannable.
 */
const val QR_QUIET_ZONE_MODULES = 4f

/**
 * Corner rounding on the finder patterns, as a fraction of one module. Small enough to stay well
 * inside what a decoder tolerates, large enough to keep the code from looking like a 1998 barcode.
 */
const val FINDER_CORNER_RADIUS_MODULES = 0.22f

@Preview
@Composable
fun QrCodeDrawerPreview() {
    Box(Modifier.background(Color.Black).padding(10.dp)) {
        QrCodeDrawer("Test QR data")
    }
}

/**
 * Draws [contents] as a QR code: always square, centred in whatever space [modifier] gives it.
 *
 * Square is enforced on an inner box rather than with `aspectRatio` on the caller's modifier. A
 * caller that fixes both dimensions (`fillMaxWidth().weight(1f)` in a column) left `aspectRatio`
 * nothing to satisfy, so it fell back to a width-by-width square centred in a shorter slot, and
 * the clip trimmed its top and bottom: on a landscape phone or a laptop window the wallet's
 * invoice lost its quiet zone and finder patterns and would not scan.
 *
 * A payload too long for any QR code draws an empty light square — there is no code to show.
 */
@Composable
fun QrCodeDrawer(
    contents: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(contents) { encodeQrMatrix(contents) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .defaultMinSize(48.dp, 48.dp)
                    .aspectRatio(1f)
                    .clip(shape = QuoteBorder)
                    .background(Color.White),
        ) {
            if (matrix != null) {
                QrCanvas(matrix, Color.Black)
            }
        }
    }
}

/**
 * Builds the code's shapes once per size and only replays them on draw.
 *
 * One path for the data modules and one for the three finders, instead of a fresh `Path` and
 * draw call per dark module — about two thousand of each for an nprofile — every time the layer
 * was re-recorded (a resize, the keyboard opening, a list item scrolling back into view).
 */
@Composable
private fun QrCanvas(
    matrix: QrMatrix,
    foregroundColor: Color,
) {
    Spacer(
        modifier =
            Modifier.fillMaxSize().drawWithCache {
                // The box is square, but size against the shorter side anyway so a non-square
                // canvas can only ever shrink the code, never misplace its bottom finder.
                val side = size.minDimension
                val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)

                // Solve for the module size with the quiet zone measured in modules, so the whole
                // code (zone included) is exactly as wide as the canvas.
                val module = side / (matrix.width + QR_QUIET_ZONE_MODULES * 2f)
                val quietZonePx = module * QR_QUIET_ZONE_MODULES

                // Scale the rounding with the module size. A fixed 20px radius is a gentle touch on
                // a large code and a serious deformation on a small one: the finder patterns are
                // what a decoder locates first, and rounding away a third of a module's worth of
                // their corners is exactly the kind of damage that makes a code readable on screen
                // and unreadable in a photo of that screen.
                val radius = CornerRadius(module * FINDER_CORNER_RADIUS_MODULES)

                val finders =
                    qrCodeFindersPath(
                        origin = origin,
                        quietZonePx = quietZonePx,
                        sideLength = side,
                        finderPatternSize = Size(module * FINDER_PATTERN_ROW_COUNT, module * FINDER_PATTERN_ROW_COUNT),
                        cornerRadius = radius,
                    )
                val data = qrCodeDataBitsPath(origin + Offset(quietZonePx, quietZonePx), matrix, module)

                onDrawBehind {
                    drawPath(finders, foregroundColor)
                    drawPath(data, foregroundColor)
                }
            },
    )
}

/**
 * Every dark module outside the three finder patterns, as one path.
 *
 * Each module is grown by half a pixel so neighbours overlap instead of leaving hairline seams
 * from anti-aliasing; with the default non-zero fill the overlaps simply merge.
 */
private fun qrCodeDataBitsPath(
    topLeft: Offset,
    matrix: QrMatrix,
    module: Float,
): Path =
    Path().apply {
        val cell = Size(module + 0.5f, module + 0.5f)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y] && !isInFinder(x, y, matrix)) {
                    addRect(Rect(Offset(topLeft.x + x * module, topLeft.y + y * module), cell))
                }
            }
        }
    }

/** The three 7x7 corners that [qrCodeFindersPath] draws with rounded shapes instead. */
private fun isInFinder(
    x: Int,
    y: Int,
    matrix: QrMatrix,
): Boolean {
    val left = x < FINDER_PATTERN_ROW_COUNT
    val top = y < FINDER_PATTERN_ROW_COUNT
    val right = x >= matrix.width - FINDER_PATTERN_ROW_COUNT
    val bottom = y >= matrix.height - FINDER_PATTERN_ROW_COUNT
    return (left && top) || (right && top) || (left && bottom)
}

const val FINDER_PATTERN_ROW_COUNT = 7
private const val INTERIOR_EXTERIOR_SHAPE_RATIO = 3f / FINDER_PATTERN_ROW_COUNT
private const val INTERIOR_EXTERIOR_OFFSET_RATIO = 2f / FINDER_PATTERN_ROW_COUNT
private const val INTERIOR_EXTERIOR_SHAPE_CORNER_RADIUS = 0.50f
private const val INTERIOR_BACKGROUND_EXTERIOR_SHAPE_RATIO = 5f / FINDER_PATTERN_ROW_COUNT
private const val INTERIOR_BACKGROUND_EXTERIOR_OFFSET_RATIO = 1f / FINDER_PATTERN_ROW_COUNT
private const val INTERIOR_BACKGROUND_EXTERIOR_SHAPE_CORNER_RADIUS = 0.5f

/**
 * A valid QR code has three finder patterns (top left, top right, bottom left), as one even-odd
 * path: each is three nested rounded squares, and the even-odd fill punches the middle ring out.
 *
 * @param origin top-left corner of the square the code is drawn in
 * @param sideLength length, in pixels, of each side of that square
 * @param finderPatternSize [Size] of each finder patten, based on the QR code spec
 */
private fun qrCodeFindersPath(
    origin: Offset,
    quietZonePx: Float,
    sideLength: Float,
    finderPatternSize: Size,
    cornerRadius: CornerRadius,
): Path =
    Path().apply {
        fillType = PathFillType.EvenOdd
        listOf(
            // Top left finder pattern.
            Offset(x = quietZonePx, y = quietZonePx),
            // Top right finder pattern.
            Offset(x = sideLength - (quietZonePx + finderPatternSize.width), y = quietZonePx),
            // Bottom left finder pattern.
            Offset(x = quietZonePx, y = sideLength - (quietZonePx + finderPatternSize.height)),
        ).forEach { offset ->
            addQrCodeFinder(
                topLeft = origin + offset,
                finderPatternSize = finderPatternSize,
                cornerRadius = cornerRadius,
            )
        }
    }

/** Adds a single finder pattern's three nested shapes. */
private fun Path.addQrCodeFinder(
    topLeft: Offset,
    finderPatternSize: Size,
    cornerRadius: CornerRadius,
) {
    // The outer rectangle for the finder pattern.
    addRoundRect(
        roundRect =
            RoundRect(
                rect = Rect(offset = topLeft, size = finderPatternSize),
                cornerRadius = cornerRadius,
            ),
    )

    // Background for the finder pattern interior (this keeps the arc ratio consistent).
    val innerBackgroundOffset =
        Offset(
            x = finderPatternSize.width * INTERIOR_BACKGROUND_EXTERIOR_OFFSET_RATIO,
            y = finderPatternSize.height * INTERIOR_BACKGROUND_EXTERIOR_OFFSET_RATIO,
        )
    addRoundRect(
        roundRect =
            RoundRect(
                rect =
                    Rect(
                        offset = topLeft + innerBackgroundOffset,
                        size = finderPatternSize * INTERIOR_BACKGROUND_EXTERIOR_SHAPE_RATIO,
                    ),
                cornerRadius = cornerRadius * INTERIOR_BACKGROUND_EXTERIOR_SHAPE_CORNER_RADIUS,
            ),
    )

    // The inner rectangle for the finder pattern.
    val innerRectOffset =
        Offset(
            x = finderPatternSize.width * INTERIOR_EXTERIOR_OFFSET_RATIO,
            y = finderPatternSize.height * INTERIOR_EXTERIOR_OFFSET_RATIO,
        )
    addRoundRect(
        roundRect =
            RoundRect(
                rect =
                    Rect(
                        offset = topLeft + innerRectOffset,
                        size = finderPatternSize * INTERIOR_EXTERIOR_SHAPE_RATIO,
                    ),
                cornerRadius = cornerRadius * INTERIOR_EXTERIOR_SHAPE_CORNER_RADIUS,
            ),
    )
}
