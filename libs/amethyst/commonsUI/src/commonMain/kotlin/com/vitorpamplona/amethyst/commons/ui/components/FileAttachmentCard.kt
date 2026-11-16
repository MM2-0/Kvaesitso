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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.innerPostModifier
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.amethyst.commons.util.extractFilename
import com.vitorpamplona.amethyst.commons.util.prettyMime

/**
 * The renderer for a declared file that none of the media viewers can display — a webxdc app,
 * an archive, an installer, any MIME [com.vitorpamplona.amethyst.commons.richtext.RichTextParser.classifyMedia]
 * returns null for.
 *
 * It exists so those files have somewhere to land other than the video player: an unknown blob
 * used to fall through an image-or-else-video branch into ExoPlayer, which buffers forever on a
 * zip. Everything shown here comes off the event's own tags (NIP-94 `alt`, `m`, `size`), so the
 * card costs no network round-trip — unlike routing the URL through the OpenGraph previewer,
 * which would try to download the blob just to rediscover the type the event already declared.
 */
@Composable
fun FileAttachmentCard(
    url: String,
    description: String?,
    mimeType: String?,
    sizeInBytes: Long?,
) {
    val uriHandler = LocalUriHandler.current
    val filename = remember(url) { extractFilename(url) }
    val subtitle = remember(mimeType, sizeInBytes) { fileSubtitle(mimeType, sizeInBytes) }

    Column(
        modifier =
            MaterialTheme.colorScheme.innerPostModifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(url) },
    ) {
        FileAttachmentRow(
            symbol = MaterialSymbols.AttachFile,
            // The alt/content text names the file for a human ("Webxdc app: Quake");
            // the hashed URL basename is the fallback when the event omits it.
            title = description?.ifBlank { null } ?: filename,
            subtitle = subtitle,
            titleMaxLines = 2,
        )

        Spacer(modifier = DoubleVertSpacer)
    }
}

/**
 * The icon + title + subtitle row shared by every card that stands in for a file it can't
 * render inline: this one and the PDF placeholder/skeleton in
 * `PdfPreviewCard`.
 */
@Composable
fun FileAttachmentRow(
    symbol: MaterialSymbol,
    title: String,
    subtitle: String?,
    titleMaxLines: Int = 1,
) {
    Row(
        modifier = MaxWidthWithHorzPadding.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Size20Modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "APK · 16 MB", dropping either half when the event doesn't declare it. */
private fun fileSubtitle(
    mimeType: String?,
    sizeInBytes: Long?,
): String? =
    listOfNotNull(
        mimeType?.ifBlank { null }?.let(::prettyMime),
        sizeInBytes?.takeIf { it > 0 }?.let(::countToHumanReadableBytes),
    ).joinToString(" · ").ifEmpty { null }
