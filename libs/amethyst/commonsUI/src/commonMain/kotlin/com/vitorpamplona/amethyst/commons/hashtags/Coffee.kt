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
fun CustomHashTagIconsCoffeePreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Coffee,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Coffee: ImageVector
    get() {
        if (customHashTagIconsCoffee != null) {
            return customHashTagIconsCoffee!!
        }
        customHashTagIconsCoffee =
            Builder(
                name = "Coffee",
                defaultWidth = 512.0.dp,
                defaultHeight = 512.0.dp,
                viewportWidth = 512.0f,
                viewportHeight = 512.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF00ffff)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(204.583f, 84.841f)
                    curveToRelative(1.808f, 1.808f, 0.933f, 0.933f, 0.0f, 0.0f)
                    close()
                    moveTo(366.274f, 85.89f)
                    curveToRelative(1.808f, 1.808f, 0.933f, 0.933f, 0.0f, 0.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFe0e0e0)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(195.829f, 118.866f)
                    curveTo(160.275f, 121.553f, 58.06f, 148.411f, 62.84f, 207.684f)
                    curveToRelative(0.811f, 23.285f, 2.035f, 46.555f, 5.059f, 69.667f)
                    curveTo(71.123f, 304.527f, 75.496f, 331.75f, 84.247f, 357.826f)
                    curveTo(99.454f, 396.095f, 133.387f, 425.805f, 173.084f, 436.368f)
                    curveToRelative(40.815f, 11.15f, 76.446f, 13.423f, 125.257f, -1.302f)
                    curveToRelative(37.884f, -10.354f, 71.389f, -38.361f, 83.885f, -76.191f)
                    curveToRelative(3.446f, -9.197f, 5.449f, -18.704f, 7.147f, -28.348f)
                    curveToRelative(20.026f, 1.025f, 40.624f, -1.266f, 59.847f, -7.378f)
                    curveToRelative(27.45f, -9.619f, 55.114f, -30.92f, 57.747f, -61.918f)
                    curveToRelative(2.014f, -19.218f, -3.317f, -39.883f, -18.899f, -52.411f)
                    curveTo(470.37f, 192.651f, 445.495f, 189.273f, 422.473f, 190.885f)
                    curveToRelative(-5.924f, -1.922f, -19.271f, 4.388f, -19.713f, -4.259f)
                    curveToRelative(-21.541f, -42.19f, -74.519f, -63.244f, -116.574f, -67.395f)
                    curveToRelative(-15.337f, -1.399f, -54.803f, -3.05f, -90.357f, -0.364f)
                    close()
                    moveTo(258.205f, 145.736f)
                    curveToRelative(35.707f, 4.411f, 74.758f, 9.193f, 102.819f, 33.628f)
                    curveToRelative(12.647f, 10.578f, 23.305f, 29.728f, 12.251f, 45.119f)
                    curveToRelative(-18.88f, 25.814f, -51.743f, 35.57f, -81.547f, 42.377f)
                    curveToRelative(-31.353f, 6.806f, -63.845f, 5.798f, -95.545f, 2.684f)
                    curveToRelative(-33.373f, -5.067f, -69.433f, -11.315f, -95.459f, -34.591f)
                    curveTo(90.48f, 225.01f, 85.348f, 208.691f, 94.105f, 196.134f)
                    curveTo(109.591f, 170.883f, 139.794f, 160.479f, 166.785f, 152.359f)
                    curveTo(177.505f, 149.627f, 188.334f, 146.966f, 199.333f, 145.737f)
                    moveToRelative(229.716f, 86.973f)
                    curveToRelative(8.709f, 0.053f, 17.284f, 0.926f, 25.221f, 4.741f)
                    curveToRelative(16.862f, 8.494f, 9.465f, 33.24f, -5.051f, 40.171f)
                    curveToRelative(-16.122f, 8.148f, -34.767f, 10.155f, -52.497f, 11.957f)
                    curveToRelative(1.831f, -18.914f, 4.279f, -37.818f, 6.3f, -56.697f)
                    curveToRelative(8.475f, 0.54f, 17.318f, -0.226f, 26.027f, -0.172f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF6d4b41)),
                    stroke = SolidColor(Color(0xFF6d4b41)),
                    strokeLineWidth = 8.81928f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(90.72f, 207.67f)
                    arcToRelative(141.919f, 61.231f, 0.0f, true, false, 283.838f, 0.0f)
                    arcToRelative(141.919f, 61.231f, 0.0f, true, false, -283.838f, 0.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFb59075)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(251.83f, 158.337f)
                    curveToRelative(-37.403f, 0.757f, -63.367f, 0.095f, -100.795f, 11.346f)
                    curveToRelative(-16.245f, 6.796f, -36.117f, 14.395f, -42.826f, 32.062f)
                    curveToRelative(4.78f, 30.851f, 60.554f, 22.125f, 56.067f, 2.789f)
                    curveToRelative(-4.141f, -14.164f, 14.984f, -19.744f, 25.607f, -15.895f)
                    curveToRelative(21.728f, 3.907f, 40.061f, 18.156f, 61.947f, 21.115f)
                    curveToRelative(8.814f, 1.936f, 20.571f, -2.794f, 17.762f, -13.62f)
                    curveToRelative(-3.874f, -6.982f, 0.155f, -14.741f, 8.487f, -14.612f)
                    curveToRelative(25.355f, -1.826f, 47.158f, 13.808f, 70.346f, 21.115f)
                    curveToRelative(7.046f, 3.956f, 15.783f, -3.153f, 8.545f, -9.653f)
                    curveToRelative(-28.173f, -23.147f, -72.369f, -35.312f, -105.14f, -34.648f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFb59074)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(293.915f, 199.809f)
                    curveToRelative(-6.744f, 4.634f, 0.262f, 16.203f, -8.579f, 20.246f)
                    curveToRelative(-16.21f, 10.232f, -35.697f, 0.44f, -52.405f, -3.622f)
                    curveToRelative(-14.159f, -3.75f, -29.518f, -10.912f, -44.098f, -6.124f)
                    curveToRelative(-10.455f, 5.464f, -3.973f, 21.891f, 7.35f, 19.424f)
                    curveToRelative(7.854f, -1.083f, 15.552f, -2.724f, 23.099f, 1.05f)
                    curveToRelative(22.268f, 6.96f, 45.971f, 11.514f, 69.296f, 8.721f)
                    curveToRelative(10.746f, -2.04f, 15.897f, -12.263f, 24.149f, -17.821f)
                    curveToRelative(10.272f, -2.429f, 22.449f, 12.795f, 30.507f, 1.75f)
                    curveToRelative(3.642f, -11.742f, -12.073f, -16.176f, -20.321f, -19.703f)
                    curveToRelative(-9.315f, -3.257f, -19.114f, -5.824f, -28.998f, -3.921f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF6aa5ac)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(403.022f, 222.383f)
                    curveToRelative(-0.027f, 0.814f, 0.014f, 9.727f, 0.052f, 10.476f)
                    curveToRelative(34.643f, -0.056f, 53.296f, 2.383f, 65.084f, 16.868f)
                    curveToRelative(4.078f, 5.361f, 7.465f, 10.616f, 10.15f, 8.407f)
                    curveToRelative(-1.559f, -19.676f, -20.181f, -34.582f, -36.438f, -36.161f)
                    curveToRelative(-12.802f, -1.147f, -26.08f, -1.131f, -38.848f, 0.408f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFbbced3)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(460.306f, 242.286f)
                    curveToRelative(10.121f, 17.211f, -6.218f, 36.214f, -22.955f, 40.032f)
                    curveToRelative(-13.308f, 3.429f, -26.932f, 6.237f, -40.629f, 7.262f)
                    curveToRelative(-1.512f, 4.605f, -7.203f, 17.175f, 1.161f, 15.749f)
                    curveToRelative(18.916f, -0.454f, 38.872f, -2.294f, 55.712f, -11.657f)
                    curveToRelative(13.424f, -7.047f, 22.474f, -20.882f, 24.652f, -35.775f)
                    curveToRelative(-5.446f, 2.157f, -11.704f, -14.707f, -17.942f, -15.61f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF97827b)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(113.238f, 244.432f)
                    curveToRelative(1.808f, 1.808f, 0.933f, 0.933f, 0.0f, 0.0f)
                    close()
                    moveTo(350.525f, 244.432f)
                    curveToRelative(1.808f, 1.808f, 0.933f, 0.933f, 0.0f, 0.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFa79691)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(345.275f, 247.582f)
                    curveToRelative(1.808f, 1.808f, 0.933f, 0.933f, 0.0f, 0.0f)
                    close()
                    moveTo(120.587f, 248.632f)
                    curveToRelative(1.808f, 1.808f, 0.933f, 0.933f, 0.0f, 0.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFbbced3)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(70.19f, 296.929f)
                    curveToRelative(0.63f, 13.988f, -1.646f, 28.325f, 2.1f, 41.998f)
                    curveToRelative(-28.14f, 7.44f, -58.322f, 24.801f, -66.817f, 54.597f)
                    curveToRelative(-8.199f, 25.343f, 9.633f, 50.004f, 29.111f, 64.51f)
                    curveToRelative(42.757f, 32.57f, 96.75f, 46.56f, 149.387f, 52.053f)
                    curveToRelative(25.565f, 3.024f, 51.567f, 2.3f, 77.309f, 1.671f)
                    curveToRelative(57.383f, -3.76f, 116.697f, -14.922f, 165.891f, -46.489f)
                    curveToRelative(24.111f, -15.167f, 48.186f, -40.127f, 45.789f, -70.696f)
                    curveToRelative(-3.387f, -27.595f, -31.449f, -41.659f, -55.239f, -48.998f)
                    curveToRelative(-6.858f, -2.016f, -13.845f, -4.048f, -20.999f, -4.549f)
                    curveToRelative(2.547f, -12.394f, 4.107f, -10.878f, -7.592f, -11.302f)
                    curveToRelative(-3.148f, 19.859f, -11.241f, 41.844f, -22.361f, 58.549f)
                    curveToRelative(-9.335f, 27.17f, -32.881f, 46.946f, -58.242f, 58.797f)
                    curveToRelative(41.174f, -5.651f, 76.469f, -60.671f, 83.995f, -91.345f)
                    curveToRelative(21.082f, 4.352f, 45.071f, 10.026f, 58.388f, 28.348f)
                    curveToRelative(9.974f, 15.413f, -3.736f, 32.736f, -13.882f, 44.068f)
                    curveToRelative(-102.215f, 85.133f, -293.119f, 73.315f, -394.849f, 9.366f)
                    curveToRelative(-11.24f, -12.658f, -26.761f, -34.252f, -13.844f, -51.488f)
                    curveToRelative(11.011f, -15.95f, 29.052f, -25.575f, 47.104f, -31.345f)
                    curveToRelative(12.867f, 37.173f, 39.595f, 70.253f, 75.071f, 87.903f)
                    curveToRelative(2.313f, -1.995f, -10.941f, -7.591f, -14.174f, -11.112f)
                    curveTo(123.074f, 420.237f, 108.45f, 408.223f, 102.359f, 391.483f)
                    curveTo(104.483f, 388.761f, 94.882f, 380.021f, 91.976f, 372.525f)
                    curveTo(78.856f, 349.743f, 75.034f, 323.229f, 70.948f, 297.746f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFe0e0e0)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(392.523f, 355.726f)
                    curveToRelative(-11.611f, 47.0f, -52.915f, 84.188f, -99.673f, 96.085f)
                    curveToRelative(-25.958f, 8.69f, -52.515f, 9.949f, -79.602f, 8.14f)
                    curveToRelative(-28.816f, -2.721f, -56.323f, -11.953f, -80.06f, -28.483f)
                    curveToRelative(-26.745f, -18.753f, -46.217f, -46.561f, -57.747f, -76.792f)
                    curveToRelative(-21.268f, 6.033f, -44.593f, 18.658f, -52.059f, 40.948f)
                    curveToRelative(-4.034f, 54.612f, 91.596f, 82.662f, 133.955f, 89.654f)
                    curveToRelative(55.068f, 8.766f, 123.339f, 7.499f, 172.191f, -3.471f)
                    curveToRelative(36.304f, -7.727f, 158.106f, -59.911f, 118.328f, -103.029f)
                    curveToRelative(-14.746f, -13.873f, -35.783f, -19.848f, -55.332f, -23.051f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF6aa5ac)),
                    stroke = null,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(366.723f, 388.627f)
                    curveToRelative(-25.239f, 35.357f, -71.86f, 51.221f, -113.533f, 54.886f)
                    curveToRelative(-24.65f, 3.208f, -48.923f, 0.367f, -73.095f, -5.062f)
                    curveToRelative(-29.288f, -7.565f, -59.668f, -27.713f, -79.046f, -51.061f)
                    curveToRelative(20.561f, 60.682f, 97.111f, 78.574f, 150.782f, 73.331f)
                    curveToRelative(45.365f, -4.706f, 103.34f, -25.754f, 114.893f, -72.093f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFb9cdd5)),
                    stroke = null,
                    fillAlpha = 0.632035f,
                    strokeLineWidth = 1.04994f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(335.914f, 14.7f)
                    curveToRelative(6.079f, 8.502f, 9.15f, 22.2f, -1.522f, 28.597f)
                    curveToRelative(-15.584f, 5.865f, -32.814f, 4.136f, -49.138f, 6.08f)
                    curveToRelative(-29.968f, 2.843f, -64.819f, 9.676f, -81.487f, 37.769f)
                    curveToRelative(-5.635f, 8.926f, -7.22f, 19.092f, -8.767f, 29.392f)
                    curveToRelative(-0.014f, 0.002f, -0.027f, 0.004f, -0.041f, 0.006f)
                    curveToRelative(-0.052f, 15.851f, 2.979f, 29.867f, 9.472f, 43.368f)
                    lineToRelative(-0.15f, 0.004f)
                    curveToRelative(8.964f, 13.262f, 19.474f, 29.828f, 35.825f, 35.985f)
                    curveToRelative(11.442f, 2.558f, 10.867f, -12.151f, 10.957f, -20.164f)
                    curveToRelative(-0.349f, -14.022f, 1.126f, -23.577f, 8.287f, -32.411f)
                    curveToRelative(-0.001f, -0.0f, -1.713f, 3.224f, -0.004f, 0.0f)
                    curveToRelative(1.709f, -3.224f, 6.5f, -8.891f, 11.209f, -13.746f)
                    curveToRelative(4.709f, -4.855f, 13.22f, -8.825f, 20.482f, -11.601f)
                    curveToRelative(7.262f, -2.776f, 72.306f, -24.482f, 85.154f, -42.383f)
                    curveTo(390.225f, 49.263f, 375.539f, 12.278f, 348.251f, 1.225f)
                    curveTo(338.856f, -2.773f, 332.82f, 9.412f, 335.914f, 14.7f)
                    close()
                }
            }.build()
        return customHashTagIconsCoffee!!
    }

private var customHashTagIconsCoffee: ImageVector? = null
