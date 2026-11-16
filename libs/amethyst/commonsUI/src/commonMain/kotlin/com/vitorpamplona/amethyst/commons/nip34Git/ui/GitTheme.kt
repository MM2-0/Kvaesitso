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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.gitDashboard.GitDashboardViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The colors every git screen draws a lifecycle state in, so the list icon, the thread's pill, the
 * timeline badge and the CI mark of one state are the same color. Two ramps, because the light
 * greens and purples go muddy on a dark background.
 */
@Immutable
class GitPalette(
    val open: Color,
    val merged: Color,
    val closed: Color,
    val pending: Color,
    val added: Color,
    val removed: Color,
)

private val GitLightPalette =
    GitPalette(
        open = Color(0xFF1A7F37),
        merged = Color(0xFF8250DF),
        closed = Color(0xFFCF222E),
        pending = Color(0xFF9A6700),
        added = Color(0xFF1A7F37),
        removed = Color(0xFFCF222E),
    )

private val GitDarkPalette =
    GitPalette(
        open = Color(0xFF3FB950),
        merged = Color(0xFFA371F7),
        closed = Color(0xFFF85149),
        pending = Color(0xFFD29922),
        added = Color(0xFF3FB950),
        removed = Color(0xFFF85149),
    )

@Composable
fun gitPalette(): GitPalette = if (MaterialTheme.colorScheme.isLight) GitLightPalette else GitDarkPalette

/**
 * The id of the note a thread draws as its root. Only that issue, patch or PR shows what is for
 * working on it (its full diff, clone commands, the changes, the status buttons); in a feed, a
 * notification or a quote (even one inside the root's own text) it is something to read and open.
 */
val LocalGitThreadRoot = staticCompositionLocalOf<String?> { null }

/** Whether [item] is the root of the thread on screen ([LocalGitThreadRoot]). */
@Composable
fun isGitThreadRoot(item: Note): Boolean = LocalGitThreadRoot.current == item.idHex

/** One corner radius for every git chip, box and card. */
val GitShape = RoundedCornerShape(6.dp)

/** The radius of a bordered git box: a README, a commit bar, a file's diff. */
val GitCardShape = RoundedCornerShape(8.dp)

/** A bordered box, as forges draw panels, in place of the filled cards a feed uses. */
@Composable
fun Modifier.gitCard(): Modifier = this.clip(GitCardShape).border(1.dp, MaterialTheme.colorScheme.subtleBorder, GitCardShape)

/** The secondary line under a title: who, when, how many. */
@Composable
fun gitMetaStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.grayText)

/**
 * The glyph of a state: open is a ring, draft a pencil, merged the merge arrow (a resolved issue a
 * check), closed a cross. Issues and PRs draw the same states, so a list of either reads the same.
 */
fun gitStateSymbol(
    kind: StatusKind,
    forIssue: Boolean,
): MaterialSymbol =
    when (kind) {
        StatusKind.OPEN -> MaterialSymbols.RadioButtonChecked
        StatusKind.DRAFT -> MaterialSymbols.EditNote
        StatusKind.APPLIED -> if (forIssue) MaterialSymbols.CheckCircle else MaterialSymbols.CallMerge
        StatusKind.CLOSED -> MaterialSymbols.Cancel
    }

@Composable
fun gitStateColor(kind: StatusKind): Color {
    val palette = gitPalette()
    return when (kind) {
        StatusKind.OPEN -> palette.open
        StatusKind.DRAFT -> MaterialTheme.colorScheme.grayText
        StatusKind.APPLIED -> palette.merged
        StatusKind.CLOSED -> palette.closed
    }
}

/** The bare state glyph a list row leads with, in the state's color. */
@Composable
fun GitStateIcon(
    kind: StatusKind,
    forIssue: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Icon(
        symbol = gitStateSymbol(kind, forIssue),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = gitStateColor(kind),
    )
}

/** A label (`#t` or a NIP-32 label) as a quiet outlined tag. */
@Composable
fun GitLabelTag(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            modifier
                .clip(GitShape)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, GitShape)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** A branch, path or ref, set in monospace on a faint fill, as forges draw them. */
@Composable
fun GitRefTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier =
            modifier
                .clip(GitShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Marks [item] read for the git dashboard's inbox while its thread is open: when it opens, and
 * again when the reader leaves, so what arrived while they read it isn't counted as new.
 */
@Composable
fun MarkGitItemRead(
    item: Note,
    accountViewModel: AccountViewModel,
) {
    DisposableEffect(item) {
        GitDashboardViewModel.markItemRead(accountViewModel.account, item)
        onDispose { GitDashboardViewModel.markItemRead(accountViewModel.account, item) }
    }
}
