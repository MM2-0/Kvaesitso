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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.memberEdit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip51Lists.ui.FollowPackAndUserItem
import com.vitorpamplona.amethyst.commons.nip51Lists.ui.PeopleListAndUserItem
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.discover_follows
import com.vitorpamplona.amethyst.commons.resources.discover_follows_explainer
import com.vitorpamplona.amethyst.commons.resources.feed_is_empty
import com.vitorpamplona.amethyst.commons.resources.follow_sets
import com.vitorpamplona.amethyst.commons.resources.follow_sets_explainer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.list.NewListButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun FollowListAndPackAndUserView(
    userToAddOrRemove: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followSetsState by accountViewModel.account.followSets.uiListFlow
        .collectAsStateWithLifecycle()
    val followPackFeedState by accountViewModel.account.starterPacks.uiListFlow
        .collectAsStateWithLifecycle()

    val userName by observeUserName(userToAddOrRemove, accountViewModel)

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        stickyHeader {
            Row(
                modifier = MaxWidthWithHorzPadding,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = SpacedBy5dp,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringRes(Res.string.follow_sets),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringRes(Res.string.follow_sets_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
                NewListButton(
                    onClick = { nav.nav(Route.PeopleListMetadataEdit()) },
                )
            }
        }
        if (followSetsState.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringRes(Res.string.feed_is_empty),
                        modifier = Modifier.padding(vertical = Size20dp),
                    )
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        } else {
            itemsIndexed(followSetsState, key = { _, item -> item.identifierTag }) { _, list ->
                PeopleListAndUserItem(
                    modifier = Modifier.fillMaxWidth(),
                    listHeader = list.title,
                    userName = userName,
                    userIsPrivateMember = list.privateMembers.contains(userToAddOrRemove),
                    userIsPublicMember = list.publicMembers.contains(userToAddOrRemove),
                    onRemoveUser = {
                        accountViewModel.launchSigner {
                            accountViewModel.account.followSets.removeUserFromSet(
                                userToAddOrRemove,
                                isPrivate = list.privateMembers.contains(userToAddOrRemove),
                                list.identifierTag,
                                accountViewModel.account,
                            )
                        }
                    },
                    privateMemberSize = list.privateMembers.size,
                    publicMemberSize = list.publicMembers.size,
                    onClick = {
                        nav.nav(Route.MyPeopleListView(list.identifierTag))
                    },
                    onAddUserToList = { userShouldBePrivate ->
                        accountViewModel.launchSigner {
                            accountViewModel.account.followSets.addUserToSet(
                                userToAddOrRemove,
                                list.identifierTag,
                                userShouldBePrivate,
                                accountViewModel.account,
                            )
                        }
                    },
                )
                HorizontalDivider(thickness = DividerThickness)
            }
        }
        stickyHeader {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = SpacedBy5dp,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringRes(Res.string.discover_follows),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringRes(Res.string.discover_follows_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
                NewListButton(
                    onClick = { nav.nav(Route.FollowPackMetadataEdit()) },
                )
            }
        }
        if (followPackFeedState.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringRes(Res.string.feed_is_empty),
                        modifier = Modifier.padding(vertical = Size20dp),
                    )
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        } else {
            itemsIndexed(followPackFeedState, key = { _, item -> item.identifierTag }) { _, list ->
                FollowPackAndUserItem(
                    modifier = Modifier.fillMaxWidth(),
                    listHeader = list.title,
                    userName = userName,
                    isMember = list.publicMembers.contains(userToAddOrRemove),
                    onClick = {
                        nav.nav(Route.MyFollowPackView(list.identifierTag))
                    },
                    onRemoveUser = {
                        accountViewModel.launchSigner {
                            accountViewModel.account.starterPacks.removeUserFromSet(
                                userToAddOrRemove,
                                list.identifierTag,
                                accountViewModel.account,
                            )
                        }
                    },
                    memberSize = list.publicMembers.size,
                    onAddUserToList = {
                        accountViewModel.launchSigner {
                            accountViewModel.account.starterPacks.addUserToSet(
                                userToAddOrRemove,
                                list.identifierTag,
                                accountViewModel.account,
                            )
                        }
                    },
                )
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}
