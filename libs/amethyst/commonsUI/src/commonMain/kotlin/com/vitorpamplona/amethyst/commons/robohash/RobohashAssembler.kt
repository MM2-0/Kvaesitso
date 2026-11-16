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
package com.vitorpamplona.amethyst.commons.robohash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory0Seven
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory1Nose
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory2HornRed
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory3Button
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory4Satellite
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory5Mustache
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory6Hat
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory7Antenna
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory8Brush
import com.vitorpamplona.amethyst.commons.robohash.parts.accessory9Horn
import com.vitorpamplona.amethyst.commons.robohash.parts.body0Trooper
import com.vitorpamplona.amethyst.commons.robohash.parts.body1Thin
import com.vitorpamplona.amethyst.commons.robohash.parts.body2Thinnest
import com.vitorpamplona.amethyst.commons.robohash.parts.body3Front
import com.vitorpamplona.amethyst.commons.robohash.parts.body4Round
import com.vitorpamplona.amethyst.commons.robohash.parts.body5Neck
import com.vitorpamplona.amethyst.commons.robohash.parts.body6IronMan
import com.vitorpamplona.amethyst.commons.robohash.parts.body7NeckThinner
import com.vitorpamplona.amethyst.commons.robohash.parts.body8Big
import com.vitorpamplona.amethyst.commons.robohash.parts.body9Huge
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes0Squint
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes1Round
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes2Single
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes3Scott
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes4RoundSingle
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes5RoundSmall
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes6WallE
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes7Bar
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes8SmallBar
import com.vitorpamplona.amethyst.commons.robohash.parts.eyes9Shield
import com.vitorpamplona.amethyst.commons.robohash.parts.face0C3po
import com.vitorpamplona.amethyst.commons.robohash.parts.face1Rock
import com.vitorpamplona.amethyst.commons.robohash.parts.face2Long
import com.vitorpamplona.amethyst.commons.robohash.parts.face3Oval
import com.vitorpamplona.amethyst.commons.robohash.parts.face4Cylinder
import com.vitorpamplona.amethyst.commons.robohash.parts.face5Baloon
import com.vitorpamplona.amethyst.commons.robohash.parts.face6Triangle
import com.vitorpamplona.amethyst.commons.robohash.parts.face7Bent
import com.vitorpamplona.amethyst.commons.robohash.parts.face8TriangleInv
import com.vitorpamplona.amethyst.commons.robohash.parts.face9Square
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth0Horz
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth1Cylinder
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth2Teeth
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth3Grid
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth4Vert
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth5MidOpen
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth6Cell
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth7Happy
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth8Buttons
import com.vitorpamplona.amethyst.commons.robohash.parts.mouth9Closed
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log

val Black = SolidColor(Color.Black)
val Gray = SolidColor(Color(0xFF6d6e70))
val Yellow = SolidColor(Color(0xFFf9ec31))
val LightYellow = SolidColor(Color(0xFFfff100))
val Brown = SolidColor(Color(0xFF461917))
val DarkYellow = SolidColor(Color(0xFFfaaf40))
val OrangeThree = SolidColor(Color(0xFFf6921e))

val LightRed = SolidColor(Color(0xFFec1c24))
val OrangeOne = SolidColor(Color(0xFFee4036))
val OrangeTwo = SolidColor(Color(0xFFf05a28))

val LightBrown = SolidColor(Color(0xFFbe1e2d))
val LightGray = SolidColor(Color(0xFFe6e7e8))
val MediumGray = SolidColor(Color(0xFFd0d2d3))

val DefaultSize = 55.dp
const val VIEWPORT_SIZE = 300f

@Preview
@Composable
fun RobohashPreview() {
    val assembler = RobohashAssembler()
    Row {
        Image(
            painter =
                rememberVectorPainter(
                    assembler.build(
                        msg = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                        isLightTheme = false,
                    ),
                ),
            contentDescription = "",
            modifier = Modifier.padding(5.dp).clip(CircleShape),
        )
        Image(
            painter =
                rememberVectorPainter(
                    assembler.build(
                        msg = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                        isLightTheme = true,
                    ),
                ),
            contentDescription = "",
            modifier = Modifier.padding(5.dp).clip(CircleShape),
        )
    }
}

class RobohashAssembler {
    private fun byteMod10(byte: Byte): Int = byte.toUByte().toInt() % 10

    private fun reduce(
        start: Int,
        channel: Byte,
    ) = (start + (channel.toUByte().toInt() * 0.3906f)).toInt()

    private fun bytesToColor(
        r: Byte,
        g: Byte,
        b: Byte,
        makeLight: Boolean,
    ): Color =
        if (makeLight) {
            // > 150-256 color channels
            Color(reduce(150, r), reduce(150, g), reduce(150, b))
        } else {
            // < 50-100 color channels
            Color(reduce(50, r), reduce(50, g), reduce(50, b))
        }

    fun build(
        msg: String,
        isLightTheme: Boolean,
    ): ImageVector {
        val hash =
            // The assembler reads up to hash[10], so the decoded array must hold ≥ 11 bytes
            // (22 hex chars). A shorter hex string — e.g. a 16-char NIP-29 group id (8 bytes) —
            // would index out of bounds, so fall back to sha256 (32 bytes) for anything smaller.
            if (Hex.isHex(msg) && msg.length >= 22) {
                Hex.decode(msg)
            } else {
                Log.w("Robohash") { "$msg is not a hex" }
                sha256(msg.encodeToByteArray())
            }

        val bgColor = SolidColor(bytesToColor(hash[0], hash[1], hash[2], isLightTheme))
        val fgColor = SolidColor(bytesToColor(hash[3], hash[4], hash[5], !isLightTheme))

        return roboBuilder(msg) {
            // Background
            addPath(
                fill = bgColor,
                pathData = BACKGROUND,
            )

            when (byteMod10(hash[6])) {
                0 -> body0Trooper(fgColor, this)
                1 -> body1Thin(fgColor, this)
                2 -> body2Thinnest(fgColor, this)
                3 -> body3Front(fgColor, this)
                4 -> body4Round(fgColor, this)
                5 -> body5Neck(fgColor, this)
                6 -> body6IronMan(fgColor, this)
                7 -> body7NeckThinner(fgColor, this)
                8 -> body8Big(fgColor, this)
                9 -> body9Huge(fgColor, this)
            }

            when (byteMod10(hash[7])) {
                0 -> face0C3po(fgColor, this)
                1 -> face1Rock(fgColor, this)
                2 -> face2Long(fgColor, this)
                3 -> face3Oval(fgColor, this)
                4 -> face4Cylinder(fgColor, this)
                5 -> face5Baloon(fgColor, this)
                6 -> face6Triangle(fgColor, this)
                7 -> face7Bent(fgColor, this)
                8 -> face8TriangleInv(fgColor, this)
                9 -> face9Square(fgColor, this)
            }

            when (byteMod10(hash[8])) {
                0 -> eyes0Squint(fgColor, this)
                1 -> eyes1Round(fgColor, this)
                2 -> eyes2Single(fgColor, this)
                3 -> eyes3Scott(fgColor, this)
                4 -> eyes4RoundSingle(fgColor, this)
                5 -> eyes5RoundSmall(fgColor, this)
                6 -> eyes6WallE(fgColor, this)
                7 -> eyes7Bar(fgColor, this)
                8 -> eyes8SmallBar(fgColor, this)
                9 -> eyes9Shield(fgColor, this)
            }

            when (byteMod10(hash[9])) {
                0 -> mouth0Horz(fgColor, this)
                1 -> mouth1Cylinder(fgColor, this)
                2 -> mouth2Teeth(fgColor, this)
                3 -> mouth3Grid(fgColor, this)
                4 -> mouth4Vert(fgColor, this)
                5 -> mouth5MidOpen(fgColor, this)
                6 -> mouth6Cell(fgColor, this)
                7 -> mouth7Happy(fgColor, this)
                8 -> mouth8Buttons(fgColor, this)
                9 -> mouth9Closed(fgColor, this)
            }

            when (byteMod10(hash[10])) {
                0 -> accessory0Seven(fgColor, this)
                1 -> accessory1Nose(fgColor, this)
                2 -> accessory2HornRed(fgColor, this)
                3 -> accessory3Button(fgColor, this)
                4 -> accessory4Satellite(fgColor, this)
                5 -> accessory5Mustache(fgColor, this)
                6 -> accessory6Hat(fgColor, this)
                7 -> accessory7Antenna(fgColor, this)
                8 -> accessory8Brush(fgColor, this)
                9 -> accessory9Horn(fgColor, this)
            }
        }
    }

    companion object {
        val BACKGROUND =
            PathData {
                moveTo(0.0f, 0.0f)
                horizontalLineToRelative(VIEWPORT_SIZE)
                verticalLineToRelative(VIEWPORT_SIZE)
                horizontalLineToRelative(-VIEWPORT_SIZE)
                close()
            }
    }
}

inline fun roboBuilder(
    name: String = "",
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> Unit,
) = ImageVector
    .Builder(
        name = name,
        defaultWidth = DefaultSize,
        defaultHeight = DefaultSize,
        viewportWidth = VIEWPORT_SIZE,
        viewportHeight = VIEWPORT_SIZE,
        autoMirror = autoMirror,
    ).apply {
        block()
    }.build()
