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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cordn.CordnRuntime
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_create_add_member
import com.vitorpamplona.amethyst.commons.resources.cordn_create_admin
import com.vitorpamplona.amethyst.commons.resources.cordn_create_people_done
import com.vitorpamplona.amethyst.commons.resources.cordn_create_people_empty
import com.vitorpamplona.amethyst.commons.resources.cordn_create_people_note
import com.vitorpamplona.amethyst.commons.resources.cordn_create_reach_count
import com.vitorpamplona.amethyst.commons.resources.cordn_create_reach_none
import com.vitorpamplona.amethyst.commons.resources.cordn_create_reach_unknown
import com.vitorpamplona.amethyst.commons.resources.cordn_create_step_people
import com.vitorpamplona.amethyst.commons.resources.cordn_create_you
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable_detail
import com.vitorpamplona.amethyst.commons.resources.cordn_info_add_member_none
import com.vitorpamplona.amethyst.commons.resources.cordn_info_add_member_placeholder
import com.vitorpamplona.amethyst.commons.resources.cordn_info_has_key_package
import com.vitorpamplona.amethyst.commons.resources.cordn_info_remove_member
import com.vitorpamplona.amethyst.commons.resources.cordn_member_count
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.rememberAccountSharedViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.CordnGroupDraft
import com.vitorpamplona.cordn.client.CordnCoverage
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Who the new group is for.
 *
 * ## Why this is a screen and not a field on the create form
 *
 * Choosing people is the step every later one depends on. cordn can only add a
 * member by spending a KeyPackage they published **to the coordinator being
 * used**, so until the roster exists "which coordinator?" has no answer worth
 * giving -- and once it exists the answer is arithmetic. Putting it inline made
 * the create form a scroll again, with a search field, a results list and a
 * roster competing with the group's own name.
 *
 * ## Reachability is counted across coordinators, not for the chosen one
 *
 * A person reachable nowhere cannot be helped by changing the choice on the
 * previous screen, and that is a different fact from "not reachable on the one
 * currently selected". Counted only over coordinators that actually answered:
 * an unreachable server contributes nothing rather than a zero, because a
 * network failure must never be rendered as a claim about a person.
 */
@Composable
fun CordnCreateMembersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val draft = rememberCordnGroupDraft(accountViewModel)

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_create_step_people), nav) },
    ) { padding ->
        if (runtime == null) {
            EmptyState(
                title = stringRes(Res.string.cordn_group_unavailable),
                description = stringRes(Res.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val me = accountViewModel.account.signer.pubKey
        val userSuggestions =
            remember {
                UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
            }
        var search by remember { mutableStateOf("") }

        val coverage by rememberCordnCoverage(runtime, draft.roster)

        // The badge on a SEARCH RESULT cannot come from coverage: coverage is
        // the roster intersected with what a coordinator holds, so somebody not
        // yet added is never in it. This is the unfiltered set, which is what
        // the question "could this person be added at all" needs.
        val reachable by rememberReachableIdentities(runtime)

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
                text = stringRes(Res.string.cordn_create_people_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    if (it.length > 2) userSuggestions.processCurrentWord(it) else userSuggestions.reset()
                },
                label = { Text(stringRes(Res.string.cordn_create_add_member)) },
                placeholder = { Text(stringRes(Res.string.cordn_info_add_member_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Three characters, as everywhere else this list appears: fewer
            // matches most of the address book and is never what someone meant.
            if (search.length > 2) {
                ShowUserSuggestionList(
                    userSuggestions = userSuggestions,
                    onSelect = { user: User ->
                        search = ""
                        userSuggestions.reset()
                        if (user.pubkeyHex != me) draft.add(user.pubkeyHex)
                    },
                    accountViewModel = accountViewModel,
                    modifier = SuggestionListDefaultHeightChat,
                    onEmpty = {
                        Text(
                            text = stringRes(Res.string.cordn_info_add_member_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    },
                    trailingContent = { user ->
                        // Marks only what the coordinator told us. An unmarked row
                        // covers both "has not published here" and "we could not
                        // ask", so it claims nothing either way.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (user.pubkeyHex in reachable) {
                                Icon(
                                    symbol = MaterialSymbols.Key,
                                    contentDescription = stringRes(Res.string.cordn_info_has_key_package),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = {
                                search = ""
                                userSuggestions.reset()
                                if (user.pubkeyHex != me) draft.add(user.pubkeyHex)
                            }) {
                                Icon(
                                    symbol = MaterialSymbols.PersonAdd,
                                    contentDescription = stringRes(Res.string.cordn_create_add_member),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                )
            }

            HorizontalDivider()

            Text(
                text = pluralStringRes(Res.plurals.cordn_member_count, draft.roster.size + 1, draft.roster.size + 1),
                style = MaterialTheme.typography.titleSmall,
            )

            // You are always in it and always able to administer it, so there is
            // nothing to toggle or remove on this row -- it is here so the count
            // above is not one short of what the group will look like.
            CreatorRow(me, draft.egalitarian, accountViewModel, nav)

            draft.roster.forEach { member ->
                MemberRow(
                    member = member,
                    isAdmin = member in draft.coAdmins,
                    egalitarian = draft.egalitarian,
                    coverage = coverage,
                    onToggleAdmin = { draft.toggleAdmin(member) },
                    onRemove = { draft.remove(member) },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (draft.roster.isEmpty()) {
                Text(
                    text = stringRes(Res.string.cordn_create_people_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { nav.popBack() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Text(stringRes(Res.string.cordn_create_people_done))
            }
        }
    }
}

/** The creator's own row: in the group, administering it, not editable. */
@Composable
private fun CreatorRow(
    me: HexKey,
    egalitarian: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserPicture(userHex = me, size = 32.dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            Text(observeUserNameByHex(me, accountViewModel), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringRes(Res.string.cordn_create_you),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!egalitarian) {
            Text(
                text = stringRes(Res.string.cordn_create_admin),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One person in the roster, with what a coordinator could do about them. */
@Composable
private fun MemberRow(
    member: HexKey,
    isAdmin: Boolean,
    egalitarian: Boolean,
    coverage: Map<HexKey, CordnCoverage>,
    onToggleAdmin: () -> Unit,
    onRemove: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val answered = coverage.values.count { it.answered }
    val reaching = coverage.values.count { it.answered && member in it.reachable }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserPicture(userHex = member, size = 32.dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            Text(observeUserNameByHex(member, accountViewModel), style = MaterialTheme.typography.bodyMedium)
            Text(
                text =
                    when {
                        answered == 0 -> stringRes(Res.string.cordn_create_reach_unknown)
                        reaching > 0 -> pluralStringRes(Res.plurals.cordn_create_reach_count, reaching, reaching)
                        else -> stringRes(Res.string.cordn_create_reach_none)
                    },
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (answered > 0 && reaching == 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }

        // Hidden in egalitarian mode: there the admin list is empty by
        // definition, so a toggle would offer a choice nothing can record.
        if (!egalitarian) {
            TextButton(onClick = onToggleAdmin) {
                Text(
                    text = stringRes(Res.string.cordn_create_admin),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                symbol = MaterialSymbols.PersonRemove,
                contentDescription = stringRes(Res.string.cordn_info_remove_member),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The one draft both create screens fill in.
 *
 * Scoped to the account's shared store, the way the chess lobby and board share
 * theirs: a Navigation destination is disposed when you leave it, so a draft kept
 * in `remember` would lose the group's name on the way to picking people. The
 * store is cleared on an account switch, so the next account starts a new draft
 * rather than inheriting a roster built under another key.
 */
@Composable
fun rememberCordnGroupDraft(accountViewModel: AccountViewModel): CordnGroupDraft =
    rememberAccountSharedViewModel(
        key = "CordnGroupDraft-${accountViewModel.account.signer.pubKey}",
    ) { CordnGroupDraft() }

/**
 * What each coordinator this account uses could do with [roster].
 *
 * Asked of coordinators there is already a session with and no others: opening
 * one is what commits to a coordinator, so a screen that is only looking must
 * not open one on the user's behalf. Cheap to call from more than one screen --
 * `CordnKeyPackages` reuses its `kp_list` snapshot for a minute, so the second
 * screen's copy of this question usually costs nothing.
 */
@Composable
fun rememberCordnCoverage(
    runtime: CordnRuntime,
    roster: List<HexKey>,
): State<Map<HexKey, CordnCoverage>> {
    val known by runtime.coordinators.collectAsStateWithLifecycle()
    return produceState<Map<HexKey, CordnCoverage>>(emptyMap(), runtime, known, roster) {
        value =
            if (roster.isEmpty()) {
                emptyMap()
            } else {
                val target = roster.toSet()
                known.associate { it.pubKey to runtime.coverage(it.pubKey, target) }
            }
    }
}

/**
 * Every identity any coordinator this account uses holds a KeyPackage for.
 *
 * The union, unfiltered, for answering "could this person be added anywhere"
 * about somebody who is not in the roster yet -- which
 * [rememberCordnCoverage] cannot answer, because it intersects with the roster
 * by construction. Empty also covers "nothing answered", so callers must only
 * ever use membership in it to mark a yes, never absence to mark a no.
 */
@Composable
fun rememberReachableIdentities(runtime: CordnRuntime): State<Set<HexKey>> {
    val known by runtime.coordinators.collectAsStateWithLifecycle()
    return produceState<Set<HexKey>>(emptySet(), runtime, known) {
        val all = mutableSetOf<HexKey>()
        known.forEach { runtime.identitiesWithKeyPackages(it.pubKey)?.let(all::addAll) }
        value = all
    }
}
