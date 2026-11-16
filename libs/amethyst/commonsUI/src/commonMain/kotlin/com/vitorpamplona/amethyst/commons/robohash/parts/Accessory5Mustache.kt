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
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Preview
@Composable
fun Accessory5MustachePreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory5Mustache(SolidColor(Color.Yellow), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory5Mustache(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(125.5f, 157.5f)
        reflectiveCurveToRelative(8.0f, -4.0f, 15.0f, -3.0f)
        reflectiveCurveToRelative(20.0f, 8.0f, 26.0f, 5.0f)
        lineToRelative(8.0f, -4.0f)
        reflectiveCurveToRelative(-3.0f, 13.0f, -14.0f, 14.0f)
        reflectiveCurveToRelative(-29.0f, -3.0f, -35.0f, -2.0f)
        reflectiveCurveToRelative(-25.0f, 7.0f, -30.0f, 6.0f)
        reflectiveCurveToRelative(-10.0f, -1.0f, -12.0f, -11.0f)
        curveToRelative(0.0f, 0.0f, -1.0f, -3.0f, 3.0f, 0.0f)
        reflectiveCurveToRelative(11.0f, 4.0f, 18.0f, -1.0f)
        reflectiveCurveTo(119.5f, 153.5f, 125.5f, 157.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(125.5f, 157.5f)
        reflectiveCurveToRelative(8.0f, -4.0f, 15.0f, -3.0f)
        reflectiveCurveToRelative(20.0f, 8.0f, 26.0f, 5.0f)
        lineToRelative(8.0f, -4.0f)
        reflectiveCurveToRelative(-3.0f, 13.0f, -14.0f, 14.0f)
        reflectiveCurveToRelative(-29.0f, -3.0f, -35.0f, -2.0f)
        reflectiveCurveToRelative(-25.0f, 7.0f, -30.0f, 6.0f)
        reflectiveCurveToRelative(-10.0f, -1.0f, -12.0f, -11.0f)
        curveToRelative(0.0f, 0.0f, -1.0f, -3.0f, 3.0f, 0.0f)
        reflectiveCurveToRelative(11.0f, 4.0f, 18.0f, -1.0f)
        reflectiveCurveTo(119.5f, 153.5f, 125.5f, 157.5f)
        close()
    }
