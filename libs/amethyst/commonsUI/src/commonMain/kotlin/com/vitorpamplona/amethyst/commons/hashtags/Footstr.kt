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
fun CustomHashTagIconsFootstrPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Footstr,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Footstr: ImageVector
    get() {
        if (customHashTagIconsFootStr != null) {
            return customHashTagIconsFootStr!!
        }
        customHashTagIconsFootStr =
            Builder(
                name = "Footstr",
                defaultWidth = 236.0.dp,
                defaultHeight = 236.0.dp,
                viewportWidth = 236.0f,
                viewportHeight = 236.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFFe102c2)),
                    stroke = null,
                    strokeLineWidth = 1.16731f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(172.024f, 18.47f)
                    lineTo(184.052f, 17.337f)
                    curveTo(183.463f, 10.976f, 181.095f, 5.502f, 177.469f, 0.069f)
                    moveTo(192.472f, 156.68f)
                    curveToRelative(15.586f, 0.841f, 18.539f, 6.439f, 27.813f, -9.063f)
                    curveToRelative(8.364f, -13.977f, 2.988f, -28.3f, 6.501f, -43.049f)
                    curveToRelative(1.282f, -5.383f, 3.715f, -10.25f, 4.063f, -15.86f)
                    curveToRelative(0.334f, -5.423f, -1.494f, -10.524f, -2.089f, -15.86f)
                    curveToRelative(-0.564f, -5.045f, -0.376f, -10.678f, -5.273f, -14.038f)
                    curveToRelative(-5.438f, -3.733f, -9.84f, 1.109f, -10.437f, 6.11f)
                    curveToRelative(-0.582f, 4.874f, 0.226f, 9.842f, -0.256f, 14.726f)
                    curveToRelative(-0.422f, 4.273f, -1.943f, 8.124f, -2.05f, 12.462f)
                    curveToRelative(-0.084f, 3.439f, -1.441f, 8.999f, -6.245f, 8.999f)
                    curveToRelative(-8.641f, 0.0f, -6.692f, -13.224f, -7.853f, -18.062f)
                    curveToRelative(-2.124f, -8.857f, -5.552f, -16.829f, -6.371f, -26.056f)
                    curveToRelative(-0.605f, -6.811f, 1.684f, -13.506f, 0.771f, -20.392f)
                    curveToRelative(-0.618f, -4.663f, -2.976f, -11.245f, -8.249f, -12.861f)
                    curveToRelative(-7.27f, -2.228f, -10.639f, 5.207f, -10.751f, 10.596f)
                    curveToRelative(-0.119f, 5.818f, 2.058f, 11.23f, 2.336f, 16.993f)
                    curveToRelative(0.445f, 9.246f, 1.175f, 18.807f, 1.322f, 27.999f)
                    curveToRelative(0.051f, 3.174f, -1.301f, 6.191f, -1.097f, 9.387f)
                    curveToRelative(0.304f, 4.758f, 7.302f, 11.044f, 3.842f, 15.448f)
                    curveToRelative(-4.18f, 5.317f, -10.196f, -0.538f, -12.803f, -4.121f)
                    curveToRelative(-5.162f, -7.099f, -8.576f, -15.319f, -13.304f, -22.635f)
                    curveToRelative(-2.224f, -3.438f, -5.693f, -6.276f, -10.262f, -5.173f)
                    curveToRelative(-12.213f, 2.948f, -2.336f, 18.13f, 1.648f, 23.277f)
                    curveToRelative(10.468f, 13.526f, 19.591f, 27.625f, 30.784f, 40.783f)
                    curveToRelative(6.229f, 7.322f, 15.101f, 11.077f, 17.959f, 20.392f)
                    moveToRelative(25.259f, -125.748f)
                    curveToRelative(1.05f, 7.343f, 2.406f, 14.051f, 2.406f, 21.524f)
                    lineToRelative(9.622f, 3.399f)
                    curveToRelative(-0.44f, -9.512f, -3.839f, -18.966f, -12.028f, -24.923f)
                    moveToRelative(-80.587f, 15.86f)
                    curveToRelative(-4.545f, 7.606f, -6.269f, 16.447f, -3.608f, 24.923f)
                    curveToRelative(3.228f, -1.535f, 6.071f, -2.76f, 9.622f, -3.399f)
                    curveToRelative(-1.064f, -6.786f, -3.047f, -15.302f, -6.014f, -21.524f)
                    moveToRelative(-78.182f, 1.133f)
                    curveToRelative(-1.517f, 6.905f, -3.735f, 13.678f, -6.014f, 20.392f)
                    lineTo(66.178f, 69.449f)
                    curveTo(63.783f, 62.514f, 62.59f, 54.306f, 58.961f, 47.925f)
                    moveTo(45.731f, 207.659f)
                    curveToRelative(2.62f, -8.546f, 8.778f, -9.623f, 13.924f, -15.984f)
                    curveToRelative(8.239f, -10.187f, 17.116f, -20.089f, 25.003f, -30.464f)
                    curveToRelative(3.56f, -4.686f, 6.036f, -10.011f, 9.622f, -14.727f)
                    curveToRelative(3.915f, -5.147f, 14.26f, -20.373f, 1.903f, -23.277f)
                    curveToRelative(-4.614f, -1.084f, -8.062f, 1.695f, -10.322f, 5.173f)
                    curveToRelative(-4.74f, 7.292f, -8.192f, 15.572f, -13.305f, 22.635f)
                    curveToRelative(-2.521f, 3.482f, -7.669f, 8.817f, -11.941f, 4.298f)
                    curveToRelative(-4.307f, -4.555f, 2.592f, -10.65f, 3.011f, -15.626f)
                    curveToRelative(0.277f, -3.275f, -1.021f, -6.315f, -1.066f, -9.543f)
                    curveToRelative(-0.179f, -12.863f, 1.339f, -26.465f, 3.26f, -39.17f)
                    curveToRelative(0.915f, -6.054f, -0.085f, -19.425f, -10.413f, -16.26f)
                    curveToRelative(-5.273f, 1.617f, -7.629f, 8.197f, -8.249f, 12.861f)
                    curveToRelative(-0.811f, 6.116f, 1.066f, 12.084f, 0.589f, 18.126f)
                    curveToRelative(-0.796f, 10.107f, -4.172f, 18.642f, -6.357f, 28.322f)
                    curveToRelative(-1.105f, 4.896f, 1.127f, 17.891f, -7.686f, 17.891f)
                    curveToRelative(-5.485f, 0.0f, -6.35f, -6.214f, -6.357f, -9.961f)
                    curveToRelative(-0.007f, -4.018f, -1.955f, -7.399f, -2.287f, -11.329f)
                    curveToRelative(-0.413f, -4.87f, 0.609f, -9.83f, 0.153f, -14.726f)
                    curveToRelative(-0.437f, -4.689f, -4.81f, -9.935f, -10.285f, -6.762f)
                    curveToRelative(-5.149f, 2.986f, -4.939f, 9.806f, -5.485f, 14.691f)
                    curveToRelative(-0.552f, 4.945f, -2.241f, 9.713f, -2.136f, 14.727f)
                    curveToRelative(0.117f, 5.621f, 2.272f, 10.533f, 3.802f, 15.86f)
                    curveToRelative(4.381f, 15.243f, -1.813f, 29.587f, 7.01f, 44.182f)
                    curveToRelative(2.41f, 3.988f, 5.784f, 9.177f, 10.775f, 10.539f)
                    curveToRelative(4.794f, 1.31f, 11.713f, -1.435f, 16.838f, -1.476f)
                    moveTo(19.269f, 81.911f)
                    curveTo(12.507f, 89.006f, 8.661f, 97.251f, 8.444f, 106.834f)
                    curveToRelative(3.191f, -1.408f, 6.204f, -2.592f, 9.622f, -3.399f)
                    curveToRelative(0.0f, -6.502f, 3.112f, -15.605f, 1.203f, -21.524f)
                    moveToRelative(80.587f, 15.86f)
                    curveToRelative(-0.331f, 7.133f, -3.121f, 13.512f, -4.811f, 20.392f)
                    curveToRelative(3.415f, 1.23f, 6.452f, 2.819f, 9.622f, 4.531f)
                    curveToRelative(2.546f, -8.112f, 1.766f, -18.722f, -4.811f, -24.923f)
                    moveToRelative(92.745f, 69.945f)
                    curveToRelative(-12.846f, 5.17f, -0.975f, 22.186f, 10.583f, 17.445f)
                    curveToRelative(14.948f, -6.131f, 2.896f, -22.87f, -10.583f, -17.445f)
                    moveToRelative(-9.752f, 20.684f)
                    curveToRelative(0.0f, 7.665f, -0.565f, 15.042f, -1.203f, 22.657f)
                    curveToRelative(5.293f, -5.307f, 10.001f, -12.128f, 14.434f, -18.126f)
                    curveToRelative(-4.675f, -1.022f, -8.892f, -2.614f, -13.231f, -4.531f)
                    moveTo(34.912f, 218.458f)
                    curveToRelative(-13.565f, 3.164f, -4.322f, 20.77f, 7.211f, 17.989f)
                    curveToRelative(7.881f, -1.9f, 12.632f, -11.136f, 5.51f, -16.777f)
                    curveToRelative(-3.211f, -2.542f, -8.913f, -2.1f, -12.721f, -1.212f)
                }
            }.build()
        return customHashTagIconsFootStr!!
    }

private var customHashTagIconsFootStr: ImageVector? = null
