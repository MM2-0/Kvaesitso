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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

// Living reference of every foreground/background pair the mobile theme uses, listed by its
// ColorScheme role name and rendered with the real AmethystTheme colors (dark | light). Each row
// previews the foreground on its background, the live WCAG contrast, the two hexes, and the app
// surfaces that consume the pair. Reads the live scheme, so editing Theme.kt re-renders it.

private class RolePair(
    val name: String,
    val bg: (ColorScheme) -> Color,
    val fg: (ColorScheme) -> Color,
    val usage: String,
    val highlight: Boolean = false,
)

private val ROLE_PAIRS =
    listOf(
        RolePair(
            "primary / onPrimary",
            { it.primary },
            { it.onPrimary },
            "Top-bar Save button, FABs, filled buttons, checked switch, sliders, progress.",
            highlight = true,
        ),
        RolePair(
            "primaryContainer / onPrimaryContainer",
            { it.primaryContainer },
            { it.onPrimaryContainer },
            "Settings icon tiles, tonal cards, tonal chips.",
        ),
        RolePair(
            "secondaryContainer / onSecondaryContainer",
            { it.secondaryContainer },
            { it.onSecondaryContainer },
            "Selected-reaction highlight, relay-group chips, chat header, reply toggle.",
        ),
        RolePair(
            "secondary / onSecondary",
            { it.secondary },
            { it.onSecondary },
            "Teal accent (purple theme); tertiary mirrors it.",
        ),
        RolePair(
            "tertiaryContainer / onTertiaryContainer",
            { it.tertiaryContainer },
            { it.onTertiaryContainer },
            "Tertiary tonal surfaces; derived from the accent.",
        ),
        RolePair(
            "error / onError",
            { it.error },
            { it.onError },
            "Destructive dialogs and error states. Material baseline.",
        ),
        RolePair(
            "surface / onSurface",
            { it.surface },
            { it.onSurface },
            "App background and primary body text.",
        ),
        RolePair(
            "surface / onSurfaceVariant",
            { it.surface },
            { it.onSurfaceVariant },
            "Secondary text, timestamps, subtitles, icons.",
        ),
        RolePair(
            "surfaceContainer / onSurface",
            { it.surfaceContainer },
            { it.onSurface },
            "Cards, bottom sheets, elevated rows.",
        ),
        RolePair(
            "surfaceVariant / onSurfaceVariant",
            { it.surfaceVariant },
            { it.onSurfaceVariant },
            "Subtle filled areas — code blocks, inset panels.",
        ),
        RolePair(
            "surfaceContainerHighest / outline",
            { it.surfaceContainerHighest },
            { it.outline },
            "Unchecked switch (off-track + thumb), inactive controls.",
        ),
    )

private fun Color.hex(): String = "#%06X".format(0xFFFFFF and toArgb())

private fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

private fun contrastLabel(ratio: Float): String =
    when {
        ratio >= 4.5f -> "AA"
        ratio >= 3f -> "AA-lg"
        else -> "low"
    }

@Composable
private fun RolePairRow(pair: RolePair) {
    val scheme = MaterialTheme.colorScheme
    val bg = pair.bg(scheme)
    val fg = pair.fg(scheme)
    val ratio = contrastRatio(bg, fg)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(92.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(
                        if (pair.highlight) 2.dp else 1.dp,
                        if (pair.highlight) scheme.onBackground else scheme.outlineVariant,
                        RoundedCornerShape(10.dp),
                    ).padding(8.dp),
        ) {
            Text("Aa", color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "%.1f:1 %s".format(ratio, contrastLabel(ratio)),
                color = fg,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                pair.name,
                color = scheme.onBackground,
                fontSize = 12.5.sp,
                fontWeight = if (pair.highlight) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                "${bg.hex()}  /  ${fg.hex()}",
                color = scheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                pair.usage,
                color = scheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RolePairList() {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "fg / bg pairs by role",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
        ROLE_PAIRS.forEach { RolePairRow(it) }
    }
}

@Preview(name = "Theme color pairs by role · dark | light", widthDp = 820, heightDp = 980, showBackground = true)
@Composable
fun ThemeColorPairsPreview() {
    ThemeComparisonRow {
        RolePairList()
    }
}
