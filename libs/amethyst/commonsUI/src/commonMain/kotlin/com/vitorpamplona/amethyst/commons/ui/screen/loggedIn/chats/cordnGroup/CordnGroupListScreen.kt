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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.cordn.ui.CoordinatorHealthRow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordn.CordnRuntime
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_create_action
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable_detail
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_count
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_down_note
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_manage
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_menu_invitations
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_none
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_none_detail
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_title
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_link
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.CordnGroupRoomCompose
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CoordinatorIdentityRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.client.CoordinatorHealth
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource

/**
 * Every cordn group this account is in, grouped by the coordinator serving it.
 *
 * ## Why it groups rather than lists
 *
 * A flat list here would be the Messages inbox with everything else removed,
 * which is not worth a screen. What the inbox structurally cannot show is the
 * thing that decides whether a cordn group works at all: a cordn message never
 * arrives over a relay, it arrives because one coordinator answered a call, so
 * when a group goes quiet the question is always "is that server up" and never
 * "which relay". Sectioning by coordinator puts the answer above the groups it
 * explains.
 *
 * ## The health is free
 *
 * `CordnRuntime.health` is a local StateFlow of what the calls the sync loop
 * was making anyway observed -- it never polls, because a poll is a call and
 * every call to a coordinator is metadata (`spec/00.md` §8). So this screen
 * costs no network at all, and "unknown" means nothing has been asked of that
 * coordinator yet, which is shown differently from "down".
 *
 * Key-package health is deliberately NOT here even though an empty pool means
 * nobody can invite you: the only truthful source is `kp_list`, which is
 * unpaginated, and fetching it per coordinator to decorate a list would be the
 * cost this branch has otherwise been careful to avoid. It stays one tap away
 * on the coordinators screen.
 */
@Composable
fun CordnGroupListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.cordn_groups_title), nav) {
                CordnGroupListMenu(nav)
            }
        },
        bottomBar = {
            AppBottomBar(Route.CordnGroupList, nav, accountViewModel) { route ->
                if (route != Route.CordnGroupList) nav.navBottomBar(route)
            }
        },
    ) { padding ->
        if (runtime == null) {
            EmptyState(
                title = stringRes(Res.string.cordn_group_unavailable),
                description = stringRes(Res.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        // `revision` rather than `all`: a room's newest message changes INSIDE
        // the room object, which `all` cannot see, so ordering off `all` alone
        // would freeze after each group's first message. See
        // CordnGroupList.revision.
        val revision by runtime.groups.revision.collectAsStateWithLifecycle()

        // Coordinators ordered by their liveliest group, groups ordered within
        // them the same way: the section that just received something rises,
        // which is the order somebody opening this screen is looking for.
        val sections =
            remember(revision) {
                runtime.groups.all.value
                    .groupBy { it.coordinatorPubKey }
                    .map { (coordinator, rooms) -> coordinator to rooms.sortedByDescending { it.newestAt() } }
                    .sortedByDescending { (_, rooms) -> rooms.firstOrNull()?.newestAt() ?: 0L }
            }

        if (sections.isEmpty()) {
            EmptyState(
                title = stringRes(Res.string.cordn_groups_none),
                description = stringRes(Res.string.cordn_groups_none_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            state = rememberLazyListState(),
        ) {
            sections.forEach { (coordinator, rooms) ->
                item(key = "head-$coordinator") {
                    CoordinatorSection(
                        coordinatorPubKey = coordinator,
                        runtime = runtime,
                        groupCount = rooms.size,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                itemsIndexed(rooms, key = { _, room -> coordinator + room.gid }) { index, room ->
                    CoordinatorGroupRow(
                        room = room,
                        coordinatorPubKey = coordinator,
                        runtime = runtime,
                        isLast = index == rooms.lastIndex,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

/** When this room last heard anything, for ordering. */
private fun CordnGroupChatroom.newestAt(): Long = newest.value?.envelope?.createdAt ?: 0L

/**
 * One coordinator, above the groups it serves.
 *
 * Tinted by its own health rather than decorated uniformly: a coordinator that
 * has stopped answering is the reason every group under it looks idle, and
 * saying so here is the whole argument for this screen existing.
 */
@Composable
private fun CoordinatorSection(
    coordinatorPubKey: HexKey,
    runtime: CordnRuntime,
    groupCount: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val health = healthOf(runtime, coordinatorPubKey)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CoordinatorIdentityRow(
            pubKey = coordinatorPubKey,
            label = runtime.sessionOrNull(coordinatorPubKey)?.config?.label,
            accountViewModel = accountViewModel,
            nav = nav,
            size = 26.dp,
        ) { name ->
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (health.isDown) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Text(
                    text = pluralStringRes(Res.plurals.cordn_groups_count, groupCount, groupCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        CoordinatorHealthRow(health, Modifier.padding(start = 34.dp))

        // Said once per coordinator rather than once per group: the groups are
        // not each broken, the one server they share is.
        if (health.isDown) {
            Row(
                Modifier.fillMaxWidth().padding(start = 34.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = stringRes(Res.string.cordn_groups_down_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * One group row, dimmed while its coordinator is down.
 *
 * Dimmed and still tappable: the history already on this device reads fine
 * offline, and blocking the tap would hide what the user came for. The dimming
 * is there so a row that cannot be receiving does not look like one that is.
 */
@Composable
private fun CoordinatorGroupRow(
    room: CordnGroupChatroom,
    coordinatorPubKey: HexKey,
    runtime: CordnRuntime,
    isLast: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val health = healthOf(runtime, coordinatorPubKey)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().alpha(if (health.isDown) DIMMED else 1f)) {
            // The inbox's own row, not a second rendering of it: it already
            // reads the live name, preview, annotations and unread count off
            // the room, and two of those drifting apart would be a bug nobody
            // would look for in a list screen.
            CordnGroupRoomCompose(room, accountViewModel, nav)
        }
        // Between rows only. A divider after the last one leaves a rule hanging
        // under the section, where the next section's own spacing already
        // separates them.
        if (!isLast) HorizontalDivider(thickness = DividerThickness)
    }
}

/**
 * This coordinator's health, or a blank state when there is no session.
 *
 * Blank rather than absent so the caller can render unconditionally: a
 * coordinator with no open session has told us nothing, which is exactly what
 * [CoordinatorHealth.State.isUnknown] means.
 *
 * The null is resolved into a flow BEFORE collecting, never by skipping the
 * collect. `collectAsStateWithLifecycle` behind a `?.` is a conditional
 * @Composable call, and this is a branch that flips at runtime -- a session
 * opening mid-screen would change the shape of the slot table under Compose.
 * Keyed on the coordinator list so it re-resolves when a session does open.
 */
@Composable
private fun healthOf(
    runtime: CordnRuntime,
    coordinatorPubKey: HexKey,
): CoordinatorHealth.State {
    val coordinators by runtime.coordinators.collectAsStateWithLifecycle()
    val flow =
        remember(runtime, coordinatorPubKey, coordinators) {
            runtime.health(coordinatorPubKey) ?: MutableStateFlow(CoordinatorHealth.State())
        }
    return flow.collectAsStateWithLifecycle().value
}

/** How much a group under a coordinator that stopped answering fades. */
private const val DIMMED = 0.55f

/**
 * The groups screen's actions behind one overflow, as on a Concord community: three icons
 * squeezed the title at phone width, and none of these is a daily action.
 */
@Composable
private fun CordnGroupListMenu(nav: INav) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { menuOpen = true }) {
        Icon(symbol = MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.more_options))
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        CordnGroupListMenuItem(Res.string.cordn_create_action, MaterialSymbols.Add) {
            menuOpen = false
            nav.nav(Route.CordnCreateGroup)
        }
        // Invitations are fetched only when someone opens that screen (every call to a
        // coordinator is metadata, spec/00.md §8), so this is a plain way in, never a badge.
        CordnGroupListMenuItem(Res.string.cordn_groups_menu_invitations, MaterialSymbols.MoveToInbox) {
            menuOpen = false
            nav.nav(Route.CordnInvitations)
        }
        // Where a cordn1… or cordn.net link someone shared gets pasted, scanned and joined.
        CordnGroupListMenuItem(Res.string.cordn_hub_link, MaterialSymbols.Link) {
            menuOpen = false
            nav.nav(Route.CordnLink())
        }
        CordnGroupListMenuItem(Res.string.cordn_groups_manage, MaterialSymbols.Dns) {
            menuOpen = false
            nav.nav(Route.CordnCoordinators)
        }
    }
}

@Composable
private fun CordnGroupListMenuItem(
    label: StringResource,
    symbol: MaterialSymbol,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringRes(label)) },
        leadingIcon = { Icon(symbol = symbol, contentDescription = null) },
        onClick = onClick,
    )
}
