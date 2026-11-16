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
fun CustomHashTagIconsLightningPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Lightning,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Lightning: ImageVector
    get() {
        if (customHashTagIconsLightning != null) {
            return customHashTagIconsLightning!!
        }
        customHashTagIconsLightning =
            Builder(
                name = "Lightning",
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
                    moveTo(405.814f, 0.318f)
                    curveTo(398.493f, 10.851f, 392.367f, 18.665f, 387.207f, 28.095f)
                    curveTo(347.366f, 84.635f, 306.066f, 140.12f, 265.203f, 195.919f)
                    curveToRelative(-6.717f, 6.205f, -2.232f, 18.181f, 7.303f, 15.634f)
                    curveToRelative(15.682f, 3.42f, 21.453f, -10.422f, 21.453f, -10.422f)
                    curveToRelative(0.0f, 0.0f, 77.893f, -124.006f, 115.841f, -186.586f)
                    curveToRelative(2.589f, -5.525f, 4.24f, -14.798f, -3.986f, -14.226f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffc927)),
                    stroke = null,
                    strokeLineWidth = 1.04238f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(134.106f, 269.928f)
                    lineToRelative(84.145f, -1.043f)
                    lineToRelative(23.28f, 8.339f)
                    lineToRelative(-22.137f, 38.786f)
                    lineToRelative(-109.766f, 188.455f)
                    lineToRelative(3.586f, 0.316f)
                    lineToRelative(279.741f, -251.785f)
                    lineToRelative(45.052f, -42.484f)
                    lineToRelative(-136.776f, 0.479f)
                    lineToRelative(-35.283f, -0.668f)
                    lineTo(406.128f, 0.168f)
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
                    moveToRelative(109.658f, 504.464f)
                    curveToRelative(0.641f, 10.482f, 14.136f, 7.316f, 18.777f, 1.428f)
                    curveToRelative(110.113f, -98.518f, 227.418f, -195.827f, 322.374f, -283.083f)
                    curveToRelative(2.864f, -9.815f, -3.787f, -12.451f, -12.851f, -12.166f)
                    curveToRelative(-78.496f, 71.808f, -160.716f, 147.927f, -240.441f, 218.409f)
                    curveToRelative(-26.97f, 23.781f, -53.639f, 47.901f, -80.56f, 71.73f)
                    curveToRelative(-2.157f, 1.697f, -4.601f, 3.112f, -7.299f, 3.683f)
                    close()
                    moveTo(233.742f, 290.738f)
                    curveToRelative(6.731f, -10.679f, 15.607f, -23.143f, -0.042f, -21.833f)
                    curveToRelative(-52.452f, -0.003f, -100.964f, 0.787f, -149.966f, 2.256f)
                    curveToRelative(-7.988f, -0.012f, -8.925f, -2.348f, -12.914f, 9.06f)
                    curveToRelative(-4.908f, 14.035f, 13.177f, 11.664f, 21.968f, 11.597f)
                    curveToRelative(42.7f, -0.17f, 85.448f, 0.628f, 128.072f, -1.042f)
                    curveToRelative(4.996f, -0.006f, 7.714f, -0.11f, 12.882f, -0.037f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffe567)),
                    stroke = null,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(295.999f, 97.743f)
                    curveToRelative(-8.582f, 4.871f, -15.257f, 12.447f, -22.999f, 18.528f)
                    curveToRelative(-35.757f, 30.897f, -71.357f, 61.981f, -107.631f, 92.275f)
                    curveTo(151.571f, 220.667f, 137.064f, 232.031f, 124.044f, 245.0f)
                    curveToRelative(-4.429f, 3.727f, -6.853f, 10.687f, -2.838f, 15.612f)
                    curveToRelative(5.438f, 7.373f, 15.928f, 3.727f, 21.794f, -1.137f)
                    curveToRelative(10.009f, -8.23f, 19.205f, -17.381f, 28.703f, -26.179f)
                    curveToRelative(10.022f, -9.859f, 19.614f, -20.178f, 30.297f, -29.335f)
                    curveToRelative(9.805f, -9.67f, 19.298f, -19.671f, 29.707f, -28.698f)
                    curveToRelative(9.648f, -9.595f, 19.134f, -19.361f, 29.354f, -28.349f)
                    curveToRelative(10.458f, -10.354f, 20.912f, -20.716f, 30.727f, -31.678f)
                    curveToRelative(3.954f, -4.612f, 10.405f, -8.489f, 10.761f, -15.087f)
                    curveToRelative(-0.53f, -3.162f, -4.126f, -3.243f, -6.55f, -2.405f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffd84b)),
                    stroke = null,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(265.0f, 273.668f)
                    curveToRelative(-10.032f, 4.073f, -14.755f, 14.571f, -20.726f, 22.922f)
                    curveToRelative(-7.915f, 11.998f, -15.59f, 24.152f, -23.688f, 36.032f)
                    curveToRelative(-6.853f, 10.398f, -13.505f, 20.926f, -20.449f, 31.265f)
                    curveToRelative(-7.068f, 11.266f, -14.611f, 22.226f, -21.705f, 33.476f)
                    curveToRelative(-4.087f, 6.761f, -9.122f, 13.119f, -11.751f, 20.637f)
                    curveToRelative(-1.054f, 3.042f, 1.146f, 7.25f, 4.719f, 6.307f)
                    curveToRelative(5.831f, -1.77f, 8.704f, -7.808f, 12.382f, -12.187f)
                    curveToRelative(29.287f, -39.128f, 58.731f, -78.141f, 87.916f, -117.344f)
                    curveToRelative(3.727f, -5.003f, 6.11f, -12.684f, 2.56f, -18.382f)
                    curveToRelative(-2.25f, -2.576f, -5.963f, -3.682f, -9.258f, -2.726f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffd84b)),
                    stroke = null,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(276.0f, 279.0f)
                    lineTo(276.0f, 285.0f)
                    lineTo(276.582f, 283.918f)
                    lineTo(276.628f, 283.502f)
                    lineTo(276.806f, 282.738f)
                    lineTo(276.806f, 281.262f)
                    lineTo(276.628f, 280.498f)
                    lineTo(276.582f, 280.082f)
                    lineTo(276.0f, 279.0f)
                    close()
                }
            }.build()
        return customHashTagIconsLightning!!
    }

private var customHashTagIconsLightning: ImageVector? = null
