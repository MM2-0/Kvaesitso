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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.concordChannelUnreadCountFlow
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.newestTimelineNote
import com.vitorpamplona.amethyst.commons.concord.ui.ConcordUnreadBadge
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_name
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.concord_channel_access_role_label
import com.vitorpamplona.amethyst.commons.resources.concord_channel_create
import com.vitorpamplona.amethyst.commons.resources.concord_channel_delete
import com.vitorpamplona.amethyst.commons.resources.concord_channel_delete_confirm
import com.vitorpamplona.amethyst.commons.resources.concord_channel_delete_message
import com.vitorpamplona.amethyst.commons.resources.concord_channel_delete_title
import com.vitorpamplona.amethyst.commons.resources.concord_channel_make_private
import com.vitorpamplona.amethyst.commons.resources.concord_channel_make_private_message
import com.vitorpamplona.amethyst.commons.resources.concord_channel_make_public
import com.vitorpamplona.amethyst.commons.resources.concord_channel_make_public_message
import com.vitorpamplona.amethyst.commons.resources.concord_channel_name_label
import com.vitorpamplona.amethyst.commons.resources.concord_channel_no_messages
import com.vitorpamplona.amethyst.commons.resources.concord_channel_private_hint
import com.vitorpamplona.amethyst.commons.resources.concord_channel_private_toggle
import com.vitorpamplona.amethyst.commons.resources.concord_channel_rename
import com.vitorpamplona.amethyst.commons.resources.concord_channel_rename_save
import com.vitorpamplona.amethyst.commons.resources.concord_channel_rotate_key
import com.vitorpamplona.amethyst.commons.resources.concord_channels_empty
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_action
import com.vitorpamplona.amethyst.commons.resources.concord_dissolve_community
import com.vitorpamplona.amethyst.commons.resources.concord_dissolve_confirm
import com.vitorpamplona.amethyst.commons.resources.concord_dissolve_message
import com.vitorpamplona.amethyst.commons.resources.concord_dissolve_title
import com.vitorpamplona.amethyst.commons.resources.concord_edit_title
import com.vitorpamplona.amethyst.commons.resources.concord_invite_action
import com.vitorpamplona.amethyst.commons.resources.concord_invite_links_action
import com.vitorpamplona.amethyst.commons.resources.concord_invite_title
import com.vitorpamplona.amethyst.commons.resources.concord_leave_community
import com.vitorpamplona.amethyst.commons.resources.concord_leave_message
import com.vitorpamplona.amethyst.commons.resources.concord_leave_owner_warning
import com.vitorpamplona.amethyst.commons.resources.concord_leave_title
import com.vitorpamplona.amethyst.commons.resources.concord_members_title
import com.vitorpamplona.amethyst.commons.resources.concord_mode_private
import com.vitorpamplona.amethyst.commons.resources.concord_mode_public
import com.vitorpamplona.amethyst.commons.resources.concord_typing_many
import com.vitorpamplona.amethyst.commons.resources.concord_typing_one
import com.vitorpamplona.amethyst.commons.resources.concord_typing_two
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.leave
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.layouts.chatRowTitleWeight
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.rememberConcordImageModel
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.observeChatPreviewText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordDirectInviteDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelPreviewLoader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The channel list of one Concord community (the "server" view). Reads the folded
 * Control Plane from the community session and renders one row per channel; tapping
 * opens that channel's [ConcordChannelScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordChannelListScreen(
    communityId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)
    // Warm every channel's last-message preview so the list fills in without opening each channel.
    ConcordChannelPreviewLoader(communityId, accountViewModel)

    val account = accountViewModel.account
    // Re-resolve on each revision so a deep link that lands before the session exists picks it up.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(account, communityId, revision) { account.concordSessions.sessionFor(communityId) }
    val state by (session?.state ?: remember { MutableStateFlow(null) })
        .collectAsStateWithLifecycle()

    // Live typing heartbeats (kind 23311) per channel, so a channel where someone is composing shows
    // "typing…" in place of its last-message preview. A single screen-level ticker re-applies the
    // freshness window for every row at once, and only spins while at least one heartbeat is live.
    val typingMap by (session?.typing ?: remember { MutableStateFlow(emptyMap<HexKey, Map<HexKey, Long>>()) })
        .collectAsStateWithLifecycle()
    var typingNow by remember { mutableLongStateOf(TimeUtils.now()) }
    LaunchedEffect(typingMap) {
        if (typingMap.values.all { it.isEmpty() }) return@LaunchedEffect
        while (true) {
            typingNow = TimeUtils.now()
            if (typingMap.values.all { per -> per.values.none { typingNow - it <= ConcordCommunitySession.TYPING_STALE_SECS } }) break
            delay(2000L)
        }
    }

    val scope = rememberCoroutineScope()
    var inviteLink by remember { mutableStateOf<String?>(null) }
    var minting by remember { mutableStateOf(false) }

    // Prefer the folded metadata name, then the stored community name from the list entry (always
    // present from the join/create — this is what shows everywhere else). Fall back to the app name
    // only if neither exists (should be unreachable), never as the normal "metadata hasn't folded
    // yet" placeholder — that showed "Amy Debug".
    val communityName =
        state?.metadata?.name
            ?: session?.entry?.name?.ifBlank { null }
            ?: stringRes(Res.string.app_name)

    // Owner from the list entry, not the folded authority: a community whose relays are dead never
    // folds a Control Plane, and that is exactly the case where leaving matters most.
    val isOwner = session?.entry?.owner == account.signer.pubKey

    // Read once here (it is @Composable) so the post-leave navigation can use it from a callback.
    val canPop = nav.canPop()
    var showLeave by remember { mutableStateOf(false) }
    var showDissolve by remember { mutableStateOf(false) }
    var showDirectInvite by remember { mutableStateOf(false) }

    if (showDirectInvite) {
        ConcordDirectInviteDialog(communityId, accountViewModel, onDismiss = { showDirectInvite = false })
    }

    if (showLeave) {
        ConcordLeaveDialog(
            communityName = communityName,
            isOwner = isOwner,
            onDismiss = { showLeave = false },
            onConfirm = {
                showLeave = false
                // Fire-and-forget: the list edit is local + a best-effort publish to our own outbox,
                // so we never hold the user behind a spinner waiting on a relay that may be dead.
                accountViewModel.leaveConcordCommunity(communityId)
                // Don't strand the user on the server view of a community they just left. Popping is
                // right when we were pushed here; when this community is a bottom-nav root there is
                // nothing to pop, so restart the stack on the Concord hub.
                if (canPop) nav.popBack() else nav.newStack(Route.Concords)
            },
        )
    }

    if (showDissolve) {
        ConcordDissolveDialog(
            communityName = communityName,
            onDismiss = { showDissolve = false },
            onConfirm = {
                showDissolve = false
                // Stay here: the dissolved community stays readable, and this screen shows it read-only.
                accountViewModel.dissolveConcordCommunity(communityId)
            },
        )
    }

    // Channel create/rename/delete are gated on MANAGE_CHANNELS (or owner) — the same predicate the
    // fold enforces, so an unauthorized action would be a silent no-op we shouldn't even offer.
    // Rank alone isn't enough on a split epoch: publishing any Control edition also takes the
    // control_root (CORD-02 §2), which a freshly promoted staffer may not hold yet (CORD-04 §3),
    // so the affordance waits for the key too.
    // hasPermission, never effectivePermissions: the latter reads the roles alone, so a banned
    // moderator kept seeing every control here. The editions they authored were dropped by everyone's
    // fold, which made these buttons silently no-op — worse than absent, and the same trap this file
    // already avoids for the Roles… menu.
    // Remembered per fold: a typing heartbeat recomposes this screen every 2s, and controlPlaneKeys()
    // takes the session lock. The write key only arrives with a List change, which bumps [revision].
    val canManageChannels =
        remember(state, session, revision) {
            state?.authority?.let {
                it.isOwner(account.signer.pubKey) ||
                    it.hasPermission(account.signer.pubKey, ConcordPermissions.MANAGE_CHANNELS)
            } == true &&
                session?.controlPlaneKeys()?.canWrite == true
        }

    // channelIdHex == null → create; else → rename that channel.
    var channelEditor by remember { mutableStateOf<ConcordChannelEditor?>(null) }
    var channelToDelete by remember { mutableStateOf<ConcordChannelEditor?>(null) }

    inviteLink?.let { link ->
        InviteLinkDialog(link = link, onDismiss = { inviteLink = null })
    }

    channelEditor?.let { editor ->
        ConcordChannelEditDialog(
            initialName = editor.initialName,
            isCreate = editor.channelIdHex == null,
            onDismiss = { channelEditor = null },
            onConfirm = { newName, makePrivate, accessRoleName ->
                channelEditor = null
                scope.launch {
                    accountViewModel.reportSignerErrors {
                        if (editor.channelIdHex == null) {
                            account.concord.createConcordChannel(communityId, newName, makePrivate, accessRoleName)
                        } else {
                            account.concord.renameConcordChannel(communityId, editor.channelIdHex, newName)
                        }
                    }
                }
            },
        )
    }

    // Privatise / publicise a channel (CORD-03 §2): both are MANAGE_CHANNELS edits, and both say
    // plainly what they can't do — a conversion protects the future only.
    var channelToConvert by remember { mutableStateOf<ConcordChannelConversion?>(null) }
    channelToConvert?.let { target ->
        ConcordChannelConvertDialog(
            target = target,
            onDismiss = { channelToConvert = null },
            onConfirm = { accessRoleName ->
                channelToConvert = null
                scope.launch {
                    accountViewModel.reportSignerErrors {
                        if (target.toPrivate) {
                            account.concord.privatizeConcordChannel(communityId, target.channelIdHex, accessRoleName)
                        } else {
                            account.concord.publicizeConcordChannel(communityId, target.channelIdHex)
                        }
                    }
                }
            },
        )
    }

    channelToDelete?.let { target ->
        val id = target.channelIdHex ?: return@let
        AlertDialog(
            onDismissRequest = { channelToDelete = null },
            title = { Text(stringRes(Res.string.concord_channel_delete_title)) },
            text = { Text(stringRes(Res.string.concord_channel_delete_message, target.initialName)) },
            confirmButton = {
                TextButton(onClick = {
                    channelToDelete = null
                    scope.launch { accountViewModel.reportSignerErrors { account.concord.deleteConcordChannel(communityId, id, target.initialName) } }
                }) {
                    Text(stringRes(Res.string.concord_channel_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { channelToDelete = null }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }

    // Rank + the Control write key (CORD-02 §2), like [canManageChannels] above.
    val canEdit =
        remember(state, session, revision) {
            state?.authority?.let {
                it.isOwner(account.signer.pubKey) ||
                    it.hasPermission(account.signer.pubKey, ConcordPermissions.MANAGE_METADATA)
            } == true &&
                session?.controlPlaneKeys()?.canWrite == true
        }

    // Minting an invite hands out a working key to the community, so it takes
    // CREATE_INVITE like any other privileged action. This button used to be the one
    // control on the screen with no gate at all.
    val canInvite =
        remember(state) {
            state?.authority?.let {
                it.isOwner(account.signer.pubKey) ||
                    it.hasPermission(account.signer.pubKey, ConcordPermissions.CREATE_INVITE)
            } == true
        }

    val description =
        remember(state) {
            state
                ?.metadata
                ?.description
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

    Scaffold(
        topBar = {
            // The bar stays to icon + name, all a returning member needs to know where they are. The
            // description and the Public/Private mode (CORD-05 §5) sit in the panel a tap on the bar
            // opens, like a NIP-28 channel's: pinned above the list they greeted every daily visit.
            TopBarExtensibleWithBackButton(
                title = {
                    ConcordServerTitle(
                        communityId = communityId,
                        name = communityName,
                        iconPointer = state?.metadata?.icon,
                        accountViewModel = accountViewModel,
                    )
                },
                extendableRow =
                    state?.let { folded ->
                        {
                            val links = folded.liveInviteLinks.size
                            ConcordAboutPanel(
                                description = description,
                                // Any live invite link in the folded registries makes the community
                                // Public; none makes it Private, where a ban rotates the keys (CORD-06 §3).
                                mode = if (folded.isPublic) pluralStringRes(Res.plurals.concord_mode_public, links, links) else stringRes(Res.string.concord_mode_private),
                                isPublic = folded.isPublic,
                            )
                        }
                    },
                // Back arrow only when pushed from elsewhere; as a bottom-nav tab the bar takes its place.
                nav = nav,
                actions = {
                    // One overflow instead of four icons: at phone width the icons squeezed the name, and
                    // none of these is a daily action. Destructive membership actions stay at the bottom,
                    // mirroring the NIP-29 relay-group top bar, never as a one-tap icon.
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        SymbolIcon(symbol = MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.concord_members_title)) },
                            leadingIcon = { SymbolIcon(symbol = MaterialSymbols.Group, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                nav.nav(Route.ConcordMembers(communityId))
                            },
                        )
                        if (canInvite) {
                            DropdownMenuItem(
                                text = { Text(stringRes(Res.string.concord_invite_action)) },
                                leadingIcon = { SymbolIcon(symbol = MaterialSymbols.PersonAdd, contentDescription = null) },
                                enabled = !minting,
                                onClick = {
                                    menuOpen = false
                                    minting = true
                                    scope.launch {
                                        try {
                                            inviteLink = account.concord.mintConcordInvite(communityId)
                                        } finally {
                                            // Always clear the flag — a thrown mint would otherwise leave the
                                            // item disabled until the screen is recreated.
                                            minting = false
                                        }
                                    }
                                },
                            )
                        }
                        // A Direct Invite (CORD-05 §6) hands keys to one known npub. No permission gates
                        // it — none could, any keyholder can whisper keys — so neither does this item;
                        // what it carries is bounded by the recipient's roles instead.
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.concord_direct_invite_action)) },
                            leadingIcon = { SymbolIcon(symbol = MaterialSymbols.Mail, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showDirectInvite = true
                            },
                        )
                        // Deliberately not gated on CREATE_INVITE, unlike minting: the links listed
                        // there are this account's own, authored by link-signer keys only we hold.
                        // Gating on the bit would mean a demoted admin could no longer retire the
                        // links they had already handed out — exactly when that matters most.
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.concord_invite_links_action)) },
                            leadingIcon = { SymbolIcon(symbol = MaterialSymbols.Link, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                nav.nav(Route.ConcordInviteLinks(communityId))
                            },
                        )
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text(stringRes(Res.string.concord_edit_title)) },
                                leadingIcon = { SymbolIcon(symbol = MaterialSymbols.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    nav.nav(Route.ConcordEdit(communityId))
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringRes(Res.string.concord_leave_community),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                SymbolIcon(symbol = MaterialSymbols.AutoMirrored.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuOpen = false
                                showLeave = true
                            },
                        )
                        // Owner only, like the verifiers: anyone else's tombstone is ignored (CORD-02 §9).
                        if (isOwner && state?.dissolved != true) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringRes(Res.string.concord_dissolve_community),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    SymbolIcon(symbol = MaterialSymbols.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menuOpen = false
                                    showDissolve = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Hidden on in-app pushes (AppBottomBar renders only when nav.showsBottomBar()),
            // so a pinned Concord community works both as a pushed detail and as a bottom-nav tab.
            AppBottomBar(Route.ConcordServer(communityId), nav, accountViewModel) { route ->
                if (route != Route.ConcordServer(communityId)) nav.navBottomBar(route)
            }
        },
        floatingActionButton = {
            if (canManageChannels) {
                FloatingActionButton(
                    onClick = { channelEditor = ConcordChannelEditor(channelIdHex = null, initialName = "") },
                    shape = CircleShape,
                ) {
                    SymbolIcon(symbol = MaterialSymbols.Add, contentDescription = stringRes(Res.string.concord_channel_create))
                }
            }
        },
    ) { padding ->
        val channels =
            state
                ?.channels
                ?.entries
                ?.toList()
                .orEmpty()
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringRes(Res.string.concord_channels_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Bottom room for the FAB, which the Scaffold's `padding` deliberately doesn't account for
            // (a FAB overlays content) — without it the last row's manager overflow menu sits under it
            // and can't be tapped. As contentPadding so rows scroll *through* the strip rather than the
            // viewport shrinking. Gated on [canManageChannels] because that is what renders the FAB:
            // a plain member has nothing to clear, and no reason to lose the space.
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = if (canManageChannels) FAB_CLEARANCE else 0.dp),
            ) {
                items(channels, key = { it.key }) { entry ->
                    val def = entry.value.definition
                    val name = def.name.ifBlank { entry.key }
                    val typingAuthors =
                        remember(typingMap, typingNow, entry.key) {
                            (typingMap[entry.key] ?: emptyMap())
                                .filterValues { typingNow - it <= ConcordCommunitySession.TYPING_STALE_SECS }
                                .keys
                                .sorted()
                        }
                    // A rotation needs the current key (CORD-06): only a holder can rotate.
                    val holdsKey = def.private && session?.entry?.let { ConcordChannelKeyring.heldKey(it, entry.key) } != null
                    ConcordChannelListRow(
                        communityId = communityId,
                        channelKey = entry.key,
                        channelName = name,
                        typingAuthors = typingAuthors,
                        canManageChannels = canManageChannels,
                        accountViewModel = accountViewModel,
                        onClick = { nav.nav(Route.Concord(communityId, entry.key)) },
                        onRename = { channelEditor = ConcordChannelEditor(channelIdHex = entry.key, initialName = name) },
                        onDelete = { channelToDelete = ConcordChannelEditor(channelIdHex = entry.key, initialName = name) },
                        onTogglePrivate = { channelToConvert = ConcordChannelConversion(entry.key, name, toPrivate = !def.private) },
                        isPrivate = def.private,
                        onRotateKey = if (holdsKey) ({ accountViewModel.rotateConcordChannelKey(communityId, entry.key) }) else null,
                    )
                }
            }
        }
    }
}

/**
 * One channel row in the community's server view: its icon, name, a preview of the last message
 * (author + snippet), the relative time of that message, and an unread-message count badge — plus
 * the manager-only overflow menu. Name/icon come from the folded Control Plane definition; the
 * preview, time, and unread count are read reactively from the channel's own message store so they
 * fill in the moment messages fold in and clear as soon as the channel is opened (last-read advances).
 */
@Composable
private fun ConcordChannelListRow(
    communityId: String,
    channelKey: String,
    channelName: String,
    typingAuthors: List<HexKey>,
    canManageChannels: Boolean,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePrivate: () -> Unit,
    isPrivate: Boolean,
    onRotateKey: (() -> Unit)?,
) {
    val account = accountViewModel.account
    // getOrCreate (not getIfExists): a channel folded on the Control Plane may have no message note
    // yet; its notes flow then makes the preview/time/unread/facepile reactive as messages arrive.
    val channel = remember(communityId, channelKey) { LocalCache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelKey)) }
    val channelState by channel
        .flow()
        .notes.stateFlow
        .collectAsStateWithLifecycle()
    // The newest *timeline* message (not the raw lastNote): skips kind-1111 thread replies and
    // hidden authors so the preview + time match the channel feed and the unread badge below.
    val lastNote = remember(channelState) { channel.newestTimelineNote(account) }
    val unread by
        remember(communityId, channelKey) { concordChannelUnreadCountFlow(account, communityId, channelKey) }
            .collectAsStateWithLifecycle(0)
    val hasUnread = unread > 0

    // Same metrics as a Messages-list row (ChatHeaderLayout): 16dp sides, 12dp vertical, no dividers.
    // The channel glyph rides inline before the name instead of holding its own column, so the
    // preview line starts at the edge and gets the width a column of identical "#"s used to take.
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (canManageChannels) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Only matters for managers: keeps the badge off the row menu's touch target.
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Line 1: # / lock, the channel name, and the last-message time pushed to the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(
                    // Every Channel is callable (CORD-07), so there is no voice-only icon.
                    symbol = if (isPrivate) MaterialSymbols.Lock else MaterialSymbols.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    channelName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = chatRowTitleWeight(hasUnread),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                lastNote?.createdAt()?.let { ts ->
                    Text(
                        timeAgo(ts, prefix = ""),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            // Line 2: the last-message preview (or a live "typing…"), then the unread-message badge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ConcordChannelPreviewLine(lastNote, typingAuthors, hasUnread, accountViewModel)
                }
                if (hasUnread) {
                    Spacer(Modifier.width(8.dp))
                    ConcordUnreadBadge(unread)
                }
            }
        }
        if (canManageChannels) {
            ConcordChannelRowMenu(
                onRename = onRename,
                onDelete = onDelete,
                onTogglePrivate = onTogglePrivate,
                isPrivate = isPrivate,
                onRotateKey = onRotateKey,
            )
        }
    }
}

/**
 * The top bar's title: the community icon and its name, on one line. The icon is what tells one
 * community's server view from another's at a glance; the member count lives on the Members screen.
 */
@Composable
private fun ConcordServerTitle(
    communityId: String,
    name: String,
    iconPointer: ImagePointer?,
    accountViewModel: AccountViewModel,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    RobohashFallbackAsyncImage(
        robot = communityId,
        model = rememberConcordImageModel(iconPointer, accountViewModel),
        // Decorative: the name is drawn right beside it, so a screen reader would read it twice.
        contentDescription = null,
        modifier = HeaderPictureModifier,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif = autoPlayGif,
    )
    Spacer(Modifier.width(10.dp))
    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * What a tap on the top bar reveals: the community's description (CORD-02 §6) in full, and its
 * Public/Private mode. Both are worth reading once, not on every visit, so neither sits above the list.
 */
@Composable
private fun ConcordAboutPanel(
    description: String?,
    mode: String,
    isPublic: Boolean,
) {
    // Capped and scrollable: it lives in the Scaffold's top bar, so a long description would
    // otherwise push the whole channel list off screen.
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SymbolIcon(
                symbol = if (isPublic) MaterialSymbols.Public else MaterialSymbols.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                mode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Bottom room the list leaves for the floating action button: a 56dp FAB + the Scaffold's 16dp margin
 * + slack, so the last row's overflow menu stays tappable instead of sitting under the FAB. Matches
 * the value `JobBoardScreen` and the relay-group list screens use.
 */
private val FAB_CLEARANCE = 96.dp

/**
 * The line under a channel name. When someone is composing it shows a live italic "X is typing…";
 * otherwise the last message's author + a snippet ("author: hello"), or a muted "No messages yet"
 * placeholder before anything has folded in. Author names resolve reactively (hex → profile name).
 */
@Composable
private fun ConcordChannelPreviewLine(
    lastNote: Note?,
    typingAuthors: List<HexKey>,
    hasUnread: Boolean,
    accountViewModel: AccountViewModel,
) {
    if (typingAuthors.isNotEmpty()) {
        val label =
            when (typingAuthors.size) {
                1 -> stringRes(Res.string.concord_typing_one, rememberConcordDisplayName(typingAuthors[0], accountViewModel))
                2 ->
                    stringRes(
                        Res.string.concord_typing_two,
                        rememberConcordDisplayName(typingAuthors[0], accountViewModel),
                        rememberConcordDisplayName(typingAuthors[1], accountViewModel),
                    )
                else -> stringRes(Res.string.concord_typing_many)
            }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val author = lastNote?.author
    val preview: String =
        if (lastNote?.event == null) {
            stringRes(Res.string.concord_channel_no_messages)
        } else if (author != null) {
            val authorName by observeUserName(author, accountViewModel)
            // The newest edit, as the bubble shows it.
            val body = observeChatPreviewText(lastNote, 80)
            if (body.isBlank()) authorName else "$authorName: $body"
        } else {
            observeChatPreviewText(lastNote, 80)
        }
    Text(
        preview,
        style = MaterialTheme.typography.bodySmall,
        // Unread lifts the preview to full contrast, as a Messages-list row does.
        color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Resolves [hex] to its best display name, reactively, falling back to a short hex. */
@Composable
private fun rememberConcordDisplayName(
    hex: HexKey,
    accountViewModel: AccountViewModel,
): String {
    val user = remember(hex) { LocalCache.checkGetOrCreateUser(hex) } ?: return remember(hex) { hex.take(8) }
    val name by observeUserName(user, accountViewModel)
    return name
}

/**
 * Confirms leaving a community. Deliberately explicit about the blast radius: leaving is a private
 * edit of *this account's* kind-13302 list — nobody is told, no roster changes — but it also drops
 * the entry that carries the community's keys, so history this account can no longer derive may be
 * gone for good. The owner gets an extra line: the entry is the only place their owner salt lives,
 * so leaving is what actually retires the community for them.
 */
@Composable
fun ConcordLeaveDialog(
    communityName: String,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.concord_leave_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.concord_leave_message, communityName))
                if (isOwner) {
                    Text(
                        stringRes(Res.string.concord_leave_owner_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringRes(Res.string.leave),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/** Confirms dissolving a community (CORD-02 §9): irreversible, so the confirm button is the error color. */
@Composable
fun ConcordDissolveDialog(
    communityName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.concord_dissolve_title, communityName)) },
        text = { Text(stringRes(Res.string.concord_dissolve_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.concord_dissolve_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/** A pending channel create ([channelIdHex] null) or rename target. */
private data class ConcordChannelEditor(
    val channelIdHex: String?,
    val initialName: String,
)

/** A pending privatise ([toPrivate]) or publicise of [channelIdHex]. */
private data class ConcordChannelConversion(
    val channelIdHex: String,
    val name: String,
    val toPrivate: Boolean,
)

/** Confirms a Private/Public conversion; privatising also names the new access Role. */
@Composable
private fun ConcordChannelConvertDialog(
    target: ConcordChannelConversion,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var roleName by remember { mutableStateOf(target.name) }
    val action = if (target.toPrivate) Res.string.concord_channel_make_private else Res.string.concord_channel_make_public
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (target.toPrivate) {
                    Text(stringRes(Res.string.concord_channel_make_private_message, target.name, roleName.ifBlank { target.name }))
                    OutlinedTextField(
                        value = roleName,
                        onValueChange = { roleName = it },
                        singleLine = true,
                        label = { Text(stringRes(Res.string.concord_channel_access_role_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(stringRes(Res.string.concord_channel_make_public_message, target.name))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(roleName.trim().ifBlank { null }) }) {
                Text(stringRes(action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/** The per-channel-row overflow menu (rename / privacy / rotate / delete), shown only to channel managers. */
@Composable
private fun ConcordChannelRowMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePrivate: () -> Unit,
    isPrivate: Boolean,
    onRotateKey: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            SymbolIcon(
                symbol = MaterialSymbols.MoreVert,
                contentDescription = stringRes(Res.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.concord_channel_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(if (isPrivate) Res.string.concord_channel_make_public else Res.string.concord_channel_make_private)) },
                onClick = {
                    expanded = false
                    onTogglePrivate()
                },
            )
            onRotateKey?.let { rotate ->
                DropdownMenuItem(
                    text = { Text(stringRes(Res.string.concord_channel_rotate_key)) },
                    onClick = {
                        expanded = false
                        rotate()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringRes(Res.string.concord_channel_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Name-entry dialog for creating a new channel or renaming an existing one. */
@Composable
private fun ConcordChannelEditDialog(
    initialName: String,
    isCreate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, makePrivate: Boolean, accessRoleName: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var makePrivate by remember { mutableStateOf(false) }
    var roleName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringRes(
                    if (isCreate) {
                        Res.string.concord_channel_create
                    } else {
                        Res.string.concord_channel_rename
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringRes(Res.string.concord_channel_name_label)) },
                    isError = !concordNameFits(name),
                    supportingText = { ConcordNameBudget(name) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // A new channel may be Private (CORD-03): its own key, and an access Role — the
                // Roles scoped to a channel ARE its access list (CORD-04 §2).
                if (isCreate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringRes(Res.string.concord_channel_private_toggle), modifier = Modifier.weight(1f))
                        Switch(checked = makePrivate, onCheckedChange = { makePrivate = it })
                    }
                    if (makePrivate) {
                        Text(
                            stringRes(Res.string.concord_channel_private_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = roleName,
                            onValueChange = { roleName = it },
                            singleLine = true,
                            placeholder = { Text(name.trim()) },
                            label = { Text(stringRes(Res.string.concord_channel_access_role_label)) },
                            isError = !concordNameFits(roleName),
                            supportingText = { if (roleName.isNotBlank()) ConcordNameBudget(roleName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                // Past the 64-byte cap every reader drops the edition, so the action would silently no-op.
                enabled = name.isNotBlank() && concordNameFits(name) && concordNameFits(roleName),
                onClick = { if (name.isNotBlank() && concordNameFits(name) && concordNameFits(roleName)) onConfirm(name.trim(), makePrivate, roleName.trim().ifBlank { null }) },
            ) {
                Text(
                    stringRes(
                        if (isCreate) {
                            Res.string.concord_channel_create
                        } else {
                            Res.string.concord_channel_rename_save
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/** Shows a freshly minted invite link as a QR code with copy + share actions. */
@Composable
private fun InviteLinkDialog(
    link: String,
    onDismiss: () -> Unit,
) {
    val concordInviteTitleStr = stringRes(Res.string.concord_invite_title)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val textSharer = rememberTextSharer()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.concord_invite_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QrCodeDrawer(contents = link, modifier = Modifier.size(220.dp))
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                textSharer.share(link, null, concordInviteTitleStr)
                onDismiss()
            }) {
                Text(stringRes(Res.string.quick_action_share))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch { clipboard.setText(link) }
                onDismiss()
            }) {
                Text(stringRes(Res.string.copy_to_clipboard))
            }
        },
    )
}
