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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.theme.highlightContextText
import com.vitorpamplona.amethyst.commons.ui.theme.highlightMarker
import com.vitorpamplona.amethyst.commons.ui.theme.highlightMarkerText
import com.vitorpamplona.amethyst.commons.ui.theme.highlightQuoteBar

/**
 * Leading for the quoted passage. The ambient `bodyLarge` sets no lineHeight at all, so
 * inheriting it drops to the font's intrinsic ~1.2em and reads as cramped; the markdown
 * blockquote this component replaced used 1.5em, which reads as too airy for a quote sitting
 * inside a feed card. This splits the difference.
 */
private val QuoteLineHeight = 1.35.em

/**
 * Stroke height above and below the baseline, as a fraction of font size. Deliberately keyed
 * to the glyphs rather than to the line box: leading is a text-spacing decision, and letting
 * it drive stroke thickness would turn a generous [QuoteLineHeight] into a solid slab.
 */
private val MarkerAscentRatio = 0.82f
private val MarkerDescentRatio = 0.22f

/** How far the marker bleeds past the first and last glyph, like a real pen stroke. */
private val MarkerHorizontalBleed = 2.5.dp

/** Grows the stroke on every side, so the wash clears the glyphs instead of hugging them. */
private val MarkerGrowth = 2.dp

private val MarkerCornerRadius = 3.dp

private val QuoteBarWidth = 3.dp

private val QuoteBarGap = 12.dp

/**
 * Distance from the quote block's left edge to the quote text itself. Anything rendered as
 * part of the quote — the source attribution below it, most importantly — must be indented by
 * this much to line up with the text rather than with the bar.
 */
val HighlightQuoteIndent = QuoteBarWidth + QuoteBarGap

/** Breathing room between the quote block and whatever sits above or below it. */
val HighlightQuoteSpacing = 8.dp

/**
 * A NIP-84 highlight: the quoted passage painted under a highlighter-pen wash, optionally
 * embedded in the surrounding context it was taken from.
 *
 * The marker is drawn per visual line from the laid-out text, so it follows soft wraps and
 * stops at the real glyph edges instead of stretching to the full paragraph width. Drawing it
 * behind the glyphs (rather than as a `SpanStyle` background) is what buys the rounded pen
 * ends and the vertical inset — a span background can only ever be a hard, full-line-height
 * rectangle.
 *
 * @param text the full passage to render, context included.
 * @param highlight the range within [text] that was highlighted, or null to mark all of it.
 */
@Composable
fun HighlightedQuote(
    text: String,
    highlight: IntRange?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current.copy(lineHeight = QuoteLineHeight),
) {
    val barColor = MaterialTheme.colorScheme.highlightQuoteBar

    // IntrinsicSize.Min lets the bar match the height of the text beside it.
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Spacer(
            Modifier
                .width(QuoteBarWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(QuoteBarWidth / 2))
                .background(barColor),
        )
        Spacer(Modifier.width(QuoteBarGap))
        HighlightedQuoteText(
            text = text,
            highlight = highlight,
            modifier = Modifier.fillMaxWidth(),
            style = style,
        )
    }
}

@Composable
fun HighlightedQuoteText(
    text: String,
    highlight: IntRange?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current.copy(lineHeight = QuoteLineHeight),
) {
    val markerColor = MaterialTheme.colorScheme.highlightMarker
    val markedText = MaterialTheme.colorScheme.highlightMarkerText
    val contextText = MaterialTheme.colorScheme.highlightContextText

    val start = highlight?.first?.coerceIn(0, text.length) ?: 0
    val end = highlight?.let { (it.last + 1).coerceIn(start, text.length) } ?: text.length

    val annotated =
        remember(text, start, end, markedText, contextText) {
            buildAnnotatedString {
                append(text)
                if (start > 0 || end < text.length) {
                    addStyle(SpanStyle(color = contextText), 0, text.length)
                }
                addStyle(SpanStyle(color = markedText, fontWeight = FontWeight.Medium), start, end)
            }
        }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = style,
        modifier =
            modifier.drawBehind {
                layout?.let { drawMarker(it, start, end, markerColor) }
            },
        onTextLayout = { layout = it },
    )
}

/**
 * Paints the pen stroke one visual line at a time. [TextLayoutResult.getPathForRange] would
 * give the same coverage in one call, but returns a single un-roundable path — per-line round
 * rects are what make it read as a marker rather than a selection.
 */
private fun DrawScope.drawMarker(
    layout: TextLayoutResult,
    start: Int,
    end: Int,
    color: Color,
) {
    if (start >= end || end > layout.layoutInput.text.length) return

    val growth = MarkerGrowth.toPx()
    val bleed = MarkerHorizontalBleed.toPx() + growth
    val radius = CornerRadius(MarkerCornerRadius.toPx())

    // The resolved style, so an inherited (Unspecified) font size still gives a real number.
    val fontSize = layout.layoutInput.style.fontSize
    val fontPx = if (fontSize.isSp) fontSize.toPx() else layout.layoutInput.density.run { 16.sp.toPx() }

    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end - 1)

    for (line in firstLine..lastLine) {
        val lineStart = maxOf(start, layout.getLineStart(line))
        val lineEnd = minOf(end, layout.getLineEnd(line, visibleEnd = true))
        if (lineEnd <= lineStart) continue

        val left = layout.getHorizontalPosition(lineStart, usePrimaryDirection = true)
        val right = layout.getHorizontalPosition(lineEnd, usePrimaryDirection = true)
        if (right <= left) continue

        // Anchored to the baseline so the stroke keeps the same weight whatever the leading is.
        val baseline = layout.getLineBaseline(line)
        val top =
            (baseline - fontPx * MarkerAscentRatio - growth)
                .coerceAtLeast(layout.getLineTop(line))
        val bottom =
            (baseline + fontPx * MarkerDescentRatio + growth)
                .coerceAtMost(layout.getLineBottom(line))
        if (bottom <= top) continue

        // A line that fills the column would otherwise bleed past its right edge and get
        // clipped, leaving the stroke visibly squared off on exactly the longest lines.
        val markLeft = (left - bleed).coerceAtLeast(0f)
        val markRight = (right + bleed).coerceAtMost(size.width)
        if (markRight <= markLeft) continue

        drawRoundRect(
            color = color,
            topLeft = Offset(markLeft, top),
            size = Size(markRight - markLeft, bottom - top),
            cornerRadius = radius,
        )
    }
}
