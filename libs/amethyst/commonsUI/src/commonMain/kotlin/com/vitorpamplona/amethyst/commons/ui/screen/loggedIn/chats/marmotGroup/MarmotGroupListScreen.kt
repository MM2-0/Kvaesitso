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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.ui.ChatUnreadBadge
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.marmot_create_group
import com.vitorpamplona.amethyst.commons.resources.marmot_empty_create_action
import com.vitorpamplona.amethyst.commons.resources.marmot_group_fallback_name
import com.vitorpamplona.amethyst.commons.resources.marmot_groups_title
import com.vitorpamplona.amethyst.commons.resources.marmot_no_groups
import com.vitorpamplona.amethyst.commons.resources.marmot_no_groups_desc
import com.vitorpamplona.amethyst.commons.resources.marmot_no_invitations
import com.vitorpamplona.amethyst.commons.resources.marmot_no_invitations_desc
import com.vitorpamplona.amethyst.commons.resources.marmot_tab_known
import com.vitorpamplona.amethyst.commons.resources.marmot_tab_known_count
import com.vitorpamplona.amethyst.commons.resources.marmot_tab_new_requests
import com.vitorpamplona.amethyst.commons.resources.marmot_tab_new_requests_count
import com.vitorpamplona.amethyst.commons.resources.marmot_unread_messages
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.commons.ui.note.elements.ToggleableTimeAgoText
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.marmotGroupPreviewText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.marmotOtherMembers
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.DisplayUserSetAsSubject
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarmotGroupListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var groupList by remember { mutableStateOf(listOf<Pair<HexKey, MarmotGroupChatroom>>()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Load group list
    LaunchedEffect(Unit) {
        loadGroupList(accountViewModel, onUpdate = { groupList = it })
    }

    // Listen for group list changes
    LaunchedEffect(Unit) {
        accountViewModel.account.marmotGroupList.groupListChanges.collect {
            loadGroupList(accountViewModel, onUpdate = { groupList = it })
        }
    }

    // KeyPackage publishing is handled at Account startup
    // (Account.ensureMarmotKeyPackagePublished), so this screen no longer
    // needs to do anything to make sure invitees can find a KeyPackage.

    val followState by accountViewModel.account.kind3FollowList.flow
        .collectAsStateWithLifecycle()
    val followingKeySet = followState.authors

    val knownGroups = remember(groupList, followingKeySet) { groupList.filter { it.second.isKnown(followingKeySet) } }
    val newRequestGroups = remember(groupList, followingKeySet) { groupList.filter { !it.second.isKnown(followingKeySet) } }
    val visibleGroups = if (selectedTab == 0) knownGroups else newRequestGroups

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // No arrow as a bottom-nav tab root: there is nothing below it to return to.
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                            )
                        }
                    }
                },
                title = { Text(stringRes(Res.string.marmot_groups_title)) },
            )
        },
        bottomBar = {
            AppBottomBar(Route.MarmotGroupList, nav, accountViewModel) { route ->
                if (route != Route.MarmotGroupList) nav.navBottomBar(route)
            }
        },
        floatingActionButton = {
            FabBottomBarPadded(nav) {
                FloatingActionButton(onClick = { nav.nav(Route.CreateMarmotGroup) }, shape = CircleShape) {
                    Icon(MaterialSymbols.Add, contentDescription = stringRes(Res.string.marmot_create_group))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Above the tabs, because the state it reports is the reason both
            // tabs can be empty: invites landing on another install look
            // exactly like no invites at all.
            MarmotInviteDeviceBanner(accountViewModel)

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text =
                                if (knownGroups.isEmpty()) {
                                    stringRes(Res.string.marmot_tab_known)
                                } else {
                                    stringRes(Res.string.marmot_tab_known_count, knownGroups.size)
                                },
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text =
                                if (newRequestGroups.isEmpty()) {
                                    stringRes(Res.string.marmot_tab_new_requests)
                                } else {
                                    stringRes(Res.string.marmot_tab_new_requests_count, newRequestGroups.size)
                                },
                        )
                    },
                )
            }

            if (visibleGroups.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text =
                                if (selectedTab == 0) {
                                    stringRes(Res.string.marmot_no_groups)
                                } else {
                                    stringRes(Res.string.marmot_no_invitations)
                                },
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text =
                                if (selectedTab == 0) {
                                    stringRes(Res.string.marmot_no_groups_desc)
                                } else {
                                    stringRes(Res.string.marmot_no_invitations_desc)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // Only the Known tab gets a button. There is no action
                        // that fills the New Requests tab — you cannot make
                        // someone invite you — so offering one there would be a
                        // dead end dressed up as a next step.
                        if (selectedTab == 0) {
                            Button(
                                onClick = { nav.nav(Route.CreateMarmotGroup) },
                                modifier = Modifier.padding(top = 20.dp),
                            ) {
                                Text(stringRes(Res.string.marmot_empty_create_action))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(visibleGroups, key = { _, it -> it.first }) { index, (groupId, chatroom) ->
                        MarmotGroupListItem(
                            groupId = groupId,
                            chatroom = chatroom,
                            accountViewModel = accountViewModel,
                            onClick = {
                                nav.nav(Route.MarmotGroupChat(groupId))
                            },
                        )
                        // Between rows only: a divider under the last one draws a
                        // line across empty space with nothing beneath it.
                        if (index < visibleGroups.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun loadGroupList(
    accountViewModel: AccountViewModel,
    onUpdate: (List<Pair<HexKey, MarmotGroupChatroom>>) -> Unit,
) {
    val groups = mutableListOf<Pair<HexKey, MarmotGroupChatroom>>()
    accountViewModel.account.marmotGroupList.rooms.forEach { key, chatroom ->
        groups.add(key to chatroom)
    }
    // Also add groups from MarmotManager that might not have messages yet
    accountViewModel.account.marmotManager?.activeGroupIds()?.forEach { groupId ->
        if (groups.none { it.first == groupId }) {
            groups.add(groupId to accountViewModel.account.marmotGroupList.getOrCreateGroup(groupId))
        }
    }
    groups.sortByDescending { it.second.newestMessage?.createdAt() ?: 0L }
    onUpdate(groups)
}

@Composable
fun MarmotGroupListItem(
    groupId: HexKey,
    chatroom: MarmotGroupChatroom,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val members by chatroom.members.collectAsStateWithLifecycle()
    val memberPubkeys = remember(members) { marmotOtherMembers(members, accountViewModel.account.signer.pubKey) }
    val newestMessage = chatroom.newestMessage

    val lastReadTime by accountViewModel.account.loadLastReadFlow(marmotGroupLastReadRoute(groupId)).collectAsStateWithLifecycle()
    // Not remembered: chatroom.messages can shrink without newestMessage or
    // lastReadTime changing (pruning, kind:5 deletion of an older message),
    // so caching on those keys would serve a stale count. The set is pruned
    // to ~100 entries, so counting per recomposition is cheap.
    val unread = chatroom.messages.count { (it.createdAt() ?: Long.MIN_VALUE) > lastReadTime }

    val previewText = marmotGroupPreviewText(newestMessage, accountViewModel)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (memberPubkeys.isNotEmpty()) {
            NonClickableUserPictures(
                userHexList = memberPubkeys,
                size = Size55dp,
                accountViewModel = accountViewModel,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!displayName.isNullOrBlank()) {
                Text(
                    text = displayName!!,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (memberPubkeys.isNotEmpty()) {
                DisplayUserSetAsSubject(
                    userList = memberPubkeys,
                    accountViewModel = accountViewModel,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                )
            } else {
                Text(
                    text = stringRes(Res.string.marmot_group_fallback_name, groupId.take(8)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // The list is sorted by newestMessage.createdAt(), so showing that time
        // is what makes the ordering legible; the message count it replaced was
        // a number no reader was asking for.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            newestMessage?.createdAt()?.let { createdAt ->
                ToggleableTimeAgoText(
                    timestamp = createdAt,
                    style = TimeAgoStyle.Short,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The row is the tap target. A toggleable timestamp would
                    // swallow taps meant to open the group.
                    toggleable = false,
                )
            }
            if (unread > 0) {
                ChatUnreadBadge(
                    count = unread,
                    contentDescription = pluralStringRes(Res.plurals.marmot_unread_messages, unread, unread),
                )
            }
        }
    }
}
