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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ConcordDirectInviteSendResult
import com.vitorpamplona.amethyst.commons.model.ConcordInviteResult
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordDirectInviteView
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_accept
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_accept_failed
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_catch_up
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_decline
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_done
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_expired
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_explainer
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_failed
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_failed_banned
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_failed_loading
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_failed_member
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_from
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_hint
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_send
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_sending
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_sent_to
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_title
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invites_title
import com.vitorpamplona.amethyst.commons.resources.concord_home_title
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_banned
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_expired
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_invalid
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_not_saved
import com.vitorpamplona.amethyst.commons.ui.components.ConcordInvitePreviewRow
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * "Invite by npub" (CORD-05 §6): pick a person with the app's ordinary user typeahead (cache, relay
 * search, NIP-05, a pasted npub/nprofile), then hand them the community's keys as a Direct Invite —
 * a giftwrap to their inbox relays carrying only the private channels their roles grant.
 */
@Composable
fun ConcordDirectInviteDialog(
    communityId: String,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<User?>(null) }
    var sending by remember { mutableStateOf(false) }

    // The outcome stays inside this dialog: the people invited so far (so several can be invited
    // in a row) and the last failure, shown under the field. A second modal to say "sent" was one
    // tap too many for a confirmation.
    val invited = remember { mutableStateListOf<String>() }
    var failure by remember { mutableStateOf<StringResource?>(null) }
    val userSuggestions =
        remember(accountViewModel) {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    LaunchedEffect(query) { userSuggestions.processCurrentWord(query) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringRes(Res.string.concord_direct_invite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.concord_direct_invite_explainer), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        picked = null
                        failure = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !sending,
                    label = { Text(stringRes(Res.string.concord_direct_invite_hint)) },
                )
                if (picked == null && query.length > 2) {
                    ShowUserSuggestionList(
                        userSuggestions = userSuggestions,
                        onSelect = { user ->
                            picked = user
                            query = user.toBestDisplayName()
                        },
                        accountViewModel = accountViewModel,
                        modifier = SuggestionListDefaultHeightChat,
                        itemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        showDividers = false,
                        contentPadding = PaddingValues(0.dp),
                    )
                }
                failure?.let {
                    Text(stringRes(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                invited.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SymbolIcon(symbol = MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(stringRes(Res.string.concord_direct_invite_sent_to, name), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            val target = picked
            TextButton(
                enabled = target != null && !sending,
                onClick = {
                    if (target == null) return@TextButton
                    sending = true
                    failure = null
                    scope.launch {
                        accountViewModel.reportSignerErrors {
                            try {
                                val result = accountViewModel.account.concord.sendConcordDirectInvite(communityId, target.pubkeyHex)
                                if (result == ConcordDirectInviteSendResult.SENT) {
                                    // Ready for the next person; the check mark below is the confirmation.
                                    invited += target.toBestDisplayName()
                                    picked = null
                                    query = ""
                                } else {
                                    // Keep the person picked so Send retries.
                                    failure = sendFailureMessage(result)
                                }
                            } finally {
                                sending = false
                            }
                        }
                    }
                },
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(Res.string.concord_direct_invite_sending))
                } else {
                    Text(stringRes(Res.string.concord_direct_invite_send, picked?.toBestDisplayName() ?: "…"))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) {
                Text(stringRes(if (invited.isEmpty()) Res.string.cancel else Res.string.concord_direct_invite_done))
            }
        },
    )
}

private fun sendFailureMessage(result: ConcordDirectInviteSendResult) =
    when (result) {
        ConcordDirectInviteSendResult.SENT -> null
        ConcordDirectInviteSendResult.ROSTER_NOT_LOADED -> Res.string.concord_direct_invite_failed_loading
        ConcordDirectInviteSendResult.RECIPIENT_BANNED -> Res.string.concord_direct_invite_failed_banned
        ConcordDirectInviteSendResult.NOT_MEMBER, ConcordDirectInviteSendResult.NOT_WRITEABLE -> Res.string.concord_direct_invite_failed_member
        ConcordDirectInviteSendResult.INVALID_RECIPIENT, ConcordDirectInviteSendResult.NOT_DELIVERED -> Res.string.concord_direct_invite_failed
    }

/**
 * Sweeps the stock Concord relays for Direct Invites once when the hub opens (invites on our DM relays
 * arrive live through the gift-wrap subscription). Call it once per screen, outside any lazy list:
 * inside a lazy item it would re-run every time the item scrolled back into view.
 */
@Composable
fun RefreshConcordDirectInvites(accountViewModel: AccountViewModel) {
    val concord = accountViewModel.account.concord
    // Sweeps in the account's scope: the hub leaves composition whenever it swaps layouts, which
    // cancelled a sweep launched here before any relay answered.
    LaunchedEffect(concord) { concord.requestConcordDirectInviteSweep() }
}

/**
 * The Direct Invites waiting for this account (CORD-05 §6), as lazy items with Accept / Decline —
 * shown at the top of the Concord communities list. Adds nothing when there are none.
 *
 * The preview is the bundle's own name and a robohash of the community id — **no** icon fetch, no
 * relay connection to the community, no Join happens before the user taps Accept. The sender is
 * shown by whatever name the cache already has, without fetching their profile.
 */
fun LazyListScope.concordPendingDirectInvites(
    invites: List<ConcordDirectInviteView>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (invites.isEmpty()) return
    item(key = "concord-direct-invites-title") {
        Text(
            stringRes(Res.string.concord_direct_invites_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
    }
    items(invites, key = { "concord-direct-invite-" + it.wrapId }) { invite ->
        Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            ConcordDirectInviteCard(invite, accountViewModel, nav)
        }
    }
}

/** One Direct Invite with Decline / Accept: on the Concord hub and as a card on Notifications. */
@Composable
fun ConcordDirectInviteCard(
    invite: ConcordDirectInviteView,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var working by remember(invite.wrapId) { mutableStateOf(false) }
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    // The sender's own profile (kind 0), loaded like any author's: the card used to show only a cached
    // name, so an inviter the app had never seen read as a bare npub. Loading a public profile
    // connects to none of the community's relays, which is what stays gated until Accept.
    val sender = remember(invite.sender) { LocalCache.checkGetOrCreateUser(invite.sender) }
    val senderName = sender?.let { observeUserName(it).value } ?: invite.sender.take(12)

    val subtitle =
        when {
            invite.expired -> stringRes(Res.string.concord_direct_invite_expired)
            invite.catchUp -> {
                // Only the channels it newly adds, named as the held community folds them.
                val names =
                    remember(invite) {
                        val folded =
                            accountViewModel.account.concordSessions
                                .sessionFor(invite.communityId)
                                ?.state
                                ?.value
                                ?.channels
                        invite.newChannelNames { id -> folded?.get(id)?.definition?.name }
                    }
                stringRes(Res.string.concord_direct_invite_catch_up, names.joinToString(", ") { "#$it" })
            }
            else -> stringRes(Res.string.concord_direct_invite_from, senderName)
        }

    ElevatedCard(Modifier.fillMaxWidth()) {
        ConcordInvitePreviewRow(
            robotSeed = invite.communityId,
            title = invite.name.ifBlank { stringRes(Res.string.concord_home_title) },
            subtitle = subtitle,
            accountViewModel = accountViewModel,
            autoPlayGif = autoPlayGif,
            trailing = {
                if (!invite.catchUp) UserPicture(invite.sender, Size35dp, accountViewModel = accountViewModel, nav = nav)
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        ) {
            OutlinedButton(
                enabled = !working,
                onClick = { accountViewModel.account.concord.declineConcordDirectInvite(invite.wrapId) },
            ) {
                Text(stringRes(Res.string.concord_direct_invite_decline))
            }
            Button(
                enabled = !working && !invite.expired,
                onClick = {
                    working = true
                    // The account hides an invite while it is being accepted, which removes this card
                    // from composition: the join runs on the view model's scope so that can't cancel it.
                    accountViewModel.viewModelScope.launch {
                        accountViewModel.reportSignerErrors {
                            try {
                                when (val result = accountViewModel.account.concord.acceptConcordDirectInvite(invite.wrapId)) {
                                    is ConcordInviteResult.Joined -> nav.nav(Route.ConcordServer(result.communityId))
                                    // The accept already running reports; this tap did nothing.
                                    is ConcordInviteResult.InProgress -> Unit
                                    is ConcordInviteResult.Expired -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_invite_failed_expired)
                                    is ConcordInviteResult.Banned -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_invite_failed_banned)
                                    is ConcordInviteResult.InvalidLink -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_invite_failed_invalid)
                                    is ConcordInviteResult.NotSaved -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_invite_failed_not_saved)
                                    else -> accountViewModel.toastManager.toast(Res.string.concord_direct_invites_title, Res.string.concord_direct_invite_accept_failed)
                                }
                            } finally {
                                working = false
                            }
                        }
                    }
                },
            ) {
                Text(stringRes(Res.string.concord_direct_invite_accept))
            }
        }
    }
}
