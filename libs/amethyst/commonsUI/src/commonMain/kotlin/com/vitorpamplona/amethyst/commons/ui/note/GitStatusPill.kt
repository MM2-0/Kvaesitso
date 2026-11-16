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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_status_closed
import com.vitorpamplona.amethyst.commons.resources.git_status_draft
import com.vitorpamplona.amethyst.commons.resources.git_status_merged
import com.vitorpamplona.amethyst.commons.resources.git_status_open
import com.vitorpamplona.amethyst.commons.resources.git_status_resolved
import org.jetbrains.compose.resources.stringResource

private val PillShape = RoundedCornerShape(8.dp)

/** The four NIP-34 patch/issue lifecycle states a [GitStatusPill] can render. */
enum class StatusKind {
    OPEN,
    APPLIED,
    CLOSED,
    DRAFT,
}

/**
 * A small colored pill for a NIP-34 git patch/issue status, styled after GitHub's
 * state badges. Pure presentation: the caller decides which [kind] to show (the
 * Android entry resolves it from the status index). Shared by Android and Desktop.
 */
@Composable
fun GitStatusPill(
    kind: StatusKind,
    modifier: Modifier = Modifier,
    forIssue: Boolean = false,
) {
    val (label, symbol, container, content) = gitStatusStyle(kind, forIssue)

    StatusPill(label, symbol, container, content, modifier)
}

/**
 * The label, icon and colors [GitStatusPill] uses for [kind], shared with the thread timeline's
 * status rows so a status change is drawn in the same colors as the badge it sets. Kind 1631 means
 * "merged" on a patch or PR and "resolved" on an issue ([forIssue]).
 */
@Composable
internal fun gitStatusStyle(
    kind: StatusKind,
    forIssue: Boolean = false,
): StatusStyle =
    when (kind) {
        StatusKind.OPEN -> {
            StatusStyle(
                label = stringResource(Res.string.git_status_open),
                symbol = MaterialSymbols.RadioButtonChecked,
                container = Color(0xFF1F883D),
                content = Color.White,
            )
        }

        StatusKind.APPLIED -> {
            StatusStyle(
                label = stringResource(if (forIssue) Res.string.git_status_resolved else Res.string.git_status_merged),
                symbol = if (forIssue) MaterialSymbols.CheckCircle else MaterialSymbols.CallMerge,
                container = Color(0xFF8250DF),
                content = Color.White,
            )
        }

        StatusKind.CLOSED -> {
            StatusStyle(
                label = stringResource(Res.string.git_status_closed),
                symbol = MaterialSymbols.Cancel,
                container = Color(0xFFCF222E),
                content = Color.White,
            )
        }

        StatusKind.DRAFT -> {
            StatusStyle(
                label = stringResource(Res.string.git_status_draft),
                symbol = MaterialSymbols.EditNote,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

/**
 * The filled, GitHub-style state badge behind [GitStatusPill]: an icon and a short label in
 * [content] on a [container] fill. Shared by the other cards that show a lifecycle state
 * (prediction markets) so every status badge in a feed looks alike.
 */
@Composable
fun StatusPill(
    label: String,
    symbol: MaterialSymbol,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(PillShape)
                .background(container)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = content,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

internal data class StatusStyle(
    val label: String,
    val symbol: MaterialSymbol,
    val container: Color,
    val content: Color,
)
