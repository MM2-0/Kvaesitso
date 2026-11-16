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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.BuzzDmListViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_to_messages
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_add_hint
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_add_member
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_add_member_invalid
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_add_member_title
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_empty_body
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_empty_title
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_hidden_count
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_just_you
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_more
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_new
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_title
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.remove_from_messages
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.TimeAgoLabels
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeAgoLabels
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * Bottom room the inbox leaves for its extended FAB — same clearance the community view uses, and
 * wide enough for the taller extended variant.
 */
private val FAB_CLEARANCE = 96.dp

/**
 * The **Buzz Direct Messages** inbox. A Buzz DM is a relay-authoritative NIP-29 group
 * whose id is a UUID, so tapping a row opens the very same [Route.RelayGroup] chat screen
 * every workspace channel uses — this screen only lists the conversations (discovered from
 * the relay's kind-41001 confirmations via [BuzzDmListViewModel]) and starts new ones.
 */
@Composable
fun BuzzDmListScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pubkey = accountViewModel.account.userProfile().pubkeyHex
    val viewModel: BuzzDmListViewModel = rememberViewModel(key = "BuzzDmList-$relayUrl-$pubkey") { BuzzDmListViewModel() }
    viewModel.bind(accountViewModel.account, relayUrl)

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val hiddenRows by viewModel.hiddenRows.collectAsStateWithLifecycle()

    // Hidden conversations stay collapsed behind a header — they are off Messages by the user's own
    // choice, so they must not compete with the live inbox; they only need to be *reachable* again.
    var showHidden by remember { mutableStateOf(false) }
    // Resolved once for the list, not per row.
    val timeLabels = rememberTimeAgoLabels()

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.buzz_dm_title), nav) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringRes(Res.string.buzz_dm_new)) },
                icon = { Icon(symbol = MaterialSymbols.Add, contentDescription = null) },
                onClick = { nav.nav(Route.BuzzNewDm(relayUrl)) },
            )
        },
    ) { padding ->
        if (rows.isEmpty() && hiddenRows.isEmpty()) {
            EmptyDmInbox(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                // Extra room at the bottom so the last row's overflow clears the FAB, which the
                // Scaffold's padding deliberately doesn't account for (a FAB overlays content).
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = FAB_CLEARANCE),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.channelId }) { row ->
                    DmRowCard(row, isHidden = false, viewModel = viewModel, timeLabels = timeLabels, accountViewModel = accountViewModel, nav = nav)
                }
                if (hiddenRows.isNotEmpty()) {
                    item(key = "hidden-header") {
                        HiddenDmHeader(
                            count = hiddenRows.size,
                            expanded = showHidden,
                            onToggle = { showHidden = !showHidden },
                        )
                    }
                    if (showHidden) {
                        items(hiddenRows, key = { "hidden-${it.channelId}" }) { row ->
                            DmRowCard(row, isHidden = true, viewModel = viewModel, timeLabels = timeLabels, accountViewModel = accountViewModel, nav = nav)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The collapsible "Hidden (N)" divider between the live inbox and the conversations I took off it.
 * Shared with the community view's inline Direct Messages section, so a hidden DM is reachable from
 * wherever the user's DMs are — this inbox screen is only reachable behind a "see all" row.
 */
@Composable
fun HiddenDmHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandMore else MaterialSymbols.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = pluralStringRes(Res.plurals.buzz_dm_hidden_count, count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One conversation: a participant avatar (stacked for group DMs), names, host, last-seen, kebab. */
@Composable
private fun DmRowCard(
    row: BuzzDmListViewModel.DmRow,
    isHidden: Boolean,
    viewModel: BuzzDmListViewModel,
    timeLabels: TimeAgoLabels,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val groupId = remember(row.channelId, row.relayUrl) { GroupId(row.channelId, row.relayUrl) }
    var menuOpen by remember { mutableStateOf(false) }
    var addMemberOpen by remember { mutableStateOf(false) }

    if (addMemberOpen) {
        AddDmMemberDialog(
            onDismiss = { addMemberOpen = false },
            onAdd = { hex ->
                addMemberOpen = false
                accountViewModel.launchSigner {
                    val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
                    accountViewModel.account.relayGroups.addBuzzDmMember(channel, hex)
                }
            },
        )
    }

    Card(
        onClick = { nav.nav(Route.RelayGroup(groupId.id, groupId.relayUrl.url)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            // A hidden DM is still openable (it's a live conversation I merely took off the list), so
            // it renders faded rather than disabled — visibly parked, not broken.
            modifier = Modifier.padding(12.dp).alpha(if (isHidden) 0.55f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DmAvatars(row.others, accountViewModel, nav)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                DmTitle(row.others, accountViewModel)
                Text(
                    text = row.relayUrl.displayUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeAgoShort(row.lastActivity, timeLabels),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                Icon(
                    symbol = MaterialSymbols.MoreVert,
                    contentDescription = stringRes(Res.string.buzz_dm_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable { menuOpen = true }
                            .size(28.dp)
                            .padding(4.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringRes(Res.string.buzz_dm_add_member)) },
                        leadingIcon = {
                            Icon(
                                symbol = MaterialSymbols.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            addMemberOpen = true
                        },
                    )
                    // A toggle, like the channel rows: hiding a DM is a per-viewer, reversible
                    // relay-side flag (kind-41012 / the 30622 snapshot), never a departure — so a
                    // hidden conversation must offer its own way back rather than vanishing for good.
                    DropdownMenuItem(
                        text = { Text(stringRes(if (isHidden) Res.string.add_to_messages else Res.string.remove_from_messages)) },
                        leadingIcon = {
                            Icon(
                                symbol = if (isHidden) MaterialSymbols.Add else MaterialSymbols.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            if (isHidden) viewModel.addToMessages(row) else viewModel.removeFromMessages(row)
                        },
                    )
                }
            }
        }
    }
}

/** The DM title: the other participants' display names, comma-joined (or a "+N" tail). */
@Composable
private fun DmTitle(
    others: List<HexKey>,
    accountViewModel: AccountViewModel,
) {
    // `map` is inline (a @Composable call is legal inside it); resolve every shown name
    // first, then join the plain strings — joinToString is NOT inline so can't call one.
    val text =
        if (others.isEmpty()) {
            stringRes(Res.string.buzz_dm_just_you)
        } else {
            val shown = others.take(2).map { UserName(it, accountViewModel) }
            val joined = shown.joinToString(", ")
            if (others.size > 2) "$joined +${others.size - 2}" else joined
        }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A resolved display name for [userHex] (falls back to a short pubkey while loading). */
@Composable
private fun UserName(
    userHex: HexKey,
    accountViewModel: AccountViewModel,
): String {
    val user: User = remember(userHex) { LocalCache.getOrCreateUser(userHex) }
    val name by observeUserName(user, accountViewModel)
    return name
}

/** A single avatar for a 1:1 DM, or two overlapping avatars for a group DM. */
@Composable
private fun DmAvatars(
    others: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when {
        others.isEmpty() ->
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        others.size == 1 -> UserPicture(others[0], 44.dp, accountViewModel = accountViewModel, nav = nav)
        else ->
            Box(modifier = Modifier.size(44.dp)) {
                UserPicture(
                    others[1],
                    30.dp,
                    pictureModifier = Modifier.align(Alignment.BottomEnd),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                UserPicture(
                    others[0],
                    30.dp,
                    pictureModifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-2).dp, y = (-2).dp),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
    }
}

/**
 * Dialog to add another person to an existing group DM: accepts an npub or 64-char hex key,
 * validates it locally, and hands the resolved hex to [onAdd] (which publishes kind-41011).
 */
@Composable
private fun AddDmMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (HexKey) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMsg = stringRes(Res.string.buzz_dm_add_member_invalid)

    fun submit() {
        val hex = decodePublicKeyAsHexOrNull(input.trim())?.takeIf { it.isValid() }
        if (hex == null) {
            error = invalidMsg
        } else {
            onAdd(hex)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.buzz_dm_add_member_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(Res.string.buzz_dm_add_hint)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = input.isNotBlank()) {
                Text(stringRes(Res.string.buzz_dm_add_member))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

/** Inviting empty state for a fresh DM inbox. */
@Composable
private fun EmptyDmInbox(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(24.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = stringRes(Res.string.buzz_dm_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringRes(Res.string.buzz_dm_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
