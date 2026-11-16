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
fun Body5NeckPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body5Neck(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun body5Neck(
    fgColor: SolidColor,
    builder: Builder,
) {
    // Body
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // Neck circle
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)

    // Neck
    builder.addPath(pathData9, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // Shades
    builder.addPath(pathData4, fill = Black, fillAlpha = 0.2f)

    // Joints
    builder.addPath(pathData11, stroke = Black, strokeLineWidth = 1.0f)

    // Body shade
    builder.addPath(pathData17, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(145.5f, 301.5f)
        reflectiveCurveToRelative(-1.0f, -18.0f, -1.0f, -23.0f)
        curveToRelative(0.0f, 0.0f, 0.37f, -3.41f, 15.68f, -2.2f)
        reflectiveCurveToRelative(10.15f, -0.26f, 10.15f, -0.26f)
        reflectiveCurveToRelative(5.17f, -0.54f, 5.17f, 2.46f)
        reflectiveCurveToRelative(0.0f, 19.0f, 3.0f, 23.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(159.5f, 286.5f)
        reflectiveCurveToRelative(16.0f, -1.0f, 16.0f, -8.0f)
        curveToRelative(0.0f, 0.0f, 4.0f, -3.0f, -14.0f, -2.0f)
        horizontalLineToRelative(-12.0f)
        reflectiveCurveToRelative(-5.0f, 0.0f, -5.0f, 2.0f)
        reflectiveCurveTo(143.5f, 285.5f, 159.5f, 286.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(167.5f, 282.5f)
        lineToRelative(4.79f, 0.67f)
        lineToRelative(0.55f, 18.33f)
        horizontalLineToRelative(5.66f)
        reflectiveCurveToRelative(-2.64f, -3.0f, -2.82f, -15.0f)
        lineToRelative(-0.18f, -8.0f)
        reflectiveCurveToRelative(-1.0f, -3.0f, -4.0f, -2.5f)
        verticalLineToRelative(3.5f)
        arcTo(6.93f, 6.93f, 0.0f, false, true, 167.5f, 282.5f)
        close()
        moveTo(161.5f, 200.5f)
        reflectiveCurveToRelative(0.0f, 20.0f, 1.0f, 27.0f)
        reflectiveCurveToRelative(3.0f, 24.0f, 4.0f, 35.0f)
        arcToRelative(166.5f, 166.5f, 0.0f, false, true, 0.61f, 20.0f)
        reflectiveCurveToRelative(4.39f, -2.0f, 4.39f, -3.0f)
        reflectiveCurveToRelative(-1.38f, -41.67f, -1.69f, -48.33f)
        reflectiveCurveToRelative(-0.31f, -31.67f, -0.31f, -31.67f)
        horizontalLineToRelative(-8.0f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(146.0f, 200.0f)
        reflectiveCurveToRelative(3.0f, 68.0f, 2.0f, 79.0f)
        curveToRelative(0.0f, 0.0f, 2.0f, 6.0f, 12.0f, 5.0f)
        reflectiveCurveToRelative(11.5f, -4.5f, 11.5f, -4.5f)
        reflectiveCurveToRelative(-3.0f, -75.0f, -2.0f, -80.0f)
        curveTo(169.5f, 199.5f, 151.5f, 199.5f, 146.0f, 200.0f)
        close()
    }
private val pathData11 =
    PathData {
        moveTo(146.43f, 210.28f)
        arcToRelative(31.28f, 31.28f, 0.0f, false, false, 13.07f, 3.22f)
        curveToRelative(7.0f, 0.0f, 9.83f, -3.22f, 9.83f, -3.22f)
        moveTo(147.43f, 222.28f)
        arcToRelative(31.28f, 31.28f, 0.0f, false, false, 13.07f, 3.22f)
        curveToRelative(7.0f, 0.0f, 9.83f, -3.22f, 9.83f, -3.22f)
        moveTo(147.43f, 234.28f)
        arcToRelative(31.28f, 31.28f, 0.0f, false, false, 13.07f, 3.22f)
        curveToRelative(7.0f, 0.0f, 9.83f, -3.22f, 9.83f, -3.22f)
        moveTo(147.43f, 246.28f)
        arcToRelative(31.28f, 31.28f, 0.0f, false, false, 13.07f, 3.22f)
        curveToRelative(7.0f, 0.0f, 9.83f, -3.22f, 9.83f, -3.22f)
        moveTo(148.43f, 259.28f)
        arcToRelative(31.28f, 31.28f, 0.0f, false, false, 13.07f, 3.22f)
        curveToRelative(7.0f, 0.0f, 9.83f, -3.22f, 9.83f, -3.22f)
        moveTo(148.43f, 271.28f)
        arcToRelative(31.28f, 31.28f, 0.0f, false, false, 13.07f, 3.22f)
        curveToRelative(7.0f, 0.0f, 9.83f, -3.22f, 9.83f, -3.22f)
    }
private val pathData17 =
    PathData {
        moveTo(144.5f, 281.5f)
        lineToRelative(2.0f, 21.0f)
        lineToRelative(33.0f, -1.0f)
        reflectiveCurveToRelative(-4.0f, -6.0f, -4.0f, -23.0f)
        curveToRelative(0.0f, 0.0f, -1.0f, 8.0f, -15.0f, 8.0f)
        reflectiveCurveTo(145.5f, 282.5f, 144.5f, 281.5f)
        close()
    }
