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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_cancel
import com.vitorpamplona.amethyst.commons.resources.browser_pill_download_from
import com.vitorpamplona.amethyst.commons.resources.browser_pill_download_risky
import com.vitorpamplona.amethyst.commons.resources.browser_pill_download_save
import com.vitorpamplona.amethyst.commons.resources.browser_pill_download_title
import com.vitorpamplona.amethyst.commons.ui.note.types.formatBytes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * A download the page started, waiting for consent before anything is fetched or written to the shared
 * Downloads collection. A page can start one without any gesture, so the card names the site (the
 * WebView-reported origin, never a page-supplied field), the exact file name that would be saved, its
 * size ([sizeBytes] is -1 when the server didn't say) and, for a network file, the host it comes from
 * ([sourceHost]) when that isn't [host]. Nothing is saved until the user taps Save.
 */
@Composable
fun DownloadPromptCard(
    host: String?,
    security: BrowserChrome.Security,
    fileName: String,
    sizeBytes: Long,
    sourceHost: String?,
    risky: Boolean,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    // Save ignores taps for a moment after the card appears, so a tap meant for whatever was under the
    // finger (say, a page dialog's button the page just replaced with this card) can't land on it.
    var armed by remember(fileName) { mutableStateOf(false) }
    LaunchedEffect(fileName) {
        delay(ARM_DELAY)
        armed = true
    }
    PageCard {
        if (host != null) {
            OriginBadge(host, security)
            Spacer(Modifier.height(20.dp))
        }
        Text(
            stringRes(Res.string.browser_pill_download_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MaterialSymbols.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, filled = true)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                // One line, cut in the middle: the extension is what tells "invoice.pdf" from "invoice.pdf.apk".
                Text(fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                val details =
                    listOfNotNull(
                        sizeBytes.takeIf { it >= 0 }?.let { formatBytes(it) },
                        // A network file can come from another host than the page asking (an ad frame, a CDN).
                        sourceHost?.takeUnless { it.equals(host, ignoreCase = true) }?.let { stringRes(Res.string.browser_pill_download_from, it) },
                    )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
        }
        if (risky) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillActionIcon(BrowserChrome.Action.ACCESS_INFO, tint = MaterialTheme.colorScheme.onErrorContainer, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringRes(Res.string.browser_pill_download_risky), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDeny) { Text(stringRes(Res.string.browser_pill_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onAllow, enabled = armed) { Text(stringRes(Res.string.browser_pill_download_save)) }
        }
    }
}

private val ARM_DELAY = 500.milliseconds
