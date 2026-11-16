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
fun CustomHashTagIconsPlebsPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Plebs,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Plebs: ImageVector
    get() {
        if (customHashTagIconsPlebs != null) {
            return customHashTagIconsPlebs!!
        }
        customHashTagIconsPlebs =
            Builder(
                name = "Plebs",
                defaultWidth = 512.0.dp,
                defaultHeight = 512.0.dp,
                viewportWidth = 512.0f,
                viewportHeight = 512.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF226699)),
                    stroke = null,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(240.077f, 212.39f)
                    curveToRelative(36.868f, -40.178f, 42.776f, -59.05f, 41.127f, -100.9f)
                    curveTo(277.003f, 50.197f, 219.658f, -13.101f, 154.473f, 3.27f)
                    curveTo(98.991f, 29.166f, 78.406f, 76.673f, 87.059f, 130.375f)
                    curveToRelative(3.326f, 19.783f, 11.266f, 38.916f, 23.309f, 55.0f)
                    curveToRelative(3.8f, 5.075f, 7.79f, 10.561f, 13.105f, 14.15f)
                    curveToRelative(29.583f, 11.547f, 63.268f, 28.18f, 92.0f, 32.55f)
                    curveToRelative(49.403f, 5.055f, 16.317f, 4.066f, 24.603f, -19.685f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF55acee)),
                    stroke = null,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(267.474f, 229.375f)
                    curveToRelative(-25.724f, 6.127f, -53.175f, 4.359f, -76.0f, -3.344f)
                    curveToRelative(-20.816f, -7.201f, -41.714f, -15.31f, -62.0f, -23.887f)
                    curveToRelative(-13.664f, -5.778f, -26.841f, -12.787f, -42.0f, -12.769f)
                    curveToRelative(-10.835f, 0.013f, -25.743f, 2.166f, -33.787f, 10.213f)
                    curveToRelative(-12.713f, 12.718f, -10.628f, 39.822f, 0.116f, 52.786f)
                    curveToRelative(7.998f, 9.651f, 20.694f, 14.759f, 31.671f, 20.248f)
                    curveToRelative(23.899f, 11.95f, 48.7f, 21.898f, 73.0f, 32.984f)
                    curveToRelative(10.316f, 4.706f, 26.02f, 8.833f, 33.387f, 17.874f)
                    curveToRelative(8.391f, 10.296f, 7.94f, 31.972f, 8.203f, 44.715f)
                    curveToRelative(9.824f, 0.0f, 21.748f, -2.388f, 31.41f, -4.334f)
                    curveToRelative(25.64f, -5.163f, 48.792f, -18.568f, 71.0f, -31.886f)
                    curveToRelative(19.64f, -11.777f, 43.51f, -31.471f, 68.0f, -22.638f)
                    curveToRelative(21.493f, 7.752f, 29.192f, 33.234f, 20.099f, 53.038f)
                    curveToRelative(-4.477f, 9.75f, -12.742f, 16.526f, -21.099f, 22.87f)
                    curveToRelative(-47.953f, 36.402f, -106.388f, 61.13f, -167.0f, 61.13f)
                    verticalLineToRelative(44.0f)
                    curveToRelative(0.572f, 13.763f, -3.286f, 20.249f, 13.0f, 21.83f)
                    curveToRelative(77.697f, 0.656f, 162.39f, 0.17f, 231.0f, 0.17f)
                    curveToRelative(8.367f, 0.0f, 21.25f, 2.254f, 22.811f, -9.0f)
                    curveToRelative(2.183f, -15.737f, 0.189f, -33.106f, 0.189f, -49.0f)
                    curveToRelative(-0.454f, -63.006f, -2.273f, -108.366f, -12.6f, -160.0f)
                    curveToRelative(-2.129f, -10.578f, -4.935f, -21.419f, -13.44f, -28.848f)
                    curveToRelative(-6.41f, -5.599f, -18.729f, 2.108f, -25.96f, 4.103f)
                    curveToRelative(-19.393f, 5.349f, -53.736f, 8.081f, -62.79f, -15.255f)
                    curveToRelative(-9.333f, -24.054f, 13.943f, -39.798f, 28.789f, -54.039f)
                    curveToRelative(33.42f, -27.883f, 43.86f, -89.356f, 34.576f, -125.961f)
                    curveToRelative(-19.6f, -76.144f, -102.286f, -105.041f, -163.006f, -34.0f)
                    curveToRelative(-2.71f, 5.553f, 4.587f, 12.392f, 7.119f, 17.0f)
                    curveToRelative(7.798f, 14.19f, 12.877f, 29.076f, 15.697f, 45.0f)
                    curveToRelative(4.022f, 22.71f, 0.21f, 46.903f, -8.812f, 68.0f)
                    curveToRelative(-1.202f, 8.804f, -13.792f, 19.122f, -14.666f, 26.0f)
                    curveToRelative(-0.543f, 5.907f, 8.62f, 9.855f, 11.49f, 14.09f)
                    curveToRelative(3.233f, 4.77f, 1.603f, 13.383f, 1.603f, 18.91f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF226699)),
                    stroke = null,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(359.659f, 263.19f)
                    curveToRelative(19.058f, 20.658f, 79.633f, 9.792f, 95.853f, -13.816f)
                    curveToRelative(8.684f, -13.517f, 9.701f, -39.101f, -3.132f, -50.67f)
                    curveToRelative(-38.377f, -27.49f, -128.225f, 26.091f, -92.721f, 64.486f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF226699)),
                    stroke = null,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(202.93f, 505.36f)
                    curveToRelative(0.592f, -18.533f, -1.138f, -37.925f, -0.752f, -57.165f)
                    curveToRelative(14.334f, 0.0f, 28.111f, -2.78f, 42.295f, -5.144f)
                    curveToRelative(44.725f, -7.454f, 88.978f, -29.993f, 125.0f, -57.051f)
                    curveToRelative(8.087f, -6.074f, 16.01f, -12.398f, 20.623f, -21.625f)
                    curveToRelative(9.25f, -18.499f, 3.946f, -45.809f, -16.623f, -54.07f)
                    curveToRelative(-19.995f, -8.031f, -39.101f, 1.93f, -56.0f, 12.07f)
                    curveToRelative(-23.173f, 13.905f, -46.408f, 28.636f, -72.0f, 37.691f)
                    curveToRelative(-10.636f, 2.618f, -32.577f, 8.464f, -41.852f, 4.706f)
                    curveToRelative(-3.523f, -16.694f, -3.297f, -31.795f, -11.761f, -42.184f)
                    curveToRelative(-24.67f, -17.172f, -70.541f, -35.836f, -115.387f, -54.994f)
                    curveToRelative(-2.592f, -1.367f, -5.913f, -4.179f, -9.0f, -3.967f)
                    curveToRelative(-4.748f, 0.327f, -8.884f, 7.105f, -11.07f, 10.748f)
                    curveToRelative(-22.391f, 67.401f, -18.925f, 158.864f, -18.93f, 219.0f)
                    curveToRelative(0.219f, 13.623f, -0.386f, 18.984f, 15.0f, 19.0f)
                    horizontalLineToRelative(30.0f)
                    curveToRelative(23.685f, -4.025f, 130.22f, 11.249f, 120.457f, -7.015f)
                    close()
                }
            }.build()
        return customHashTagIconsPlebs!!
    }

private var customHashTagIconsPlebs: ImageVector? = null
