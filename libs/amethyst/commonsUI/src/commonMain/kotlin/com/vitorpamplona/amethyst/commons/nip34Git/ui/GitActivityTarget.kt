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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_untitled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderTargetNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * The issue, patch or PR that an activity ([note]: a status, a push, a cover note) acts on, as a
 * forge's inbox names it: one bordered line with its state, its title and its repository, instead
 * of the whole item quoted with its description. Tapping it opens the item's thread. Until the item
 * itself has loaded, falls back to the quoted card, which loads it.
 */
@Composable
fun GitActivityTarget(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val item = note.replyTo?.lastOrNull() ?: return
    val event = item.event
    if (event == null) {
        RenderTargetNote(note, quotesLeft, backgroundColor, accountViewModel, nav)
        return
    }

    GitItemReference(item, nav, bordered = true)
}

/**
 * [item], an issue, patch or PR, as one line: its state, its title and its repository. Used
 * wherever the item is named rather than read: what an activity acts on, the post a comment
 * answers, a quote. [bordered] draws its own box, for when nothing around it does.
 */
@Composable
fun GitItemReference(
    item: Note,
    nav: INav,
    bordered: Boolean,
) {
    val event = item.event ?: return
    val kind = observeGitStatus(item)?.statusKind() ?: StatusKind.OPEN
    val title = observeGitItemTitle(item)
    val repository = remember(event) { gitRepositoryNameOf(event) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (bordered) it.gitCard() else it }
                .clickable { nav.nav(Route.Note(item.idHex)) }
                .padding(horizontal = if (bordered) 12.dp else 0.dp, vertical = if (bordered) 10.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GitStateIcon(kind = kind, forIssue = event is GitIssueEvent, contentDescription = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title ?: stringRes(Res.string.git_untitled),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (repository != null) {
                Text(text = repository, style = gitMetaStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The name of the repository [item] is filed in (its first coordinate), or its identifier until the announcement loads. */
fun gitRepositoryNameOf(item: Event): String? =
    GitAuthority.repositoriesOf(item).firstOrNull()?.let { address ->
        (LocalCache.getAddressableNoteIfExists(address)?.event as? GitRepositoryEvent)?.name()?.ifBlank { null } ?: address.dTag
    }
