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
package com.vitorpamplona.amethyst.commons.icons

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun VectorPreview() {
    Image(following(Color(0xFF7F2EFF), Color.White), null)
}

// Two-tone follow badge. [shield] fills the outer shape and outlines the figure; [figure] fills the
// inner person. Callers pass the FilledTonalButton pair (secondaryContainer / onSecondaryContainer)
// so the badge tracks the accent and matches the tonal buttons. Build once and cache with remember().
public fun following(
    shield: Color,
    figure: Color,
): ImageVector =
    ImageVector
        .Builder(
            name = "Following",
            defaultWidth = 30.dp,
            defaultHeight = 30.dp,
            viewportWidth = 30f,
            viewportHeight = 30f,
        ).apply {
            path(
                fill = SolidColor(shield),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(27.8f, 12.8f)
                lineTo(27.8f, 12.8f)
                curveTo(27.8f, 5f, 27.2f, 0f, 24.4f, 0f)
                reflectiveCurveTo(15f, 0f, 15f, 0f)
                reflectiveCurveTo(8.4f, 0f, 5.6f, 0f)
                curveTo(2.8f, 0f, 2.2f, 5f, 2.2f, 12.8f)
                verticalLineToRelative(0f)
                lineToRelative(0f, 0f)
                curveToRelative(0f, 0f, 0f, 0f, 0f, 0f)
                curveTo(2.2f, 24.9f, 15f, 30f, 15f, 30f)
                reflectiveCurveTo(27.8f, 24.9f, 27.8f, 12.8f)
                curveTo(27.8f, 12.8f, 27.8f, 12.8f, 27.8f, 12.8f)
                lineTo(27.8f, 12.8f)
                close()
            }
            path(
                fill = SolidColor(figure),
                fillAlpha = 1.0f,
                stroke = SolidColor(shield),
                strokeAlpha = 1.0f,
                strokeLineWidth = 0.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(15.1f, 24f)
                lineToRelative(-0.5f, -0.2f)
                curveToRelative(-0.2f, -0.1f, -4.5f, -2f, -6.8f, -6.4f)
                curveToRelative(-0.3f, -0.6f, -0.1f, -1.4f, 0.6f, -1.7f)
                curveToRelative(0.6f, -0.3f, 1.4f, -0.1f, 1.7f, 0.6f)
                curveToRelative(1.4f, 2.8f, 3.9f, 4.4f, 5f, 4.9f)
                curveToRelative(1.1f, -0.6f, 3.6f, -2.2f, 5f, -4.9f)
                curveToRelative(0.3f, -0.6f, 1.1f, -0.9f, 1.7f, -0.6f)
                curveToRelative(0.6f, 0.3f, 0.9f, 1.1f, 0.6f, 1.7f)
                curveToRelative(-2.2f, 4.4f, -6.6f, 6.3f, -6.8f, 6.4f)
                lineTo(15.1f, 24f)
                close()
            }
            path(
                fill = SolidColor(figure),
                fillAlpha = 1.0f,
                stroke = SolidColor(shield),
                strokeAlpha = 1.0f,
                strokeLineWidth = 0.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(15f, 15f)
                curveToRelative(-2.9f, 0f, -5.2f, -2.3f, -5.2f, -5.2f)
                reflectiveCurveToRelative(2.3f, -5.2f, 5.2f, -5.2f)
                reflectiveCurveToRelative(5.2f, 2.3f, 5.2f, 5.2f)
                reflectiveCurveTo(17.8f, 15f, 15f, 15f)
                close()
                moveTo(15f, 7.2f)
                curveToRelative(-1.4f, 0f, -2.6f, 1.2f, -2.6f, 2.6f)
                reflectiveCurveToRelative(1.2f, 2.6f, 2.6f, 2.6f)
                reflectiveCurveToRelative(2.6f, -1.2f, 2.6f, -2.6f)
                reflectiveCurveTo(16.4f, 7.2f, 15f, 7.2f)
                close()
            }
        }.build()
