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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withTranslation
import com.vitorpamplona.amethyst.commons.richtext.MathParser
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Renders a LaTeX formula (the inner text of a `$...$` / `$$...$$` span) as an
 * image, tinted to follow the current text color and sized to the current font.
 *
 * JLaTeXMath throws on malformed input, so anything it can't parse falls back to
 * the raw delimited text rather than crashing the feed.
 */
@Composable
fun LatexEquation(
    latex: String,
    displayMode: Boolean,
    leading: String = "",
    trailing: String = "",
) {
    val color = LocalContentColor.current.toArgb()
    val density = LocalDensity.current
    val fontSize = LocalTextStyle.current.fontSize

    // Inline math has no legitimate `\\` line break, so a doubled backslash is an
    // over-escaped command (`\\ldots` → `\ldots`). Without this JLaTeXMath would
    // read `\\` as a line break and render `ldots`/`cdots` as literal letters.
    val formula = if (displayMode) latex else MathParser.collapseDoubledBackslashes(latex)

    // Display math renders a touch larger than the surrounding prose, matching
    // the visual weight KaTeX gives block equations.
    val textSizePx =
        with(density) {
            val base = if (fontSize.isUsable()) fontSize.toPx() else 16.sp.toPx()
            if (displayMode) base * 1.2f else base
        }

    val drawable =
        remember(formula, color, textSizePx) {
            runCatching {
                JLatexMathDrawable
                    .builder(formula)
                    .textSize(textSizePx)
                    .color(color)
                    .align(JLatexMathDrawable.ALIGN_LEFT)
                    .build()
            }.getOrNull()
        }

    if (drawable == null) {
        // Couldn't parse — show the raw delimited source so nothing is lost.
        Text(leading + (if (displayMode) "$$$latex$$" else "$$latex$") + trailing)
        return
    }

    // Baseline alignment. The parent FlowRow centers each word vertically, so a
    // tight equation bitmap floats above the line — most of a formula's mass sits
    // above the baseline, so centering its box lifts the baseline above the text.
    // We pad the box so that, once centered, the equation's baseline coincides
    // with a surrounding text word's baseline. With text ascent/descent A/Dsc and
    // icon total height H and depth-below-baseline Dp, the padded box satisfies
    // `baseline-from-center = (A - Dsc) / 2` — the same value a Text box has — when
    //   pad = (A - Dsc) - H + 2*Dp   (on top if positive, on bottom if negative).
    val resolvedStyle = LocalTextStyle.current.let { if (fontSize.isUsable()) it else it.copy(fontSize = 16.sp) }
    val measurer = rememberTextMeasurer()
    val (drawTopPx, boxHeightPx) =
        remember(drawable, resolvedStyle) {
            val text = measurer.measure("Mg", resolvedStyle)
            val ascent = text.firstBaseline
            val descent = text.size.height - ascent
            val iconHeight = drawable.intrinsicHeight.toFloat()
            val iconDepth = drawable.icon().iconDepth.toFloat()
            val pad = (ascent - descent) - iconHeight + 2f * iconDepth
            maxOf(pad, 0f) to (iconHeight + maxOf(pad, 0f) + maxOf(-pad, 0f))
        }

    val widthDp = with(density) { drawable.intrinsicWidth.toDp() }
    val boxHeightDp = with(density) { boxHeightPx.toDp() }

    // Wide display equations can overflow the column; allow them to scroll
    // horizontally instead of being clipped.
    val equationSize = Modifier.size(widthDp, boxHeightDp)
    val equationModifier = if (displayMode) Modifier.horizontalScroll(rememberScrollState()).then(equationSize) else equationSize

    // Row keeps leading/trailing punctuation (the `(` and `)` in `($x$)`) hugging
    // the equation rather than getting a word-gap from the parent FlowRow.
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leading.isNotEmpty()) {
            Text(leading)
        }
        Canvas(modifier = equationModifier) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                // Position the icon's baseline on the text baseline within the padded box.
                native.withTranslation(y = drawTopPx) {
                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                    drawable.draw(this)
                }
            }
        }
        if (trailing.isNotEmpty()) {
            Text(trailing)
        }
    }
}

private fun TextUnit.isUsable(): Boolean = this != TextUnit.Unspecified && this.type == TextUnitType.Sp
