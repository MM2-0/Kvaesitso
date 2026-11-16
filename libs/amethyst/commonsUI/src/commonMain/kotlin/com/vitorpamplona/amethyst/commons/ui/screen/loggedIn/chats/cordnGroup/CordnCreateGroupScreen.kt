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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.cordn.ui.CordnExposureCard
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordn.CordnGroupCreation
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_none
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_seen
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_seen_on
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_discover_unheard
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_hide_older
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_relays_more
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_show_all
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_show_fewer
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_show_older
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_stale_note
import com.vitorpamplona.amethyst.commons.resources.cordn_create_action
import com.vitorpamplona.amethyst.commons.resources.cordn_create_action_invite
import com.vitorpamplona.amethyst.commons.resources.cordn_create_admin_count
import com.vitorpamplona.amethyst.commons.resources.cordn_create_admin_only_me_on
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coordinator_change
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coordinator_discover
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coordinator_hide
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coordinator_new
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coordinator_pubkey
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coordinator_relays
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coverage_all
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coverage_partial
import com.vitorpamplona.amethyst.commons.resources.cordn_create_coverage_unknown
import com.vitorpamplona.amethyst.commons.resources.cordn_create_description
import com.vitorpamplona.amethyst.commons.resources.cordn_create_egalitarian
import com.vitorpamplona.amethyst.commons.resources.cordn_create_egalitarian_note
import com.vitorpamplona.amethyst.commons.resources.cordn_create_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_create_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_create_name
import com.vitorpamplona.amethyst.commons.resources.cordn_create_no_coordinator
import com.vitorpamplona.amethyst.commons.resources.cordn_create_outcome_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_create_outcome_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_create_outcome_link
import com.vitorpamplona.amethyst.commons.resources.cordn_create_outcome_no_welcome
import com.vitorpamplona.amethyst.commons.resources.cordn_create_outcome_open
import com.vitorpamplona.amethyst.commons.resources.cordn_create_outcome_skipped
import com.vitorpamplona.amethyst.commons.resources.cordn_create_outcome_title
import com.vitorpamplona.amethyst.commons.resources.cordn_create_outcome_waiting
import com.vitorpamplona.amethyst.commons.resources.cordn_create_people_none
import com.vitorpamplona.amethyst.commons.resources.cordn_create_step_admins
import com.vitorpamplona.amethyst.commons.resources.cordn_create_step_name
import com.vitorpamplona.amethyst.commons.resources.cordn_create_step_people
import com.vitorpamplona.amethyst.commons.resources.cordn_create_step_where
import com.vitorpamplona.amethyst.commons.resources.cordn_create_title
import com.vitorpamplona.amethyst.commons.resources.cordn_create_unreachable_count
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable_detail
import com.vitorpamplona.amethyst.commons.resources.cordn_member_count
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CoordinatorIdentityRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.coordinatorDisplayName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CordnCoverage
import com.vitorpamplona.cordn.client.GroupExposure
import com.vitorpamplona.cordn.spec00Coordinator.DiscoveredCoordinator
import com.vitorpamplona.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Starting a cordn group: pick the coordinator, name it, create it.
 *
 * ## The coordinator is the first field, not a setting
 *
 * `spec/00.md` §4 makes the coordinator the sole authority for a group's
 * stream, and a `gid` means nothing outside the one that issued it. So
 * "which coordinator" is not a preference that could sensibly default — it is
 * half of the group's identity, chosen once and unchangeable afterwards. It is
 * asked first, in the open, with the exposure card underneath saying what
 * choosing it costs.
 *
 * ## Creating tells the coordinator nothing
 *
 * `CordnRuntime.createGroup` is local MLS work. The coordinator hears about
 * the group when the first Commit or message is posted, which is why a group
 * of one is still entirely private and why this screen works against a
 * coordinator that is down. The exposure card is therefore about what will
 * become true once someone is invited, not about what just happened.
 *
 * Inviting is not here. `CordnGroupManager.invite` exists and works, but its
 * user-facing half — key-package discovery, share links, join requests — is
 * Stage C of `amethyst/plans/2026-09-19-cordn-ui.md`. A member picker that
 * could only offer accounts that happen to have published a KeyPackage to this
 * exact coordinator would promise more than it can do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnCreateGroupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime

    if (runtime == null) {
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
                    title = { Text(stringRes(Res.string.cordn_create_title)) },
                )
            },
        ) { padding ->
            // The app's own empty state, centred and titled, rather than a
            // sentence stranded in the top-left corner.
            EmptyState(
                title = stringRes(Res.string.cordn_group_unavailable),
                description = stringRes(Res.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    val known by runtime.coordinators.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val me = accountViewModel.account.signer.pubKey

    // The draft outlives this screen: picking people is its own
    // destination now, and a Navigation destination is disposed when you
    // leave it, so `remember` here would lose a half-typed name on the way
    // back from the draft.roster.
    val draft = rememberCordnGroupDraft(accountViewModel)
    var creation by remember { mutableStateOf<CordnGroupCreation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val failureFallback = stringRes(Res.string.cordn_create_failed)
    var busy by remember { mutableStateOf(false) }

    // Coordinators this account has never used, from their CEP-6
    // announcements. Not added to the account by looking: picking one here
    // is what commits to it, and `createGroup` opens the session.
    var discovering by remember { mutableStateOf(false) }
    val discoverFailed = stringRes(Res.string.cordn_coordinators_discover_failed)

    // Offers this account already holds are not offers; they are the choices
    // above, and listing them twice would let the same coordinator be picked
    // from two places with different relays.
    val offers =
        remember(draft.discovered, known) {
            val knownKeys = known.mapTo(mutableSetOf()) { it.pubKey }
            draft.discovered
                ?.coordinators
                ?.filter { it.pubKey !in knownKeys }
                .orEmpty()
        }

    val choices = remember(known, offers) { known + offers.map { it.toConfig() } }

    // A re-discovery can drop the coordinator that was picked -- it stopped
    // announcing, or it is now in `known` and therefore not an offer. Left
    // alone, `draft.selected` would name a coordinator no row shows: no radio
    // checked, no exposure card, Create disabled, and nothing saying why.
    // Falling back to the manual entry is the one state that explains itself,
    // because its fields appear.
    LaunchedEffect(choices) {
        if (draft.selected != null && choices.none { it.pubKey == draft.selected }) draft.selected = null
    }

    val config = remember(draft.selected, draft.pubKeyInput, draft.relaysInput, choices) { resolve(choices, draft.selected, draft.pubKeyInput, draft.relaysInput) }

    // Held, not read here: the coverage answers arrive one by one, and only the form below shows
    // them. Reading them at this level would recompose the whole screen, top bar included, on each.
    val coverageState = rememberCordnCoverage(runtime, draft.roster)

    val canCreate = config != null && draft.name.isNotBlank()
    val create: () -> Unit = create@{
        val target = config ?: return@create
        busy = true
        error = null
        scope.launch {
            try {
                // Only the people this coordinator can reach are
                // attempted. An invitation is a Welcome left against
                // a KeyPackage the invitee published *here*, so
                // asking for one it does not hold is a call that can
                // only fail -- and the draft.roster keeps them either way,
                // for the link the outcome offers.
                // takeIf(answered): an unanswered coordinator reports an
                // EMPTY reachable set, so reading it directly would turn
                // "we could not ask" into "invite nobody" and create the
                // group silently empty. Unknown means attempt everyone
                // and let each call say what went wrong.
                val chosenCoverage = draft.selected?.let { coverageState.value[it] }
                val reachable = chosenCoverage?.takeIf { it.answered }?.reachable
                val invitees = draft.roster.filter { reachable == null || it in reachable }
                val result =
                    runtime.createGroupAndInvite(
                        config = target,
                        metadata =
                            CordnGroupMetadata(
                                name = draft.name.trim(),
                                description = draft.description.trim(),
                                adminPubkeys = draft.adminPubKeys(me),
                            ),
                        invitees = invitees,
                    )
                // Straight through when there is nothing to report:
                // a dialog that only ever says "all five went out"
                // is one nobody reads the sixth time.
                if (result.failed.isEmpty() && invitees.size == draft.roster.size) {
                    draft.clear()
                    nav.nav(Route.CordnGroupChat(target.pubKey, result.gid))
                } else {
                    creation = result
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                val message = e.message ?: failureFallback
                error = message
                // The action is in the top bar and the error line is at the bottom of a long form,
                // so the failure is also said where the reader is looking.
                accountViewModel.toastManager.toast(Res.string.cordn_create_title, message, e)
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                // How many will be asked, as the button said when it closed the form.
                postLabel =
                    if (draft.roster.isEmpty()) {
                        stringRes(Res.string.cordn_create_action)
                    } else {
                        pluralStringRes(Res.plurals.cordn_create_action_invite, draft.roster.size, draft.roster.size)
                    },
                titleRes = Res.string.cordn_create_title,
                isActive = { canCreate },
                onCancel = { nav.popBack() },
                onPost = create,
                isBusy = { busy },
            )
        },
    ) { padding ->
        // Collected, not read as `.value` below: the exposure card counts the
        // groups this coordinator already carries, and reading the flow inside
        // composition would leave that count frozen at whatever it was when the
        // screen opened — the one number on the card that can change while you
        // are looking at it, since creating a group is what changes it.
        val rooms by runtime.groups.all.collectAsStateWithLifecycle()
        val coverage by coverageState

        // The best-covering coordinator is offered, not imposed: the moment the
        // user picks one themselves it stops moving under them, even if the
        // roster later changes and a different one would now reach more people.
        LaunchedEffect(coverage, draft.userPicked) {
            if (draft.userPicked || coverage.isEmpty()) return@LaunchedEffect
            val best = coverage.values.filter { it.answered }.maxByOrNull { it.reachable.size }
            if (best != null && best.reachable.isNotEmpty()) draft.selected = best.coordinatorPubKey
        }

        val chosenCoverage = draft.selected?.let { coverage[it] }

        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(Res.string.cordn_create_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StepHeading(1, stringRes(Res.string.cordn_create_step_name))

            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft.name = it },
                label = { Text(stringRes(Res.string.cordn_create_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SpellCheckedOutlinedTextField(
                value = draft.description,
                onValueChange = { draft.description = it },
                label = { Text(stringRes(Res.string.cordn_create_description)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            StepHeading(2, stringRes(Res.string.cordn_create_step_people))

            RosterSummary(
                roster = draft.roster,
                coverage = coverage,
                onOpen = { nav.nav(Route.CordnCreateGroupMembers) },
                accountViewModel = accountViewModel,
                nav = nav,
            )

            StepHeading(3, stringRes(Res.string.cordn_create_step_where))

            // Collapsed to the one coordinator that will be used, because with a
            // draft.roster in hand there is usually nothing left to decide. The whole
            // list is still one tap away for the times there is.
            CoordinatorSummary(
                config = config,
                coverage = chosenCoverage,
                rosterSize = draft.roster.size,
                expanded = draft.coordinatorOpen,
                onToggle = { draft.coordinatorOpen = !draft.coordinatorOpen },
                accountViewModel = accountViewModel,
                nav = nav,
            )

            if (draft.coordinatorOpen) {
                known.forEach { coordinator ->
                    CoordinatorChoice(
                        label = coordinatorDisplayName(coordinator.pubKey, coordinator.label, accountViewModel),
                        pubKey = coordinator.pubKey,
                        selected = draft.selected == coordinator.pubKey,
                        onSelect = {
                            draft.selected = coordinator.pubKey
                            draft.userPicked = true
                        },
                        relays = relayLabel(coordinator.relays),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                // Split on staleness rather than listing everything flat. A run
                // against a public relay returns a long tail of coordinators that
                // last announced months ago, and creating a group on one that has
                // gone away fails at the first call -- so the live ones come first
                // and the rest sit behind a count.
                val cutoff = TimeUtils.now() - TimeUtils.ONE_MONTH
                val live = offers.filter { it.announcedAt >= cutoff }
                val stale = offers.filter { it.announcedAt < cutoff }
                val names = offers.map { resolvedName(it, accountViewModel) }

                // Bounded rather than lazy. The obvious fix for a long list is a
                // LazyColumn, and it is the wrong one here: this screen is a form,
                // and a lazy list disposes what scrolls off it -- which for the
                // text fields below would throw away focus and IME state mid-typing.
                // Capping the rows keeps composition bounded without putting a form
                // inside a recycler.
                val shownLive = if (draft.showAllLive) live else live.take(LIVE_PREVIEW)

                shownLive.forEach { offer ->
                    CoordinatorChoice(
                        // Its own word for itself, and only that: nothing here has
                        // verified the name a coordinator announces.
                        label = disambiguate(resolvedName(offer, accountViewModel), offer.pubKey, names),
                        pubKey = offer.pubKey,
                        selected = draft.selected == offer.pubKey,
                        onSelect = {
                            draft.selected = offer.pubKey
                            draft.userPicked = true
                        },
                        detail = announcedLabel(offer.announcedAt),
                        relays = relayLabel(offer.relays),
                        about = offer.surface.about?.takeIf { it.isNotBlank() },
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                if (live.size > LIVE_PREVIEW) {
                    TextButton(onClick = { draft.showAllLive = !draft.showAllLive }) {
                        Text(
                            if (draft.showAllLive) {
                                stringRes(Res.string.cordn_coordinators_show_fewer)
                            } else {
                                stringRes(Res.string.cordn_coordinators_show_all, live.size)
                            },
                        )
                    }
                }

                if (stale.isNotEmpty()) {
                    TextButton(onClick = { draft.showStale = !draft.showStale }) {
                        Text(
                            if (draft.showStale) {
                                stringRes(Res.string.cordn_coordinators_hide_older)
                            } else {
                                pluralStringRes(Res.plurals.cordn_coordinators_show_older, stale.size, stale.size)
                            },
                        )
                    }
                }

                if (draft.showStale && stale.isNotEmpty()) {
                    Text(
                        text = stringRes(Res.string.cordn_coordinators_stale_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stale.forEach { offer ->
                        CoordinatorChoice(
                            label = disambiguate(resolvedName(offer, accountViewModel), offer.pubKey, names),
                            pubKey = offer.pubKey,
                            selected = draft.selected == offer.pubKey,
                            onSelect = {
                                draft.selected = offer.pubKey
                                draft.userPicked = true
                            },
                            detail = announcedLabel(offer.announcedAt),
                            relays = relayLabel(offer.relays),
                            about = offer.surface.about?.takeIf { it.isNotBlank() },
                            dimmed = true,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }

                CoordinatorChoice(
                    label = stringRes(Res.string.cordn_create_coordinator_new),
                    pubKey = null,
                    selected = draft.selected == null,
                    onSelect = {
                        draft.selected = null
                        draft.userPicked = true
                    },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                // Asks relays, never a coordinator: an announcement is an ordinary
                // event, so looking costs nothing with any of them and tells none
                // of them anything.
                OutlinedButton(
                    onClick = {
                        discovering = true
                        error = null
                        scope.launch {
                            try {
                                draft.discovered = runtime.discover(accountViewModel.account.outboxRelays.flow.value)
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                                error = e.message ?: discoverFailed
                            } finally {
                                discovering = false
                            }
                        }
                    },
                    enabled = !discovering && !busy,
                ) {
                    Text(stringRes(Res.string.cordn_create_coordinator_discover))
                }

                draft.discovered?.takeIf { offers.isEmpty() }?.let { result ->
                    Text(
                        text =
                            if (result.unreachable.isEmpty()) {
                                stringRes(Res.string.cordn_coordinators_discover_none)
                            } else {
                                // "Nobody is announcing" and "we were not told" are
                                // different answers and only one of them is final.
                                // Reporting the first for the second sends someone
                                // off to paste a pubkey by hand over a timeout.
                                pluralStringRes(Res.plurals.cordn_coordinators_discover_unheard, result.unreachable.size, result.unreachable.size)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (draft.selected == null) {
                    OutlinedTextField(
                        value = draft.pubKeyInput,
                        onValueChange = {
                            draft.pubKeyInput = it
                            // Typing one in is choosing it. Without this the
                            // best-coverage effect would replace a hand-entered
                            // coordinator the moment the roster produced a number.
                            draft.userPicked = true
                            error = null
                        },
                        label = { Text(stringRes(Res.string.cordn_create_coordinator_pubkey)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.relaysInput,
                        onValueChange = {
                            draft.relaysInput = it
                            draft.userPicked = true
                            error = null
                        },
                        label = { Text(stringRes(Res.string.cordn_create_coordinator_relays)) },
                        // A coordinator has no address beyond its pubkey (§8.5), so
                        // the relays are how it is reached and more than one is
                        // ordinary. One per line rather than comma-separated,
                        // because a relay URL can contain a comma and a newline
                        // cannot be mistyped into one.
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            StepHeading(4, stringRes(Res.string.cordn_create_step_admins))

            // spec/01.md §5.3: leaving the admin list empty is not "set it up
            // later", it is choosing draft.egalitarian permanently -- so it is offered
            // as a decision here rather than described as one.
            //
            // With a draft.roster in hand the choice is the real one the web client
            // makes: which of the people being added can add and remove others.
            // Choosing anybody at all must include yourself, or the group is
            // born unadministrable, so "you" is never one of the toggles.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringRes(Res.string.cordn_create_egalitarian), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text =
                            if (draft.egalitarian) {
                                stringRes(Res.string.cordn_create_egalitarian_note)
                            } else if (draft.coAdmins.isEmpty()) {
                                stringRes(Res.string.cordn_create_admin_only_me_on)
                            } else {
                                pluralStringRes(Res.plurals.cordn_create_admin_count, draft.coAdmins.size + 1, draft.coAdmins.size + 1)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = draft.egalitarian, onCheckedChange = { draft.egalitarian = it })
            }

            config?.let {
                CordnExposureCard(
                    GroupExposure(
                        coordinator = it.pubKey,
                        // This group, plus whatever this coordinator already
                        // serves for this account: the disclosure is about what
                        // one coordinator can correlate, so a second group on
                        // the same one widens it.
                        linkedGroupCount = 1 + rooms.count { room -> room.coordinatorPubKey == it.pubKey },
                        joinedFromShareLink = false,
                        publishedKeyPackage = false,
                        encryptionPinned = true,
                    ),
                )
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        creation?.let { done ->
            CordnCreationOutcome(
                creation = done,
                roster = draft.roster,
                coordinatorPubKey = config?.pubKey,
                accountViewModel = accountViewModel,
                nav = nav,
                // The draft is spent either way: the group exists, so a
                // dismissal must not leave this roster behind to seed the next
                // new group with the last one's people.
                onDismiss = {
                    creation = null
                    draft.clear()
                },
            )
        }
    }
}

@Composable
private fun CoordinatorChoice(
    label: String,
    pubKey: HexKey?,
    selected: Boolean,
    onSelect: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
    detail: String? = null,
    /** Where it answers. A coordinator has no address beyond its pubkey (§8.5). */
    relays: String? = null,
    /** Its own sentence about itself, if it published one. */
    about: String? = null,
    /** Dimmed for a coordinator that stopped announcing long ago. */
    dimmed: Boolean = false,
) {
    val fade = if (dimmed) 0.6f else 1f
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        // Null on the "use a different one" row, which names no coordinator
        // yet, so there is no identity to show for it.
        if (pubKey != null) {
            // Spacing goes outside, not in `pictureModifier`. That one is the
            // INNER modifier: it styles the image within a box already fixed at
            // `size`, which is why the sibling screen uses it for a border. An
            // end padding there took 8.dp off the drawable's width and none off
            // its height, so every coordinator's avatar drew as a 16x24 oval.
            UserPicture(
                userHex = pubKey,
                size = 24.dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = fade),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            relays?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            about?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade),
                )
            }
        }
    }
}

/** How many live coordinators a discovery run shows before it asks. */
private const val LIVE_PREVIEW = 8

/** Its announced name, or null when it published none. */
private fun DiscoveredCoordinator.announcedName(): String? = surface.name?.takeIf { it.isNotBlank() }

/**
 * What to call an offer: its announced name, else its profile, else a short
 * npub -- never a hex prefix. The announcement still wins, because in a
 * discovery list the CEP-6 surface is the thing being offered.
 */
@Composable
private fun resolvedName(
    offer: DiscoveredCoordinator,
    accountViewModel: AccountViewModel,
): String = coordinatorDisplayName(offer.pubKey, offer.announcedName(), accountViewModel)

/**
 * How long ago a coordinator last announced, in words that stay words.
 *
 * `timeAgoNoDot` answers with a span for something recent and an absolute date
 * for anything past a month, and the caller used one template for both — so
 * every stale coordinator on the discovery list read "Last announced Aug 7
 * ago". The date branch gets a template with no "ago" in it.
 */
@Composable
private fun announcedLabel(announcedAt: Long): String =
    if (TimeUtils.now() - announcedAt > TimeUtils.ONE_MONTH) {
        stringRes(Res.string.cordn_coordinators_discover_seen_on, timeAgoNoDot(announcedAt).trim())
    } else {
        stringRes(Res.string.cordn_coordinators_discover_seen, timeAgoNoDot(announcedAt).trim())
    }

/** The hosts it answers on, the first two and a count of the rest. */
@Composable
private fun relayLabel(relays: List<NormalizedRelayUrl>): String? {
    if (relays.isEmpty()) return null
    val hosts = relays.map { it.url.substringAfter("://").trim('/') }.distinct()
    val shown = hosts.take(2).joinToString(", ")
    return if (hosts.size <= 2) {
        shown
    } else {
        stringRes(Res.string.cordn_coordinators_relays_more, shown, hosts.size - 2)
    }
}

/**
 * Names are the coordinator's own word for itself and collide constantly — the
 * reference server ships as "My coordinator", so a discovery run returns a
 * dozen rows with that name and nothing to tell them apart. A pubkey prefix is
 * added only to the ones that actually clash, so the common case stays clean.
 */
internal fun disambiguate(
    name: String,
    pubKey: HexKey,
    allNames: List<String>,
): String = if (allNames.count { it == name } > 1) "$name \u00b7 ${pubKey.take(8)}" else name

/**
 * The coordinator this screen would create against, or null while the form
 * cannot name one.
 *
 * Null rather than a thrown error: an unfinished form is the normal state of a
 * form, and the button is disabled off this rather than the user being told
 * they are wrong while they type. `CoordinatorConfig` itself rejects a bad
 * pubkey or an empty relay list in its `init`, so the checks here are only
 * about not constructing one yet.
 */
private fun resolve(
    known: List<CoordinatorConfig>,
    selected: String?,
    pubKeyInput: String,
    relaysInput: String,
): CoordinatorConfig? {
    if (selected != null) return known.firstOrNull { it.pubKey == selected }
    return CoordinatorConfig.fromInput(pubKeyInput, relaysInput)
}

/** A numbered step label, so the form reads as an order rather than a pile. */
@Composable
private fun StepHeading(
    step: Int,
    title: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = step.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = title, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Who is in the group, as a row that opens the screen for choosing them.
 *
 * A count and a few faces rather than the roster itself: the roster is where
 * every later step's answer comes from, so it earns its own destination, and
 * repeating it here would put two editors of the same list on screen at once.
 */
@Composable
private fun RosterSummary(
    roster: List<HexKey>,
    coverage: Map<HexKey, CordnCoverage>,
    onOpen: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (roster.isEmpty()) {
            Text(
                text = stringRes(Res.string.cordn_create_people_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                roster.take(FACES).forEach {
                    UserPicture(userHex = it, size = 28.dp, accountViewModel = accountViewModel, nav = nav)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = pluralStringRes(Res.plurals.cordn_member_count, roster.size + 1, roster.size + 1),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Counted over coordinators that answered, so an unreachable one
                // never turns into "nobody can be reached".
                val unreachable = roster.count { member -> coverage.values.none { it.answered && member in it.reachable } }
                if (unreachable > 0 && coverage.values.any { it.answered }) {
                    Text(
                        text = pluralStringRes(Res.plurals.cordn_create_unreachable_count, unreachable, unreachable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Icon(
            symbol = MaterialSymbols.AutoMirrored.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How many faces the summary row shows before it just counts. */
private const val FACES = 5

/**
 * The coordinator that will be used, and why, in one row.
 *
 * Collapsed by default because with a roster entered there is usually nothing
 * left to decide -- one coordinator reaches everybody and the rest do not. The
 * full list is one tap away for when that is not true.
 */
@Composable
private fun CoordinatorSummary(
    config: CoordinatorConfig?,
    coverage: CordnCoverage?,
    rosterSize: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (config == null) {
            Text(
                text = stringRes(Res.string.cordn_create_no_coordinator),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            CoordinatorIdentityRow(
                pubKey = config.pubKey,
                label = config.label,
                accountViewModel = accountViewModel,
                nav = nav,
                size = 32.dp,
                modifier = Modifier.weight(1f),
            ) { name ->
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    if (rosterSize > 0) {
                        // Says which of the three it is: reaches everybody,
                        // leaves somebody out, or was never asked. The third is
                        // not the second.
                        val reached = coverage?.reachable?.size
                        Text(
                            text =
                                when {
                                    coverage == null || !coverage.answered -> stringRes(Res.string.cordn_create_coverage_unknown)
                                    reached == rosterSize -> pluralStringRes(Res.plurals.cordn_create_coverage_all, rosterSize, rosterSize)
                                    else -> stringRes(Res.string.cordn_create_coverage_partial, reached ?: 0, rosterSize)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (coverage != null && coverage.answered && reached != rosterSize) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }

        TextButton(onClick = onToggle) {
            Text(
                if (expanded) {
                    stringRes(Res.string.cordn_create_coordinator_hide)
                } else {
                    stringRes(Res.string.cordn_create_coordinator_change)
                },
            )
        }
    }
}

/**
 * What happened after Create, when it was not simply "everything".
 *
 * Shown instead of a thrown error because the group exists either way: a
 * failed invitation is one row, not a failed creation. Every unfinished person
 * gets the action that would actually work for them -- a retry where the
 * coordinator refused, a link where it holds no KeyPackage at all and no retry
 * ever could.
 */
@Composable
private fun CordnCreationOutcome(
    creation: CordnGroupCreation,
    roster: List<HexKey>,
    coordinatorPubKey: HexKey?,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val open: () -> Unit = {
        onDismiss()
        coordinatorPubKey?.let { nav.nav(Route.CordnGroupChat(it, creation.gid)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.cordn_create_outcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringRes(Res.string.cordn_create_outcome_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val attempted = creation.outcomes.associateBy { it.pubKey }
                roster.forEach { member ->
                    val outcome = attempted[member]
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UserPicture(userHex = member, size = 26.dp, accountViewModel = accountViewModel, nav = nav)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = observeUserNameByHex(member, accountViewModel),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text =
                                    when {
                                        outcome == null -> stringRes(Res.string.cordn_create_outcome_skipped)
                                        outcome.sent -> stringRes(Res.string.cordn_create_outcome_waiting)
                                        outcome.joinedWithoutWelcome -> stringRes(Res.string.cordn_create_outcome_no_welcome)
                                        else -> outcome.failure?.message ?: stringRes(Res.string.cordn_create_outcome_failed)
                                    },
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (outcome?.sent == true) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                            )
                        }
                        // Nobody was told anything, so the only thing that can
                        // reach somebody with no KeyPackage here is a link they
                        // open themselves -- which needs the group to exist,
                        // and now it does.
                        if (outcome == null) {
                            TextButton(onClick = open) { Text(stringRes(Res.string.cordn_create_outcome_link)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = open) { Text(stringRes(Res.string.cordn_create_outcome_open)) } },
    )
}
