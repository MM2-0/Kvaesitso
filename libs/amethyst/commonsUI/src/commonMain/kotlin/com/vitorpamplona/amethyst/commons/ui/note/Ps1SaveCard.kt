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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ps1_save_block
import com.vitorpamplona.amethyst.commons.resources.ps1_save_empty_slot
import com.vitorpamplona.amethyst.commons.resources.ps1_save_title
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import org.jetbrains.compose.resources.stringResource

/** Floppy-disk emoji fallback for blocks without a decodable save icon. */
private const val FLOPPY_PREFIX = "💾 "

/**
 * Minimal, fixed-size card for a PS1 memory-card save block (kind 38192).
 *
 * The content is a full 8 KiB memory-card block, hex-encoded (~16K characters),
 * so the card never renders it whole: it shows the save title, a metadata line
 * (game product code, region, block number), and a short monospace hex preview
 * clamped to a few lines — no expansion, no network calls. The card is identical
 * in the feed and the opened view, so it takes no makeItShort flag.
 *
 * The save's animated 16×16 icon is decoded from raw memory-card pixels, which is
 * platform-specific bitmap work, so it arrives as the [icon] slot supplied by the
 * host. A null [icon] falls back to a floppy-disk emoji prefix on the title.
 */
@Composable
fun Ps1SaveCard(
    title: String?,
    filename: String?,
    region: String?,
    blockNumber: Int?,
    isBlank: Boolean,
    hexPreview: String?,
    icon: (@Composable () -> Unit)?,
) {
    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = (if (icon == null) FLOPPY_PREFIX else "") + (title ?: stringResource(Res.string.ps1_save_title)),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val details =
            listOfNotNull(
                filename,
                region,
                blockNumber?.let { stringResource(Res.string.ps1_save_block, it) },
            ).joinToString(" · ")

        if (details.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isBlank) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.ps1_save_empty_slot),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        } else if (hexPreview != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hexPreview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
