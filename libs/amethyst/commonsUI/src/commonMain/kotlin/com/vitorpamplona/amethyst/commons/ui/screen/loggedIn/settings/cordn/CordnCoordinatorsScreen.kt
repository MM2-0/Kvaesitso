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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.cordn.ui.SettingsFormBlock
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cordn.CordnRuntime
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_add_action
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_add_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_copy_key
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_action
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_add
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_none
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_seen
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_unheard
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_health_down
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_health_ok
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_health_retrying
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_health_unknown
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_identify
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_key
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_label
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_label_note
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_more
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_none
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_purge
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_purge_body
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_purge_title
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_relays_label
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_remove
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_rename
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_section_discover
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_section_manual
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_section_yours
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_server
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_server_claim
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_server_silent
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_title
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coordinator_pubkey
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coordinator_relays
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable_detail
import com.vitorpamplona.amethyst.commons.resources.cordn_info_save
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CopyableKeyRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CoordinatorIdentityRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.coordinatorDisplayName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CoordinatorHealth
import com.vitorpamplona.cordn.spec00Coordinator.CoordinatorServerInfo
import com.vitorpamplona.cordn.spec00Coordinator.CordnCoordinatorDiscovery
import com.vitorpamplona.cordn.spec00Coordinator.DiscoveredCoordinator
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The coordinators this account talks to.
 *
 * ## Why a coordinator is a first-class thing and not a relay setting
 *
 * Relays are interchangeable and redundant; losing one costs nothing. A
 * coordinator is the sole authority for the groups it serves (`spec/00.md`
 * §4) — losing it loses the ordering, and a second one does not mirror the
 * first. So this screen lists named things a user chose, with what each one
 * costs and two very different ways to stop using one.
 *
 * ## Remove and purge are different decisions
 *
 * Removing closes the session and leaves the MLS state on disk, so re-adding
 * brings the groups back. Purging deletes the ratchet trees, the cursors and
 * the KeyPackage private halves, after which those groups can only be
 * re-entered by a fresh invitation — MLS state cannot be rebuilt from
 * anywhere else. The confirmation says that, because "delete" normally means
 * the reversible one.
 *
 * Neither tells the coordinator anything. It keeps what it already had; this
 * screen is about the device, and implying otherwise would be selling a
 * deletion nobody can perform.
 */
@Composable
fun CordnCoordinatorsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_coordinators_title), nav) },
    ) { padding ->
        if (runtime == null) {
            // The app's own empty state, centred and titled, rather than a
            // sentence stranded in the top-left corner.
            EmptyState(
                title = stringRes(Res.string.cordn_group_unavailable),
                description = stringRes(Res.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val coordinators by runtime.coordinators.collectAsStateWithLifecycle()

        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringRes(Res.string.cordn_coordinators_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Three sections rather than one column split by rules: what you
            // already use, what is on offer, and the manual escape hatch. The
            // dividers said "something else follows" without saying what.
            SettingsSection(Res.string.cordn_coordinators_section_yours) {
                SettingsFormBlock {
                    coordinators.forEachIndexed { index, config ->
                        CoordinatorCard(config, runtime, accountViewModel, nav)
                        // Between coordinators, never after the last: the
                        // section's own card edge already ends the list.
                        if (index != coordinators.lastIndex) HorizontalDivider(thickness = DividerThickness)
                    }

                    if (coordinators.isEmpty()) {
                        Text(
                            text = stringRes(Res.string.cordn_coordinators_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsSection(Res.string.cordn_coordinators_section_discover) {
                SettingsFormBlock {
                    DiscoverCoordinators(runtime, accountViewModel, nav, coordinators.map { it.pubKey }.toSet())
                }
            }

            // Last, and it reads as the fallback it is now that discovery is
            // above it: pasting a 64-character key is what you do when nobody
            // announced the one you were told to use.
            SettingsSection(Res.string.cordn_coordinators_section_manual) {
                SettingsFormBlock {
                    AddCoordinator(runtime)
                }
            }
        }
    }
}

@Composable
private fun CoordinatorCard(
    config: CoordinatorConfig,
    runtime: CordnRuntime,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scope = rememberCoroutineScope()
    var info by remember(config.pubKey) { mutableStateOf<CoordinatorServerInfo?>(null) }
    var infoChecked by remember(config.pubKey) { mutableStateOf(false) }
    var confirmingPurge by remember(config.pubKey) { mutableStateOf(false) }
    var renaming by remember(config.pubKey) { mutableStateOf(false) }
    var menuOpen by remember(config.pubKey) { mutableStateOf(false) }

    val health = runtime.health(config.pubKey)?.collectAsStateWithLifecycle()?.value

    // No Card here: SettingsSection already IS the card, and a second one
    // inside it rendered as a square-cornered slab of a different grey inside
    // the section's rounded one -- every other cordn settings section puts its
    // content straight into the form block.
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CoordinatorIdentityRow(
            pubKey = config.pubKey,
            label = config.label,
            accountViewModel = accountViewModel,
            nav = nav,
            size = 36.dp,
            trailing = {
                // The label was only ever settable while adding a coordinator
                // by hand, so one that arrived from discovery, or came back
                // with a restore, could not be named at all.
                IconButton(onClick = { renaming = true }) {
                    Icon(
                        symbol = MaterialSymbols.Edit,
                        contentDescription = stringRes(Res.string.cordn_coordinators_rename),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) { name ->
            // Name and state together at the top, because "is this one working"
            // is the question this screen is opened with. Everything verifiable
            // sits below, where it is read once rather than scanned.
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                health?.let { HealthLine(it) }
            }
        }

        if (renaming) {
            RenameCoordinatorDialog(
                current = config.label,
                onDismiss = { renaming = false },
                onSave = {
                    renaming = false
                    scope.launch { runtime.relabel(config.pubKey, it) }
                },
            )
        }

        // An npub, not the 64 hex characters that used to sit here. This is the
        // screen where you check that the coordinator you were told to use is
        // the one you added -- and what you were told is an npub, which a hex
        // string cannot be compared against at all. The full value is one tap
        // away on the clipboard, which is how a comparison is actually done.
        val coordinator = remember(config.pubKey) { LocalCache.getOrCreateUser(config.pubKey) }
        CopyableKeyRow(
            label = stringRes(Res.string.cordn_coordinators_key),
            shown = coordinator.pubkeyDisplayHex(),
            copied = coordinator.pubkeyNpub(),
            copyDescription = stringRes(Res.string.cordn_coordinators_copy_key),
        )

        // Every relay, not the first two and a count: this is the management
        // screen, and an address the coordinator answers on is the thing you
        // came here to check or to rule out.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringRes(Res.string.cordn_coordinators_relays_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            config.relays.forEach { relay ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        symbol = MaterialSymbols.Link,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = relay.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Only ever what a call of ours already observed -- nothing here
        // polls. The handshake below is a call the user asked for.
        if (infoChecked) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text =
                        info?.let {
                            stringRes(
                                Res.string.cordn_coordinators_server,
                                listOfNotNull(it.name, it.version, it.protocolVersion).joinToString(" \u00b7 "),
                            )
                        } ?: stringRes(Res.string.cordn_coordinators_server_silent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    // Said next to it, every time, because a name on a settings
                    // screen reads as verified and this one is not: §8.5 makes
                    // the pubkey the identity and nothing else.
                    text = stringRes(Res.string.cordn_coordinators_server_claim),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = {
                scope.launch {
                    info = runCatchingCancellable { runtime.serverInfo(config.pubKey) }.getOrNull()
                    infoChecked = true
                }
            }) {
                Text(stringRes(Res.string.cordn_coordinators_identify))
            }

            Spacer(Modifier.weight(1f))

            // Behind a menu rather than beside Identify. Purge deletes this
            // coordinator's groups and keys off the device, and it sat one
            // tap away from a harmless handshake button, at the same size.
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        symbol = MaterialSymbols.MoreVert,
                        contentDescription = stringRes(Res.string.cordn_coordinators_more),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringRes(Res.string.cordn_coordinators_remove)) },
                        onClick = {
                            menuOpen = false
                            scope.launch { runtime.forget(config.pubKey) }
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringRes(Res.string.cordn_coordinators_purge),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            confirmingPurge = true
                        },
                    )
                }
            }
        }
    }

    if (confirmingPurge) {
        AlertDialog(
            onDismissRequest = { confirmingPurge = false },
            title = { Text(stringRes(Res.string.cordn_coordinators_purge_title)) },
            text = { Text(stringRes(Res.string.cordn_coordinators_purge_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingPurge = false
                    scope.launch { runtime.purge(config.pubKey) }
                }) {
                    Text(stringRes(Res.string.cordn_coordinators_purge), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingPurge = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }
}

/**
 * What this coordinator's own traffic has shown, and nothing more.
 *
 * `CoordinatorHealth` records what the calls the app was making anyway
 * observed; it never polls, because a poll is a call and every call is
 * metadata (§8). So "unknown" here means nothing has been asked of it yet,
 * which is genuinely different from "down" and is shown differently.
 */
@Composable
private fun HealthLine(state: CoordinatorHealth.State) {
    val text =
        when {
            state.isUnknown -> stringRes(Res.string.cordn_coordinators_health_unknown)
            state.isDown -> pluralStringRes(Res.plurals.cordn_coordinators_health_down, state.consecutiveFailures, state.consecutiveFailures)
            state.consecutiveFailures > 0 -> stringRes(Res.string.cordn_coordinators_health_retrying)
            else -> stringRes(Res.string.cordn_coordinators_health_ok)
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.isDown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Coordinators that announced themselves, offered instead of a hex field.
 *
 * Adding one used to mean pasting a 64-character public key and a list of
 * relay URLs, which is not something anyone can do without being told the
 * answer out of band. Coordinators publish CEP-6 announcements; reading them
 * is a relay query that touches no coordinator, so nothing here tells anyone
 * that this account exists.
 *
 * Deliberately not automatic on entry. The query is cheap but it is still the
 * user's relays being asked a question on their behalf, and a screen that
 * reaches out the moment it opens is the kind of thing this feature is
 * supposed to be careful about.
 */
@Composable
private fun DiscoverCoordinators(
    runtime: CordnRuntime,
    accountViewModel: AccountViewModel,
    nav: INav,
    known: Set<String>,
) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<CordnCoordinatorDiscovery.Result?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failed = stringRes(Res.string.cordn_coordinators_discover_failed)

    Text(
        text = stringRes(Res.string.cordn_coordinators_discover_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedButton(
        onClick = {
            busy = true
            error = null
            scope.launch {
                try {
                    result = runtime.discover(accountViewModel.account.outboxRelays.flow.value)
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    error = e.message ?: failed
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(stringRes(Res.string.cordn_coordinators_discover_action))
    }

    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    // The app's own crossfade, so performance mode still turns it off.
    CrossfadeIfEnabled(
        targetState = result,
        label = "cordn-discovery",
    ) { found ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (found == null) return@Column

            // Already-added ones are dropped rather than shown disabled: this
            // list is "what you could add", and a row that does nothing is
            // just something else to read.
            val offers = found.coordinators.filter { it.pubKey !in known }

            if (offers.isEmpty()) {
                Text(
                    text =
                        if (found.unreachable.isEmpty()) {
                            stringRes(Res.string.cordn_coordinators_discover_none)
                        } else {
                            // "Nobody is announcing" and "we were not told" are
                            // different answers and only one of them is final.
                            pluralStringRes(Res.plurals.cordn_coordinators_discover_unheard, found.unreachable.size, found.unreachable.size)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            offers.forEach { offer -> DiscoveredCard(offer, runtime, accountViewModel, nav) }
        }
    }
}

/**
 * Names a coordinator, or clears the name it was given.
 *
 * Empty saves as no label rather than an empty one, so the display falls back
 * to the profile and then the key instead of rendering a blank line.
 */
@Composable
private fun RenameCoordinatorDialog(
    current: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var input by remember { mutableStateOf(current.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.cordn_coordinators_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringRes(Res.string.cordn_coordinators_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    // The same caution the add form gives, for the same reason.
                    text = stringRes(Res.string.cordn_coordinators_label_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(input.trim().ifEmpty { null }) }) {
                Text(stringRes(Res.string.cordn_info_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun DiscoveredCard(
    offer: DiscoveredCoordinator,
    runtime: CordnRuntime,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // The announcement's name, not the profile's: in a discovery list
            // the CEP-6 surface is the thing being offered, and both are the
            // server's own word for itself anyway. The avatar is worth having
            // regardless -- it is the only part of this card that is hard to
            // impersonate at a glance.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserPicture(userHex = offer.pubKey, size = 24.dp, accountViewModel = accountViewModel, nav = nav)
                Text(
                    // Its own word for itself, and said so: a coordinator cannot
                    // prove a name, which is why this never becomes the label.
                    // Passed as the label so the announcement still wins, with
                    // the profile catching an offer that announced no name.
                    text = coordinatorDisplayName(offer.pubKey, offer.surface.name, accountViewModel),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            offer.surface.about?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = offer.relays.joinToString { it.url },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // Staleness matters more here than anywhere: the last live
                // survey found most announcements were months-dead demos.
                text = stringRes(Res.string.cordn_coordinators_discover_seen, timeAgoNoDot(offer.announcedAt).trim()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            runtime.session(offer.toConfig())
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringRes(Res.string.cordn_coordinators_discover_add))
            }
        }
    }
}

@Composable
private fun AddCoordinator(runtime: CordnRuntime) {
    val scope = rememberCoroutineScope()
    var pubKeyInput by remember { mutableStateOf("") }
    var relaysInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val failed = stringRes(Res.string.cordn_coordinators_add_failed)

    val config =
        remember(pubKeyInput, relaysInput, labelInput) {
            CoordinatorConfig.fromInput(pubKeyInput, relaysInput, labelInput)
        }

    OutlinedTextField(
        value = pubKeyInput,
        onValueChange = {
            pubKeyInput = it
            error = null
        },
        label = { Text(stringRes(Res.string.cordn_create_coordinator_pubkey)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = relaysInput,
        onValueChange = {
            relaysInput = it
            error = null
        },
        label = { Text(stringRes(Res.string.cordn_create_coordinator_relays)) },
        singleLine = false,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = labelInput,
        onValueChange = { labelInput = it },
        label = { Text(stringRes(Res.string.cordn_coordinators_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        // A label is ours, never theirs. A coordinator cannot prove a name and
        // this one is not asked for one.
        text = stringRes(Res.string.cordn_coordinators_label_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    Button(
        onClick = {
            val target = config ?: return@Button
            busy = true
            error = null
            scope.launch {
                try {
                    runtime.session(target)
                    pubKeyInput = ""
                    relaysInput = ""
                    labelInput = ""
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    error = e.message ?: failed
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && config != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringRes(Res.string.cordn_coordinators_add_action))
    }
}
