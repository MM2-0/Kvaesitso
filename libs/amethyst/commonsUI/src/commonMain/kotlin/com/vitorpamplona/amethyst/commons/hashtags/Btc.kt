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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
fun CustomHashTagIconsBtcPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Btc,
            ),
        contentDescription = "",
        modifier = Modifier.size(55.dp),
    )
}

public val CustomHashTagIcons.Btc: ImageVector
    get() {
        if (customHashTagIconsBtc != null) {
            return customHashTagIconsBtc!!
        }
        customHashTagIconsBtc =
            Builder(
                name = "Btc",
                defaultWidth = 64.0.dp,
                defaultHeight = 64.0.dp,
                viewportWidth =
                64.0f,
                viewportHeight = 64.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFFf7931a)),
                    stroke = null,
                    strokeLineWidth = 1.57894f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(54.9248f, 25.7592f)
                    curveTo(55.9306f, 19.0361f, 50.8116f, 15.4219f, 43.8122f, 13.0109f)
                    lineTo(46.0827f, 3.9036f)
                    lineTo(40.5391f, 2.522f)
                    lineTo(38.3286f, 11.3893f)
                    curveToRelative(-1.4574f, -0.3632f, -2.9542f, -0.7058f, -4.4415f, -1.0453f)
                    lineToRelative(2.2263f, -8.9257f)
                    lineToRelative(-5.5405f, -1.3816f)
                    lineToRelative(-2.2721f, 9.1041f)
                    curveTo(27.0944f, 8.8662f, 25.9102f, 8.5946f, 24.7608f, 8.3088f)
                    lineToRelative(0.0063f, -0.0284f)
                    lineToRelative(-7.6452f, -1.9089f)
                    lineToRelative(-1.4747f, 5.921f)
                    curveToRelative(0.0f, 0.0f, 4.1131f, 0.9426f, 4.0263f, 1.001f)
                    curveToRelative(2.2452f, 0.5605f, 2.651f, 2.0463f, 2.5831f, 3.2242f)
                    lineToRelative(-2.5863f, 10.3752f)
                    curveToRelative(0.1547f, 0.0395f, 0.3553f, 0.0963f, 0.5763f, 0.1847f)
                    curveToRelative(-0.1847f, -0.0458f, -0.3821f, -0.0963f, -0.5858f, -0.1453f)
                    lineTo(16.0356f, 41.4665f)
                    curveToRelative(-0.2747f, 0.6821f, -0.971f, 1.7053f, -2.5405f, 1.3168f)
                    curveToRelative(0.0553f, 0.0805f, -4.0294f, -1.0058f, -4.0294f, -1.0058f)
                    lineToRelative(-2.7521f, 6.3457f)
                    lineToRelative(7.2142f, 1.7984f)
                    curveToRelative(1.3421f, 0.3363f, 2.6574f, 0.6884f, 3.9521f, 1.02f)
                    lineToRelative(-2.2942f, 9.2115f)
                    lineToRelative(5.5373f, 1.3816f)
                    lineToRelative(2.2721f, -9.1136f)
                    curveToRelative(1.5126f, 0.4105f, 2.981f, 0.7895f, 4.4179f, 1.1463f)
                    lineToRelative(-2.2642f, 9.071f)
                    lineToRelative(5.5436f, 1.3816f)
                    lineToRelative(2.2942f, -9.1941f)
                    curveToRelative(9.4531f, 1.7889f, 16.5615f, 1.0674f, 19.5535f, -7.4826f)
                    curveToRelative(2.411f, -6.8842f, -0.12f, -10.8552f, -5.0936f, -13.4446f)
                    curveToRelative(3.6221f, -0.8353f, 6.3505f, -3.2179f, 7.0784f, -8.1394f)
                    close()
                    moveTo(42.2585f, 43.5207f)
                    curveToRelative(-1.7131f, 6.8842f, -13.3041f, 3.1626f, -17.062f, 2.2295f)
                    lineToRelative(3.0442f, -12.2036f)
                    curveToRelative(3.7579f, 0.9379f, 15.8083f, 2.7947f, 14.0178f, 9.9741f)
                    close()
                    moveTo(43.9733f, 25.6597f)
                    curveToRelative(-1.5631f, 6.2621f, -11.2104f, 3.0805f, -14.3399f, 2.3005f)
                    lineToRelative(2.76f, -11.0683f)
                    curveToRelative(3.1295f, 0.78f, 13.2078f, 2.2358f, 11.5799f, 8.7678f)
                    close()
                }
            }.build()
        return customHashTagIconsBtc!!
    }

private var customHashTagIconsBtc: ImageVector? = null
