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
fun Body1ThinPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body1Thin(SolidColor(Color.Yellow), this)
                },
            ),
        contentDescription = "",
    )
}

fun body1Thin(
    fgColor: SolidColor,
    builder: Builder,
) {
    // Body
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // Body shade, must stay before grays
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData13, fill = Black, fillAlpha = 0.2f)

    // Gray legs and neck
    builder.addPath(pathData6, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // Shades Legs and neck
    builder.addPath(pathData15, fill = Black, fillAlpha = 0.2f)

    // Joints
    builder.addPath(pathData16, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(160.5f, 246.5f)
        reflectiveCurveToRelative(-14.0f, -3.0f, -27.0f, 13.0f)
        reflectiveCurveToRelative(-22.0f, 45.0f, -22.0f, 45.0f)
        horizontalLineToRelative(108.0f)
        reflectiveCurveToRelative(-20.0f, -35.0f, -25.0f, -40.0f)
        reflectiveCurveTo(176.5f, 247.5f, 160.5f, 246.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(121.5f, 303.5f)
        reflectiveCurveToRelative(5.0f, -24.0f, 10.0f, -33.0f)
        reflectiveCurveToRelative(3.28f, -8.07f, 7.64f, -8.53f)
        reflectiveCurveToRelative(15.13f, -1.22f, 23.75f, 2.66f)
        reflectiveCurveToRelative(8.18f, 3.25f, 8.18f, 3.25f)
        lineToRelative(13.12f, -12.15f)
        reflectiveCurveToRelative(-12.68f, -9.66f, -26.18f, -9.44f)
        reflectiveCurveToRelative(-23.5f, 12.22f, -26.5f, 15.22f)
        reflectiveCurveToRelative(-14.17f, 23.45f, -19.09f, 40.22f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(149.0f, 192.0f)
        reflectiveCurveToRelative(1.0f, 62.0f, 2.0f, 64.0f)
        arcToRelative(11.16f, 11.16f, 0.0f, false, false, 9.06f, 3.21f)
        curveToRelative(5.44f, -0.71f, 6.44f, -2.71f, 6.94f, -5.21f)
        curveToRelative(0.0f, 0.0f, -2.5f, -48.5f, -2.5f, -62.5f)
        close()
        moveTo(193.5f, 272.5f)
        arcToRelative(6.85f, 6.85f, 0.0f, false, false, -2.0f, 8.0f)
        curveToRelative(2.0f, 5.0f, 8.0f, 6.0f, 8.0f, 6.0f)
        reflectiveCurveToRelative(7.0f, -6.0f, 15.0f, -2.0f)
        reflectiveCurveToRelative(9.0f, 7.0f, 9.0f, 15.0f)
        reflectiveCurveToRelative(14.0f, 3.0f, 14.0f, 3.0f)
        lineToRelative(2.0f, -2.0f)
        reflectiveCurveToRelative(4.0f, -21.0f, -19.0f, -31.0f)
        curveTo(220.5f, 269.5f, 209.5f, 265.5f, 193.5f, 272.5f)
        close()
        moveTo(127.07f, 268.36f)
        reflectiveCurveTo(108.5f, 263.5f, 98.5f, 279.5f)
        curveToRelative(0.0f, 0.0f, -5.0f, 9.0f, 1.0f, 20.0f)
        reflectiveCurveToRelative(7.0f, 4.0f, 7.0f, 4.0f)
        lineToRelative(7.0f, -5.25f)
        reflectiveCurveToRelative(-6.0f, -7.75f, -2.0f, -13.75f)
        curveToRelative(0.0f, 0.0f, 2.75f, -4.25f, 8.88f, -3.12f)
        close()
    }
private val pathData7 =
    PathData {
    }
private val pathData13 =
    PathData {
        moveTo(195.58f, 301.91f)
        horizontalLineTo(218.0f)
        reflectiveCurveTo(203.0f, 275.78f, 198.73f, 270.14f)
        reflectiveCurveTo(186.5f, 256.5f, 184.5f, 255.5f)
        lineToRelative(-13.0f, 12.0f)
        reflectiveCurveToRelative(8.0f, 4.69f, 10.48f, 8.84f)
        reflectiveCurveTo(192.66f, 293.32f, 195.58f, 301.91f)
        close()
    }

private val pathData15 =
    PathData {
        moveTo(159.5f, 191.5f)
        reflectiveCurveToRelative(3.0f, 36.0f, 3.0f, 40.0f)
        reflectiveCurveToRelative(1.0f, 27.0f, 1.0f, 27.0f)
        lineToRelative(3.0f, -1.0f)
        lineToRelative(-2.0f, -66.0f)
        close()
        moveTo(196.5f, 284.5f)
        arcToRelative(25.68f, 25.68f, 0.0f, false, true, 18.0f, -5.0f)
        curveToRelative(11.0f, 1.0f, 16.0f, 9.0f, 18.0f, 17.0f)
        reflectiveCurveToRelative(-4.0f, 10.0f, -4.0f, 10.0f)
        lineToRelative(-5.0f, -7.0f)
        reflectiveCurveToRelative(0.05f, -13.88f, -10.0f, -15.44f)
        reflectiveCurveToRelative(-14.0f, 2.44f, -14.0f, 2.44f)
        reflectiveCurveTo(195.5f, 286.5f, 196.5f, 284.5f)
        close()
        moveTo(123.53f, 274.89f)
        reflectiveCurveToRelative(-7.0f, -2.39f, -13.0f, 0.61f)
        reflectiveCurveToRelative(-9.0f, 7.0f, -9.0f, 12.0f)
        reflectiveCurveToRelative(5.0f, 12.0f, 7.0f, 14.0f)
        reflectiveCurveToRelative(4.83f, -3.86f, 4.83f, -3.86f)
        reflectiveCurveToRelative(-3.83f, -5.14f, -2.83f, -11.14f)
        curveToRelative(0.0f, 0.0f, 1.0f, -6.0f, 10.0f, -5.0f)
        close()
    }
private val pathData16 =
    PathData {
        moveTo(145.5f, 261.5f)
        reflectiveCurveToRelative(28.0f, 1.0f, 38.0f, 17.0f)
        reflectiveCurveToRelative(13.0f, 26.0f, 13.0f, 26.0f)
        horizontalLineToRelative(-86.0f)
        reflectiveCurveToRelative(13.77f, -34.15f, 18.39f, -38.57f)
        reflectiveCurveTo(144.5f, 261.5f, 145.5f, 261.5f)
        close()

        moveTo(166.0f, 234.5f)
        reflectiveCurveToRelative(-0.5f, 3.0f, -5.5f, 3.0f)
        arcToRelative(64.39f, 64.39f, 0.0f, false, true, -10.3f, -1.0f)
        moveTo(165.8f, 225.5f)
        arcToRelative(10.11f, 10.11f, 0.0f, false, true, -6.3f, 2.0f)
        curveToRelative(-4.0f, 0.0f, -7.65f, -0.2f, -9.82f, -2.1f)
        moveTo(165.5f, 214.5f)
        arcToRelative(12.68f, 12.68f, 0.0f, false, true, -6.0f, 2.0f)
        curveToRelative(-3.0f, 0.0f, -7.0f, 0.25f, -10.0f, -1.37f)
        moveTo(164.5f, 203.5f)
        arcToRelative(8.76f, 8.76f, 0.0f, false, true, -6.0f, 2.0f)
        curveToRelative(-4.0f, 0.0f, -8.0f, 0.0f, -9.0f, -1.0f)
        moveTo(166.5f, 245.5f)
        reflectiveCurveToRelative(-1.0f, 3.0f, -6.0f, 3.0f)
        arcToRelative(21.51f, 21.51f, 0.0f, false, true, -10.0f, -2.0f)
        moveTo(206.0f, 268.82f)
        arcToRelative(2.89f, 2.89f, 0.0f, false, false, -1.5f, 2.68f)
        curveToRelative(0.0f, 2.0f, 0.1f, 8.87f, 7.0f, 11.94f)
        moveTo(229.44f, 274.89f)
        reflectiveCurveToRelative(-3.94f, -1.39f, -5.94f, 1.61f)
        reflectiveCurveToRelative(-3.62f, 9.25f, -2.81f, 12.13f)
        moveTo(223.56f, 300.34f)
        curveToRelative(-0.06f, 0.16f, 0.94f, -2.84f, 5.94f, -2.84f)
        arcToRelative(21.66f, 21.66f, 0.0f, false, true, 10.09f, 2.37f)
        moveTo(119.5f, 281.5f)
        reflectiveCurveToRelative(2.0f, -7.0f, 1.0f, -10.0f)
        arcToRelative(5.45f, 5.45f, 0.0f, false, false, -3.56f, -3.62f)
        moveTo(111.5f, 284.5f)
        arcToRelative(14.54f, 14.54f, 0.0f, false, false, -8.0f, -5.0f)
        curveToRelative(-5.0f, -1.0f, -5.44f, 0.94f, -5.44f, 0.94f)
        moveTo(111.0f, 293.56f)
        arcToRelative(10.89f, 10.89f, 0.0f, false, false, -7.48f, 0.94f)
        curveToRelative(-4.0f, 2.0f, -4.0f, 5.0f, -4.0f, 5.0f)
    }
