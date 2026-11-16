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
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Preview
@Composable
fun Body0TropperPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body0Trooper(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun body0Trooper(
    fgColor: SolidColor,
    builder: Builder,
) {
    // body
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.2f)

    // body shades
    builder.addPath(pathData4, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData6, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)

    // neck Circle
    builder.addPath(pathData12, stroke = Black, strokeLineWidth = 1.0f)

    // neck
    builder.addPath(pathData13, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // Joints
    builder.addPath(pathData14, stroke = Black, strokeLineWidth = 1.0f)

    // Joint shade
    builder.addPath(pathData25, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(106.0f, 247.52f)
        reflectiveCurveToRelative(-34.47f, 3.0f, -50.47f, 32.0f)
        curveToRelative(0.0f, 0.0f, -5.0f, 9.0f, -6.0f, 23.0f)
        horizontalLineToRelative(27.0f)
        reflectiveCurveTo(77.43f, 272.54f, 106.0f, 247.52f)
        close()
        moveTo(226.5f, 236.5f)
        reflectiveCurveToRelative(-6.0f, -10.0f, -26.0f, -12.0f)
        reflectiveCurveToRelative(-56.0f, -2.0f, -81.0f, 13.0f)
        reflectiveCurveToRelative(-43.0f, 44.0f, -43.0f, 65.0f)
        horizontalLineToRelative(115.0f)
        reflectiveCurveToRelative(3.0f, -31.0f, 22.0f, -53.0f)
        curveToRelative(0.0f, 0.0f, 8.88f, -7.45f, 14.44f, -9.22f)
        curveTo(227.94f, 240.28f, 228.5f, 239.5f, 226.5f, 236.5f)
        close()
        moveTo(286.5f, 302.5f)
        reflectiveCurveToRelative(4.0f, -50.0f, -16.0f, -59.0f)
        reflectiveCurveToRelative(-44.0f, -8.0f, -57.0f, 6.0f)
        reflectiveCurveToRelative(-22.0f, 43.0f, -22.0f, 53.0f)
        close()
    }

private val pathData4 =
    PathData {
        moveTo(91.18f, 264.0f)
        reflectiveCurveToRelative(5.32f, 0.48f, 3.32f, 9.48f)
        reflectiveCurveToRelative(-5.0f, 7.0f, -7.0f, 17.0f)
        reflectiveCurveToRelative(-2.5f, 12.0f, -2.5f, 12.0f)
        horizontalLineTo(76.5f)
        reflectiveCurveTo(75.85f, 285.54f, 91.18f, 264.0f)
        close()
        moveTo(80.61f, 255.48f)
        reflectiveCurveToRelative(2.89f, 2.0f, -6.11f, 12.0f)
        arcToRelative(94.0f, 94.0f, 0.0f, false, false, -17.0f, 26.0f)
        curveToRelative(-3.0f, 7.0f, -1.75f, 9.0f, -1.75f, 9.0f)
        horizontalLineTo(49.5f)
        reflectiveCurveTo(50.73f, 270.47f, 80.61f, 255.48f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(268.5f, 247.5f)
        reflectiveCurveToRelative(-8.0f, 8.0f, -11.0f, 26.0f)
        reflectiveCurveToRelative(-2.25f, 29.0f, -2.25f, 29.0f)
        horizontalLineTo(286.5f)
        reflectiveCurveToRelative(3.84f, -41.79f, -12.58f, -56.9f)
        curveTo(273.92f, 245.6f, 271.5f, 244.5f, 268.5f, 247.5f)
        close()
        moveTo(218.22f, 229.55f)
        reflectiveCurveToRelative(-18.72f, 0.95f, -31.72f, 20.95f)
        reflectiveCurveToRelative(-17.0f, 37.0f, -17.5f, 52.0f)
        horizontalLineToRelative(22.5f)
        curveToRelative(-1.42f, 0.12f, 8.08f, -36.84f, 16.0f, -45.5f)
        curveToRelative(10.0f, -15.5f, 21.0f, -16.5f, 21.0f, -16.5f)
        reflectiveCurveTo(228.93f, 235.6f, 218.22f, 229.55f)
        close()
    }
private val pathData12 =
    PathData {
        moveTo(153.5f, 251.5f)
        curveToRelative(7.6f, -0.12f, 26.0f, -2.0f, 27.0f, -14.0f)
        reflectiveCurveToRelative(-27.0f, -8.0f, -27.0f, -8.0f)
        reflectiveCurveToRelative(-22.0f, 1.0f, -22.0f, 11.0f)
        reflectiveCurveTo(147.35f, 251.6f, 153.5f, 251.5f)
        close()
    }
private val pathData13 =
    PathData {
        moveTo(138.5f, 166.5f)
        reflectiveCurveToRelative(12.0f, 33.0f, 4.0f, 72.0f)
        curveToRelative(0.0f, 0.0f, 1.0f, 6.0f, 12.0f, 5.0f)
        reflectiveCurveToRelative(12.0f, -5.0f, 12.0f, -5.0f)
        reflectiveCurveToRelative(6.0f, -37.0f, -10.0f, -75.0f)
        curveTo(156.5f, 163.5f, 145.5f, 166.5f, 138.5f, 166.5f)
        close()
    }

private val pathData14 =
    PathData {
        moveTo(161.0f, 176.0f)
        arcToRelative(15.59f, 15.59f, 0.0f, false, true, -7.53f, 3.51f)
        curveToRelative(-5.0f, 1.0f, -9.0f, 1.0f, -11.52f, 0.0f)
        moveTo(163.66f, 185.38f)
        curveToRelative(-1.18f, 2.51f, -2.36f, 7.71f, -20.0f, 5.42f)
        moveTo(166.32f, 200.74f)
        reflectiveCurveToRelative(-4.63f, 7.77f, -21.23f, 3.27f)
        moveTo(167.42f, 213.41f)
        reflectiveCurveToRelative(-3.73f, 9.13f, -22.33f, 4.11f)
        moveTo(167.42f, 227.17f)
        reflectiveCurveToRelative(-7.75f, 9.36f, -23.34f, 1.85f)
    }
private val pathData25 =
    PathData {
        moveTo(150.5f, 164.5f)
        reflectiveCurveToRelative(10.5f, 29.5f, 11.0f, 45.0f)
        arcToRelative(328.75f, 328.75f, 0.0f, false, true, -1.0f, 33.0f)
        lineToRelative(6.0f, -3.0f)
        curveToRelative(3.29f, -1.87f, 0.5f, -61.5f, -10.0f, -76.0f)
        close()
    }
