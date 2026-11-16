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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_merge_action
import com.vitorpamplona.amethyst.commons.resources.git_status_close
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_draft
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_merged
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_merged_with_git
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_merged_with_git_hint
import com.vitorpamplona.amethyst.commons.resources.git_status_ready
import com.vitorpamplona.amethyst.commons.resources.git_status_reopen
import com.vitorpamplona.amethyst.commons.resources.git_status_resolve
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

/** Compact sizing shared by the small action buttons on git cards. */
val GitCardButtonHeight = Modifier.height(32.dp)
val GitCardButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

private val PrimaryShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 0.dp, bottomEnd = 0.dp)
private val MenuShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 6.dp, bottomEnd = 6.dp)
private val SingleShape = RoundedCornerShape(6.dp)

private class StatusAction(
    val target: StatusKind,
    val symbol: MaterialSymbol,
    val label: String,
    /** Opens the in-app merge instead of setting [target] directly. */
    val mergesInApp: Boolean = false,
    /** A second line in the menu, when the label alone could be mistaken for another action. */
    val hint: String? = null,
)

/**
 * NIP-34 status controls for an issue, patch or pull request, as one split button: the step the
 * item most likely takes next from its [current] status, and a menu with the others.
 * - closed, merged or resolved: "Reopen", alone;
 * - an open patch or PR: "Merge" to a repository maintainer ([canMerge]) when the app can merge
 *   it ([onMerge]), with "Mark merged" (for a merge made with git) in the menu, else "Mark merged";
 *   "Mark as draft" to anyone else; a draft one: "Ready for review";
 * - an open issue: "Resolve";
 * - anything open: "Close", always in the menu.
 * The host decides who may see them and publishes the status event.
 */
@Composable
fun GitStatusButtons(
    current: StatusKind,
    isPatchOrPr: Boolean,
    canMerge: Boolean,
    onSetStatus: (StatusKind) -> Unit,
    onMerge: (() -> Unit)? = null,
) {
    val reopen = StatusAction(StatusKind.OPEN, MaterialSymbols.RadioButtonChecked, stringRes(Res.string.git_status_reopen))
    val ready = StatusAction(StatusKind.OPEN, MaterialSymbols.RadioButtonChecked, stringRes(Res.string.git_status_ready))
    val draft = StatusAction(StatusKind.DRAFT, MaterialSymbols.EditNote, stringRes(Res.string.git_status_mark_draft))
    val markMerged = StatusAction(StatusKind.APPLIED, MaterialSymbols.CallMerge, stringRes(Res.string.git_status_mark_merged))
    val mergeInApp = StatusAction(StatusKind.APPLIED, MaterialSymbols.CallMerge, stringRes(Res.string.git_merge_action), mergesInApp = true)
    // Beside the in-app merge, marking it merged is for a merge already made with git: say so.
    val markedMergedWithGit =
        StatusAction(
            StatusKind.APPLIED,
            MaterialSymbols.CheckCircle,
            stringRes(Res.string.git_status_mark_merged_with_git),
            hint = stringRes(Res.string.git_status_mark_merged_with_git_hint),
        )
    val merges = if (onMerge != null) listOf(mergeInApp, markedMergedWithGit) else listOf(markMerged)
    val resolve = StatusAction(StatusKind.APPLIED, MaterialSymbols.CheckCircle, stringRes(Res.string.git_status_resolve))
    val close = StatusAction(StatusKind.CLOSED, MaterialSymbols.Cancel, stringRes(Res.string.git_status_close))

    val actions: List<StatusAction> =
        when {
            current == StatusKind.CLOSED || current == StatusKind.APPLIED -> listOf(reopen)
            !isPatchOrPr -> listOf(resolve, close)
            current == StatusKind.DRAFT -> listOf(ready) + (if (canMerge) merges else emptyList()) + close
            canMerge -> merges + listOf(draft, close)
            else -> listOf(draft, close)
        }

    val primary = actions.first()
    val rest = actions.drop(1)
    val container = if (primary.target == StatusKind.DRAFT) MaterialTheme.colorScheme.surfaceVariant else gitStateColor(primary.target)
    val content = if (primary.target == StatusKind.DRAFT) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
    val colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)

    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Button(
            onClick = { if (primary.mergesInApp && onMerge != null) onMerge() else onSetStatus(primary.target) },
            shape = if (rest.isEmpty()) SingleShape else PrimaryShape,
            colors = colors,
            modifier = GitCardButtonHeight,
            contentPadding = GitCardButtonPadding,
        ) {
            Icon(primary.symbol, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(primary.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
        }

        if (rest.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { expanded = true },
                    shape = MenuShape,
                    colors = colors,
                    modifier = GitCardButtonHeight,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Icon(MaterialSymbols.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    rest.forEach { action ->
                        val tint = if (action.target == StatusKind.CLOSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.grayText
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = action.label,
                                        color = if (action.target == StatusKind.CLOSED) MaterialTheme.colorScheme.error else Color.Unspecified,
                                    )
                                    action.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
                                }
                            },
                            leadingIcon = { Icon(action.symbol, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint) },
                            onClick = {
                                expanded = false
                                if (action.mergesInApp && onMerge != null) onMerge() else onSetStatus(action.target)
                            },
                        )
                    }
                }
            }
        }
    }
}
