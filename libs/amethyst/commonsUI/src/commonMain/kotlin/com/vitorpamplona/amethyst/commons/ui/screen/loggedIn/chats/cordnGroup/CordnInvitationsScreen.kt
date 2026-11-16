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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable_detail
import com.vitorpamplona.amethyst.commons.resources.cordn_group_untitled
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_accept
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_decline
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_decline_warning
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_load_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_members
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_members_more
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_no_coordinators
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_none
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_refresh
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_skipped
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_title
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_unreachable
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_via
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CoordinatorIdentityRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.coordinatorDisplayName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.client.CordnInvitation
import com.vitorpamplona.cordn.client.CordnInvitations
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Invitations to cordn groups, waiting to be answered.
 *
 * ## Why this is a screen you open, not a badge that appears
 *
 * Finding out whether anyone has invited you means asking each coordinator,
 * and every call to a coordinator is metadata (`spec/00.md` §8). A live badge
 * would tell each of them how often this account opens the app, forever, to
 * save a tap. So the fetch happens when someone comes here, and the screen
 * says what it is showing and when it looked.
 *
 * ## What an invitation can honestly say
 *
 * The group's name, who is already in it, and which coordinator it came
 * through — all read out of the Welcome. Not "X invited you": a Welcome
 * carries the ratchet tree, not the identity of whoever signed the Commit
 * that produced it, so naming an inviter would be a guess presented as a
 * fact.
 *
 * Declining is permanent, and the screen says so rather than discovering it
 * afterwards. A retired Welcome is gone from the coordinator and the
 * KeyPackage it was addressed to has been spent; getting back in means being
 * invited again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnInvitationsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val scope = rememberCoroutineScope()

    var invitations by remember { mutableStateOf<CordnInvitations?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val loadFailed = stringRes(Res.string.cordn_invitations_load_failed)

    suspend fun reload() {
        busy = true
        error = null
        try {
            invitations = runtime?.invitations()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            error = e.message ?: loadFailed
        } finally {
            busy = false
        }
    }

    LaunchedEffect(runtime) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(Res.string.back))
                        }
                    }
                },
                title = { Text(stringRes(Res.string.cordn_invitations_title)) },
                actions = {
                    IconButton(onClick = { scope.launch { reload() } }, enabled = !busy) {
                        Icon(MaterialSymbols.Refresh, contentDescription = stringRes(Res.string.cordn_invitations_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (runtime == null) {
                EmptyState(
                    title = stringRes(Res.string.cordn_group_unavailable),
                    description = stringRes(Res.string.cordn_group_unavailable_detail),
                )
                return@Column
            }

            // Collected rather than read as `.value` further down: the empty
            // state says either "you have no coordinators" or "nobody has
            // invited you", and adding a coordinator in another tab has to
            // change which of those two this screen is showing.
            val coordinators by runtime.coordinators.collectAsStateWithLifecycle()

            Text(
                text = stringRes(Res.string.cordn_invitations_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (busy) CircularProgressIndicator()

            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            val loaded = invitations
            if (loaded != null && !busy) {
                loaded.pending.forEach { invitation ->
                    InvitationCard(
                        invitation = invitation,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onAccept = {
                            scope.launch {
                                try {
                                    val gid = runtime.accept(invitation)
                                    nav.nav(Route.CordnGroupChat(invitation.coordinator.pubKey, gid))
                                } catch (e: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    error = e.message ?: loadFailed
                                    reload()
                                }
                            }
                        },
                        onDecline = {
                            scope.launch {
                                try {
                                    runtime.decline(invitation)
                                } catch (e: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    error = e.message ?: loadFailed
                                }
                                reload()
                            }
                        },
                    )
                }

                // A coordinator that did not answer is its own row. Folding it
                // into "nothing waiting" would tell someone they have no
                // invitations when the truth is that nobody asked.
                loaded.unreachable.forEach {
                    NoticeCard(
                        title =
                            stringRes(
                                Res.string.cordn_invitations_unreachable,
                                coordinatorDisplayName(it.coordinator.pubKey, it.coordinator.label, accountViewModel),
                            ),
                        detail = it.reason,
                        isError = true,
                    )
                }

                // Left on the coordinator for another device of this account.
                // Shown rather than hidden, because otherwise the invitation a
                // friend swears they sent simply does not exist here.
                loaded.skipped.forEach {
                    NoticeCard(
                        title = stringRes(Res.string.cordn_invitations_skipped),
                        detail = it.reason,
                        isError = false,
                    )
                }

                if (loaded.isEmpty) {
                    Text(
                        text =
                            if (coordinators.isEmpty()) {
                                stringRes(Res.string.cordn_invitations_no_coordinators)
                            } else {
                                stringRes(Res.string.cordn_invitations_none)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Faces on the invitation card before it folds into "+N". */
private const val MEMBER_FACES = 5

/** How many of them are also named in full underneath. */
private const val MEMBER_NAMES = 3

@Composable
private fun InvitationCard(
    invitation: CordnInvitation,
    accountViewModel: AccountViewModel,
    nav: INav,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val welcome = invitation.welcome

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = welcome.metadata?.name?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.cordn_group_untitled, welcome.gid.take(8)),
                style = MaterialTheme.typography.titleMedium,
            )
            welcome.metadata?.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            // "Already in it", never "invited you" — see the screen KDoc.
            Text(
                text = pluralStringResource(Res.plurals.cordn_invitations_members, welcome.members.size, welcome.members.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Who, not just how many. This is the moment someone decides
            // whether to join an encrypted group, and the roster is already in
            // the Welcome — printing only its size withheld the one fact the
            // decision actually turns on.
            if (welcome.members.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    welcome.members.take(MEMBER_FACES).forEach { member ->
                        UserPicture(
                            userHex = member,
                            size = 28.dp,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                    if (welcome.members.size > MEMBER_FACES) {
                        Text(
                            text = stringRes(Res.string.cordn_invitations_members_more, welcome.members.size - MEMBER_FACES),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Resolved through `map`, which is inline and so keeps the
                // composable context; joinToString's transform is not.
                val names = welcome.members.take(MEMBER_NAMES).map { observeUserNameByHex(it, accountViewModel) }
                Text(
                    // Named in full for the first few, so a decision does not
                    // rest on recognising an avatar.
                    text = names.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Accepting is a decision about the coordinator as much as about
            // the group -- it is the party that will hold the membership and
            // serve the messages -- so it is named and pictured rather than
            // abbreviated to a key.
            CoordinatorIdentityRow(
                pubKey = invitation.coordinator.pubKey,
                label = invitation.coordinator.label,
                accountViewModel = accountViewModel,
                nav = nav,
                size = 20.dp,
            ) { name ->
                Text(
                    text = stringRes(Res.string.cordn_invitations_via, name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onAccept) { Text(stringRes(Res.string.cordn_invitations_accept)) }
                TextButton(onClick = onDecline) { Text(stringRes(Res.string.cordn_invitations_decline)) }
            }
            Text(
                text = stringRes(Res.string.cordn_invitations_decline_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoticeCard(
    title: String,
    detail: String,
    isError: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
