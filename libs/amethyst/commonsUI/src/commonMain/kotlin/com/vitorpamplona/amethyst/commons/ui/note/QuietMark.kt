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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

/**
 * Quiet passive-state marker for note headers: Draft, Edited, pinned,
 * private rumor, ... One spec for all of them so the header reads as a single
 * system: bold gray text at the row's text size with an optional 16dp icon.
 * Contrast with [HeaderPill], the chip tier for tappable/verifiable metadata.
 *
 * Spacing between markers is owned by the parent row (`Arrangement.spacedBy`),
 * not baked in here.
 */
@Composable
fun QuietMark(
    text: String? = null,
    symbol: MaterialSymbol? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    ) {
        if (symbol != null) {
            Icon(
                symbol = symbol,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.size(16.dp),
            )
        }
        if (text != null) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.placeholderText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
