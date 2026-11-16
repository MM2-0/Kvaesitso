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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip43

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip43RelayMembers.ui.RelayRoleChips
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_members_count
import com.vitorpamplona.amethyst.commons.resources.relay_members_empty
import com.vitorpamplona.amethyst.commons.resources.relay_members_invite_code
import com.vitorpamplona.amethyst.commons.resources.relay_members_invite_code_hint
import com.vitorpamplona.amethyst.commons.resources.relay_members_join_sent
import com.vitorpamplona.amethyst.commons.resources.relay_members_leave_sent
import com.vitorpamplona.amethyst.commons.resources.relay_members_loading
import com.vitorpamplona.amethyst.commons.resources.relay_members_request_join
import com.vitorpamplona.amethyst.commons.resources.relay_members_request_leave
import com.vitorpamplona.amethyst.commons.resources.relay_members_title
import com.vitorpamplona.amethyst.commons.resources.relay_members_unverifiable
import com.vitorpamplona.amethyst.commons.resources.relay_members_you_are_member
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserCompose
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.fetchAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.leaveRequest.RelayLeaveRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.tags.RelayMember
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayMembersScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val normalizedRelayUrl = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) }
    if (normalizedRelayUrl == null) return

    var members by remember { mutableStateOf<List<RelayMember>>(emptyList()) }
    var roles by remember { mutableStateOf<Map<String, RelayRole>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    // The relay publishes no NIP-11 `self`, so nothing it serves can be verified as relay-signed.
    var isUnverifiable by remember { mutableStateOf(false) }
    var isMember by remember { mutableStateOf(false) }
    var joinRequestSent by remember { mutableStateOf(false) }
    var leaveRequestSent by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // NIP-43 lists (13534) and roles (33534) MUST be signed by the relay's NIP-11 `self`. Resolve it
    // first (loading state meanwhile) and fetch once, by that author only. A relay that publishes no
    // `self` gets an explanatory state instead of lists anyone could have signed.
    LaunchedEffect(normalizedRelayUrl) {
        launch(Dispatchers.IO) {
            var relaySelf: HexKey? = null
            LocalCache.appHost.nip11Cache.loadRelayInfo(
                relay = normalizedRelayUrl,
                onInfo = { relaySelf = it.self },
                onError = { _, _, _ -> },
            )

            val self = relaySelf
            if (self == null) {
                members = emptyList()
                roles = emptyMap()
                isMember = false
                isUnverifiable = true
                isLoading = false
                return@launch
            }

            val authors = listOf(self)
            val filters =
                listOf(
                    Filter(kinds = listOf(RelayMembershipListEvent.KIND), authors = authors, limit = 1),
                    Filter(kinds = listOf(RelayRoleEvent.KIND), authors = authors),
                )

            val events =
                accountViewModel.account.client
                    .taggedAs(SubPurpose.RELAY_INFO, "Members")
                    .fetchAsFlow(normalizedRelayUrl, filters)
                    .lastOrNull()

            val membershipEvent =
                events
                    ?.mapNotNull { it as? RelayMembershipListEvent }
                    ?.filter { it.pubKey == self }
                    ?.maxByOrNull { it.createdAt }

            roles =
                events
                    ?.mapNotNull { it as? RelayRoleEvent }
                    ?.filter { it.pubKey == self }
                    ?.groupBy { it.roleId() }
                    ?.mapValues { (_, versions) -> versions.maxBy { it.createdAt }.role() }
                    ?: emptyMap()

            val memberList = membershipEvent?.membersWithRoles() ?: emptyList()
            members = memberList
            isMember = memberList.any { it.pubKey == accountViewModel.account.signer.pubKey }
            isUnverifiable = false
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringRes(Res.string.relay_members_title, normalizedRelayUrl.displayUrl()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
        ) {
            MembershipActions(
                isMember = isMember,
                isLoading = isLoading,
                joinRequestSent = joinRequestSent,
                leaveRequestSent = leaveRequestSent,
                inviteCode = inviteCode,
                onInviteCodeChange = { inviteCode = it },
                onJoinRequest = {
                    val claim = inviteCode.trim()
                    accountViewModel.launchSigner {
                        sendJoinRequest(normalizedRelayUrl, claim, accountViewModel)
                        joinRequestSent = true
                    }
                },
                onLeaveRequest = {
                    accountViewModel.launchSigner {
                        sendLeaveRequest(normalizedRelayUrl, accountViewModel)
                        leaveRequestSent = true
                    }
                },
            )

            HorizontalDivider()

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringRes(Res.string.relay_members_loading))
                }
            } else if (members.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        symbol = MaterialSymbols.People,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringRes(if (isUnverifiable) Res.string.relay_members_unverifiable else Res.string.relay_members_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = stringRes(Res.string.relay_members_count, members.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(members, key = { it.pubKey }) { member ->
                        val user = remember(member.pubKey) { accountViewModel.account.cache.getOrCreateUser(member.pubKey) }
                        Column {
                            UserCompose(
                                baseUser = user,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                            // Role ids without a published 33534 definition still show, by id.
                            val memberRoles = remember(member, roles) { member.roles.map { roles[it] ?: RelayRole(it) } }
                            RelayRoleChips(
                                roles = memberRoles,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MembershipActions(
    isMember: Boolean,
    isLoading: Boolean,
    joinRequestSent: Boolean,
    leaveRequestSent: Boolean,
    inviteCode: String,
    onInviteCodeChange: (String) -> Unit,
    onJoinRequest: () -> Unit,
    onLeaveRequest: () -> Unit,
) {
    if (!isLoading && !isMember && !joinRequestSent) {
        // NIP-43 join requests (kind 28934) must carry the invite code the relay issued.
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            Text(
                text = stringRes(Res.string.relay_members_invite_code_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = inviteCode,
                onValueChange = onInviteCodeChange,
                label = { Text(stringRes(Res.string.relay_members_invite_code)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) return@Row

        if (isMember) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    symbol = MaterialSymbols.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringRes(Res.string.relay_members_you_are_member),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (leaveRequestSent) {
                Text(
                    text = stringRes(Res.string.relay_members_leave_sent),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(
                    onClick = onLeaveRequest,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(Res.string.relay_members_request_leave))
                }
            }
        } else {
            if (joinRequestSent) {
                Text(
                    text = stringRes(Res.string.relay_members_join_sent),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Button(onClick = onJoinRequest, enabled = inviteCode.isNotBlank()) {
                    Icon(
                        symbol = MaterialSymbols.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(Res.string.relay_members_request_join))
                }
            }
        }
    }
}

@Preview
@Composable
private fun MembershipActionsNotMemberPreview() {
    ThemeComparisonColumn {
        MembershipActions(
            isMember = false,
            isLoading = false,
            joinRequestSent = false,
            leaveRequestSent = false,
            inviteCode = "",
            onInviteCodeChange = {},
            onJoinRequest = {},
            onLeaveRequest = {},
        )
    }
}

@Preview
@Composable
private fun MembershipActionsIsMemberPreview() {
    ThemeComparisonColumn {
        MembershipActions(
            isMember = true,
            isLoading = false,
            joinRequestSent = false,
            leaveRequestSent = false,
            inviteCode = "",
            onInviteCodeChange = {},
            onJoinRequest = {},
            onLeaveRequest = {},
        )
    }
}

@Preview
@Composable
private fun MembershipActionsJoinSentPreview() {
    ThemeComparisonColumn {
        MembershipActions(
            isMember = false,
            isLoading = false,
            joinRequestSent = true,
            leaveRequestSent = false,
            inviteCode = "",
            onInviteCodeChange = {},
            onJoinRequest = {},
            onLeaveRequest = {},
        )
    }
}

@Preview
@Composable
private fun MembershipActionsLeaveSentPreview() {
    ThemeComparisonColumn {
        MembershipActions(
            isMember = true,
            isLoading = false,
            joinRequestSent = false,
            leaveRequestSent = true,
            inviteCode = "",
            onInviteCodeChange = {},
            onJoinRequest = {},
            onLeaveRequest = {},
        )
    }
}

suspend fun sendJoinRequest(
    relay: NormalizedRelayUrl,
    claim: String,
    accountViewModel: AccountViewModel,
) {
    val template = RelayJoinRequestEvent.build(claim)
    val signedEvent = accountViewModel.account.signer.sign(template)
    accountViewModel.account.cache.justConsumeMyOwnEvent(signedEvent)
    accountViewModel.account.client.publish(signedEvent, setOf(relay))
}

suspend fun sendLeaveRequest(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
) {
    val template = RelayLeaveRequestEvent.build()
    val signedEvent = accountViewModel.account.signer.sign(template)
    accountViewModel.account.cache.justConsumeMyOwnEvent(signedEvent)
    accountViewModel.account.client.publish(signedEvent, setOf(relay))
}
