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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.robohash.Black
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Preview
@Composable
fun Accessory0SevenPreview() {
    Image(
        modifier = Modifier.size(10000.dp),
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory0Seven(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory0Seven(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
    builder.addPath(pathData7, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.5f)
}

private val pathData1 =
    PathData {
        moveTo(141.5f, 79.5f)
        reflectiveCurveToRelative(-1.0f, 11.0f, -1.0f, 17.0f)
        verticalLineToRelative(8.0f)
        reflectiveCurveToRelative(-11.0f, 3.0f, -15.0f, 3.0f)
        horizontalLineToRelative(-10.0f)
        reflectiveCurveToRelative(-1.0f, -11.0f, -1.0f, -15.0f)
        reflectiveCurveToRelative(-2.0f, -10.0f, 6.0f, -13.0f)
        arcToRelative(138.0f, 138.0f, 0.0f, false, true, 14.0f, -1.0f)
        curveTo(140.5f, 78.5f, 141.5f, 79.5f, 141.5f, 79.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(116.5f, 106.5f)
        reflectiveCurveToRelative(22.0f, -2.67f, 24.0f, -3.33f)
        verticalLineToRelative(1.33f)
        reflectiveCurveToRelative(-11.42f, 2.74f, -13.21f, 2.87f)
        reflectiveCurveToRelative(-11.79f, 0.13f, -11.79f, 0.13f)
        verticalLineToRelative(-0.94f)
        lineToRelative(1.0f, -0.06f)
    }
private val pathData7 =
    PathData {
        moveTo(117.0f, 88.0f)
        horizontalLineToRelative(3.0f)
        arcToRelative(3.49f, 3.49f, 0.0f, false, true, 2.0f, -1.0f)
        curveToRelative(1.0f, 0.0f, 9.5f, -1.5f, 9.5f, -1.5f)
        reflectiveCurveToRelative(-9.0f, 7.0f, -10.0f, 13.0f)
        verticalLineToRelative(4.0f)
        lineToRelative(7.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, -6.0f, 2.0f, -10.0f)
        arcToRelative(22.28f, 22.28f, 0.0f, false, true, 7.0f, -6.0f)
        verticalLineToRelative(-3.0f)
        lineToRelative(-1.0f, -1.0f)
        lineToRelative(-18.0f, 2.0f)
        lineToRelative(-1.0f, 1.0f)
        close()
    }
