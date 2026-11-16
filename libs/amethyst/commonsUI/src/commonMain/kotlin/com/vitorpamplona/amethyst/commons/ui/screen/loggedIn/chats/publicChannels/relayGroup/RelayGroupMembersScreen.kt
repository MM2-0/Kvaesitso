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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.ui.PresenceDot
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzAgentActivityState
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMembership
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_add_people_hint
import com.vitorpamplona.amethyst.commons.resources.buzz_agent_working
import com.vitorpamplona.amethyst.commons.resources.buzz_import_added
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.relay_group_add_member
import com.vitorpamplona.amethyst.commons.resources.relay_group_assign_role
import com.vitorpamplona.amethyst.commons.resources.relay_group_demote_member
import com.vitorpamplona.amethyst.commons.resources.relay_group_make_admin
import com.vitorpamplona.amethyst.commons.resources.relay_group_make_moderator
import com.vitorpamplona.amethyst.commons.resources.relay_group_members_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_remove_user
import com.vitorpamplona.amethyst.commons.resources.relay_group_remove_user_confirm
import com.vitorpamplona.amethyst.commons.resources.relay_group_role_admin
import com.vitorpamplona.amethyst.commons.resources.relay_group_role_moderator
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.LoadRelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.aoObserver.ObserverFrameEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay

/**
 * The roster of a NIP-29 group: everyone the relay lists as an admin (kind 39001)
 * or member (kind 39002), each with their role badge. A moderator (admin or
 * moderator role) additionally gets a per-user overflow menu to promote/demote
 * (kind 9000 put-user) and kick (kind 9001 remove-user); the host relay enforces
 * the actual permission, this UI just surfaces the actions.
 */
@Composable
fun RelayGroupMembersScreen(
    id: HexKey,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val channelId = remember(id, relay) { GroupId(id, relay) }

    LoadRelayGroupChannel(channelId) { channel ->
        RelayGroupMembers(channel, accountViewModel, nav)
    }
}

private class RosterEntry(
    val pubkey: HexKey,
    val membership: RelayGroupMembership,
    // The relay-assigned role names for this member (from the kind-39001 admin list),
    // e.g. ["admin"], ["ceo", "gardener"]. Empty for plain members. Used to show the
    // real relay-defined role label instead of only the coarse admin/moderator bucket.
    val roles: List<String>,
)

@Composable
private fun RelayGroupMembers(
    baseChannel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Fetch the relay-signed roster (kinds 39001/39002) from the host relay while this
    // standalone screen is open. observeChannel alone does NOT do this for a relay group
    // (its finder only handles public-chat / live-activity channels), so without this the
    // screen would only show whatever the chat screen happened to cache.
    RelayGroupCardWarmupSubscription(baseChannel, accountViewModel.dataSources().relayGroupCardWarmup, accountViewModel)

    // Recompose when the relay-signed roster (39001/39002) changes.
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val myPubkey = accountViewModel.userProfile().pubkeyHex
    val iCanModerate = channel.membershipOf(myPubkey).canModerate()
    val iAmAdmin = channel.membershipOf(myPubkey) == RelayGroupMembership.ADMIN

    // Feed the process-wide agent-activity state while this screen is open so a "Working…" line
    // can light up next to an agent that's currently running. Observer frames (24200) are
    // ephemeral and `#p`-addressed to the owner, so only the owner ever sees them — a non-owner
    // simply never records anything and no indicator shows. Buzz relays only.
    val relay = channel.groupId.relayUrl
    LaunchedEffect(relay, myPubkey) {
        if (!BuzzRelayDialect.isBuzz(relay)) return@LaunchedEffect
        val filter = Filter(kinds = listOf(ObserverFrameEvent.KIND), tags = mapOf("p" to listOf(myPubkey)))
        accountViewModel.account.client.taggedAs(SubPurpose.RELAY_GROUPS).subscribeAsFlow(relay, filter).collect { events ->
            events.filterIsInstance<ObserverFrameEvent>().forEach { frame ->
                BuzzAgentActivityState.record(frame.agentPubKey() ?: frame.pubKey, frame.createdAt)
            }
        }
    }

    // Admins/moderators first, then plain members; alphabetical only inside a rank
    // is overkill here — rely on relay order but push elevated roles to the top.
    val roster =
        remember(channel.admins, channel.members) {
            val rolesByPubkey = channel.admins.associate { it.pubKey to it.roles }
            val everyone = (channel.admins.map { it.pubKey } + channel.members).distinct()
            everyone
                .map { RosterEntry(it, channel.membershipOf(it), rolesByPubkey[it] ?: emptyList()) }
                .sortedBy { it.membership.rank() }
        }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Column {
                        Text(
                            text = stringRes(Res.string.relay_group_members_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = channel.toBestDisplayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                nav = nav,
            )
        },
        // Only a moderator can add a member (the relay rejects a kind-9000 from anyone else).
        // The search sits at the bottom of the screen rather than behind a button: adding people is
        // usually adding *several*, and a dialog made that "open, search, pick, dialog closes,
        // reopen" per person. Inline, each pick lands in the roster above while the field keeps
        // focus for the next name.
        bottomBar = {
            if (iCanModerate) {
                AddMemberBar(
                    isAlreadyIn = { channel.membershipOf(it) != RelayGroupMembership.NONE },
                    onAdd = { accountViewModel.addRelayGroupUser(channel, it) },
                    accountViewModel = accountViewModel,
                )
            }
        },
    ) { padding ->
        if (roster.isEmpty()) {
            // The roster always has at least the group's admins once it loads, so an
            // empty list means the kind-39001/39002 events haven't arrived yet.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                itemsIndexed(roster, key = { _, entry -> entry.pubkey }) { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    RelayGroupMemberRow(
                        entry = entry,
                        channel = channel,
                        isSelf = entry.pubkey == myPubkey,
                        viewerCanModerate = iCanModerate,
                        viewerIsAdmin = iAmAdmin,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

/**
 * The always-present "add a member" search docked at the bottom of the roster: the app's ordinary
 * user typeahead (local cache + relay + NIP-05 + a pasted npub), with its results rising above the
 * field the way a chat composer's suggestions do.
 *
 * Picking someone adds them and clears the query but keeps the keyboard, so a moderator can add a
 * handful of people in one pass. Someone already in the group shows an "Added" hint instead of the
 * add icon and does nothing when tapped — the relay would reject the duplicate anyway.
 */
@Composable
private fun AddMemberBar(
    isAlreadyIn: (HexKey) -> Boolean,
    onAdd: (HexKey) -> Unit,
    accountViewModel: AccountViewModel,
) {
    var query by remember { mutableStateOf("") }
    val userSuggestions =
        remember(accountViewModel) {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    LaunchedEffect(query) { userSuggestions.processCurrentWord(query) }

    // Docked at the bottom, so it has to clear the gesture bar and ride above the keyboard —
    // otherwise the field it is meant to be typed into is the part that gets covered.
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePaddingSafe(),
    ) {
        if (query.length > 2) {
            ShowUserSuggestionList(
                userSuggestions = userSuggestions,
                onSelect = { user ->
                    if (!isAlreadyIn(user.pubkeyHex)) {
                        onAdd(user.pubkeyHex)
                        query = ""
                    }
                },
                accountViewModel = accountViewModel,
                modifier = SuggestionListDefaultHeightChat,
                contentPadding = PaddingValues(0.dp),
                trailingContent = { user ->
                    if (isAlreadyIn(user.pubkeyHex)) {
                        Text(
                            text = stringRes(Res.string.buzz_import_added),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            symbol = MaterialSymbols.PersonAdd,
                            contentDescription = stringRes(Res.string.relay_group_add_member),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = {
                Icon(
                    symbol = MaterialSymbols.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            label = { Text(stringRes(Res.string.buzz_add_people_hint)) },
        )
    }
}

/** Sort key so admins float above moderators above plain members. */
private fun RelayGroupMembership.rank(): Int =
    when (this) {
        RelayGroupMembership.ADMIN -> 0
        RelayGroupMembership.MODERATOR -> 1
        else -> 2
    }

@Composable
private fun RelayGroupMemberRow(
    entry: RosterEntry,
    channel: RelayGroupChannel,
    isSelf: Boolean,
    viewerCanModerate: Boolean,
    viewerIsAdmin: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isBuzzRelay = remember(channel.groupId.relayUrl) { BuzzRelayDialect.isBuzz(channel.groupId.relayUrl) }

    // Create-or-get (never a one-shot null): UsernameDisplay observes the user's
    // metadata flow, so the name fills in when the kind:0 arrives instead of being
    // stuck on truncated hex forever.
    val user = remember(entry.pubkey) { LocalCache.checkGetOrCreateUser(entry.pubkey) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            UserPicture(entry.pubkey, Size35dp, accountViewModel = accountViewModel, nav = nav)
            PresenceDot(entry.pubkey, Modifier.align(Alignment.BottomEnd), ringColor = MaterialTheme.colorScheme.surface)
        }

        Column(Modifier.weight(1f)) {
            if (user != null) {
                UsernameDisplay(user, accountViewModel = accountViewModel)
            } else {
                Text(
                    text = entry.pubkey.take(8),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A live "Working…" line for an agent member currently emitting observer frames;
            // tapping it opens this community's Agent Console (Observer tab).
            BotWorkingLabel(entry.pubkey) { nav.nav(Route.AgentConsole(channel.groupId.relayUrl.url)) }
        }

        MemberRoleBadge(entry)

        // Moderators can act on others (not themselves); the relay is the final
        // authority, but hide obviously-useless menus (a moderator can't touch an admin).
        val canActOnTarget =
            viewerCanModerate &&
                !isSelf &&
                (viewerIsAdmin || entry.membership != RelayGroupMembership.ADMIN)

        if (canActOnTarget) {
            // Button and menu share one Box: an expanded DropdownMenu still emits a node into its
            // parent, so as a direct child of this `spacedBy` Row it added a second 12.dp gap and
            // visibly nudged the button sideways the moment you tapped it.
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        symbol = MaterialSymbols.MoreVert,
                        contentDescription = stringRes(Res.string.more_options),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val declaredRoles = channel.groupRoles
                    if (declaredRoles.isNotEmpty()) {
                        // The relay declares its own role set (kind 39003) — offer exactly those
                        // instead of the built-in admin/moderator pair. Roles are privilege grants,
                        // so only admins assign them; the relay is the final authority.
                        if (viewerIsAdmin) {
                            declaredRoles.forEach { role ->
                                val alreadyHasRole = entry.roles.any { it.equals(role.name, true) }
                                if (!alreadyHasRole) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(stringRes(Res.string.relay_group_assign_role, role.name))
                                                role.description?.let {
                                                    Text(
                                                        text = it,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            menuOpen = false
                                            // Additive: NIP-29 allows multiple roles per member and the menu only
                                            // offers roles they lack, so keep the ones they already hold.
                                            accountViewModel.putRelayGroupUser(channel, entry.pubkey, entry.roles + role.name)
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        // No 39003 role set advertised: fall back to the built-in admin/moderator shortcuts.
                        if (viewerIsAdmin && entry.membership != RelayGroupMembership.ADMIN) {
                            DropdownMenuItem(
                                text = { Text(stringRes(Res.string.relay_group_make_admin)) },
                                onClick = {
                                    menuOpen = false
                                    accountViewModel.putRelayGroupUser(channel, entry.pubkey, listOf(RelayGroupMembership.ROLE_ADMIN))
                                },
                            )
                        }
                        // Buzz's roles are owner/admin/member/guest/bot — there is no moderator, and a
                        // role it cannot parse fails the whole put-user. Offering it there is offering a
                        // menu item that cannot do anything.
                        if (!isBuzzRelay && entry.membership != RelayGroupMembership.MODERATOR && entry.membership != RelayGroupMembership.ADMIN) {
                            DropdownMenuItem(
                                text = { Text(stringRes(Res.string.relay_group_make_moderator)) },
                                onClick = {
                                    menuOpen = false
                                    accountViewModel.putRelayGroupUser(channel, entry.pubkey, listOf(RelayGroupMembership.ROLE_MODERATOR))
                                },
                            )
                        }
                    }
                    if (entry.membership == RelayGroupMembership.MODERATOR || entry.membership == RelayGroupMembership.ADMIN) {
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.relay_group_demote_member)) },
                            onClick = {
                                menuOpen = false
                                accountViewModel.putRelayGroupUser(channel, entry.pubkey, emptyList())
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringRes(Res.string.relay_group_remove_user),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            confirmRemove = true
                        },
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        val displayName = user?.toBestDisplayName() ?: entry.pubkey.take(8)
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringRes(Res.string.relay_group_remove_user)) },
            text = { Text(stringRes(Res.string.relay_group_remove_user_confirm, displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    accountViewModel.removeRelayGroupUser(channel, entry.pubkey)
                }) {
                    Text(stringRes(Res.string.relay_group_remove_user), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }
}

/** How recent an observer frame must be for an agent to read as "Working…" right now. */
private const val BOT_WORKING_WINDOW_SECS = 90L

/**
 * A live "Working…" line shown for an agent member that emitted an observer frame within the last
 * [BOT_WORKING_WINDOW_SECS]. Renders nothing for a normal member (no frames) or a stale agent, and
 * re-checks freshness on a slow tick so it fades out when the agent goes quiet.
 */
@Composable
private fun BotWorkingLabel(
    agent: HexKey,
    onClick: () -> Unit,
) {
    val activity by BuzzAgentActivityState.flow.collectAsStateWithLifecycle()
    val last = activity[agent] ?: return

    var nowSecs by remember { mutableLongStateOf(TimeUtils.now()) }
    // Tick only while the agent could still read as "working"; stop once stale so an idle agent
    // doesn't wake this row every 4s forever. A newer frame changes `last` and restarts the tick.
    LaunchedEffect(agent, last) {
        while (TimeUtils.now() - last <= BOT_WORKING_WINDOW_SECS) {
            nowSecs = TimeUtils.now()
            delay(4_000)
        }
        nowSecs = TimeUtils.now()
    }
    if (nowSecs - last > BOT_WORKING_WINDOW_SECS) return

    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Text(
            text = stringRes(Res.string.buzz_agent_working),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A small colored pill for an elevated role; plain members get nothing.
 *
 * When the relay assigns explicit role labels (kind 39001), those are shown verbatim
 * (e.g. `ceo`, `moderator`) so relay-defined roles are visible rather than collapsed
 * into the coarse admin/moderator bucket. Falls back to the generic admin/moderator
 * label when the member is in the admin list without any named role.
 */
@Composable
private fun MemberRoleBadge(entry: RosterEntry) {
    val label =
        when {
            entry.roles.isNotEmpty() -> entry.roles.joinToString(", ")
            entry.membership == RelayGroupMembership.ADMIN -> stringRes(Res.string.relay_group_role_admin)
            entry.membership == RelayGroupMembership.MODERATOR -> stringRes(Res.string.relay_group_role_moderator)
            else -> return
        }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
