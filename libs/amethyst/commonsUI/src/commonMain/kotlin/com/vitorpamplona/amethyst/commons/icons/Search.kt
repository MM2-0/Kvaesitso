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
package com.vitorpamplona.amethyst.commons.icons

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun VectorPreview() {
    Image(Search, null)
}

private var labelsSearch: ImageVector? = null

val Search: ImageVector
    get() =
        labelsSearch ?: Builder(
            name = "Search",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveTo(21.53f, 20.47f)
                lineToRelative(-3.66f, -3.66f)
                curveTo(19.195f, 15.24f, 20.0f, 13.214f, 20.0f, 11.0f)
                curveToRelative(0.0f, -4.97f, -4.03f, -9.0f, -9.0f, -9.0f)
                reflectiveCurveToRelative(-9.0f, 4.03f, -9.0f, 9.0f)
                reflectiveCurveToRelative(4.03f, 9.0f, 9.0f, 9.0f)
                curveToRelative(2.215f, 0.0f, 4.24f, -0.804f, 5.808f, -2.13f)
                lineToRelative(3.66f, 3.66f)
                curveToRelative(0.147f, 0.146f, 0.34f, 0.22f, 0.53f, 0.22f)
                reflectiveCurveToRelative(0.385f, -0.073f, 0.53f, -0.22f)
                curveToRelative(0.295f, -0.293f, 0.295f, -0.767f, 0.002f, -1.06f)
                close()
                moveTo(3.5f, 11.0f)
                curveToRelative(0.0f, -4.135f, 3.365f, -7.5f, 7.5f, -7.5f)
                reflectiveCurveToRelative(7.5f, 3.365f, 7.5f, 7.5f)
                reflectiveCurveToRelative(-3.365f, 7.5f, -7.5f, 7.5f)
                reflectiveCurveToRelative(-7.5f, -3.365f, -7.5f, -7.5f)
                close()
            }
        }.build()
            .also { labelsSearch = it }
