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
package com.vitorpamplona.amethyst.commons.robohash.parts

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.robohash.Black
import com.vitorpamplona.amethyst.commons.robohash.Gray
import com.vitorpamplona.amethyst.commons.robohash.Yellow
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Preview
@Composable
fun Body6IronManPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body6IronMan(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun body6IronMan(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // Body shades
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData7, fill = Black, fillAlpha = 0.4f)

    // Iron man circle
    builder.addPath(pathData12, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)
    builder.addPath(pathData14, fill = Yellow, stroke = Black, strokeLineWidth = 1.0f)

    // Gray neck
    builder.addPath(pathData19, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // Neck Shades
    builder.addPath(pathData20, fill = Black, fillAlpha = 0.2f)

    // Joints
    builder.addPath(pathData21, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(158.5f, 234.5f)
        reflectiveCurveToRelative(-59.0f, 1.0f, -84.0f, 42.0f)
        reflectiveCurveToRelative(23.0f, 42.0f, 23.0f, 42.0f)
        lineToRelative(132.0f, -13.0f)
        reflectiveCurveToRelative(-5.0f, -16.0f, 5.0f, -27.0f)
        reflectiveCurveToRelative(19.0f, -6.0f, 19.0f, -6.0f)
        reflectiveCurveTo(219.5f, 231.5f, 158.5f, 234.5f)
        close()
        moveTo(277.5f, 301.5f)
        curveToRelative(-0.33f, -7.3f, -4.07f, -15.56f, -12.0f, -25.0f)
        curveToRelative(0.0f, 0.0f, -11.5f, -7.5f, -25.28f, -2.69f)
        curveToRelative(-7.72f, 4.69f, -13.72f, 11.69f, -11.72f, 28.69f)
        close()
        moveTo(74.5f, 276.5f)
        arcToRelative(29.05f, 29.05f, 0.0f, false, false, -26.0f, 16.0f)
        curveToRelative(-9.0f, 17.0f, 10.0f, 17.0f, 10.0f, 17.0f)
        lineToRelative(8.79f, -9.5f)
        reflectiveCurveTo(64.5f, 293.5f, 74.5f, 276.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(215.0f, 245.0f)
        reflectiveCurveToRelative(6.5f, 1.46f, -0.5f, 16.46f)
        reflectiveCurveToRelative(-13.0f, 26.0f, -10.0f, 41.0f)
        reflectiveCurveToRelative(24.32f, 0.0f, 24.32f, 0.0f)
        reflectiveCurveToRelative(-3.6f, -15.79f, 7.0f, -25.4f)
        curveToRelative(12.91f, -11.65f, 19.71f, -3.56f, 16.64f, -5.6f)
        curveTo(252.5f, 271.5f, 235.49f, 253.58f, 215.0f, 245.0f)
        close()
        moveTo(261.5f, 304.5f)
        reflectiveCurveToRelative(4.0f, -7.0f, 3.0f, -15.0f)
        reflectiveCurveToRelative(-8.36f, -16.57f, -8.36f, -16.57f)
        arcToRelative(25.86f, 25.86f, 0.0f, false, true, 18.77f, 16.29f)
        arcToRelative(27.17f, 27.17f, 0.0f, false, true, 2.58f, 15.29f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(62.5f, 280.5f)
        reflectiveCurveToRelative(5.0f, 0.0f, 0.0f, 9.0f)
        reflectiveCurveToRelative(-8.0f, 11.0f, -9.0f, 14.0f)
        reflectiveCurveToRelative(-6.5f, -2.33f, -6.5f, -2.33f)
        reflectiveCurveToRelative(-3.23f, -4.81f, 5.63f, -14.74f)
        curveTo(52.63f, 286.43f, 57.5f, 280.5f, 62.5f, 280.5f)
        close()
        moveTo(77.5f, 301.5f)
        reflectiveCurveToRelative(0.0f, -19.0f, 13.0f, -30.0f)
        reflectiveCurveToRelative(16.0f, -5.0f, 15.0f, -2.0f)
        reflectiveCurveToRelative(-14.0f, 24.0f, -14.0f, 32.0f)
        reflectiveCurveTo(77.5f, 301.5f, 77.5f, 301.5f)
        close()
    }
private val pathData12 =
    PathData {
        moveTo(103.5f, 303.5f)
        reflectiveCurveToRelative(2.0f, -30.0f, 41.0f, -29.0f)
        reflectiveCurveToRelative(41.0f, 29.0f, 41.0f, 29.0f)
        horizontalLineTo(174.25f)
        curveToRelative(-0.75f, -2.0f, -12.12f, -18.58f, -30.75f, -18.0f)
        curveToRelative(-15.88f, -0.37f, -26.93f, 4.64f, -30.5f, 18.0f)
        close()

        moveTo(113.5f, 301.5f)
        reflectiveCurveToRelative(3.0f, -16.0f, 28.0f, -16.0f)
        reflectiveCurveToRelative(32.0f, 17.0f, 32.0f, 17.0f)
        close()
    }
private val pathData14 =
    PathData {
        moveTo(118.5f, 300.5f)
        reflectiveCurveToRelative(9.0f, -12.0f, 24.0f, -11.0f)
        reflectiveCurveToRelative(24.0f, 12.0f, 24.0f, 12.0f)
        close()
    }
private val pathData19 =
    PathData {
        moveTo(147.0f, 200.0f)
        reflectiveCurveToRelative(0.0f, 44.0f, 1.0f, 45.0f)
        arcToRelative(14.41f, 14.41f, 0.0f, false, false, 14.0f, 4.0f)
        curveToRelative(8.0f, -2.0f, 7.0f, -7.0f, 7.0f, -7.0f)
        arcToRelative(144.32f, 144.32f, 0.0f, false, true, -2.5f, -24.5f)
        verticalLineToRelative(-19.0f)
        reflectiveCurveTo(148.5f, 198.5f, 147.0f, 200.0f)
        close()
    }
private val pathData20 =
    PathData {
        moveTo(159.5f, 202.5f)
        reflectiveCurveToRelative(1.0f, 17.0f, 2.0f, 23.0f)
        reflectiveCurveToRelative(2.0f, 22.83f, -2.0f, 23.91f)
        curveToRelative(0.0f, 0.0f, 9.62f, -0.9f, 9.31f, -8.41f)
        reflectiveCurveToRelative(-2.43f, -19.17f, -2.37f, -30.34f)
        reflectiveCurveToRelative(0.06f, -12.17f, 0.06f, -12.17f)
        lineToRelative(-7.0f, 0.11f)
        close()
    }
private val pathData21 =
    PathData {
        moveTo(147.0f, 233.5f)
        reflectiveCurveToRelative(1.08f, 3.0f, 5.4f, 3.0f)
        reflectiveCurveToRelative(12.83f, -1.33f, 15.0f, -4.66f)
        moveTo(147.5f, 219.32f)
        reflectiveCurveToRelative(1.0f, 2.68f, 5.0f, 2.68f)
        reflectiveCurveToRelative(11.89f, -1.18f, 13.94f, -4.16f)
        moveTo(147.5f, 208.07f)
        reflectiveCurveToRelative(1.0f, 1.93f, 5.0f, 1.93f)
        reflectiveCurveToRelative(11.89f, -0.85f, 13.94f, -3.0f)
    }
