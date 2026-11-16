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
import com.vitorpamplona.amethyst.commons.robohash.Brown
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Mouth5MidOpen() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth5MidOpen(SolidColor(Color.Yellow), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth5MidOpen(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
}

private val pathData1 =
    PathData {
        moveTo(149.0f, 179.0f)
        reflectiveCurveToRelative(-12.0f, 2.0f, -15.0f, 2.0f)
        reflectiveCurveToRelative(-13.5f, 1.5f, -15.5f, 5.5f)
        reflectiveCurveToRelative(-1.0f, 11.5f, 8.0f, 10.5f)
        reflectiveCurveToRelative(12.0f, -1.5f, 20.0f, -2.5f)
        reflectiveCurveToRelative(13.0f, -4.0f, 14.0f, -6.0f)
        reflectiveCurveToRelative(0.0f, -5.0f, -2.0f, -7.0f)
        reflectiveCurveTo(154.5f, 178.5f, 149.0f, 179.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(149.0f, 179.0f)
        reflectiveCurveToRelative(-12.0f, 2.0f, -15.0f, 2.0f)
        reflectiveCurveToRelative(-11.5f, 0.5f, -14.5f, 4.5f)
        curveToRelative(-4.0f, 4.0f, -2.0f, 11.5f, 7.0f, 11.5f)
        curveToRelative(8.0f, 0.0f, 12.0f, -1.5f, 20.0f, -2.5f)
        reflectiveCurveToRelative(13.0f, -4.0f, 14.0f, -6.0f)
        reflectiveCurveToRelative(0.0f, -5.0f, -2.0f, -7.0f)
        reflectiveCurveTo(154.5f, 178.5f, 149.0f, 179.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(121.5f, 184.5f)
        reflectiveCurveToRelative(-3.0f, 4.0f, -1.0f, 6.0f)
        reflectiveCurveToRelative(4.0f, 3.0f, 12.0f, 2.0f)
        reflectiveCurveToRelative(13.0f, -3.0f, 18.0f, -3.0f)
        reflectiveCurveToRelative(10.83f, -2.26f, 9.42f, -6.13f)
        reflectiveCurveToRelative(-5.75f, -5.12f, -8.58f, -4.5f)
        reflectiveCurveToRelative(-12.7f, 1.53f, -16.26f, 2.08f)
        reflectiveCurveToRelative(-6.7f, -0.1f, -10.63f, 1.73f)
        reflectiveCurveTo(121.5f, 184.5f, 121.5f, 184.5f)
        close()
    }
