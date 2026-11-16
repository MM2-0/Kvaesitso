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
fun Body8BigPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body8Big(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun body8Big(
    fgColor: SolidColor,
    builder: Builder,
) {
    // body
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // neck base
    builder.addPath(pathData4, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData7, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)

    // Body shades
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.4f)

    // Neck and arms
    builder.addPath(pathData32, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // Joints
    builder.addPath(pathData33, stroke = Black, strokeLineWidth = 1.0f)

    // Shades
    builder.addPath(pathData20, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(95.13f, 227.69f)
        reflectiveCurveTo(74.5f, 232.5f, 73.5f, 248.5f)
        curveToRelative(-0.44f, 7.05f, 4.25f, 12.09f, 9.63f, 15.53f)
        arcToRelative(52.45f, 52.45f, 0.0f, false, false, 14.79f, 6.13f)
        reflectiveCurveToRelative(-3.16f, -18.66f, -2.79f, -30.66f)
        reflectiveCurveTo(95.13f, 227.69f, 95.13f, 227.69f)
        close()
        moveTo(97.5f, 301.5f)
        reflectiveCurveToRelative(2.0f, -21.0f, 0.0f, -35.0f)
        reflectiveCurveToRelative(-2.0f, -29.0f, -2.0f, -36.0f)
        reflectiveCurveToRelative(14.0f, -26.11f, 46.0f, -28.05f)
        reflectiveCurveToRelative(59.0f, -1.95f, 63.0f, 18.05f)
        reflectiveCurveToRelative(3.0f, 30.0f, 3.0f, 30.0f)
        reflectiveCurveToRelative(2.0f, 53.0f, 0.0f, 55.0f)
        reflectiveCurveTo(97.5f, 301.5f, 97.5f, 301.5f)
        close()
        moveTo(204.5f, 220.5f)
        reflectiveCurveToRelative(28.0f, 1.0f, 29.0f, 23.0f)
        reflectiveCurveTo(216.6f, 267.0f, 208.0f, 268.24f)
        curveTo(208.0f, 268.24f, 209.5f, 233.5f, 204.5f, 220.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(95.5f, 230.5f)
        reflectiveCurveToRelative(-7.0f, 20.0f, 40.0f, 23.0f)
        reflectiveCurveToRelative(69.0f, -15.69f, 69.51f, -30.35f)
        reflectiveCurveToRelative(-12.33f, -22.39f, -48.92f, -21.52f)
        reflectiveCurveTo(100.5f, 212.5f, 95.5f, 230.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(87.5f, 232.5f)
        reflectiveCurveToRelative(-9.0f, 9.0f, -9.0f, 14.0f)
        reflectiveCurveToRelative(6.0f, 16.0f, 2.0f, 15.0f)
        reflectiveCurveToRelative(-7.57f, -12.0f, -5.79f, -18.48f)
        reflectiveCurveTo(86.5f, 229.5f, 87.5f, 232.5f)
        close()
        moveTo(104.5f, 263.5f)
        arcToRelative(4.45f, 4.45f, 0.0f, false, false, 2.0f, 5.0f)
        curveToRelative(3.0f, 2.0f, 7.0f, 0.0f, 6.0f, -2.0f)
        reflectiveCurveTo(106.5f, 260.5f, 104.5f, 263.5f)
        close()
        moveTo(109.5f, 276.5f)
        reflectiveCurveToRelative(-5.0f, -1.0f, -5.0f, 9.0f)
        arcToRelative(161.75f, 161.75f, 0.0f, false, false, 1.0f, 18.0f)
        lineToRelative(11.0f, 1.0f)
        reflectiveCurveToRelative(-3.0f, -5.0f, -3.0f, -14.0f)
        reflectiveCurveTo(112.5f, 278.5f, 109.5f, 276.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(102.5f, 227.5f)
        reflectiveCurveToRelative(-4.0f, 22.0f, 38.0f, 21.0f)
        curveToRelative(0.0f, 0.0f, 56.0f, 2.0f, 57.0f, -26.0f)
        reflectiveCurveToRelative(-69.0f, -14.0f, -69.0f, -14.0f)
        reflectiveCurveTo(106.5f, 214.5f, 102.5f, 227.5f)
        close()
    }
private val pathData20 =
    PathData {
        moveTo(217.85f, 223.44f)
        reflectiveCurveToRelative(9.65f, 6.06f, 7.65f, 17.06f)
        reflectiveCurveToRelative(-8.0f, 16.09f, -17.0f, 18.54f)
        verticalLineToRelative(9.46f)
        reflectiveCurveToRelative(24.08f, -0.06f, 25.0f, -22.53f)
        curveTo(233.54f, 246.0f, 236.21f, 233.39f, 217.85f, 223.44f)
        close()
        moveTo(90.5f, 270.5f)
        reflectiveCurveToRelative(7.0f, 25.0f, 0.0f, 32.0f)
        lineToRelative(7.0f, -1.0f)
        verticalLineToRelative(-31.0f)
        lineToRelative(-8.76f, -3.76f)
        close()
        moveTo(85.5f, 273.5f)
        reflectiveCurveToRelative(5.0f, 22.0f, -2.0f, 29.0f)
        reflectiveCurveToRelative(-4.6f, -3.27f, -4.6f, -3.27f)
        reflectiveCurveToRelative(4.73f, -4.71f, 5.66f, -15.22f)
        lineToRelative(0.94f, -10.51f)
        moveTo(225.8f, 272.89f)
        reflectiveCurveToRelative(-12.3f, -6.39f, -12.3f, 0.61f)
        reflectiveCurveToRelative(11.0f, 28.0f, 14.0f, 31.0f)
        reflectiveCurveToRelative(-6.0f, 0.0f, -6.0f, 0.0f)
        reflectiveCurveToRelative(-10.71f, -12.76f, -12.36f, -19.88f)
        reflectiveCurveToRelative(-0.64f, -16.12f, -0.64f, -16.12f)
        lineToRelative(16.4f, -5.2f)
        close()
        moveTo(197.51f, 304.5f)
        reflectiveCurveToRelative(4.0f, -34.0f, 0.0f, -53.0f)
        curveToRelative(-2.39f, -11.36f, -2.58f, -11.5f, -2.55f, -11.32f)
        curveToRelative(0.0f, 0.0f, 8.57f, -4.37f, 10.06f, -17.0f)
        reflectiveCurveToRelative(3.49f, 45.35f, 3.49f, 45.35f)
        verticalLineToRelative(37.0f)
        close()

        moveTo(151.5f, 183.5f)
        arcToRelative(14.82f, 14.82f, 0.0f, false, false, 0.0f, 10.0f)
        curveToRelative(2.0f, 5.0f, 5.0f, 18.0f, 4.0f, 24.0f)
        arcToRelative(61.38f, 61.38f, 0.0f, false, true, -2.35f, 9.74f)
        reflectiveCurveToRelative(9.35f, -4.74f, 8.35f, -9.74f)
        reflectiveCurveToRelative(-2.37f, -8.51f, -3.18f, -17.76f)
        arcToRelative(104.59f, 104.59f, 0.0f, false, true, 0.18f, -18.24f)
        horizontalLineToRelative(-6.4f)
        close()
    }
private val pathData32 =
    PathData {
        moveTo(224.9f, 263.3f)
        reflectiveCurveToRelative(-1.4f, 20.2f, 16.6f, 39.2f)
        horizontalLineToRelative(-22.0f)
        reflectiveCurveToRelative(-7.35f, -6.33f, -11.18f, -20.67f)
        lineToRelative(0.18f, -13.33f)
        reflectiveCurveTo(218.3f, 268.1f, 224.9f, 263.3f)
        close()
        moveTo(76.5f, 302.5f)
        reflectiveCurveToRelative(9.0f, -10.0f, 8.0f, -24.0f)
        lineToRelative(-1.0f, -14.0f)
        lineToRelative(14.0f, 6.0f)
        reflectiveCurveToRelative(2.0f, 24.0f, 0.0f, 31.0f)
        reflectiveCurveTo(76.5f, 302.5f, 76.5f, 302.5f)
        close()
        moveTo(139.5f, 181.5f)
        lineToRelative(1.0f, 39.0f)
        reflectiveCurveToRelative(0.0f, 7.0f, 10.0f, 7.0f)
        reflectiveCurveToRelative(11.0f, -10.0f, 11.0f, -10.0f)
        arcToRelative(155.16f, 155.16f, 0.0f, false, true, -3.0f, -16.0f)
        curveToRelative(-1.0f, -8.0f, 0.0f, -20.0f, 0.0f, -20.0f)
        horizontalLineToRelative(-19.0f)
        close()
    }
private val pathData33 =
    PathData {
        moveTo(158.0f, 185.5f)
        reflectiveCurveToRelative(0.33f, 3.92f, -18.35f, 2.0f)
        moveTo(158.0f, 197.5f)
        reflectiveCurveToRelative(0.0f, 6.0f, -17.5f, 1.0f)
        moveTo(160.0f, 210.5f)
        reflectiveCurveToRelative(-2.35f, 7.0f, -20.0f, 2.0f)
        moveTo(215.68f, 298.0f)
        reflectiveCurveToRelative(4.82f, -4.52f, 10.82f, -4.52f)
        arcToRelative(18.1f, 18.1f, 0.0f, false, true, 9.41f, 2.26f)
        moveTo(208.5f, 281.5f)
        arcToRelative(10.58f, 10.58f, 0.0f, false, true, 11.0f, -6.0f)
        curveToRelative(8.0f, 1.0f, 7.71f, 3.0f, 7.71f, 3.0f)
        moveTo(97.5f, 298.5f)
        reflectiveCurveToRelative(-2.26f, -5.48f, -16.63f, -2.74f)
        moveTo(97.5f, 282.5f)
        reflectiveCurveToRelative(-3.87f, -3.0f, -12.94f, -1.0f)
    }
