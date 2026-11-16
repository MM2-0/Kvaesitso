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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relayauth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose.relayAuthPurposeLabelRes
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_auth_ago
import com.vitorpamplona.amethyst.commons.resources.relay_auth_auto_login_when
import com.vitorpamplona.amethyst.commons.resources.relay_auth_auto_message_follows
import com.vitorpamplona.amethyst.commons.resources.relay_auth_auto_message_strangers
import com.vitorpamplona.amethyst.commons.resources.relay_auth_auto_my_relays_and_venues
import com.vitorpamplona.amethyst.commons.resources.relay_auth_auto_read_follows
import com.vitorpamplona.amethyst.commons.resources.relay_auth_blocked_row_desc
import com.vitorpamplona.amethyst.commons.resources.relay_auth_blocked_section
import com.vitorpamplona.amethyst.commons.resources.relay_auth_exception_always
import com.vitorpamplona.amethyst.commons.resources.relay_auth_exception_never
import com.vitorpamplona.amethyst.commons.resources.relay_auth_exception_removed_undo
import com.vitorpamplona.amethyst.commons.resources.relay_auth_exceptions
import com.vitorpamplona.amethyst.commons.resources.relay_auth_forget_session
import com.vitorpamplona.amethyst.commons.resources.relay_auth_global_policy
import com.vitorpamplona.amethyst.commons.resources.relay_auth_no_blocked
import com.vitorpamplona.amethyst.commons.resources.relay_auth_no_exceptions
import com.vitorpamplona.amethyst.commons.resources.relay_auth_no_recent
import com.vitorpamplona.amethyst.commons.resources.relay_auth_policy_always
import com.vitorpamplona.amethyst.commons.resources.relay_auth_policy_always_desc
import com.vitorpamplona.amethyst.commons.resources.relay_auth_policy_custom
import com.vitorpamplona.amethyst.commons.resources.relay_auth_policy_custom_desc
import com.vitorpamplona.amethyst.commons.resources.relay_auth_policy_never
import com.vitorpamplona.amethyst.commons.resources.relay_auth_policy_never_desc
import com.vitorpamplona.amethyst.commons.resources.relay_auth_recent_section
import com.vitorpamplona.amethyst.commons.resources.relay_auth_remove_exception
import com.vitorpamplona.amethyst.commons.resources.relay_auth_segment_always
import com.vitorpamplona.amethyst.commons.resources.relay_auth_segment_never
import com.vitorpamplona.amethyst.commons.resources.relay_auth_session_forgotten_undo
import com.vitorpamplona.amethyst.commons.resources.relay_auth_session_row_desc
import com.vitorpamplona.amethyst.commons.resources.relay_auth_session_section
import com.vitorpamplona.amethyst.commons.resources.relay_auth_session_undo_blocked
import com.vitorpamplona.amethyst.commons.resources.relay_auth_settings_title
import com.vitorpamplona.amethyst.commons.resources.relay_auth_undo
import com.vitorpamplona.amethyst.commons.resources.relay_info
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.MediumRelayIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.RelayIconFilter
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Relay login settings, for ONE account — the decisions are stored per account, so the app bar names
 * whose they are.
 *
 * The screen used to render a single list headed "Per-relay overrides" that was really the union of
 * three different things: explicit overrides, the recorded grant rationale, and last-used timestamps.
 * Most rows were not overrides at all. They are now three lists that each say what they hold:
 *
 * - **Exceptions** — explicit ALLOW/DENY only, so the header is true. `✕` removes the exception and
 *   the relay drops back to the rules above, with an undo.
 * - **Blocked by your block list** — kind-10006 relays. These outrank every control on this screen
 *   and used to be invisible here.
 * - **Recent logins** — the log, labelled by *what the relay was doing* rather than an unexplained
 *   row of avatars.
 */
@Composable
fun RelayAuthSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val store: RelayAuthPermissionStore = account.relayAuthPermissions
    val ledger = account.relayAuthLedger
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val globalPolicy by account.settings.defaultRelayAuthPolicy.collectAsState()
    val blockedRelays by account.blockedRelayList.flow.collectAsState()
    val sessionGrants by account.relayAuthSessionGrants.grants.collectAsState()

    var exceptions by remember { mutableStateOf<Map<String, RelayAuthDecision>>(emptyMap()) }
    var rationales by remember { mutableStateOf<Map<String, Map<AuthPurposeKind, Set<HexKey>>>>(emptyMap()) }
    var lastUsed by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        withContext(Dispatchers.IO) {
            exceptions = store.allDecisions()
            rationales = store.allRationales()
            lastUsed = store.allLastUsed()
        }
    }

    val exceptionUrls = remember(exceptions) { exceptions.keys.sorted() }
    val blockedUrls = remember(blockedRelays) { blockedRelays.map { it.url }.sorted() }
    // Answers given to the prompt without the "remember" switch. Any relay that also carries a rule
    // above is shown there instead — the rule is what actually decides it.
    val sessionUrls =
        remember(sessionGrants, exceptions, blockedUrls) {
            (sessionGrants - exceptions.keys - blockedUrls.toSet()).sorted()
        }
    // The log is everything we have a record of that is not already stated above as a rule.
    val logUrls =
        remember(exceptions, rationales, lastUsed, blockedUrls, sessionUrls) {
            ((rationales.keys + lastUsed.keys) - exceptions.keys - blockedUrls.toSet() - sessionUrls.toSet())
                .sortedByDescending { lastUsed[it] ?: 0L }
        }

    val removedLabel = stringRes(Res.string.relay_auth_exception_removed_undo)
    val sessionForgottenLabel = stringRes(Res.string.relay_auth_session_forgotten_undo)
    val sessionUndoBlockedLabel = stringRes(Res.string.relay_auth_session_undo_blocked)
    val undoLabel = stringRes(Res.string.relay_auth_undo)

    fun forgetSessionGrant(url: String) {
        ledger.revokeSessionGrant(url)
        scope.launch {
            val display = url.normalizeRelayUrlOrNull()?.displayUrl() ?: url
            val result =
                snackbarHostState.showSnackbar(
                    message = sessionForgottenLabel.replace("%1\$s", display),
                    actionLabel = undoLabel,
                    withDismissAction = true,
                )
            // An action label makes Material3 show this indefinitely, so the undo can be tapped long
            // after the fact — including after the policy above was switched to "Never log in", which
            // clears every grant. The ledger refuses to write a new one in that state; report that
            // instead of leaving a tapped undo looking like it silently did nothing.
            if (result == SnackbarResult.ActionPerformed && !ledger.grantForSession(url)) {
                snackbarHostState.showSnackbar(
                    message = sessionUndoBlockedLabel.replace("%1\$s", display),
                    withDismissAction = true,
                )
            }
        }
    }

    fun removeException(url: String) {
        scope.launch {
            val previous = exceptions[url]
            // Clears the override only. The usage history is not this row's to delete — it has its
            // own list now, which is what made the old combined "Forget" ambiguous.
            ledger.clearDecision(url)
            reloadKey++
            val result =
                snackbarHostState.showSnackbar(
                    message = removedLabel.replace("%1\$s", url.normalizeRelayUrlOrNull()?.displayUrl() ?: url),
                    actionLabel = undoLabel,
                    withDismissAction = true,
                )
            if (result == SnackbarResult.ActionPerformed && previous != null) {
                ledger.setDecision(url, previous)
                reloadKey++
            }
        }
    }

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(Res.string.relay_auth_settings_title),
                nav = nav,
                actions = { AccountChip(accountViewModel) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // The mode. One group, one question, one clause of explanation each — every
                    // clause adds a fact its title does not already carry.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupHeader(stringRes(Res.string.relay_auth_global_policy))
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            RelayAuthPolicy.entries.forEachIndexed { index, policy ->
                                if (index > 0) SettingsDivider()
                                val (titleRes, descRes) =
                                    when (policy) {
                                        RelayAuthPolicy.ALWAYS ->
                                            Res.string.relay_auth_policy_always to Res.string.relay_auth_policy_always_desc
                                        RelayAuthPolicy.NEVER ->
                                            Res.string.relay_auth_policy_never to Res.string.relay_auth_policy_never_desc
                                        RelayAuthPolicy.CUSTOM ->
                                            Res.string.relay_auth_policy_custom to Res.string.relay_auth_policy_custom_desc
                                    }
                                PolicyRow(
                                    selected = globalPolicy == policy,
                                    title = stringRes(titleRes),
                                    description = stringRes(descRes),
                                    // Account, not settings: choosing "never log in" also drops this
                                    // session's grants, and that pairing is the account's rule rather
                                    // than this screen's. See Account.changeDefaultRelayAuthPolicy.
                                    onClick = { account.changeDefaultRelayAuthPolicy(policy) },
                                )
                            }
                        }
                    }

                    // The exemptions. The group header carries the grammar, so each row is a short
                    // completion of the sentence rather than a title plus a paragraph restating it.
                    if (globalPolicy == RelayAuthPolicy.CUSTOM) {
                        val myRelays by account.settings.relayAuthTrustMyRelaysAndVenues.collectAsState()
                        val readFollows by account.settings.relayAuthTrustReadFollows.collectAsState()
                        val messageFollows by account.settings.relayAuthTrustMessageFollows.collectAsState()
                        val messageStrangers by account.settings.relayAuthTrustMessageStrangers.collectAsState()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GroupHeader(stringRes(Res.string.relay_auth_auto_login_when))
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                SettingsSwitchTile(
                                    icon = MaterialSymbols.Dns,
                                    title = Res.string.relay_auth_auto_my_relays_and_venues,
                                    checked = myRelays,
                                    onCheckedChange = { account.settings.changeRelayAuthTrustMyRelaysAndVenues(it) },
                                )
                                SettingsDivider()
                                SettingsSwitchTile(
                                    icon = MaterialSymbols.Download,
                                    title = Res.string.relay_auth_auto_read_follows,
                                    checked = readFollows,
                                    onCheckedChange = { account.settings.changeRelayAuthTrustReadFollows(it) },
                                )
                                SettingsDivider()
                                SettingsSwitchTile(
                                    icon = MaterialSymbols.Mail,
                                    title = Res.string.relay_auth_auto_message_follows,
                                    checked = messageFollows,
                                    onCheckedChange = { account.settings.changeRelayAuthTrustMessageFollows(it) },
                                )
                                SettingsDivider()
                                SettingsSwitchTile(
                                    icon = MaterialSymbols.Public,
                                    title = Res.string.relay_auth_auto_message_strangers,
                                    checked = messageStrangers,
                                    onCheckedChange = { account.settings.changeRelayAuthTrustMessageStrangers(it) },
                                )
                            }
                        }
                    }

                    GroupHeader(stringRes(Res.string.relay_auth_exceptions))
                }
                Spacer(Modifier.height(8.dp))
            }

            if (exceptionUrls.isEmpty()) {
                item { EmptyCard(stringRes(Res.string.relay_auth_no_exceptions)) }
            } else {
                itemsIndexed(exceptionUrls, key = { _, url -> "exception:$url" }) { index, url ->
                    GroupedRow(index, exceptionUrls.size) {
                        ExceptionRow(
                            url = url,
                            decision = exceptions[url] ?: RelayAuthDecision.ALLOW,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onDecision = { next ->
                                scope.launch {
                                    ledger.setDecision(url, next)
                                    reloadKey++
                                }
                            },
                            onRemove = { removeException(url) },
                        )
                    }
                }
            }

            // Only rendered when something is granted: an empty card here would advertise a list the
            // user has no way to add to from this screen.
            if (sessionUrls.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    GroupHeader(stringRes(Res.string.relay_auth_session_section))
                    Spacer(Modifier.height(8.dp))
                }
                itemsIndexed(sessionUrls, key = { _, url -> "session:$url" }) { index, url ->
                    GroupedRow(index, sessionUrls.size) {
                        SessionGrantRow(
                            url = url,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onPromote = { next ->
                                scope.launch {
                                    ledger.setDecision(url, next)
                                    reloadKey++
                                }
                            },
                            onForget = { forgetSessionGrant(url) },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                GroupHeader(stringRes(Res.string.relay_auth_blocked_section))
                Spacer(Modifier.height(8.dp))
            }

            if (blockedUrls.isEmpty()) {
                item { EmptyCard(stringRes(Res.string.relay_auth_no_blocked)) }
            } else {
                itemsIndexed(blockedUrls, key = { _, url -> "blocked:$url" }) { index, url ->
                    GroupedRow(index, blockedUrls.size) {
                        BlockedRow(url, accountViewModel, nav)
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                GroupHeader(stringRes(Res.string.relay_auth_recent_section))
                Spacer(Modifier.height(8.dp))
            }

            if (logUrls.isEmpty()) {
                item { EmptyCard(stringRes(Res.string.relay_auth_no_recent)) }
            } else {
                itemsIndexed(logUrls, key = { _, url -> "log:$url" }) { index, url ->
                    GroupedRow(index, logUrls.size) {
                        RecentLoginRow(
                            url = url,
                            purposes = rationales[url]?.keys.orEmpty(),
                            lastUsedSecs = lastUsed[url],
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onPromote = { decision ->
                                scope.launch {
                                    ledger.setDecision(url, decision)
                                    reloadKey++
                                }
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Whose decisions these are. They are stored per account, so the screen has to say which. */
@Composable
private fun AccountChip(accountViewModel: AccountViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(100.dp),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Text(
            text = accountViewModel.account.userProfile().toBestDisplayName(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Primary-colored section label, matching the header used across settings. */
@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** One row of a grouped settings card: rounded outer corners on the first and last rows only. */
@Composable
private fun GroupedRow(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(sectionCardShape(index, count))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (index > 0) SettingsDivider()
        content()
    }
}

private fun sectionCardShape(
    index: Int,
    count: Int,
): Shape =
    when {
        count <= 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        index == count - 1 -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        else -> RectangleShape
    }

/** A mode of the global policy: radio + title + one clause. */
@Composable
private fun PolicyRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A relay the user has explicitly ruled on. The segmented pair shows which way — unlike the old chip,
 * which read "Allow" both for an explicit allow and for "allowed by whatever policy is set", so the
 * user could not tell which relays would change if they switched modes. `✕` removes the exception.
 */
@Composable
private fun ExceptionRow(
    url: String,
    decision: RelayAuthDecision,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDecision: (RelayAuthDecision) -> Unit,
    onRemove: () -> Unit,
) {
    RelayRowFrame(
        url = url,
        accountViewModel = accountViewModel,
        nav = nav,
        subtitle = {
            Text(
                text =
                    stringRes(
                        if (decision == RelayAuthDecision.ALLOW) Res.string.relay_auth_exception_always else Res.string.relay_auth_exception_never,
                    ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        },
        trailing = {
            DecisionSegments(decision, onDecision)
            IconButton(onClick = onRemove) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.relay_auth_remove_exception))
            }
        },
    )
}

/**
 * A relay the user logged in to from the prompt without asking to remember it. It behaves like an
 * ALLOW exception for the rest of this run and then disappears, so it gets its own group rather than
 * sitting in "Exceptions" — nothing here survives a restart.
 */
@Composable
private fun SessionGrantRow(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    onPromote: (RelayAuthDecision) -> Unit,
    onForget: () -> Unit,
) {
    RelayRowFrame(
        url = url,
        accountViewModel = accountViewModel,
        nav = nav,
        subtitle = {
            Text(
                text = stringRes(Res.string.relay_auth_session_row_desc),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        },
        trailing = {
            // Neither segment is selected: a session grant is not an override, and promoting it to
            // one is exactly what these two buttons are for.
            DecisionSegments(current = null, onDecision = onPromote)
            IconButton(onClick = onForget) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.relay_auth_forget_session))
            }
        },
    )
}

/** A relay on the kind-10006 block list: a hard DENY that outranks everything else on this screen. */
@Composable
private fun BlockedRow(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayRowFrame(
        url = url,
        accountViewModel = accountViewModel,
        nav = nav,
        subtitle = {
            Text(
                text = stringRes(Res.string.relay_auth_blocked_row_desc),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        },
        trailing = {
            Icon(
                MaterialSymbols.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
    )
}

/**
 * A relay we have logged in to, and what it was doing. The purpose chips are the recorded rationale
 * read out loud — "your inbox", "a conversation" — which is what the unlabelled facepile never said.
 */
@Composable
private fun RecentLoginRow(
    url: String,
    purposes: Set<AuthPurposeKind>,
    lastUsedSecs: Long?,
    accountViewModel: AccountViewModel,
    nav: INav,
    onPromote: (RelayAuthDecision) -> Unit,
) {
    RelayRowFrame(
        url = url,
        accountViewModel = accountViewModel,
        nav = nav,
        subtitle = {
            if (purposes.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    purposes.take(3).forEach { PurposeChip(it) }
                }
            }
            if (lastUsedSecs != null && lastUsedSecs > 0L) {
                Text(
                    text = stringRes(Res.string.relay_auth_ago, timeAgo(lastUsedSecs, prefix = "")),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        // The only two useful actions on a log row: promote it into an exception, either way.
        trailing = { DecisionSegments(current = null, onDecision = onPromote) },
    )
}

@Composable
private fun PurposeChip(kind: AuthPurposeKind) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(100.dp),
    ) {
        Text(
            text = stringRes(relayAuthPurposeLabelRes(kind)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

/**
 * Always / Never as a pair. [current] is null on a log row, where neither is set yet — the model has
 * three states (ALLOW, DENY, no override) and this control now shows all three honestly.
 */
@Composable
private fun DecisionSegments(
    current: RelayAuthDecision?,
    onDecision: (RelayAuthDecision) -> Unit,
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(100.dp)),
    ) {
        Segment(
            label = stringRes(Res.string.relay_auth_segment_always),
            selected = current == RelayAuthDecision.ALLOW,
            selectedContainer = MaterialTheme.colorScheme.primary,
            selectedContent = MaterialTheme.colorScheme.onPrimary,
            onClick = { onDecision(RelayAuthDecision.ALLOW) },
        )
        Segment(
            label = stringRes(Res.string.relay_auth_segment_never),
            selected = current == RelayAuthDecision.DENY,
            selectedContainer = MaterialTheme.colorScheme.error,
            selectedContent = MaterialTheme.colorScheme.onError,
            onClick = { onDecision(RelayAuthDecision.DENY) },
        )
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    selectedContainer: Color,
    selectedContent: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) selectedContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) selectedContent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

/** Icon + shortened URL + caller-supplied subtitle and trailing controls. Tapping opens relay info. */
@Composable
private fun RelayRowFrame(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    subtitle: @Composable ColumnScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    val relay = remember(url) { url.normalizeRelayUrlOrNull() }

    Row(
        modifier =
            Modifier
                .clickable { nav.nav(Route.RelayInfo(url)) }
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RelayIcon(relay, url, accountViewModel)
        Column(Modifier.weight(1f)) {
            Text(
                text = relay?.displayUrl() ?: url,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            subtitle()
        }
        trailing()
    }
}

/** The relay's NIP-11 icon (robohash fallback), matching the other relay lists in the app. */
@Composable
private fun RelayIcon(
    relay: NormalizedRelayUrl?,
    url: String,
    accountViewModel: AccountViewModel,
) {
    val info = if (relay != null) loadRelayInfo(relay).value else null
    RobohashFallbackAsyncImage(
        robot = info?.id ?: relay?.displayUrl() ?: url,
        model = info?.icon,
        contentDescription = stringRes(Res.string.relay_info, url),
        colorFilter = RelayIconFilter,
        modifier = MediumRelayIconModifier,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
    )
}
