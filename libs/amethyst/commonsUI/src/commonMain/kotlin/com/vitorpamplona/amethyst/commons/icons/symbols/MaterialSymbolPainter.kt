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
package com.vitorpamplona.amethyst.commons.icons.symbols

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun rememberMaterialSymbolPainter(
    symbol: MaterialSymbol,
    tint: Color = LocalContentColor.current,
    // Lets a caller draw from a different glyph font (e.g. Amethyst's own icon font) while
    // reusing this painter, its shared TextMeasurer and its caching.
    family: FontFamily? = null,
    // Draws the symbol solid instead of outlined, via the font's FILL axis. Ignored when
    // [family] is given: that font is the caller's own and need not have the axis.
    filled: Boolean = false,
): Painter {
    val fontFamily = family ?: if (filled) materialSymbolsFilledFontFamily() else materialSymbolsFontFamily()
    val textMeasurer = materialSymbolsTextMeasurer()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(symbol, fontFamily, textMeasurer, density, tint, rtl) {
        MaterialSymbolPainter(symbol, fontFamily, textMeasurer, density, tint, rtl)
    }
}

private class MaterialSymbolPainter(
    private val symbol: MaterialSymbol,
    private val fontFamily: FontFamily,
    private val textMeasurer: TextMeasurer,
    private val density: Density,
    private val tint: Color,
    private val rtl: Boolean,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val sizePx = size.minDimension
        val fontSize = with(density) { sizePx.toSp() }
        // Bake the tint into the measured style. drawText(color = ...) override is unreliable
        // across Compose versions when the measured style has Color.Unspecified — falls back to
        // Color.Black and produces black-on-black icons.
        // FontSynthesis.None: Android's "Bold text" setting raises every requested weight (by 300),
        // and Compose would fake-bold the glyph to reach it. Icons keep the font's own weight; the
        // same setting's shift of the variable font's wght axis is undone in symbolFont.
        val layout =
            textMeasurer.measure(
                text = symbol.glyph,
                style = TextStyle(fontFamily = fontFamily, fontSize = fontSize, color = tint, fontSynthesis = FontSynthesis.None),
            )
        val tx = (size.width - layout.size.width) / 2f
        val ty = (size.height - layout.size.height) / 2f
        if (symbol.autoMirror && rtl) {
            scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                translate(tx, ty) { drawText(layout) }
            }
        } else {
            translate(tx, ty) { drawText(layout) }
        }
    }
}
