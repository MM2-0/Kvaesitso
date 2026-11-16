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
package com.vitorpamplona.amethyst.commons.ui.note.types.dlists.tiers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListItemView
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListLink
import com.vitorpamplona.amethyst.commons.decentralizedLists.ResolvedConcept
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.GITHUB_BYLINE_FIELD
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.GITHUB_HANDLE_FIELD
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.githubAccountOf
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dlist_github_repositories
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListDefinitionLine
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListKicker
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListLinkButtons
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListMoreFields
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * A GitHub account on its own page (tier three, Brainstorm `components/items/GitHubAccountView`).
 * The handle leads, as GitHub shows an account, beside the account's own avatar (GitHub serves it
 * at the handle), with the byline the list gives it under it. The buttons are the links the
 * definition's URL templates build: the profile, and that profile's repositories tab beside it; a
 * definition that names no template gives none, and the view adds no destination of its own.
 *
 * What it reads is the governing definition's `github-username` and `description` fields, the
 * community concept's names for them: a copy that renames the handle leaves the item to tier two.
 * Brainstorm's page also shows GitHub's own numbers (followers, repos, top repositories) through a
 * same-origin proxy so readers never contact GitHub; there is no such proxy here, so they are not
 * fetched.
 */
@Composable
internal fun GitHubAccountView(
    event: Event,
    resolved: ResolvedConcept,
    hinted: DListItemView,
    links: List<DListLink>,
    openList: (() -> Unit)?,
    accountViewModel: AccountViewModel,
) {
    val definition = resolved.governing
    val account = remember(event, definition) { githubAccountOf(event.tags, definition) } ?: return

    // The profile is the first link the templates build on github.com; the rest are offered as they are.
    val profile = links.firstOrNull { it.host == "github.com" }
    val others = links.filter { it != profile }

    DListKicker(MaterialSymbols.Code, definition.singular, null, openList)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DListPicture(account.avatarUrl, definition.image, 64.dp, accountViewModel, mark = MaterialSymbols.Code)
        Column(Modifier.weight(1f)) {
            Text(text = account.handle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            account.byline?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (profile != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconLink(profile.href, "${profile.label} · ${profile.host}", MaterialSymbols.Code, primary = true)
                // The repositories tab of that same profile; a template that already carries a query keeps its one button.
                if ('?' !in profile.href && '#' !in profile.href) {
                    IconLink("${profile.href}?tab=repositories", stringRes(Res.string.dlist_github_repositories), MaterialSymbols.Folder)
                }
            }
        }
    }

    DListLinkButtons(others)

    val used = remember(hinted) { hinted.usedFields + GITHUB_HANDLE_FIELD + GITHUB_BYLINE_FIELD }
    DListMoreFields(hinted.moreFields.filter { it.field !in used }, hinted.undeclared, definition.singular)
    DListDefinitionLine(resolved)
}

/** A square button whose icon is its whole face; the label is for screen readers. */
@Composable
internal fun IconLink(
    href: String,
    label: String,
    symbol: MaterialSymbol,
    primary: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val open = {
        runCatching { uriHandler.openUri(href) }
        Unit
    }
    if (primary) {
        FilledIconButton(onClick = open) { Icon(symbol = symbol, contentDescription = label, modifier = Modifier.size(20.dp)) }
    } else {
        OutlinedIconButton(onClick = open) { Icon(symbol = symbol, contentDescription = label, modifier = Modifier.size(20.dp)) }
    }
}
