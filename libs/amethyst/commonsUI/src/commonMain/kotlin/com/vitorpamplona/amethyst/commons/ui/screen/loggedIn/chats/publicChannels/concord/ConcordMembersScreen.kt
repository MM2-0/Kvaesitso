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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordMembership
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.concord_members_ban
import com.vitorpamplona.amethyst.commons.resources.concord_members_ban_message
import com.vitorpamplona.amethyst.commons.resources.concord_members_ban_message_refound
import com.vitorpamplona.amethyst.commons.resources.concord_members_ban_title
import com.vitorpamplona.amethyst.commons.resources.concord_members_empty
import com.vitorpamplona.amethyst.commons.resources.concord_members_kick
import com.vitorpamplona.amethyst.commons.resources.concord_members_kick_message
import com.vitorpamplona.amethyst.commons.resources.concord_members_kick_title
import com.vitorpamplona.amethyst.commons.resources.concord_members_make_admin
import com.vitorpamplona.amethyst.commons.resources.concord_members_remove
import com.vitorpamplona.amethyst.commons.resources.concord_members_remove_admin
import com.vitorpamplona.amethyst.commons.resources.concord_members_remove_confirm
import com.vitorpamplona.amethyst.commons.resources.concord_members_remove_message
import com.vitorpamplona.amethyst.commons.resources.concord_members_remove_title
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_message
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_none_assignable
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_out_of_reach
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_save
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_title
import com.vitorpamplona.amethyst.commons.resources.concord_members_title
import com.vitorpamplona.amethyst.commons.resources.concord_members_unban
import com.vitorpamplona.amethyst.commons.resources.concord_role_admin
import com.vitorpamplona.amethyst.commons.resources.concord_role_banned
import com.vitorpamplona.amethyst.commons.resources.concord_role_kicked
import com.vitorpamplona.amethyst.commons.resources.concord_role_left
import com.vitorpamplona.amethyst.commons.resources.concord_role_owner
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookAction
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookEntry
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The members roster of one Concord community — the analog of NIP-29's
 * `RelayGroupMembersScreen`. Concord has no relay-signed roster (membership is key
 * possession), so this shows the *privileged* roster derivable from the folded
 * Control Plane: the owner, every role-holder (admins/moderators), and banned
 * users. The overflow menu offers promote/demote (owner) and ban/unban, gated on
 * the viewer's authority exactly as the write path enforces on fold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordMembersScreen(
    communityId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account

    // Self-sufficient: mount the Concord plane subscription so the community folds even when this
    // screen is opened directly (deep link), not only from the hub.
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)

    // Page every channel's bounded history once so the roster includes observed authors who only posted
    // outside the live tail (CORD-02 §5) — the difference between a handful of recent posters and the
    // real membership.
    ConcordMemberHarvest(communityId, accountViewModel)

    // Re-resolve the session as sessions are created/folded (revision-keyed), so a deep link that
    // lands before the community's session exists still picks it up once it does.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(account, communityId, revision) { account.concordSessions.sessionFor(communityId) }
    val state by (session?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()

    // The Guestbook membership (self-signed joins) plus everyone seen publishing a channel message
    // (observed authors, CORD-02 §5) — most members never post a Join, so without the latter the
    // roster collapses to just the owner + privileged roles.
    val guestbookMembers by (session?.members ?: remember { MutableStateFlow(emptySet<HexKey>()) }).collectAsStateWithLifecycle()
    val observedAuthors by (session?.observedAuthors ?: remember { MutableStateFlow(emptySet<HexKey>()) }).collectAsStateWithLifecycle()
    // The coalesced Guestbook: a member whose latest motion is a Leave or an honored Kick (and who has
    // not posted since) shows as departed (CORD-02 §5, CORD-04 §6).
    val guestbook by (session?.guestbook ?: remember { MutableStateFlow(emptyMap<HexKey, GuestbookEntry>()) }).collectAsStateWithLifecycle()

    val myPubKey = account.signer.pubKey
    val roster =
        remember(state, guestbookMembers, observedAuthors, guestbook) {
            val s = state ?: return@remember emptyList<RosterEntry>()
            val authority = s.authority
            val departed = session?.departedMembers().orEmpty()
            val pubkeys =
                (listOf(s.ownerPubKey) + authority.roleHolders() + authority.bannedMembers() + guestbookMembers + observedAuthors)
                    .map { it.lowercase() }
                    .distinct()
            pubkeys
                .map {
                    // The member's most-privileged role name (lowest position ranks highest), so the
                    // roster shows the real "Admin"/"Moderator"/custom label instead of a coarse badge.
                    val roleName =
                        authority
                            .rolesFor(it)
                            .minByOrNull { r -> r.position }
                            ?.name
                            ?.takeIf { n -> n.isNotBlank() }
                    RosterEntry(it, ConcordMembership.of(authority, it), roleName, authority.rolesOf(it), departed[it]?.action)
                }.sortedWith(compareBy({ it.sortRank() }, { it.pubkey }))
        }

    val iAmOwner = state?.authority?.isOwner(myPubKey) == true
    // hasPermission, never effectivePermissions: a banned BAN-holder used to keep the whole Ban /
    // Remove menu. It only stayed harmless because `canBanTarget` below routes through canActOn,
    // which IS ban-aware — a thin margin for the escalation in docs/concord-soft-ban-audit.md.
    val iCanBan = state?.let { it.authority.isOwner(myPubKey) || it.authority.hasPermission(myPubKey, ConcordPermissions.BAN) } == true
    val iCanManageRoles = state?.authority?.hasPermission(myPubKey, ConcordPermissions.MANAGE_ROLES) == true
    val iCanKick = state?.let { it.authority.isOwner(myPubKey) || it.authority.hasPermission(myPubKey, ConcordPermissions.KICK) } == true

    // The roles this viewer may actually hand out. The fold drops a grant whose granter does
    // not *strictly* outrank every assigned role, so offering a role at or above our own
    // position would publish an edition that every client then silently discards. The owner
    // sits at rank 0 and no role may claim position 0, so this admits everything for them.
    val assignableRoles =
        remember(state, myPubKey) {
            val authority = state?.authority ?: return@remember emptyList<AssignableRole>()
            val myRank = authority.rank(myPubKey) ?: return@remember emptyList()
            authority
                .roles()
                .filter { (_, role) -> myRank < role.position }
                .map { (id, role) -> AssignableRole(id, role.name, role.position) }
                .sortedBy { it.position }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringRes(Res.string.concord_members_title), maxLines = 1)
                        state?.metadata?.name?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(Res.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (roster.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringRes(Res.string.concord_members_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(roster, key = { it.pubkey }) { entry ->
                    ConcordMemberRow(
                        entry = entry,
                        communityId = communityId,
                        isSelf = entry.pubkey.equals(myPubKey, ignoreCase = true),
                        viewerIsOwner = iAmOwner,
                        viewerCanBan = iCanBan,
                        // Ban/Remove are rank-gated the same way Roles… is. The owner short-circuits
                        // because canActOn begins at hasPermission, which is false while banned, and
                        // a rogue BAN holder can currently banlist the owner — see the note on
                        // Account.concordBanTarget.
                        canBanTarget =
                            iAmOwner ||
                                state?.authority?.canActOn(myPubKey, entry.pubkey, ConcordPermissions.BAN) == true,
                        // A Kick needs KICK and a strict outrank of the target (CORD-04 §6), the same rank rule.
                        canKickTarget = iCanKick && state?.authority?.canActOn(myPubKey, entry.pubkey, ConcordPermissions.KICK) == true,
                        // In a Private community a ban also Refounds (CORD-05 §5), so the dialog must not
                        // promise they keep reading.
                        banRotatesKeys = state?.banRequiresRefounding(listOf(entry.pubkey)) == true,
                        viewerCanManageRoles = iCanManageRoles,
                        // canActOn folds the whole rank rule for us: we hold MANAGE_ROLES, we're not
                        // banned, the target isn't the owner (unremovable), and we strictly outrank
                        // them — which also rules out acting on ourselves (equal cannot act on equal).
                        canManageRolesOnTarget = state?.authority?.canActOn(myPubKey, entry.pubkey, ConcordPermissions.MANAGE_ROLES) == true,
                        assignableRoles = assignableRoles,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ConcordMemberRow(
    entry: RosterEntry,
    communityId: String,
    isSelf: Boolean,
    viewerIsOwner: Boolean,
    viewerCanBan: Boolean,
    canBanTarget: Boolean,
    canKickTarget: Boolean,
    banRotatesKeys: Boolean,
    viewerCanManageRoles: Boolean,
    canManageRolesOnTarget: Boolean,
    assignableRoles: List<AssignableRole>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val user = remember(entry.pubkey) { LocalCache.checkGetOrCreateUser(entry.pubkey) }
    val isOwnerTarget = entry.membership == ConcordMembership.OWNER
    val isBanned = entry.membership == ConcordMembership.BANNED
    val isAdmin = entry.membership == ConcordMembership.ADMIN

    // Owner can promote/demote anyone but the owner; ban is available to owner + BAN holders that
    // strictly outrank the target, never against the owner or yourself. A banned user only offers
    // "unban" — and unban is rank-gated too, so whoever cannot ban you cannot lift your ban either.
    val canToggleAdmin = viewerIsOwner && !isOwnerTarget && !isBanned && !isSelf
    val canBan = viewerCanBan && canBanTarget && !isOwnerTarget && !isSelf
    // Hard removal (CORD-06 Refounding) rotates the community key; same authority as ban.
    val canRemove = viewerCanBan && canBanTarget && !isOwnerTarget && !isSelf
    // A Kick is the cooperative removal (CORD-04 §6): pointless against a banned or already-kicked member.
    val canKick = canKickTarget && !isOwnerTarget && !isSelf && !isBanned && entry.departure != GuestbookAction.KICK
    // Shown to any MANAGE_ROLES holder, but disabled with a reason when this particular
    // member (or every defined role) is out of our reach — a grant we don't outrank
    // publishes fine and is then dropped by every client's fold, so a silently no-op
    // control would be worse than none. The owner's own row never offers it: the owner
    // is unremovable and outranks everyone, so canManageRolesOnTarget is false there.
    val rolesBlockedReason =
        when {
            !canManageRolesOnTarget -> stringRes(Res.string.concord_members_roles_out_of_reach)
            assignableRoles.isEmpty() -> stringRes(Res.string.concord_members_roles_none_assignable)
            else -> null
        }
    val hasMenu = canToggleAdmin || canBan || canRemove || canKick || viewerCanManageRoles

    var editRoles by remember { mutableStateOf(false) }
    if (editRoles) {
        ConcordRolesDialog(
            assignable = assignableRoles,
            current = entry.roleIds,
            onConfirm = { selected ->
                accountViewModel.setConcordRoles(communityId, entry.pubkey, selected)
                editRoles = false
            },
            onDismiss = { editRoles = false },
        )
    }

    var confirmKick by remember { mutableStateOf(false) }
    if (confirmKick) {
        ConcordKickMemberDialog(
            onConfirm = {
                accountViewModel.kickConcordMember(communityId, entry.pubkey)
                confirmKick = false
            },
            onDismiss = { confirmKick = false },
        )
    }

    // A ban is reversible here, but not for the banned member: clients such as Armada drop the
    // community from a banned member's list on sight, so a mis-tap still costs them the community.
    var confirmBan by remember { mutableStateOf(false) }
    if (confirmBan) {
        ConcordConfirmMemberActionDialog(
            title = Res.string.concord_members_ban_title,
            message = if (banRotatesKeys) Res.string.concord_members_ban_message_refound else Res.string.concord_members_ban_message,
            confirm = Res.string.concord_members_ban,
            onConfirm = {
                accountViewModel.setConcordBan(communityId, entry.pubkey, ban = true)
                confirmBan = false
            },
            onDismiss = { confirmBan = false },
        )
    }

    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        ConcordConfirmMemberActionDialog(
            title = Res.string.concord_members_remove_title,
            message = Res.string.concord_members_remove_message,
            confirm = Res.string.concord_members_remove_confirm,
            onConfirm = {
                accountViewModel.removeConcordMember(communityId, entry.pubkey)
                confirmRemove = false
            },
            onDismiss = { confirmRemove = false },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(entry.pubkey, Size35dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            if (user != null) {
                UsernameDisplay(user, accountViewModel = accountViewModel)
            } else {
                Text(entry.pubkey.take(8), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MemberBadge(entry.membership, entry.roleName, entry.departure)
        if (hasMenu) {
            var expanded by remember { mutableStateOf(false) }
            // One Box for button + menu: an expanded DropdownMenu emits a node, and as a direct child
            // of this `spacedBy` Row that adds a gap and shifts the button as you tap it.
            Box {
                IconButton(onClick = { expanded = true }) {
                    SymbolIcon(symbol = MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.more_options))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (canToggleAdmin) {
                        DropdownMenuItem(
                            text = { Text(stringRes(if (isAdmin) Res.string.concord_members_remove_admin else Res.string.concord_members_make_admin)) },
                            onClick = {
                                accountViewModel.setConcordAdmin(communityId, entry.pubkey, makeAdmin = !isAdmin)
                                expanded = false
                            },
                        )
                    }
                    if (viewerCanManageRoles) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringRes(Res.string.concord_members_roles))
                                    rolesBlockedReason?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            enabled = rolesBlockedReason == null,
                            onClick = {
                                editRoles = true
                                expanded = false
                            },
                        )
                    }
                    if (canKick) {
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.concord_members_kick)) },
                            onClick = {
                                confirmKick = true
                                expanded = false
                            },
                        )
                    }
                    if (canBan) {
                        DropdownMenuItem(
                            text = { Text(stringRes(if (isBanned) Res.string.concord_members_unban else Res.string.concord_members_ban)) },
                            onClick = {
                                // Unbanning restores access, so only a ban asks first.
                                if (isBanned) accountViewModel.setConcordBan(communityId, entry.pubkey, ban = false) else confirmBan = true
                                expanded = false
                            },
                        )
                    }
                    if (canRemove) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringRes(Res.string.concord_members_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                confirmRemove = true
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A small pill labelling the member's standing (owner / role name / banned / kicked / left; plain members render nothing). */
@Composable
private fun MemberBadge(
    membership: ConcordMembership,
    roleName: String?,
    departure: GuestbookAction?,
) {
    val label =
        when {
            membership == ConcordMembership.BANNED -> stringRes(Res.string.concord_role_banned)
            departure == GuestbookAction.KICK -> stringRes(Res.string.concord_role_kicked)
            departure == GuestbookAction.LEAVE -> stringRes(Res.string.concord_role_left)
            membership == ConcordMembership.OWNER -> stringRes(Res.string.concord_role_owner)
            // Show the actual granted role ("Admin", "Moderator", or a custom role) rather than a
            // one-size-fits-all badge; fall back to the generic "Admin" label if a role-holder's
            // role name somehow didn't resolve.
            roleName != null -> roleName
            membership == ConcordMembership.ADMIN -> stringRes(Res.string.concord_role_admin)
            else -> return
        }
    val container =
        when {
            membership == ConcordMembership.BANNED -> MaterialTheme.colorScheme.errorContainer
            departure != null -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    val content =
        when {
            membership == ConcordMembership.BANNED -> MaterialTheme.colorScheme.onErrorContainer
            departure != null -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Multi-select over the roles the viewer may assign (CORD-04 role grant).
 *
 * A grant REPLACES the member's role set rather than merging into it, so the box starts
 * checked on everything they already hold — otherwise saving would silently strip the
 * roles that weren't re-checked. Every currently-held role is guaranteed to appear in
 * [assignable]: the caller only opens this when it strictly outranks the member, and the
 * member's rank is the *lowest* position they hold, so all of their roles sit strictly
 * below us too. Like "Make admin", saving applies immediately — no extra confirmation.
 */
@Composable
private fun ConcordRolesDialog(
    assignable: List<AssignableRole>,
    current: Set<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(current) { mutableStateListOf<String>().apply { addAll(current) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.concord_members_roles_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringRes(Res.string.concord_members_roles_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                assignable.forEach { role ->
                    val checked = role.id in selected
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selected.remove(role.id) else selected.add(role.id)
                                }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(role.name.ifBlank { role.id.take(8) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }) {
                Text(stringRes(Res.string.concord_members_roles_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

/** Confirms a Kick — spells out that it is cooperative and re-joinable (CORD-04 §6). */
@Composable
private fun ConcordKickMemberDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.concord_members_kick_title)) },
        text = { Text(stringRes(Res.string.concord_members_kick_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.concord_members_kick), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

/** Confirms a hard removal — spells out that it rotates the community key (CORD-06). */
@Composable
private fun ConcordConfirmMemberActionDialog(
    title: StringResource,
    message: StringResource,
    confirm: StringResource,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(title)) },
        text = { Text(stringRes(message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

private class RosterEntry(
    val pubkey: HexKey,
    val membership: ConcordMembership,
    /** The member's most-privileged role name (e.g. "Admin"/"Moderator"), null for a plain member. */
    val roleName: String?,
    /** Every role id the member currently holds — the preselection for the role picker. */
    val roleIds: Set<String>,
    /** Their winning Leave or honored Kick when the Guestbook shows them departed, else null. */
    val departure: GuestbookAction?,
)

/** One role the viewer is allowed to hand out, ordered by [position] (lower ranks higher). */
private class AssignableRole(
    val id: String,
    val name: String,
    val position: Long,
)

/** Owner first, then admins, then plain members, then departed (left/kicked) members, then banned last. */
private fun RosterEntry.sortRank(): Int = if (departure != null && membership != ConcordMembership.BANNED && membership != ConcordMembership.OWNER) 35 else membership.sortRank() * 10

private fun ConcordMembership.sortRank(): Int =
    when (this) {
        ConcordMembership.OWNER -> 0
        ConcordMembership.ADMIN -> 1
        ConcordMembership.MEMBER -> 2
        ConcordMembership.NONE -> 3
        ConcordMembership.BANNED -> 4
    }
