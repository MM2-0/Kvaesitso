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
package com.vitorpamplona.amethyst.commons.ui.note.types.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kind_app_curation_set
import com.vitorpamplona.amethyst.commons.resources.kind_favorite_algo_feeds
import com.vitorpamplona.amethyst.commons.resources.kind_git_repositories
import com.vitorpamplona.amethyst.commons.resources.kind_simple_groups
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.types.AppIcon
import com.vitorpamplona.amethyst.commons.ui.note.types.Chip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.gitRepositoryList.GitRepositoryListEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent

/** NIP-51 kind 10018: the git repositories this user follows. */
@Composable
fun RenderGitRepositoryList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? GitRepositoryListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicRepositories() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateRepositories(it) }

    ListCard(
        title = stringRes(Res.string.kind_git_repositories),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { repo ->
        key(repo.address.toValue()) {
            AddressMemberRow(repo.address, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** NIP-51 kind 10090: the DVM content feeds this user keeps to hand. */
@Composable
fun RenderFavoriteAlgoFeedsList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? FavoriteAlgoFeedsListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicFavoriteAlgoFeeds() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateFavoriteAlgoFeeds(it) }

    ListCard(
        title = stringRes(Res.string.kind_favorite_algo_feeds),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { feed ->
        key(feed.address.toValue()) {
            AddressMemberRow(feed.address, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/**
 * NIP-51 kind 30267: "references to multiple software applications".
 *
 * Zapstore names these with a `name` tag instead of `title`, and pins them to the platforms the
 * apps were picked for with NIP-82 `f` tags. Each app is a compact row — icon, name, summary —
 * rather than the full app card, which carries screenshots and a reactions row that would turn a
 * twenty-app set into a wall.
 */
@Composable
fun RenderAppCurationSet(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? AppCurationSetEvent ?: return

    val apps = remember(noteEvent) { noteEvent.apps() }
    val platforms = remember(noteEvent) { noteEvent.platforms() }

    ListCard(
        title = listTitle(noteEvent.titleOrName(), noteEvent.dTag(), Res.string.kind_app_curation_set),
        description = noteEvent.description(),
        items = apps,
        // Not a PrivateTagArrayEvent: an app set has no encrypted half to be locked out of.
        hasUnreadablePrivateItems = false,
        backgroundColor = backgroundColor,
        subtitle = if (platforms.isNotEmpty()) ({ PlatformChips(platforms) }) else null,
    ) { app ->
        key(app.address.toValue()) {
            if (app.address.kind == SoftwareApplicationEvent.KIND) {
                SoftwareAppMemberRow(app.address, accountViewModel, nav)
            } else {
                AddressMemberRow(app.address, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformChips(platforms: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        platforms.forEach { Chip(it) }
    }
}

/**
 * A NIP-82 app (kind 32267) as one line of a set: its icon, name and summary, opening the app's
 * detail screen. Until the app event arrives the row shows its app id, so the list keeps its
 * shape instead of jumping as nineteen apps load one by one.
 */
@Composable
private fun SoftwareAppMemberRow(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        val app = note?.let { observeNoteEvent<SoftwareApplicationEvent>(it, accountViewModel).value }

        val name = app?.name()?.takeIf { it.isNotBlank() } ?: address.dTag
        val summary = app?.summary()?.takeIf { it.isNotBlank() }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.nav(Route.SoftwareAppDetail(address)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                AppIcon(icon = app?.icon(), name = name, sizeDp = 40)

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (app == null) MaterialTheme.colorScheme.grayText else Color.Unspecified,
                    )
                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.grayText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

/**
 * NIP-51 kind 10009: the simple groups this user belongs to.
 *
 * A group is named by an id on a relay rather than by a coordinate, so it gets its own row: the
 * group's name when it carries one, and the relay that hosts it underneath.
 */
@Composable
fun RenderSimpleGroupList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? SimpleGroupListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicGroups() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateGroups(it) }

    ListCard(
        title = stringRes(Res.string.kind_simple_groups),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { group ->
        key(group.groupId + group.relayUrl) {
            LabelMemberRow(group.name?.takeIf { it.isNotBlank() } ?: group.groupId)
        }
    }
}
