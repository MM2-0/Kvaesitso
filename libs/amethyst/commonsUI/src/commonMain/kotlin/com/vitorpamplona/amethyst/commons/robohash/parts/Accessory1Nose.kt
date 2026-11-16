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
fun Accessory1NosePreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory1Nose(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory1Nose(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData4, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(103.5f, 165.5f)
        reflectiveCurveToRelative(11.0f, -4.0f, 15.0f, -6.0f)
        arcToRelative(59.46f, 59.46f, 0.0f, false, true, 7.0f, -3.0f)
        reflectiveCurveToRelative(5.0f, 0.0f, 6.0f, 3.0f)
        reflectiveCurveToRelative(0.0f, 5.0f, -1.0f, 6.0f)
        reflectiveCurveToRelative(-23.0f, 8.0f, -23.0f, 8.0f)
        arcToRelative(4.45f, 4.45f, 0.0f, false, true, -5.0f, -2.0f)
        curveTo(99.5f, 166.5f, 103.5f, 165.5f, 103.5f, 165.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(102.0f, 167.0f)
        curveToRelative(-1.07f, 1.3f, 1.29f, 6.38f, 2.64f, 6.19f)
        reflectiveCurveToRelative(6.07f, 0.81f, 4.36f, -4.12f)
        curveTo(107.18f, 165.0f, 103.05f, 165.72f, 102.0f, 167.0f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(106.5f, 173.5f)
        curveToRelative(3.0f, -1.0f, 3.0f, -3.0f, 2.0f, -5.0f)
        curveToRelative(-3.0f, -4.0f, -6.0f, -2.0f, -6.0f, -2.24f)
        moveTo(109.1f, 163.4f)
        reflectiveCurveToRelative(4.4f, 0.1f, 5.4f, 3.1f)
        arcToRelative(5.22f, 5.22f, 0.0f, false, true, -0.78f, 5.0f)
    }
