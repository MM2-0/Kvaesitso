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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupAdminCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMigrationDetector
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_migration_by_admins
import com.vitorpamplona.amethyst.commons.resources.relay_group_migration_by_friends
import com.vitorpamplona.amethyst.commons.resources.relay_group_migration_dismiss
import com.vitorpamplona.amethyst.commons.resources.relay_group_migration_fork_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_migration_move
import com.vitorpamplona.amethyst.commons.resources.relay_group_migration_moved_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_migration_open
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent

/** Cap on how many people's kind-10009 lists one open group fetches for the migration check. */
private const val MAX_WATCHED_LISTS = 24

/**
 * NIP-29 §Detecting migrations and forks, for a group the user is in: watches the kind-10009 lists
 * of the group's admins (live from kind 39001, plus the locally cached set so this still works while
 * the host relay is down) and of the members the user follows. When any of them lists this group id
 * on a different relay, a self-hiding bar tells the user the group may have moved (or forked) and
 * offers to open it on the new relay, or to move there — which rewrites the user's own kind-10009.
 */
@Composable
fun RelayGroupMigrationBar(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val joined by accountViewModel.account.relayGroupList.liveRelayGroupIds
        .collectAsStateWithLifecycle()
    if (channel.groupId !in joined) return

    val liveAdmins = channel.admins
    LaunchedEffect(channel.groupId, liveAdmins) {
        RelayGroupAdminCache.remember(channel.groupId, liveAdmins.mapTo(HashSet()) { it.pubKey })
    }
    val cachedAdmins by RelayGroupAdminCache.flow.collectAsStateWithLifecycle()

    val follows by accountViewModel.account.kind3FollowList.flow
        .collectAsStateWithLifecycle()

    val me = accountViewModel.userProfile().pubkeyHex
    val admins =
        remember(liveAdmins, cachedAdmins) {
            liveAdmins.mapTo(HashSet()) { it.pubKey } + (cachedAdmins[channel.groupId.toKey()] ?: emptySet())
        }
    val friends = remember(channel.memberCount(), follows, admins) { channel.participatingFollows(follows.authors).toSet() - admins }
    val watched = remember(admins, friends) { (admins + friends - me).take(MAX_WATCHED_LISTS) }

    // Fetches (via the event finder) and observes each watched person's kind-10009.
    val lists =
        watched.map { pubkey ->
            key(pubkey) {
                val note = remember(pubkey) { LocalCache.getOrCreateAddressableNote(SimpleGroupListEvent.createAddress(pubkey)) }
                observeNoteEvent<SimpleGroupListEvent>(note, accountViewModel).value
            }
        }

    val relocations =
        remember(lists, admins, friends) {
            RelayGroupMigrationDetector.detect(channel.groupId, lists.filterNotNull(), admins, friends, me)
        }

    var dismissed by rememberSaveable(channel.groupId.toKey()) { mutableStateOf(emptyList<String>()) }
    val relocation = relocations.firstOrNull { it.relay.url !in dismissed } ?: return

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text =
                                stringRes(
                                    if (relocation.looksLikeMove) {
                                        Res.string.relay_group_migration_moved_title
                                    } else {
                                        Res.string.relay_group_migration_fork_title
                                    },
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text =
                                stringRes(
                                    if (relocation.admins.isNotEmpty()) {
                                        Res.string.relay_group_migration_by_admins
                                    } else {
                                        Res.string.relay_group_migration_by_friends
                                    },
                                    relocation.relay.displayUrl(),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { dismissed = dismissed + relocation.relay.url }) {
                        Text(stringRes(Res.string.relay_group_migration_dismiss))
                    }
                    TextButton(onClick = { nav.nav(Route.RelayGroup(channel.groupId.id, relocation.relay.url)) }) {
                        Text(stringRes(Res.string.relay_group_migration_open))
                    }
                    TextButton(
                        onClick = {
                            accountViewModel.moveRelayGroup(channel, relocation.relay)
                            nav.nav(Route.RelayGroup(channel.groupId.id, relocation.relay.url))
                        },
                    ) {
                        Text(stringRes(Res.string.relay_group_migration_move))
                    }
                }
            }
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}
