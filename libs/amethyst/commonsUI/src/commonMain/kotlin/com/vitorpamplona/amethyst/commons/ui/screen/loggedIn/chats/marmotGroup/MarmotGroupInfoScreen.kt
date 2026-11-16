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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.leave
import com.vitorpamplona.amethyst.commons.resources.marmot_add_member
import com.vitorpamplona.amethyst.commons.resources.marmot_add_member_placeholder
import com.vitorpamplona.amethyst.commons.resources.marmot_add_to_group
import com.vitorpamplona.amethyst.commons.resources.marmot_adding_user
import com.vitorpamplona.amethyst.commons.resources.marmot_admin_granted
import com.vitorpamplona.amethyst.commons.resources.marmot_admin_revoked
import com.vitorpamplona.amethyst.commons.resources.marmot_disband_group
import com.vitorpamplona.amethyst.commons.resources.marmot_disband_group_action
import com.vitorpamplona.amethyst.commons.resources.marmot_disband_group_confirm
import com.vitorpamplona.amethyst.commons.resources.marmot_edit_group_info
import com.vitorpamplona.amethyst.commons.resources.marmot_enable_encrypted_media
import com.vitorpamplona.amethyst.commons.resources.marmot_enable_encrypted_media_explainer
import com.vitorpamplona.amethyst.commons.resources.marmot_enable_encrypted_media_needs_server
import com.vitorpamplona.amethyst.commons.resources.marmot_encrypted_media_enabled_toast
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_add_user
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_disband
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_enable_encrypted_media
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_grant_admin
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_leave_group
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_remove_member
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_revoke_admin
import com.vitorpamplona.amethyst.commons.resources.marmot_grant
import com.vitorpamplona.amethyst.commons.resources.marmot_grant_admin_confirm
import com.vitorpamplona.amethyst.commons.resources.marmot_grant_admin_privileges
import com.vitorpamplona.amethyst.commons.resources.marmot_grant_admin_title
import com.vitorpamplona.amethyst.commons.resources.marmot_group_disbanded_toast
import com.vitorpamplona.amethyst.commons.resources.marmot_group_disbanding_toast
import com.vitorpamplona.amethyst.commons.resources.marmot_group_info_title
import com.vitorpamplona.amethyst.commons.resources.marmot_keypackage_required
import com.vitorpamplona.amethyst.commons.resources.marmot_leave_group
import com.vitorpamplona.amethyst.commons.resources.marmot_leave_group_confirm
import com.vitorpamplona.amethyst.commons.resources.marmot_legacy_group_no_disband
import com.vitorpamplona.amethyst.commons.resources.marmot_member_count
import com.vitorpamplona.amethyst.commons.resources.marmot_member_removed
import com.vitorpamplona.amethyst.commons.resources.marmot_member_suffix_admin
import com.vitorpamplona.amethyst.commons.resources.marmot_member_suffix_you
import com.vitorpamplona.amethyst.commons.resources.marmot_relay_last_event
import com.vitorpamplona.amethyst.commons.resources.marmot_relay_no_events
import com.vitorpamplona.amethyst.commons.resources.marmot_relays_header
import com.vitorpamplona.amethyst.commons.resources.marmot_remove_member
import com.vitorpamplona.amethyst.commons.resources.marmot_remove_member_confirm
import com.vitorpamplona.amethyst.commons.resources.marmot_retention_active
import com.vitorpamplona.amethyst.commons.resources.marmot_revoke
import com.vitorpamplona.amethyst.commons.resources.marmot_revoke_admin_confirm
import com.vitorpamplona.amethyst.commons.resources.marmot_revoke_admin_privileges
import com.vitorpamplona.amethyst.commons.resources.marmot_revoke_admin_title
import com.vitorpamplona.amethyst.commons.resources.marmot_this_group
import com.vitorpamplona.amethyst.commons.resources.marmot_unknown_error
import com.vitorpamplona.amethyst.commons.resources.marmot_user_fallback_name
import com.vitorpamplona.amethyst.commons.resources.members
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.marmotGroupTitle
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.marmotOtherMembers
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.MediumRelayIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.ripple24dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.client.GroupMemberInfo
import com.vitorpamplona.marmot.groups.MarmotGroupPermissions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarmotGroupInfoScreen(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val marmotEnableEncryptedMediaNeedsServerStr = stringRes(Res.string.marmot_enable_encrypted_media_needs_server)
    val chatroom =
        remember(nostrGroupId) {
            accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        }
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val groupDescription by chatroom.description.collectAsStateWithLifecycle()
    val adminPubkeys by chatroom.adminPubkeys.collectAsStateWithLifecycle()
    val groupRelays by chatroom.relays.collectAsStateWithLifecycle()
    val relayActivity by chatroom.relayActivity.collectAsStateWithLifecycle()
    val members by chatroom.members.collectAsStateWithLifecycle()
    val isCurrentProfile by chatroom.isCurrentProfile.collectAsStateWithLifecycle()
    val hasEncryptedMedia by chatroom.hasEncryptedMediaPolicy.collectAsStateWithLifecycle()
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDisbandDialog by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var isDisbanding by remember { mutableStateOf(false) }
    var isEnablingMedia by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<GroupMemberInfo?>(null) }
    var memberToPromote by remember { mutableStateOf<GroupMemberInfo?>(null) }
    var memberToDemote by remember { mutableStateOf<GroupMemberInfo?>(null) }
    var addSearchInput by remember { mutableStateOf("") }
    var addStatus by remember { mutableStateOf<String?>(null) }
    var isAddError by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myPubkey = accountViewModel.account.signer.pubKey
    val shortNotice = rememberShortNotice()
    val longNotice = rememberLongNotice()

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                            )
                        }
                    }
                },
                title = { Text(stringRes(Res.string.marmot_group_info_title)) },
                actions = {
                    IconButton(onClick = { nav.nav(Route.MarmotGroupEditInfo(nostrGroupId)) }) {
                        Icon(
                            symbol = MaterialSymbols.Edit,
                            contentDescription = stringRes(Res.string.marmot_edit_group_info),
                        )
                    }
                    // Disband ends the conversation for EVERYONE, so only an
                    // admin sees it and it sits behind its own confirmation.
                    // Peers reject a non-admin's lifecycle commit anyway; not
                    // offering it is what keeps a member from trying.
                    //
                    // A legacy group is hidden for a different reason: it has
                    // no carrier for the lifecycle component at all, so the
                    // commit is refused before it is built. The room says why
                    // further down rather than leaving the absence unexplained.
                    if (myPubkey in adminPubkeys && isCurrentProfile) {
                        IconButton(
                            onClick = { showDisbandDialog = true },
                            enabled = !isLeaving && !isDisbanding,
                        ) {
                            Icon(
                                symbol = MaterialSymbols.DeleteForever,
                                contentDescription = stringRes(Res.string.marmot_disband_group),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(
                        onClick = { showLeaveDialog = true },
                        enabled = !isLeaving && !isDisbanding,
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ExitToApp,
                            contentDescription = stringRes(Res.string.marmot_leave_group),
                            tint = MaterialTheme.colorScheme.error,
                        )
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
                    // consume the scaffold insets so imePadding() unions with the nav-bar inset in
                    // `padding` instead of stacking on top of it while the keyboard is up.
                    .consumeWindowInsets(padding)
                    .imePaddingSafe(),
        ) {
            // Group header section (fixed at top)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                marmotGroupTitle(
                                    displayName,
                                    remember(members) { marmotOtherMembers(members, accountViewModel.account.signer.pubKey) },
                                    nostrGroupId,
                                    accountViewModel,
                                ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!groupDescription.isNullOrEmpty()) {
                            Text(
                                text = groupDescription!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text(
                            text = pluralStringRes(Res.plurals.marmot_member_count, members.size, members.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // Disappearing messages, when the group has them. Shown
                        // rather than editable: the setting is fixed at epoch 0,
                        // and a member who cannot change it still needs to know
                        // their messages are on a clock.
                        val retention = remember(nostrGroupId) { accountViewModel.marmotRetentionSeconds(nostrGroupId) }
                        if (retention > 0L) {
                            Text(
                                text = stringRes(Res.string.marmot_retention_active, formatRetention(retention)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    if (groupRelays.isNotEmpty()) {
                        GroupRelayStrip(
                            relayUrls = groupRelays,
                            relayActivity = relayActivity,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
            HorizontalDivider()

            // Members list (scrollable, takes remaining vertical space)
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (groupRelays.isNotEmpty()) {
                    item {
                        RelayHealthSection(
                            relayUrls = groupRelays,
                            relayActivity = relayActivity,
                            nav = nav,
                        )
                        HorizontalDivider()
                    }
                }

                // Groups are created WITHOUT the encrypted-media component so
                // that epoch 0 matches the reference implementation's byte for
                // byte; the spec's answer is that a group which wants one
                // commits it. This is where an admin does that. Offered only
                // while the group lacks it, because the component has no
                // defined removal and this is a one-way change.
                if (isCurrentProfile && !hasEncryptedMedia && myPubkey in adminPubkeys) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Button(
                                onClick = {
                                    if (!accountViewModel.hasBlossomServers()) {
                                        longNotice.show(marmotEnableEncryptedMediaNeedsServerStr)
                                        return@Button
                                    }
                                    isEnablingMedia = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            accountViewModel.enableMarmotEncryptedMediaV2(nostrGroupId)
                                            launch(Dispatchers.Main) {
                                                shortNotice.show(loadStringRes(Res.string.marmot_encrypted_media_enabled_toast))
                                            }
                                        } catch (e: Exception) {
                                            currentCoroutineContext().ensureActive()
                                            launch(Dispatchers.Main) {
                                                longNotice.show(
                                                    loadStringRes(
                                                        Res.string.marmot_failed_to_enable_encrypted_media,
                                                        e.message,
                                                    ),
                                                )
                                            }
                                        } finally {
                                            isEnablingMedia = false
                                        }
                                    }
                                },
                                enabled = !isEnablingMedia && !isLeaving && !isDisbanding,
                            ) {
                                Text(stringRes(Res.string.marmot_enable_encrypted_media))
                            }
                            Text(
                                text = stringRes(Res.string.marmot_enable_encrypted_media_explainer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // An admin of a legacy group would otherwise just find the
                // disband action missing. Say why, and say that it cannot be
                // fixed by waiting for an update, so the only surprising part
                // — that a NEW group would have it — is the part explained.
                if (!isCurrentProfile && myPubkey in adminPubkeys) {
                    item {
                        Text(
                            text = stringRes(Res.string.marmot_legacy_group_no_disband),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                item {
                    Text(
                        text = stringRes(Res.string.members),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                val memberPubkeys = members.map { it.pubkey }
                items(members, key = { it.leafIndex }) { member ->
                    val isMe = member.pubkey == myPubkey
                    val isAdmin = member.pubkey in adminPubkeys
                    // The same rules peers apply to the commit (MIP-03), so the menu only
                    // offers what would be accepted. Self-promote is a no-op and stepping
                    // down goes through the leave flow, so the user's own row has no toggle.
                    val canPromote = !isMe && MarmotGroupPermissions.canPromote(adminPubkeys, myPubkey, member.pubkey)
                    val canDemote = !isMe && MarmotGroupPermissions.canDemote(adminPubkeys, memberPubkeys, myPubkey, member.pubkey)
                    MemberRow(
                        member = member,
                        isMe = isMe,
                        isAdmin = isAdmin,
                        canRemove = MarmotGroupPermissions.canRemove(adminPubkeys, myPubkey, member.pubkey),
                        canPromote = canPromote,
                        canDemote = canDemote,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onRemoveClick = { memberToRemove = member },
                        onPromoteClick = { memberToPromote = member },
                        onDemoteClick = { memberToDemote = member },
                    )
                    HorizontalDivider()
                }
            }

            HorizontalDivider()

            // Suggestions + add-member input (fixed at bottom)
            AddMemberInline(
                nostrGroupId = nostrGroupId,
                searchInput = addSearchInput,
                onSearchInputChange = { value ->
                    addSearchInput = value
                    if (!isAddError) addStatus = null
                    if (value.length > 2) {
                        userSuggestions.processCurrentWord(value)
                    } else {
                        userSuggestions.reset()
                    }
                },
                userSuggestions = userSuggestions,
                statusMessage = addStatus,
                isError = isAddError,
                isAdding = isAdding,
                accountViewModel = accountViewModel,
                onAdd = { user ->
                    isAdding = true
                    isAddError = false
                    val targetPubkey = user.pubkeyHex
                    val targetName = user.toBestDisplayName()
                    scope.launch(Dispatchers.IO) {
                        addStatus = loadStringRes(Res.string.marmot_adding_user, targetName)
                        try {
                            val result = accountViewModel.addMarmotGroupMember(nostrGroupId, targetPubkey)
                            if (result.startsWith("Success")) {
                                addStatus = null
                                isAddError = false
                                addSearchInput = ""
                                userSuggestions.reset()
                            } else {
                                addStatus = loadStringRes(Res.string.marmot_failed_to_add_user, targetName, result.removePrefix("Error: "))
                                isAddError = true
                            }
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            addStatus = loadStringRes(Res.string.marmot_failed_to_add_user, targetName, e.message ?: loadStringRes(Res.string.marmot_unknown_error))
                            isAddError = true
                        } finally {
                            isAdding = false
                        }
                    }
                },
            )
        }
    }

    if (showLeaveDialog) {
        LeaveGroupDialog(
            groupName = displayName ?: stringRes(Res.string.marmot_this_group),
            onConfirm = {
                showLeaveDialog = false
                isLeaving = true
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.leaveMarmotGroup(nostrGroupId)
                        nav.nav(Route.Message)
                    } catch (e: Exception) {
                        isLeaving = false
                        currentCoroutineContext().ensureActive()
                        launch(Dispatchers.Main) {
                            longNotice.show(loadStringRes(Res.string.marmot_failed_to_leave_group, e.message))
                        }
                    }
                }
            },
            onDismiss = { showLeaveDialog = false },
        )
    }

    if (showDisbandDialog) {
        DisbandGroupDialog(
            groupName = displayName ?: stringRes(Res.string.marmot_this_group),
            onConfirm = {
                showDisbandDialog = false
                isDisbanding = true
                scope.launch(Dispatchers.IO) {
                    try {
                        // Ended, or ending. A disband terminalizes only once
                        // convergence SELECTS the Commit, so the request can
                        // still be pending here — and telling someone their
                        // conversation is over when it may not be is the one
                        // wrong answer. Either way the group takes no further
                        // outbound work, so leaving the screen is right.
                        val ended = accountViewModel.disbandMarmotGroup(nostrGroupId)
                        launch(Dispatchers.Main) {
                            shortNotice.show(
                                loadStringRes(
                                    if (ended) {
                                        Res.string.marmot_group_disbanded_toast
                                    } else {
                                        Res.string.marmot_group_disbanding_toast
                                    },
                                ),
                            )
                        }
                        nav.nav(Route.Message)
                    } catch (e: Exception) {
                        // A disband that reached no relay leaves the group
                        // live, so the screen must stay usable rather than
                        // navigate away on a change that did not happen.
                        isDisbanding = false
                        currentCoroutineContext().ensureActive()
                        launch(Dispatchers.Main) {
                            longNotice.show(loadStringRes(Res.string.marmot_failed_to_disband, e.message))
                        }
                    }
                }
            },
            onDismiss = { showDisbandDialog = false },
        )
    }

    memberToRemove?.let { member ->
        ConfirmRemoveMemberDialog(
            memberPubkey = member.pubkey,
            accountViewModel = accountViewModel,
            onConfirm = {
                memberToRemove = null
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.removeMarmotGroupMember(nostrGroupId, member.leafIndex)
                        launch(Dispatchers.Main) {
                            shortNotice.show(loadStringRes(Res.string.marmot_member_removed))
                        }
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        launch(Dispatchers.Main) {
                            longNotice.show(loadStringRes(Res.string.marmot_failed_to_remove_member, e.message))
                        }
                    }
                }
            },
            onDismiss = { memberToRemove = null },
        )
    }

    memberToPromote?.let { member ->
        ConfirmGrantAdminDialog(
            memberPubkey = member.pubkey,
            accountViewModel = accountViewModel,
            onConfirm = {
                memberToPromote = null
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.grantMarmotGroupAdmin(nostrGroupId, member.pubkey)
                        launch(Dispatchers.Main) {
                            shortNotice.show(loadStringRes(Res.string.marmot_admin_granted))
                        }
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        launch(Dispatchers.Main) {
                            longNotice.show(loadStringRes(Res.string.marmot_failed_to_grant_admin, e.message))
                        }
                    }
                }
            },
            onDismiss = { memberToPromote = null },
        )
    }

    memberToDemote?.let { member ->
        ConfirmRevokeAdminDialog(
            memberPubkey = member.pubkey,
            accountViewModel = accountViewModel,
            onConfirm = {
                memberToDemote = null
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.revokeMarmotGroupAdmin(nostrGroupId, member.pubkey)
                        launch(Dispatchers.Main) {
                            shortNotice.show(loadStringRes(Res.string.marmot_admin_revoked))
                        }
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        launch(Dispatchers.Main) {
                            longNotice.show(loadStringRes(Res.string.marmot_failed_to_revoke_admin, e.message))
                        }
                    }
                }
            },
            onDismiss = { memberToDemote = null },
        )
    }
}

@Composable
private fun AddMemberInline(
    nostrGroupId: HexKey,
    searchInput: String,
    onSearchInputChange: (String) -> Unit,
    userSuggestions: UserSuggestionState,
    statusMessage: String?,
    isError: Boolean,
    isAdding: Boolean,
    accountViewModel: AccountViewModel,
    onAdd: (com.vitorpamplona.amethyst.commons.model.User) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isAdding && searchInput.length > 2) {
            ShowUserSuggestionList(
                userSuggestions = userSuggestions,
                onSelect = { user -> onAdd(user) },
                accountViewModel = accountViewModel,
                modifier = SuggestionListDefaultHeightChat,
                onEmpty = {
                    Text(
                        stringRes(Res.string.marmot_keypackage_required),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = { user ->
                    IconButton(onClick = { onAdd(user) }) {
                        Icon(
                            symbol = MaterialSymbols.PersonAdd,
                            contentDescription = stringRes(Res.string.marmot_add_to_group),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            HorizontalDivider()
        }

        if (statusMessage != null) {
            Text(
                text = statusMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }

        OutlinedTextField(
            value = searchInput,
            onValueChange = onSearchInputChange,
            label = { Text(stringRes(Res.string.marmot_add_member)) },
            placeholder = { Text(stringRes(Res.string.marmot_add_member_placeholder)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            enabled = !isAdding,
        )
    }
}

@Composable
fun MemberRow(
    member: GroupMemberInfo,
    isMe: Boolean,
    isAdmin: Boolean,
    canRemove: Boolean,
    canPromote: Boolean,
    canDemote: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRemoveClick: () -> Unit,
    onPromoteClick: () -> Unit,
    onDemoteClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.Profile(member.pubkey)) }
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(
            userHex = member.pubkey,
            size = 36.dp,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Column(modifier = Modifier.weight(1f)) {
            LoadUser(baseUserHex = member.pubkey) { user ->
                val displayName = user?.toBestDisplayName() ?: stringRes(Res.string.marmot_user_fallback_name, member.pubkey.take(16))
                val youSuffix = stringRes(Res.string.marmot_member_suffix_you)
                val adminSuffix = stringRes(Res.string.marmot_member_suffix_admin)
                val suffix =
                    buildString {
                        if (isMe) append(youSuffix)
                        if (isAdmin) append(adminSuffix)
                    }
                Text(
                    text = "$displayName$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isMe || isAdmin) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        if (canPromote) {
            IconButton(onClick = onPromoteClick) {
                Icon(
                    symbol = MaterialSymbols.StarBorder,
                    contentDescription = stringRes(Res.string.marmot_grant_admin_privileges),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (canDemote) {
            IconButton(onClick = onDemoteClick) {
                Icon(
                    symbol = MaterialSymbols.Star,
                    contentDescription = stringRes(Res.string.marmot_revoke_admin_privileges),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    symbol = MaterialSymbols.PersonRemove,
                    contentDescription = stringRes(Res.string.marmot_remove_member),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun LeaveGroupDialog(
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.marmot_leave_group)) },
        text = {
            Text(stringRes(Res.string.marmot_leave_group_confirm, groupName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.leave), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/**
 * Confirmation for the one group action that cannot be undone.
 *
 * Disband is absorbing: every member's copy terminalizes and no commit walks it
 * back, so the wording says "for everyone" and "cannot be reopened" rather than
 * the usual "are you sure".
 */
@Composable
fun DisbandGroupDialog(
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.marmot_disband_group)) },
        text = {
            Text(stringRes(Res.string.marmot_disband_group_confirm, groupName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.marmot_disband_group_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmRemoveMemberDialog(
    memberPubkey: HexKey,
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.marmot_remove_member)) },
        text = {
            LoadUser(baseUserHex = memberPubkey) { user ->
                val name = user?.toBestDisplayName() ?: stringRes(Res.string.marmot_user_fallback_name, memberPubkey.take(16))
                Text(stringRes(Res.string.marmot_remove_member_confirm, name))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmGrantAdminDialog(
    memberPubkey: HexKey,
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.marmot_grant_admin_title)) },
        text = {
            LoadUser(baseUserHex = memberPubkey) { user ->
                val name = user?.toBestDisplayName() ?: stringRes(Res.string.marmot_user_fallback_name, memberPubkey.take(16))
                Text(stringRes(Res.string.marmot_grant_admin_confirm, name))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.marmot_grant))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmRevokeAdminDialog(
    memberPubkey: HexKey,
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.marmot_revoke_admin_title)) },
        text = {
            LoadUser(baseUserHex = memberPubkey) { user ->
                val name = user?.toBestDisplayName() ?: stringRes(Res.string.marmot_user_fallback_name, memberPubkey.take(16))
                Text(stringRes(Res.string.marmot_revoke_admin_confirm, name))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.marmot_revoke), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/** Window (in seconds) within which a relay is considered actively carrying this group's traffic. */
private const val RELAY_ACTIVITY_WINDOW_SECS = 7L * 24 * 60 * 60

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupRelayStrip(
    relayUrls: List<String>,
    relayActivity: Map<NormalizedRelayUrl, Long>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val normalized =
        remember(relayUrls) {
            relayUrls.mapNotNull { url -> url.normalizeRelayUrlOrNull()?.let { url to it } }
        }

    if (normalized.isEmpty()) return

    FlowRow(
        modifier = Modifier.padding(start = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val nowSeconds = TimeUtils.now()
        normalized.forEach { (raw, relay) ->
            val lastSeen = relayActivity[relay]
            val isActive = lastSeen != null && (nowSeconds - lastSeen) <= RELAY_ACTIVITY_WINDOW_SECS
            GroupRelayTile(
                relay = relay,
                fallbackUrl = raw,
                isActive = isActive,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupRelayTile(
    relay: NormalizedRelayUrl,
    fallbackUrl: String,
    isActive: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relayInfo by loadRelayInfo(relay)
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val clickableModifier =
        remember(relay) {
            Modifier
                .size(35.dp)
                .combinedClickable(
                    indication = ripple24dp,
                    interactionSource = MutableInteractionSource(),
                    onLongClick = {
                        scope.launch {
                            clipboardManager.setText(relay.url)
                        }
                    },
                    onClick = { nav.nav(Route.RelayInfo(relay.url)) },
                )
        }

    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center,
    ) {
        RenderRelayIcon(
            displayUrl = relayInfo.id ?: fallbackUrl,
            iconUrl = relayInfo.icon,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            pingInMs = 0,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            iconModifier = MediumRelayIconModifier,
        )

        val dotColor =
            if (isActive) {
                MaterialTheme.colorScheme.allGoodColor
            } else {
                MaterialTheme.colorScheme.placeholderText
            }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
            )
        }
    }
}

/**
 * Detailed per-relay listing showing URL, connection-status dot, and the
 * freshness of the most recent kind:445 group event we've received from
 * each relay. Complements the compact [GroupRelayStrip] at the top of the
 * screen — that one is an at-a-glance visual; this one surfaces the
 * underlying data so users can diagnose which relay is lagging.
 */
@Composable
fun RelayHealthSection(
    relayUrls: List<String>,
    relayActivity: Map<NormalizedRelayUrl, Long>,
    nav: INav,
) {
    val normalized =
        remember(relayUrls) {
            relayUrls.mapNotNull { url -> url.normalizeRelayUrlOrNull()?.let { url to it } }
        }
    if (normalized.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringRes(Res.string.marmot_relays_header),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        val nowSeconds = TimeUtils.now()
        normalized.forEach { (raw, relay) ->
            val lastSeen = relayActivity[relay]
            val isActive = lastSeen != null && (nowSeconds - lastSeen) <= RELAY_ACTIVITY_WINDOW_SECS
            RelayHealthRow(
                relay = relay,
                fallbackUrl = raw,
                lastSeen = lastSeen,
                isActive = isActive,
                nav = nav,
            )
        }
    }
}

@Composable
private fun RelayHealthRow(
    relay: NormalizedRelayUrl,
    fallbackUrl: String,
    lastSeen: Long?,
    isActive: Boolean,
    nav: INav,
) {
    val dotColor =
        if (isActive) {
            MaterialTheme.colorScheme.allGoodColor
        } else {
            MaterialTheme.colorScheme.placeholderText
        }
    val subtitle =
        if (lastSeen == null) {
            stringRes(Res.string.marmot_relay_no_events)
        } else {
            stringRes(Res.string.marmot_relay_last_event, timeAgo(lastSeen))
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.RelayInfo(relay.url)) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Column(
            modifier =
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
        ) {
            Text(
                text = relay.url.ifEmpty { fallbackUrl },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A retention duration as a reader sees it.
 *
 * Deliberately coarse — the exact second is committed group state, but what a
 * member needs from this line is "how long roughly", and rounding down keeps a
 * 90-minute setting from reading as "1 hour" only after it has already been
 * displayed as "2 hours" somewhere else.
 */
private fun formatRetention(seconds: Long): String =
    when {
        seconds % 604_800L == 0L -> "${seconds / 604_800L}w"
        seconds % 86_400L == 0L -> "${seconds / 86_400L}d"
        seconds % 3_600L == 0L -> "${seconds / 3_600L}h"
        seconds % 60L == 0L -> "${seconds / 60L}m"
        else -> "${seconds}s"
    }
