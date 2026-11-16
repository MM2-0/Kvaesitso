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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_repo_branches
import com.vitorpamplona.amethyst.commons.resources.git_repo_commits
import com.vitorpamplona.amethyst.commons.resources.git_repo_default_branch
import com.vitorpamplona.amethyst.commons.resources.git_repo_item_count
import com.vitorpamplona.amethyst.commons.resources.git_repo_retry
import com.vitorpamplona.amethyst.commons.resources.git_repo_search_files
import com.vitorpamplona.amethyst.commons.resources.git_repo_tags
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

/** Centered spinner + caption while the repository or a file is loading. */
@Composable
fun GitLoadingBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(34.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

/** Centered icon + message, with an optional primary retry action. */
@Composable
fun GitMessageBox(
    symbol: MaterialSymbol,
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = symbol,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (onRetry != null) {
                FilledTonalButton(onClick = onRetry) {
                    Icon(MaterialSymbols.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringRes(Res.string.git_repo_retry),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * Compact bar identifying the snapshot: a branch/tag picker and the entry count of the
 * current directory.
 */
@Composable
fun RepoInfoBar(
    branch: String?,
    entryCount: Int,
    branches: List<String> = emptyList(),
    tags: List<String> = emptyList(),
    onSelectRef: ((String?) -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    // The branch picker on the left; search and history as quiet actions on the right.
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onSelectRef != null && (branches.size + tags.size) > 1) {
            BranchSelector(branch, branches, tags, onSelectRef)
        } else if (branch != null) {
            InfoChip(symbol = MaterialSymbols.AltRoute, label = branch)
        }
        Text(
            text = pluralStringRes(Res.plurals.git_repo_item_count, entryCount, entryCount),
            style = gitMetaStyle(),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(
                    symbol = MaterialSymbols.Search,
                    contentDescription = stringRes(Res.string.git_repo_search_files),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.grayText,
                )
            }
        }
        if (onHistory != null) {
            Row(
                modifier = Modifier.clip(GitShape).clickable(onClick = onHistory).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(symbol = MaterialSymbols.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.grayText)
                Text(text = stringRes(Res.string.git_repo_commits), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.grayText)
            }
        }
    }
}

@Composable
private fun BranchSelector(
    current: String?,
    branches: List<String>,
    tags: List<String>,
    onSelectRef: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .gitCard()
                    .clickable { expanded = true }
                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(MaterialSymbols.AltRoute, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.grayText)
            Text(
                text = current ?: stringRes(Res.string.git_repo_default_branch),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
            Icon(MaterialSymbols.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (branches.isNotEmpty()) {
                SectionHeader(stringRes(Res.string.git_repo_branches))
                branches.forEach { name ->
                    GitRefMenuItem(name, MaterialSymbols.AltRoute, selected = name == current) {
                        expanded = false
                        if (name != current) onSelectRef(name)
                    }
                }
            }
            if (tags.isNotEmpty()) {
                SectionHeader(stringRes(Res.string.git_repo_tags))
                tags.forEach { name ->
                    GitRefMenuItem(name, MaterialSymbols.Tag, selected = name == current) {
                        expanded = false
                        if (name != current) onSelectRef(name)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** One branch or tag in a ref menu: the chosen one in bold and the accent colour. */
@Composable
internal fun GitRefMenuItem(
    name: String,
    symbol: MaterialSymbol,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                name,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        },
        leadingIcon = { Icon(symbol, contentDescription = null, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
    )
}

/**
 * A form field that picks one of [options] (branches): the same menu rows as the code browser's
 * branch chip, under a labelled, read-only field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GitBranchField(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                GitRefMenuItem(option, MaterialSymbols.AltRoute, selected = option == selected) {
                    expanded = false
                    onSelect(option)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    symbol: MaterialSymbol,
    label: String,
) {
    Row(
        modifier =
            Modifier
                .gitCard()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
