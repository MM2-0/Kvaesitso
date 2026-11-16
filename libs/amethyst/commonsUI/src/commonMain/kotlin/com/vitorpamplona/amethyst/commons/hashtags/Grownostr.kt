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
fun CustomHashTagIconsGrownostrPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Grownostr,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Grownostr: ImageVector
    get() {
        if (customHashTagIconsGrowNostr != null) {
            return customHashTagIconsGrowNostr!!
        }
        customHashTagIconsGrowNostr =
            Builder(
                name = "Grownostr",
                defaultWidth = 128.0.dp,
                defaultHeight = 128.0.dp,
                viewportWidth = 128.0f,
                viewportHeight = 128.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF6200ee)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(9.224f, 0.486f)
                    curveTo(32.035f, 17.55f, 41.727f, 30.892f, 55.972f, 46.502f)
                    curveTo(58.127f, 41.355f, 55.87f, 32.947f, 54.169f, 27.762f)
                    curveTo(47.608f, 7.761f, 28.73f, 0.498f, 9.224f, 0.486f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF6200ee)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(71.434f, 71.774f)
                    curveTo(80.152f, 58.93f, 119.83f, 33.376f, 120.809f, 24.042f)
                    curveTo(99.67f, 24.479f, 79.224f, 31.773f, 72.715f, 53.799f)
                    curveToRelative(-1.57f, 5.311f, -1.279f, 12.494f, -1.28f, 17.975f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF3700b3)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(106.897f, 34.151f)
                    curveTo(85.728f, 55.379f, 83.294f, 51.88f, 72.931f, 73.418f)
                    curveTo(99.006f, 72.295f, 124.096f, 52.448f, 120.96f, 24.204f)
                    curveToRelative(-6.391f, 2.874f, -10.261f, 5.064f, -14.063f, 9.946f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF3700b3)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(8.753f, 0.486f)
                    curveTo(9.992f, 16.183f, 11.87f, 33.641f, 26.582f, 42.19f)
                    curveToRelative(8.311f, 4.828f, 18.0f, 6.141f, 27.276f, 7.889f)
                    curveTo(51.373f, 34.848f, 52.381f, 36.195f, 23.789f, 11.482f)
                    curveTo(19.47f, 7.834f, 15.869f, 4.353f, 8.753f, 0.486f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF818181)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(32.781f, 4.205f)
                    lineToRelative(1.24f, 1.24f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF35b458)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(21.622f, 10.404f)
                    curveToRelative(1.727f, 1.778f, 2.769f, 2.535f, 4.959f, 3.72f)
                    curveToRelative(-2.998f, 0.895f, -5.545f, 1.175f, -8.679f, 1.24f)
                    verticalLineToRelative(3.72f)
                    curveToRelative(12.329f, 0.0f, 18.717f, 3.247f, 26.037f, 13.638f)
                    horizontalLineToRelative(-8.679f)
                    verticalLineToRelative(3.72f)
                    curveToRelative(3.974f, 0.004f, 8.635f, -0.583f, 11.99f, 1.987f)
                    curveToRelative(5.095f, 3.903f, 6.276f, 10.83f, 7.861f, 16.61f)
                    curveToRelative(4.072f, 14.857f, 6.186f, 30.48f, 6.186f, 45.874f)
                    curveToRelative(0.0f, 7.101f, -12.143f, 35.848f, 4.959f, 24.797f)
                    curveToRelative(2.201f, -11.686f, -0.793f, -24.101f, -0.55f, -34.716f)
                    curveToRelative(3.591f, -12.146f, 10.482f, -24.099f, 19.148f, -30.583f)
                    curveToRelative(4.709f, -1.594f, 11.079f, 2.021f, 12.398f, -4.132f)
                    lineTo(84.854f, 57.518f)
                    curveTo(92.594f, 48.012f, 98.343f, 42.675f, 110.891f, 42.64f)
                    verticalLineToRelative(-2.48f)
                    horizontalLineToRelative(-8.679f)
                    lineToRelative(4.959f, -6.199f)
                    curveTo(99.438f, 34.305f, 94.621f, 42.1f, 88.573f, 46.36f)
                    curveTo(88.312f, 42.964f, 88.199f, 42.117f, 84.854f, 41.4f)
                    curveTo(84.466f, 54.133f, 72.011f, 71.537f, 63.777f, 79.835f)
                    curveTo(63.682f, 68.403f, 61.36f, 55.477f, 56.361f, 45.12f)
                    curveTo(53.377f, 38.937f, 48.971f, 33.724f, 45.694f, 27.762f)
                    curveToRelative(-1.764f, -3.211f, -2.345f, -6.791f, -4.234f, -9.919f)
                    curveToRelative(-0.849f, 1.547f, -0.929f, 1.919f, -1.24f, 3.72f)
                    curveTo(34.743f, 17.118f, 28.995f, 10.143f, 21.622f, 10.404f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF818181)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(42.699f, 10.404f)
                    lineToRelative(1.24f, 1.24f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF818181)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(103.452f, 25.282f)
                    lineToRelative(1.24f, 1.24f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF01ff01)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(12.943f, 27.762f)
                    lineToRelative(1.24f, 1.24f)
                    lineToRelative(-1.24f, -1.24f)
                    moveToRelative(76.87f, 2.48f)
                    lineToRelative(1.24f, 1.24f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF818181)),
                    stroke = null,
                    strokeLineWidth = 1.23984f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(73.695f, 48.839f)
                    lineToRelative(1.24f, 1.24f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf99721)),
                    stroke = null,
                    strokeLineWidth = 1.18196f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(0.081f, 127.881f)
                    curveToRelative(9.289f, 0.0f, 23.307f, 0.004f, 33.058f, -0.192f)
                    curveToRelative(18.284f, -0.22f, 37.955f, -11.486f, 49.607f, -18.06f)
                    curveToRelative(-6.533f, -6.661f, -21.685f, -9.693f, -28.933f, -6.422f)
                    curveToRelative(-6.339f, 4.411f, -16.531f, 6.015f, -23.401f, 9.187f)
                    curveToRelative(-10.618f, 8.433f, -25.203f, 8.662f, -30.331f, 15.487f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF857f2d)),
                    stroke = null,
                    strokeLineWidth = 1.01399f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(61.97f, 101.518f)
                    lineToRelative(1.014f, 1.014f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffff01)),
                    stroke = null,
                    strokeLineWidth = 1.01399f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(51.83f, 105.574f)
                    lineToRelative(1.014f, 1.014f)
                    lineToRelative(-1.014f, -1.014f)
                    moveToRelative(23.322f, 0.0f)
                    lineToRelative(1.014f, 1.014f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFdf7f07)),
                    stroke = null,
                    strokeLineWidth = 1.19243f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(31.334f, 127.881f)
                    horizontalLineToRelative(96.757f)
                    curveToRelative(-5.648f, -7.803f, -12.978f, -6.398f, -22.037f, -10.163f)
                    curveToRelative(-9.516f, -3.956f, -14.557f, -9.591f, -27.043f, -7.551f)
                    curveToRelative(-9.571f, 1.564f, -14.284f, 7.555f, -22.675f, 10.005f)
                    curveToRelative(-9.678f, 2.827f, -17.397f, 1.076f, -25.003f, 7.709f)
                    close()
                }
            }.build()
        return customHashTagIconsGrowNostr!!
    }

private var customHashTagIconsGrowNostr: ImageVector? = null
