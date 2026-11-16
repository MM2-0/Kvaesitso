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
package com.vitorpamplona.amethyst.commons.hashtags

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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun CustomHashTagIconsTunestrPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Tunestr,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Tunestr: ImageVector
    get() {
        if (customHashTagIconsTunestr != null) {
            return customHashTagIconsTunestr!!
        }
        customHashTagIconsTunestr =
            Builder(
                name = "Tunestr",
                defaultWidth = 600.0.dp,
                defaultHeight = 600.0.dp,
                viewportWidth = 600.0f,
                viewportHeight = 600.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFFeb3c27)),
                    stroke = SolidColor(Color(0x00000000)),
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(171.93f, 82.63f)
                    lineTo(577.1f, 0.0f)
                    lineTo(577.1f, 112.99f)
                    lineTo(171.93f, 195.62f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFeb3c27)),
                    stroke = SolidColor(Color(0x00000000)),
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(171.93f, 82.54f)
                    lineTo(237.16f, 82.54f)
                    lineTo(237.16f, 504.91f)
                    lineTo(171.93f, 504.91f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFeb3c27)),
                    stroke = SolidColor(Color(0x00000000)),
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(235.04f, 488.2f)
                    curveTo(246.7f, 531.7f, 209.13f, 579.56f, 151.13f, 595.1f)
                    curveTo(93.13f, 610.64f, 36.67f, 587.98f, 25.01f, 544.48f)
                    curveTo(13.35f, 500.98f, 50.92f, 453.12f, 108.92f, 437.58f)
                    curveTo(166.92f, 422.04f, 223.38f, 444.7f, 235.04f, 488.2f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFeb3c27)),
                    stroke = SolidColor(Color(0x00000000)),
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(511.87f, 13.54f)
                    lineTo(577.1f, 13.54f)
                    lineTo(577.1f, 435.91f)
                    lineTo(511.87f, 435.91f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFeb3c27)),
                    stroke = SolidColor(Color(0x00000000)),
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(574.98f, 419.21f)
                    curveTo(586.64f, 462.71f, 549.07f, 510.57f, 491.07f, 526.11f)
                    curveTo(433.07f, 541.65f, 376.61f, 518.99f, 364.95f, 475.49f)
                    curveTo(353.29f, 431.99f, 390.86f, 384.13f, 448.86f, 368.59f)
                    curveTo(506.86f, 353.05f, 563.32f, 375.71f, 574.98f, 419.21f)
                    close()
                }
            }.build()
        return customHashTagIconsTunestr!!
    }

private var customHashTagIconsTunestr: ImageVector? = null
