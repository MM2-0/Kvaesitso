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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.quartz.nip19Bech32.Bech32Transcription

private const val MASK_CHAR = '•'

/**
 * Shows a bech32 key (usually an nsec) as numbered rows of 5-4-4-4-4 character
 * groups (see [Bech32Transcription]) so it can be copied onto paper by hand.
 *
 * Monospace keeps the groups aligned as columns across rows, and each row is
 * auto-sized to stay on one line on narrow screens and large font scales.
 * [masked] keeps the exact same layout with every character replaced by a dot,
 * so revealing the key doesn't shift the screen.
 */
@Composable
fun KeyTranscriptionGrid(
    bech32: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    uppercase: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
    lineNumberColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val rows =
        remember(bech32, masked, uppercase) {
            val source = if (uppercase) bech32.uppercase() else bech32
            Bech32Transcription.groups(source).map { groups ->
                groups.joinToString(" ") { group ->
                    if (masked) MASK_CHAR.toString().repeat(group.length) else group
                }
            }
        }

    val keyStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = lineNumberColor,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    text = row,
                    style = keyStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = keyStyle.fontSize),
                )
            }
        }
    }
}
