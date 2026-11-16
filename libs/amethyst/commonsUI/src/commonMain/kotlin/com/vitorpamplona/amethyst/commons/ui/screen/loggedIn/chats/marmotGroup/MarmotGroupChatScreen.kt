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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.marmot_add_member_action
import com.vitorpamplona.amethyst.commons.resources.marmot_group_default_name
import com.vitorpamplona.amethyst.commons.resources.marmot_member_count
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.marmotOtherMembers
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.DisplayUserSetAsSubject
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarmotGroupChatScreen(
    nostrGroupId: HexKey,
    draftMessage: String? = null,
    replyToInnerNote: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val chatroom =
        remember(nostrGroupId) {
            accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        }
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val memberCount by chatroom.memberCount.collectAsStateWithLifecycle()
    val members by chatroom.members.collectAsStateWithLifecycle()
    val memberPubkeys = remember(members) { marmotOtherMembers(members, accountViewModel.account.signer.pubKey) }

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // Hidden in a list/detail screen's detail pane, where there is nothing to go back to.
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                            )
                        }
                    }
                },
                title = {
                    Row(
                        modifier =
                            Modifier.clickable {
                                nav.nav(Route.MarmotGroupInfo(nostrGroupId))
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (memberPubkeys.isNotEmpty()) {
                            NonClickableUserPictures(
                                userHexList = memberPubkeys,
                                size = 36.dp,
                                accountViewModel = accountViewModel,
                            )
                        }
                        Column {
                            if (!displayName.isNullOrBlank()) {
                                Text(displayName!!)
                            } else if (memberPubkeys.isNotEmpty()) {
                                DisplayUserSetAsSubject(
                                    userList = memberPubkeys,
                                    accountViewModel = accountViewModel,
                                    fontWeight = FontWeight.Normal,
                                )
                            } else {
                                Text(stringRes(Res.string.marmot_group_default_name))
                            }
                            if (memberCount > 0) {
                                Text(
                                    text = pluralStringRes(Res.plurals.marmot_member_count, memberCount, memberCount),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { nav.nav(Route.MarmotGroupInfo(nostrGroupId)) }) {
                        Icon(
                            symbol = MaterialSymbols.GroupAdd,
                            contentDescription = stringRes(Res.string.marmot_add_member_action),
                        )
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
        allowBarHide = false,
    ) {
        Column(Modifier.padding(it)) {
            MarmotGroupChatView(
                nostrGroupId = nostrGroupId,
                draftMessage = draftMessage,
                replyToInnerNote = replyToInnerNote,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
