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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_follow_into_title
import com.vitorpamplona.amethyst.commons.resources.drss_lists_empty_sets
import com.vitorpamplona.amethyst.commons.resources.drss_new_list_placeholder
import com.vitorpamplona.amethyst.commons.resources.drss_no_write_list
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag

/**
 * Follows a user, usually a feed's bot, into one of the follow sets drss.io writes to: those under
 * the write folder, or a new one put there. A tap on a set follows or unfollows.
 */
@Composable
fun DrssFollowIntoScreen(
    pubKeyHex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val drss = account.drss
    val version by drss.version.collectAsStateWithLifecycle()
    val write by drss.writeList.collectAsStateWithLifecycle()
    val sets = remember(version, write) { drss.writableSets(FollowSetEvent.KIND) }

    DrssScreenFrame(stringRes(Res.string.drss_follow_into_title), nav) {
        LoadUser(pubKeyHex) { user ->
            if (user == null) return@LoadUser
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UserPicture(user.pubkeyHex, 40.dp, accountViewModel = accountViewModel, nav = nav)
                UsernameDisplay(user, fontWeight = FontWeight.Bold, accountViewModel = accountViewModel)
            }
            HorizontalDivider()
            DrssCreateRow(stringRes(Res.string.drss_new_list_placeholder)) { title ->
                accountViewModel.launchSigner { drss.newFollowSet(title, listOf(UserTag(user.pubkeyHex, user.bestRelayHint())), account) }
            }
            if (write == null) DrssHint(stringRes(Res.string.drss_no_write_list))
            HorizontalDivider()
            if (sets.isEmpty()) {
                DrssHint(stringRes(Res.string.drss_lists_empty_sets))
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(sets, key = { it.note.address.toValue() }) { set ->
                        DrssToggleRow(
                            note = set.note,
                            folder = set.folder,
                            isIn = { event -> (event as? FollowSetEvent)?.publicMembers()?.any { it is UserTag && it.pubKey == user.pubkeyHex } == true },
                            onToggle = { isIn ->
                                accountViewModel.launchSigner {
                                    if (isIn) drss.unfollowFrom(set.note.address, user, account) else drss.followInto(set.note.address, user, account)
                                }
                            },
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
        }
    }
}
