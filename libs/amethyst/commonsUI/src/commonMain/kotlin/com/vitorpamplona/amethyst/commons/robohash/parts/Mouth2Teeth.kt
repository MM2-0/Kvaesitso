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
import com.vitorpamplona.amethyst.commons.robohash.Black
import com.vitorpamplona.amethyst.commons.robohash.DarkYellow
import com.vitorpamplona.amethyst.commons.robohash.OrangeOne
import com.vitorpamplona.amethyst.commons.robohash.OrangeTwo
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Mouth2Teeth() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth2Teeth(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth2Teeth(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 2.0f)
    builder.addPath(pathData3, fill = OrangeOne)
    builder.addPath(pathData4, fill = OrangeTwo)
    builder.addPath(pathData5, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)
    builder.addPath(pathData6, fill = DarkYellow, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(170.5f, 168.5f)
        reflectiveCurveTo(151.0f, 177.0f, 141.0f, 179.0f)
        lineToRelative(-1.0f, 0.2f)
        arcToRelative(112.0f, 112.0f, 0.0f, false, true, -32.68f, 1.74f)
        quadToRelative(-1.88f, -0.18f, -3.82f, -0.44f)
        lineToRelative(6.0f, 20.0f)
        arcToRelative(51.86f, 51.86f, 0.0f, false, false, 26.0f, 5.0f)
        curveToRelative(15.0f, -1.0f, 35.0f, -7.0f, 41.0f, -15.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(118.0f, 181.0f)
        reflectiveCurveToRelative(3.13f, 7.7f, 4.06f, 9.85f)
        reflectiveCurveToRelative(2.31f, 5.09f, 2.12f, 7.12f)
        horizontalLineToRelative(0.0f)
        lineToRelative(0.19f, 4.0f)
        arcToRelative(72.8f, 72.8f, 0.0f, false, true, 7.93f, 0.57f)
        curveTo(135.0f, 203.0f, 144.0f, 201.0f, 144.0f, 201.0f)
        arcToRelative(14.36f, 14.36f, 0.0f, false, true, -0.15f, -3.0f)
        curveToRelative(0.15f, -1.0f, -3.27f, -16.16f, -3.27f, -16.16f)
        lineToRelative(-1.0f, -2.27f)
        lineTo(135.0f, 180.0f)
        lineToRelative(-9.08f, 1.13f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(155.48f, 174.52f)
        lineTo(159.0f, 185.15f)
        lineToRelative(2.0f, 9.85f)
        verticalLineToRelative(2.0f)
        reflectiveCurveToRelative(10.25f, -4.55f, 12.63f, -6.27f)
        lineTo(176.0f, 189.0f)
        lineToRelative(-3.63f, -13.65f)
        lineToRelative(-1.85f, -6.79f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(110.5f, 199.5f)
        lineToRelative(2.0f, -2.0f)
        reflectiveCurveToRelative(-3.0f, -10.0f, -4.0f, -13.0f)
        reflectiveCurveToRelative(-1.87f, -3.63f, -1.87f, -3.63f)
        lineToRelative(-3.13f, -0.37f)
        lineToRelative(3.38f, 11.26f)
        lineToRelative(2.5f, 8.33f)
        close()

        moveTo(113.5f, 198.5f)
        reflectiveCurveToRelative(2.0f, 3.0f, 14.0f, 4.0f)
        reflectiveCurveToRelative(35.0f, -6.0f, 35.0f, -6.0f)
        reflectiveCurveTo(174.56f, 191.0f, 176.0f, 188.77f)
        lineToRelative(0.47f, 1.73f)
        reflectiveCurveToRelative(-7.38f, 11.0f, -40.69f, 15.0f)
        curveToRelative(0.0f, 0.0f, -16.31f, 1.0f, -26.31f, -5.0f)
        lineToRelative(3.0f, -3.0f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(119.94f, 181.44f)
        reflectiveCurveToRelative(2.56f, 10.06f, 3.56f, 13.06f)
        arcToRelative(27.84f, 27.84f, 0.0f, false, true, 0.88f, 7.67f)
        reflectiveCurveToRelative(-7.88f, 0.33f, -11.88f, -4.67f)
        lineToRelative(-5.0f, -15.0f)
        arcToRelative(5.38f, 5.38f, 0.0f, false, false, -1.0f, -1.7f)
        arcTo(80.36f, 80.36f, 0.0f, false, false, 119.94f, 181.44f)
        close()
        moveTo(140.5f, 181.5f)
        curveToRelative(0.0f, 0.14f, 4.14f, 18.0f, 3.56f, 19.5f)
        curveToRelative(0.0f, 0.0f, 15.93f, -2.76f, 17.18f, -4.13f)
        curveToRelative(0.0f, 0.0f, -1.75f, -10.37f, -2.75f, -13.37f)
        reflectiveCurveToRelative(-3.0f, -9.0f, -3.0f, -9.0f)
        reflectiveCurveToRelative(-7.82f, 3.0f, -10.4f, 3.47f)
        arcToRelative(54.09f, 54.09f, 0.0f, false, false, -5.58f, 1.51f)
        arcTo(6.0f, 6.0f, 0.0f, false, true, 140.5f, 181.5f)
        close()
    }
