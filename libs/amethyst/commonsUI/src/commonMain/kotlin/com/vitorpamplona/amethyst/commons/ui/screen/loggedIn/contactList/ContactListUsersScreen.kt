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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.contactList

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.contact_list_screen_title
import com.vitorpamplona.amethyst.commons.resources.contact_list_screen_title_with_count
import com.vitorpamplona.amethyst.commons.resources.fetching_follow_list
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.UserCompose
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wot.sortedByFollowsThenTrust
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

@Composable
fun ContactListUsersScreen(
    noteId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = noteId) { note ->
        if (note == null) {
            Scaffold(
                topBar = {
                    TopBarWithBackButton(
                        caption = stringRes(Res.string.contact_list_screen_title),
                        nav = nav,
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringRes(Res.string.fetching_follow_list))
                }
            }
        } else {
            val users by observeNoteEventAndMap<ContactListEvent, ImmutableList<User>>(note, accountViewModel) { event ->
                event
                    ?.verifiedFollowKeySet()
                    ?.filter { !accountViewModel.account.isHidden(it) }
                    ?.mapNotNull { LocalCache.checkGetOrCreateUser(it) }
                    ?.sortedByFollowsThenTrust(accountViewModel.account, follows = accountViewModel.account.allFollows.flow.value.authors)
                    ?.toPersistentList()
                    ?: persistentListOf()
            }

            Scaffold(
                topBar = {
                    TopBarWithBackButton(
                        caption = stringRes(Res.string.contact_list_screen_title_with_count, users.size),
                        nav = nav,
                    )
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    itemsIndexed(users, key = { _, item -> item.pubkeyHex }) { _, item ->
                        UserCompose(item, accountViewModel = accountViewModel, nav = nav)
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }
            }
        }
    }
}
