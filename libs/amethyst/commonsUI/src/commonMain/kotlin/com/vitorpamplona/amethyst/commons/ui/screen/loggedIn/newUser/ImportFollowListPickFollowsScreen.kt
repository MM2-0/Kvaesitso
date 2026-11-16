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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.newUser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accounts_found
import com.vitorpamplona.amethyst.commons.resources.fetching_follow_list
import com.vitorpamplona.amethyst.commons.resources.follow_accounts
import com.vitorpamplona.amethyst.commons.resources.no_follows_found
import com.vitorpamplona.amethyst.commons.resources.num_selected
import com.vitorpamplona.amethyst.commons.resources.select_all
import com.vitorpamplona.amethyst.commons.resources.select_users_to_follow
import com.vitorpamplona.amethyst.commons.resources.skip_for_now
import com.vitorpamplona.amethyst.commons.resources.start_with_a_great_feed_by_following_the_same_people_as_someone_you_trust
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserLine
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

@Composable
fun ImportFollowListPickFollowsScreen(
    userHex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val contactListNote =
        remember(userHex) {
            LocalCache.getOrCreateAddressableNote(ContactListEvent.createAddress(userHex))
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {},
                nav = nav,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PickFollowsBody(
                contactListNote = contactListNote,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun PickFollowsBody(
    contactListNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        ImportHeader()
        Spacer(Modifier.height(20.dp))

        DisplayFollowList(contactListNote, Modifier.weight(1f), accountViewModel, nav)
    }
}

@Composable
fun DisplayFollowList(
    contactListNote: AddressableNote,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val contactsState by observeNoteEventAndMap<ContactListEvent, ImmutableList<User>?>(contactListNote, accountViewModel) { contactList ->
        contactList
            ?.unverifiedFollowKeySet()
            ?.toSet()
            ?.mapNotNull {
                LocalCache.checkGetOrCreateUser(it)
            }?.toPersistentList()
    }

    val contacts = contactsState

    if (contacts == null) {
        LoadingIndicator(stringRes(Res.string.fetching_follow_list), modifier, nav)
    } else if (contacts.isEmpty()) {
        ErrorMessage(stringRes(Res.string.no_follows_found), modifier, nav)
    } else {
        PreviewList(
            contacts,
            modifier,
            accountViewModel,
            nav,
        )
    }
}

@Composable
private fun PreviewList(
    contacts: ImmutableList<User>,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var selected by remember { mutableStateOf(setOf<User>()) }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringRes(Res.string.accounts_found, contacts.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringRes(Res.string.num_selected, selected.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Select all
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected.size == contacts.size,
                onCheckedChange = {
                    selected = if (it) contacts.toSet() else setOf()
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.select_all), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }

        HorizontalDivider()

        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.weight(1f)) {
            items(items = contacts, key = { it.pubkeyHex }) { entry ->
                FollowEntryRow(
                    user = entry,
                    isSelected = entry in selected,
                    onToggle = { selected = if (entry in selected) selected - entry else selected + entry },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    accountViewModel.follow(selected.toList())
                    nav.popUpTo(Route.Home, Route.Home::class)
                },
            ) {
                Text(stringRes(Res.string.follow_accounts, selected.size))
            }
        }
    }
}

@Composable
private fun LoadingIndicator(
    message: String,
    modifier: Modifier,
    nav: INav,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { nav.popUpTo(Route.Home, Route.Home::class) }) { Text(stringRes(Res.string.skip_for_now)) }
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    modifier: Modifier,
    nav: INav,
) {
    Column(modifier) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { nav.popUpTo(Route.Home, Route.Home::class) }) { Text(stringRes(Res.string.skip_for_now)) }
        }
    }
}

@Composable
private fun ImportHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                MaterialSymbols.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringRes(Res.string.select_users_to_follow),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringRes(Res.string.start_with_a_great_feed_by_following_the_same_people_as_someone_you_trust),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FollowEntryRow(
    user: User,
    isSelected: Boolean,
    onToggle: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isSelected) MaterialSymbols.CheckCircle else MaterialSymbols.Circle,
            contentDescription = null,
            tint =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        UserLine(user, accountViewModel) {
            nav.nav(Route.Profile(user.pubkeyHex))
        }
    }
}
