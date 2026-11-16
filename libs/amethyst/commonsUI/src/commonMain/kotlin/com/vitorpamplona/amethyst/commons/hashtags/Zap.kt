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
fun CustomHashTagIconsZapPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Zap,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Zap: ImageVector
    get() {
        if (customHashTagIconsZap != null) {
            return customHashTagIconsZap!!
        }
        customHashTagIconsZap =
            Builder(
                name = "Zap",
                defaultWidth = 512.0.dp,
                defaultHeight = 512.0.dp,
                viewportWidth = 512.0f,
                viewportHeight = 512.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFFfeb804)),
                    stroke = null,
                    strokeLineWidth = 1.04238f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(402.129f, 1.262f)
                    curveTo(394.807f, 11.794f, 388.681f, 19.608f, 383.521f, 29.039f)
                    curveTo(343.68f, 85.579f, 302.38f, 141.064f, 261.518f, 196.862f)
                    curveToRelative(-6.717f, 6.205f, -2.232f, 18.181f, 7.303f, 15.634f)
                    curveToRelative(15.682f, 3.42f, 21.453f, -10.422f, 21.453f, -10.422f)
                    curveToRelative(0.0f, 0.0f, 77.893f, -124.006f, 115.841f, -186.586f)
                    curveToRelative(2.589f, -5.525f, 4.24f, -14.798f, -3.986f, -14.226f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF9c59ff)),
                    stroke = null,
                    strokeLineWidth = 1.04238f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(130.421f, 270.872f)
                    lineToRelative(84.145f, -1.043f)
                    lineToRelative(23.28f, 8.339f)
                    lineToRelative(-22.137f, 38.786f)
                    lineToRelative(-109.766f, 188.455f)
                    lineToRelative(3.586f, 0.316f)
                    lineToRelative(279.741f, -251.785f)
                    lineToRelative(45.052f, -42.484f)
                    lineToRelative(-136.776f, 0.479f)
                    lineToRelative(-35.283f, -0.668f)
                    lineTo(402.443f, 1.112f)
                    curveToRelative(0.0f, 0.0f, -223.081f, 181.661f, -329.737f, 270.145f)
                    lineToRelative(-1.05f, 0.837f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFfeb804)),
                    stroke = null,
                    strokeLineWidth = 1.04238f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(105.973f, 505.407f)
                    curveToRelative(0.641f, 10.482f, 14.136f, 7.316f, 18.777f, 1.428f)
                    curveToRelative(110.113f, -98.518f, 227.418f, -195.827f, 322.374f, -283.083f)
                    curveToRelative(2.864f, -9.815f, -3.787f, -12.451f, -12.851f, -12.166f)
                    curveToRelative(-78.496f, 71.808f, -160.716f, 147.927f, -240.441f, 218.409f)
                    curveToRelative(-26.97f, 23.781f, -53.639f, 47.901f, -80.56f, 71.73f)
                    curveToRelative(-2.157f, 1.697f, -4.601f, 3.112f, -7.299f, 3.683f)
                    close()
                    moveTo(230.057f, 291.682f)
                    curveToRelative(6.731f, -10.679f, 15.607f, -23.143f, -0.042f, -21.833f)
                    curveToRelative(-52.452f, -0.003f, -100.964f, 0.787f, -149.966f, 2.256f)
                    curveToRelative(-7.988f, -0.012f, -8.925f, -2.348f, -12.914f, 9.06f)
                    curveToRelative(-4.908f, 14.035f, 13.177f, 11.664f, 21.968f, 11.597f)
                    curveToRelative(42.7f, -0.17f, 85.448f, 0.628f, 128.072f, -1.042f)
                    curveToRelative(4.996f, -0.006f, 7.714f, -0.11f, 12.882f, -0.037f)
                    close()
                }
            }.build()
        return customHashTagIconsZap!!
    }

private var customHashTagIconsZap: ImageVector? = null
