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
fun CustomHashTagIconsFlowerstrPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Flowerstr,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Flowerstr: ImageVector
    get() {
        if (customHashTagIconsFlowerstr != null) {
            return customHashTagIconsFlowerstr!!
        }
        customHashTagIconsFlowerstr =
            Builder(
                name = "Flowerstr",
                defaultWidth = 1145.dp,
                defaultHeight = 1780.dp,
                viewportWidth = 1145f,
                viewportHeight = 1780f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF42963c)),
                    stroke = SolidColor(Color(0xFF306d2c)),
                    strokeLineWidth = 0.689972f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(602.8f, 1767.7f)
                    curveToRelative(-58.2f, 4.7f, -59.2f, -13.3f, -47.2f, -37.5f)
                    curveToRelative(23.1f, -147.5f, 12.0f, -295.5f, -6.0f, -443.0f)
                    curveToRelative(-11.0f, -111.5f, -14.1f, -222.9f, -11.0f, -334.9f)
                    curveToRelative(42.2f, -2.8f, 67.2f, 1.9f, 52.2f, 25.1f)
                    curveToRelative(-5.0f, 158.4f, 12.0f, 316.8f, 28.1f, 474.3f)
                    curveToRelative(6.0f, 104.8f, 1.0f, 210.1f, -16.1f, 314.9f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF42963c)),
                    stroke = SolidColor(Color(0xFF306d2c)),
                    strokeLineWidth = 0.789335f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(592.0f, 1497.3f)
                    curveToRelative(17.0f, 82.0f, 31.7f, 166.2f, -1.1f, 247.7f)
                    curveToRelative(-12.5f, 55.6f, -117.7f, 21.5f, -140.4f, -12.1f)
                    arcTo(3489.7f, 1697.0f, 0.0f, false, true, 168.6f, 1370.2f)
                    curveTo(81.5f, 1212.7f, 15.8f, 1050.9f, 2.2f, 887.4f)
                    curveTo(-5.7f, 861.6f, 12.4f, 787.3f, 78.1f, 836.8f)
                    curveTo(181.1f, 922.7f, 247.8f, 1017.3f, 328.2f, 1108.7f)
                    curveToRelative(99.6f, 122.7f, 217.3f, 244.4f, 260.3f, 375.9f)
                    lineToRelative(2.3f, 6.6f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF42963c)),
                    stroke = SolidColor(Color(0xFF306d2c)),
                    strokeLineWidth = 0.766261f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(587.0f, 1501.8f)
                    curveToRelative(-16.0f, 82.2f, -29.8f, 166.7f, 1.1f, 248.4f)
                    curveToRelative(11.7f, 55.7f, 110.6f, 21.5f, 131.9f, -12.1f)
                    curveToRelative(122.3f, -113.7f, 195.7f, -239.0f, 264.9f, -363.7f)
                    curveToRelative(81.9f, -157.9f, 143.6f, -320.1f, 156.4f, -484.1f)
                    curveToRelative(7.4f, -25.9f, -9.6f, -100.5f, -71.3f, -50.8f)
                    curveToRelative(-96.8f, 86.1f, -159.6f, 181.0f, -235.1f, 272.7f)
                    curveToRelative(-93.6f, 123.1f, -204.2f, 245.1f, -244.7f, 377.0f)
                    lineToRelative(-2.1f, 6.6f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF9545db)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(270.0f, 923.0f)
                    curveToRelative(95.0f, 74.0f, 223.0f, 73.0f, 337.0f, 67.0f)
                    curveToRelative(102.0f, -3.0f, 222.0f, -12.0f, 290.0f, -98.0f)
                    curveToRelative(90.0f, -111.0f, 98.0f, -265.0f, 76.0f, -401.0f)
                    arcTo(612.0f, 612.0f, 0.0f, false, false, 636.0f, 32.0f)
                    curveTo(590.0f, 3.0f, 539.0f, -8.0f, 494.0f, 28.0f)
                    arcTo(622.0f, 622.0f, 0.0f, false, false, 171.0f, 438.0f)
                    curveToRelative(-37.0f, 141.0f, -36.0f, 303.0f, 46.0f, 429.0f)
                    curveToRelative(15.0f, 22.0f, 32.0f, 41.0f, 52.0f, 57.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF965bd6)),
                    stroke = SolidColor(Color(0xFF9545db)),
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(551.0f, 976.0f)
                    curveTo(401.0f, 894.0f, 594.0f, 503.0f, 858.0f, 241.0f)
                    curveTo(975.0f, 125.0f, 1113.0f, 28.0f, 1140.0f, 40.0f)
                    curveToRelative(27.0f, 13.0f, -87.0f, 122.0f, -132.0f, 308.0f)
                    curveToRelative(-27.0f, 112.0f, 2.0f, 121.0f, -26.0f, 287.0f)
                    curveToRelative(-25.0f, 152.0f, -39.0f, 229.0f, -116.0f, 284.0f)
                    curveToRelative(-84.0f, 60.0f, -240.0f, 98.0f, -315.0f, 58.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF965bd6)),
                    stroke = SolidColor(Color(0xFF9545db)),
                    strokeLineWidth = 1.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(571.0f, 976.0f)
                    curveTo(716.0f, 897.0f, 530.0f, 519.0f, 276.0f, 266.0f)
                    curveTo(163.0f, 154.0f, 31.0f, 60.0f, 5.0f, 72.0f)
                    curveToRelative(-26.0f, 12.0f, 83.0f, 118.0f, 127.0f, 297.0f)
                    curveToRelative(26.0f, 108.0f, -2.0f, 117.0f, 25.0f, 277.0f)
                    curveToRelative(24.0f, 147.0f, 37.0f, 221.0f, 112.0f, 274.0f)
                    curveToRelative(80.0f, 58.0f, 231.0f, 95.0f, 303.0f, 55.0f)
                    close()
                }
            }.build()
        return customHashTagIconsFlowerstr!!
    }

private var customHashTagIconsFlowerstr: ImageVector? = null
