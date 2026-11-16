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
fun Body3FrontPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body3Front(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun body3Front(
    fgColor: SolidColor,
    builder: Builder,
) {
    // body
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // body shades
    builder.addPath(pathData3, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData11, fill = Black, fillAlpha = 0.2f)

    //  Gray neck and arms
    builder.addPath(pathData13, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // chest
    // Joints
    builder.addPath(pathData24, stroke = Black, strokeLineWidth = 1.0f)

    // Shades
    builder.addPath(pathData25, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(148.46f, 242.82f)
        reflectiveCurveToRelative(-35.0f, 5.68f, -33.0f, 23.68f)
        curveToRelative(0.0f, 0.0f, -1.0f, 7.0f, 9.0f, 20.0f)
        reflectiveCurveToRelative(13.0f, 20.0f, 13.0f, 20.0f)
        lineToRelative(51.0f, -2.0f)
        reflectiveCurveToRelative(1.0f, -7.0f, 9.0f, -18.0f)
        reflectiveCurveToRelative(11.0f, -13.0f, 11.0f, -24.0f)
        reflectiveCurveToRelative(-13.09f, -25.36f, -60.0f, -19.68f)
    }
private val pathData3 =
    PathData {
        moveTo(131.83f, 247.83f)
        reflectiveCurveToRelative(-6.33f, 4.67f, -7.33f, 12.67f)
        curveToRelative(0.0f, 0.0f, -3.0f, 7.0f, 4.0f, 14.0f)
        reflectiveCurveToRelative(13.0f, 18.0f, 14.0f, 25.0f)
        reflectiveCurveToRelative(-7.56f, 2.2f, -7.56f, 2.2f)
        reflectiveCurveToRelative(-5.72f, -8.89f, -11.08f, -16.0f)
        reflectiveCurveToRelative(-10.81f, -14.18f, -7.58f, -25.17f)
        curveTo(116.28f, 260.49f, 118.17f, 254.17f, 131.83f, 247.83f)
        close()
    }
private val pathData11 =
    PathData {
        moveTo(193.33f, 245.17f)
        reflectiveCurveToRelative(-1.83f, 5.33f, 1.17f, 8.33f)
        reflectiveCurveToRelative(4.0f, 4.0f, 2.0f, 8.0f)
        arcToRelative(21.05f, 21.05f, 0.0f, false, false, -3.72f, 4.0f)
        curveToRelative(-1.28f, 2.0f, 0.72f, 4.0f, -1.28f, 9.0f)
        reflectiveCurveToRelative(-15.0f, 28.0f, -15.0f, 28.0f)
        lineToRelative(12.74f, -0.81f)
        reflectiveCurveToRelative(-0.74f, -2.19f, 8.26f, -15.19f)
        reflectiveCurveToRelative(13.0f, -17.33f, 10.0f, -29.17f)
        curveTo(207.5f, 257.33f, 204.17f, 249.83f, 193.33f, 245.17f)
        close()
    }
private val pathData13 =
    PathData {
        moveTo(166.5f, 205.5f)
        horizontalLineToRelative(-14.0f)
        arcToRelative(12.13f, 12.13f, 0.0f, false, false, -5.0f, 1.0f)
        lineToRelative(1.0f, 38.0f)
        reflectiveCurveToRelative(0.0f, 11.0f, 10.0f, 11.0f)
        reflectiveCurveToRelative(11.0f, -9.0f, 11.0f, -9.0f)
        reflectiveCurveToRelative(-3.0f, -17.0f, -3.0f, -27.0f)
        close()
        moveTo(197.5f, 286.5f)
        reflectiveCurveToRelative(12.0f, 3.0f, 14.0f, 13.0f)
        reflectiveCurveToRelative(16.0f, 4.0f, 16.0f, 4.0f)
        reflectiveCurveToRelative(4.0f, -1.0f, -2.0f, -15.0f)
        arcTo(31.19f, 31.19f, 0.0f, false, false, 207.19f, 271.0f)
        arcToRelative(29.16f, 29.16f, 0.0f, false, true, -5.19f, 9.56f)
        arcTo(32.56f, 32.56f, 0.0f, false, false, 197.5f, 286.5f)
        close()
        moveTo(117.31f, 275.57f)
        reflectiveCurveTo(103.5f, 282.5f, 100.5f, 301.5f)
        reflectiveCurveToRelative(17.0f, 0.0f, 17.0f, 0.0f)
        reflectiveCurveToRelative(1.85f, -8.67f, 8.92f, -12.34f)
        curveTo(126.42f, 289.16f, 118.13f, 278.64f, 117.31f, 275.57f)
        close()
    }
private val pathData24 =
    PathData {
        // Chest
        moveTo(116.5f, 272.83f)
        reflectiveCurveToRelative(-2.0f, -6.33f, 6.0f, -8.33f)
        reflectiveCurveToRelative(19.0f, -5.0f, 40.0f, -5.0f)
        reflectiveCurveToRelative(41.0f, 6.0f, 40.0f, 18.0f)
        arcToRelative(10.0f, 10.0f, 0.0f, false, true, -2.17f, 5.0f)

        // Joints
        moveTo(166.0f, 224.5f)
        reflectiveCurveToRelative(-2.06f, 3.0f, -9.25f, 3.0f)
        reflectiveCurveToRelative(-9.25f, -1.5f, -9.25f, -1.5f)
        moveTo(166.0f, 212.5f)
        reflectiveCurveToRelative(-2.06f, 3.0f, -9.25f, 3.0f)
        reflectiveCurveToRelative(-9.25f, -1.5f, -9.25f, -1.5f)
        moveTo(167.0f, 238.5f)
        reflectiveCurveToRelative(-2.06f, 3.0f, -9.25f, 3.0f)
        reflectiveCurveToRelative(-9.25f, -1.5f, -9.25f, -1.5f)
        moveTo(219.5f, 279.5f)
        reflectiveCurveToRelative(-11.86f, 4.53f, -11.93f, 12.76f)
        moveTo(228.5f, 296.5f)
        arcToRelative(15.7f, 15.7f, 0.0f, false, false, -17.0f, 4.0f)
        moveTo(109.0f, 283.0f)
        reflectiveCurveToRelative(7.48f, 11.61f, 11.58f, 11.55f)
        moveTo(102.0f, 296.0f)
        reflectiveCurveToRelative(6.9f, 7.76f, 11.5f, 6.47f)
    }
private val pathData25 =
    PathData {
        moveTo(159.17f, 205.5f)
        reflectiveCurveToRelative(-0.67f, 9.0f, 1.33f, 15.0f)
        reflectiveCurveToRelative(4.0f, 15.0f, 3.0f, 21.0f)
        reflectiveCurveToRelative(-1.0f, 12.74f, -3.0f, 13.87f)
        reflectiveCurveToRelative(8.0f, -0.87f, 9.0f, -8.87f)
        arcToRelative(168.89f, 168.89f, 0.0f, false, true, -3.0f, -28.92f)
        verticalLineTo(205.5f)
        close()
        moveTo(224.0f, 304.64f)
        lineToRelative(4.82f, -4.14f)
        reflectiveCurveToRelative(-2.14f, -16.5f, -11.0f, -23.0f)
        reflectiveCurveToRelative(-10.33f, -6.0f, -10.33f, -6.0f)
        lineToRelative(-3.27f, 6.0f)
        reflectiveCurveTo(222.47f, 286.77f, 224.0f, 304.64f)
        close()
        moveTo(119.5f, 283.5f)
        reflectiveCurveToRelative(-13.0f, 7.0f, -12.0f, 21.0f)
        reflectiveCurveToRelative(10.64f, -5.12f, 10.64f, -5.12f)
        reflectiveCurveToRelative(6.36f, -8.88f, 8.36f, -9.88f)
        lineToRelative(-5.5f, -7.0f)
        close()
    }
