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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.quartz.utils.Hex

/** The three meanings every conflict visual uses: lost, gained, edited. Theme-aware. */
@Immutable
class ConflictTones(
    val removed: Color,
    val added: Color,
    val changed: Color,
    val kept: Color,
) {
    fun removedContainer() = removed.copy(alpha = 0.14f)

    fun addedContainer() = added.copy(alpha = 0.14f)

    fun changedContainer() = changed.copy(alpha = 0.14f)
}

@Composable
fun conflictTones(): ConflictTones {
    val scheme = MaterialTheme.colorScheme
    return ConflictTones(
        removed = scheme.error,
        added = scheme.allGoodColor,
        changed = scheme.warningColor,
        kept = scheme.primary,
    )
}

/** "523 → 120": the saved size against the new one, the headline of list conflicts. */
@Composable
fun HeroCounts(
    savedLabel: String,
    saved: Int,
    newLabel: String,
    new: Int,
    modifier: Modifier = Modifier,
) {
    val tones = conflictTones()
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(savedLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.placeholderText)
            Text(saved.toString(), fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
        }
        Text("→", fontSize = 24.sp, color = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.padding(bottom = 6.dp))
        Column {
            Text(newLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.placeholderText)
            Text(
                new.toString(),
                fontSize = 44.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                color = if (new < saved) tones.removed else tones.added,
            )
        }
    }
}

/** One segment of a [SplitBar]. */
@Immutable
class BarSegment(
    val weight: Int,
    val color: Color,
)

/** A single bar split into kept / dropped / new segments, proportional to their counts. */
@Composable
fun SplitBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val visible = segments.filter { it.weight > 0 }
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        visible.forEach {
            Box(Modifier.weight(it.weight.toFloat()).fillMaxHeight().background(it.color))
        }
    }
}

/** A colored dot and a label: the legend under a [SplitBar]. */
@Composable
fun LegendItem(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.placeholderText)
    }
}

/** A big signed number over a caption, on a tinted tile. */
@Composable
fun StatTile(
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.placeholderText, textAlign = TextAlign.Center)
    }
}

/** A small uppercase tag: DROPPED, NEW, RENAMED… */
@Composable
fun StatusTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
    )
}

/** An uppercase section caption. */
@Composable
fun SectionCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.placeholderText,
    )
}

/**
 * A 3×3 color fingerprint of a key, so two keys can be told apart at a glance without
 * reading hex. Deterministic: the same key always draws the same grid.
 */
@Composable
fun KeyFingerprint(
    hex: String,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val bytes = runCatching { Hex.decode(hex) }.getOrNull() ?: ByteArray(0)
    val colors =
        List(9) { i ->
            val b = if (bytes.isEmpty()) 0 else bytes[i % bytes.size].toInt() and 0xFF
            val b2 = if (bytes.isEmpty()) 0 else bytes[(i + 9) % bytes.size].toInt() and 0xFF
            Color.hsl(hue = b / 255f * 360f, saturation = 0.55f + (b2 % 30) / 100f, lightness = 0.62f)
        }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(borderColor)
            .padding(3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        colors.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(it)) }
            }
        }
    }
}

/** A tinted rounded panel, the base of every hero block. */
@Composable
fun TintedPanel(
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(tint)
            .padding(18.dp),
    ) { content() }
}
