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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.agents.AgentKeyProbeResult
import com.vitorpamplona.amethyst.commons.agents.AgentProviderGroup
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPreset
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.agents.OpenRouterConnectResult
import com.vitorpamplona.amethyst.commons.agents.connectOpenRouter
import com.vitorpamplona.amethyst.commons.agents.probeAgentKey
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditFailure
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditKind
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditState
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEdits
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_paste
import com.vitorpamplona.amethyst.commons.resources.agent_keys_add
import com.vitorpamplona.amethyst.commons.resources.agent_keys_budget_hint
import com.vitorpamplona.amethyst.commons.resources.agent_keys_cap_tip
import com.vitorpamplona.amethyst.commons.resources.agent_keys_change
import com.vitorpamplona.amethyst.commons.resources.agent_keys_checking
import com.vitorpamplona.amethyst.commons.resources.agent_keys_choose_provider
import com.vitorpamplona.amethyst.commons.resources.agent_keys_connect_openrouter
import com.vitorpamplona.amethyst.commons.resources.agent_keys_custom
import com.vitorpamplona.amethyst.commons.resources.agent_keys_failed
import com.vitorpamplona.amethyst.commons.resources.agent_keys_get_key
import com.vitorpamplona.amethyst.commons.resources.agent_keys_key_optional
import com.vitorpamplona.amethyst.commons.resources.agent_keys_key_placeholder
import com.vitorpamplona.amethyst.commons.resources.agent_keys_local_no_key
import com.vitorpamplona.amethyst.commons.resources.agent_keys_make_preferred
import com.vitorpamplona.amethyst.commons.resources.agent_keys_more_providers
import com.vitorpamplona.amethyst.commons.resources.agent_keys_on_your_computer
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_denied
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_done_page
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_failed
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_timed_out
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_unsupported
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_waiting
import com.vitorpamplona.amethyst.commons.resources.agent_keys_or
import com.vitorpamplona.amethyst.commons.resources.agent_keys_other_providers
import com.vitorpamplona.amethyst.commons.resources.agent_keys_preferred
import com.vitorpamplona.amethyst.commons.resources.agent_keys_recommended
import com.vitorpamplona.amethyst.commons.resources.agent_keys_rejected
import com.vitorpamplona.amethyst.commons.resources.agent_keys_remove_body
import com.vitorpamplona.amethyst.commons.resources.agent_keys_remove_title
import com.vitorpamplona.amethyst.commons.resources.agent_keys_runs_locally
import com.vitorpamplona.amethyst.commons.resources.agent_keys_save_failed
import com.vitorpamplona.amethyst.commons.resources.agent_keys_save_no_write_relays
import com.vitorpamplona.amethyst.commons.resources.agent_keys_save_not_accepted
import com.vitorpamplona.amethyst.commons.resources.agent_keys_save_signer
import com.vitorpamplona.amethyst.commons.resources.agent_keys_save_unreachable
import com.vitorpamplona.amethyst.commons.resources.agent_keys_save_unreadable
import com.vitorpamplona.amethyst.commons.resources.agent_keys_saving
import com.vitorpamplona.amethyst.commons.resources.agent_keys_status_none
import com.vitorpamplona.amethyst.commons.resources.agent_keys_status_using
import com.vitorpamplona.amethyst.commons.resources.agent_keys_tagline
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test
import com.vitorpamplona.amethyst.commons.resources.agent_keys_title
import com.vitorpamplona.amethyst.commons.resources.agent_keys_unreachable
import com.vitorpamplona.amethyst.commons.resources.agent_keys_unsupported
import com.vitorpamplona.amethyst.commons.resources.agent_keys_unverified
import com.vitorpamplona.amethyst.commons.resources.agent_keys_url
import com.vitorpamplona.amethyst.commons.resources.agent_keys_url_https
import com.vitorpamplona.amethyst.commons.resources.agent_keys_url_invalid
import com.vitorpamplona.amethyst.commons.resources.agent_keys_works
import com.vitorpamplona.amethyst.commons.resources.agent_keys_works_models
import com.vitorpamplona.amethyst.commons.resources.agent_keys_your_keys
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.hide_password
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.resources.routstr_balance_failed
import com.vitorpamplona.amethyst.commons.resources.routstr_balance_low
import com.vitorpamplona.amethyst.commons.resources.routstr_tile_subtitle
import com.vitorpamplona.amethyst.commons.resources.routstr_top_up
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.resources.show_password
import com.vitorpamplona.amethyst.commons.routstr.RoutstrAccount
import com.vitorpamplona.amethyst.commons.routstr.RoutstrFailure
import com.vitorpamplona.amethyst.commons.routstr.RoutstrKeyBalance
import com.vitorpamplona.amethyst.commons.ui.components.PillGood
import com.vitorpamplona.amethyst.commons.ui.components.PillTextField
import com.vitorpamplona.amethyst.commons.ui.components.PillTone
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.rememberRoutstr
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.rememberRoutstrBalance
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.rememberRoutstrNodeName
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.routstrErrorText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.satsText
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings › AI agent: the keys (kind 12000) apps use to run AI tasks for this account.
 * The first key is the preferred one; adding a key makes it preferred.
 */
@Composable
fun AgentKeysSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val keys by accountViewModel.account.agentKeys.flow
        .collectAsStateWithLifecycle()
    val editor = accountViewModel.account.agentKeyEditor
    // The save runs on the account, so it finishes after the screen goes; only its outcome is forgotten.
    val edit by editor.state.collectAsStateWithLifecycle()
    DisposableEffect(editor) {
        val release = editor.attach()
        onDispose { release() }
    }
    var adding by remember { mutableStateOf(false) }
    val routstr = rememberRoutstr(accountViewModel)

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.agent_keys_title), nav) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AgentHero(using = usingNow(keys, edit), edit = edit, onRetry = { editor.retry() })

            if (keys.isNotEmpty()) {
                SavedKeys(
                    keys = keys,
                    adding = adding,
                    edit = edit,
                    onAdd = { adding = !adding },
                    onMakePreferred = { key -> editor.submit(AgentKeyEditKind.MAKE_DEFAULT, key) },
                    onRemove = { key -> editor.submit(AgentKeyEditKind.REMOVE, key) },
                    routstr = routstr,
                    onTopUp = { key -> nav.nav(Route.RoutstrSetup(topUpUrl = key.url, topUpMask = AgentProviderPresets.maskKey(key.key))) },
                )
            }

            AddAgentKey(
                showGrid = adding,
                onShowGrid = { adding = true },
                onTest = { key -> accountViewModel.httpClientBuilder.probeAgentKey(key) },
                onConnectOpenRouter = { donePage, openBrowser -> accountViewModel.httpClientBuilder.connectOpenRouter(donePage, openBrowser = openBrowser) },
                onSave = { key -> editor.submit(AgentKeyEditKind.SAVE, key) },
                onSaved = { adding = false },
                onRoutstr = { nav.nav(Route.RoutstrSetup()) },
                saving = edit.isBusyWith(AgentKeyEditKind.SAVE),
                busy = edit.isBusy,
                compactStart = keys.isEmpty() && !adding,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The provider the chip names: the one an edit just put first as soon as it is on this device
 * (the key list catches up a moment later), else the first saved key.
 */
private fun usingNow(
    keys: List<AgentKeyTag>,
    edit: AgentKeyEditState,
): String? {
    if (edit is AgentKeyEditState.Busy && edit.savedHere && edit.kind != AgentKeyEditKind.REMOVE) return AgentProviderPresets.displayName(edit.url)
    if (edit is AgentKeyEditState.Done && edit.kind != AgentKeyEditKind.REMOVE) return AgentProviderPresets.displayName(edit.url)
    return keys.firstOrNull()?.let { AgentProviderPresets.displayName(it.url) }
}

/** What the screen is for and which key is in use: a quiet surface in the app's own colours, like the front page's. */
@Composable
private fun AgentHero(
    using: String?,
    edit: AgentKeyEditState,
    onRetry: () -> Unit,
) {
    var showTip by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp), filled = true)
                }
                Spacer(Modifier.width(14.dp))
                // The top bar names the screen; the hero only says what it is for.
                Text(
                    stringRes(Res.string.agent_keys_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showTip = !showTip }) {
                    Icon(MaterialSymbols.Info, contentDescription = stringRes(Res.string.agent_keys_cap_tip), tint = scheme.onSurfaceVariant)
                }
            }

            // "Using", not "Connected": saving a key says nothing about whether it works right now.
            // While an edit reads the relays and signs, the chip says so; once the new list is on
            // this device it names the provider, with the spinner until a relay has taken it.
            val busy = edit is AgentKeyEditState.Busy
            val savingHere = busy && !(edit as AgentKeyEditState.Busy).savedHere
            Surface(shape = CircleShape, color = scheme.surfaceContainerHighest) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = scheme.primary)
                    } else {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (using != null) PillGood else scheme.onSurfaceVariant.copy(alpha = 0.6f)),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            savingHere -> stringRes(Res.string.agent_keys_saving)
                            using != null -> stringRes(Res.string.agent_keys_status_using, using)
                            else -> stringRes(Res.string.agent_keys_status_none)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                }
            }

            (edit as? AgentKeyEditState.Failed)?.let { failed ->
                // Read out when it appears: the edit the reader started didn't go through.
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(MaterialSymbols.Error, contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp), filled = true)
                    Spacer(Modifier.width(8.dp))
                    Text(editFailureText(failed.failure), style = MaterialTheme.typography.bodyMedium, color = scheme.error, modifier = Modifier.weight(1f))
                    // Any failed edit, not just a save: the same change again, from a fresh read.
                    if (failed.failure != AgentKeyEditFailure.UNREADABLE && failed.failure != AgentKeyEditFailure.NO_WRITE_RELAYS) {
                        TextButton(onClick = onRetry) { Text(stringRes(Res.string.retry)) }
                    }
                }
            }

            AnimatedVisibility(showTip) {
                Text(stringRes(Res.string.agent_keys_cap_tip), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Why an edit didn't go through, in a sentence: what happened, and whether anything changed. */
@Composable
private fun editFailureText(failure: AgentKeyEditFailure): String =
    when (failure) {
        AgentKeyEditFailure.NO_QUORUM -> stringRes(Res.string.agent_keys_save_unreachable)
        AgentKeyEditFailure.NO_WRITE_RELAYS -> stringRes(Res.string.agent_keys_save_no_write_relays)
        AgentKeyEditFailure.SIGNER_REFUSED -> stringRes(Res.string.agent_keys_save_signer)
        AgentKeyEditFailure.NOT_ACCEPTED -> stringRes(Res.string.agent_keys_save_not_accepted)
        AgentKeyEditFailure.UNREADABLE -> stringRes(Res.string.agent_keys_save_unreadable)
        AgentKeyEditFailure.FAILED -> stringRes(Res.string.agent_keys_save_failed)
    }

@Composable
private fun SavedKeys(
    keys: List<AgentKeyTag>,
    adding: Boolean,
    edit: AgentKeyEditState,
    onAdd: () -> Unit,
    onMakePreferred: (AgentKeyTag) -> Unit,
    onRemove: (AgentKeyTag) -> Unit,
    routstr: RoutstrAccount?,
    onTopUp: (AgentKeyTag) -> Unit,
) {
    var confirmRemoval by remember { mutableStateOf<AgentKeyTag?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(Res.string.agent_keys_your_keys), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            // The same button closes the picker it opened.
            TextButton(onClick = onAdd) {
                Icon(if (adding) MaterialSymbols.Close else MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringRes(if (adding) Res.string.close else Res.string.agent_keys_add))
            }
        }

        keys.forEachIndexed { index, key ->
            val preferred = index == 0
            val preset = AgentProviderPresets.forUrl(key.url)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (preferred) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
                border = if (preferred) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderAvatar(preset?.id ?: key.url, AgentProviderPresets.monogramFor(key.url), size = 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rememberRoutstrNodeName(routstr, key.url) ?: AgentProviderPresets.displayName(key.url), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (key.hasKey()) {
                            Text(
                                AgentProviderPresets.maskKey(key.key),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // A key-less entry still says what it is: a runner here, or somewhere else.
                            Text(
                                if (AgentProviderPresets.isLocalUrl(key.url)) stringRes(Res.string.agent_keys_local_no_key) else AgentProviderPresets.hostOf(key.url) ?: key.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        RoutstrKeyBalanceLine(routstr, key, onTopUp)
                    }
                    // The row an edit is about spins; every row waits for it, as the screen saves one edit at a time.
                    if (edit is AgentKeyEditState.Busy && AgentKeyEdits.sameUrl(edit.url, key.url)) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { if (!preferred) onMakePreferred(key) }, enabled = !edit.isBusy) {
                            Icon(
                                MaterialSymbols.Star,
                                contentDescription = stringRes(if (preferred) Res.string.agent_keys_preferred else Res.string.agent_keys_make_preferred),
                                tint = if (preferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                filled = preferred,
                            )
                        }
                    }
                    IconButton(onClick = { confirmRemoval = key }, enabled = !edit.isBusy) {
                        Icon(MaterialSymbols.Delete, contentDescription = stringRes(Res.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    confirmRemoval?.let { key ->
        AlertDialog(
            onDismissRequest = { confirmRemoval = null },
            title = { Text(stringRes(Res.string.agent_keys_remove_title)) },
            text = { Text(stringRes(Res.string.agent_keys_remove_body, AgentProviderPresets.displayName(key.url))) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(key)
                    confirmRemoval = null
                }) { Text(stringRes(Res.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = null }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }
}

/** Two letters on a colour picked from the provider id, so each provider keeps its colour. */
@Composable
private fun ProviderAvatar(
    seed: String,
    monogram: String,
    size: Int,
) {
    val hue = (seed.hashCode().toLong() and 0xFFFFFFFFL) % 360
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.hsl(hue.toFloat(), 0.55f, 0.48f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            monogram,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size >= 40) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddAgentKey(
    showGrid: Boolean,
    onShowGrid: () -> Unit,
    onTest: suspend (AgentKeyTag) -> AgentKeyProbeResult,
    onConnectOpenRouter: suspend (donePageText: String, openBrowser: (String) -> Unit) -> OpenRouterConnectResult,
    onSave: (AgentKeyTag) -> Deferred<AgentKeyEditState>?,
    onSaved: () -> Unit,
    /** Routstr has its own flow: a node picker and a key bought in sats, not a pasted key. */
    onRoutstr: () -> Unit,
    /** A save is on its way: the button says "Saving…". */
    saving: Boolean,
    /** Any edit is on its way (one at a time): the button waits. */
    busy: Boolean,
    compactStart: Boolean,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val donePageText = stringRes(Res.string.agent_keys_openrouter_done_page)

    var preset by remember { mutableStateOf<AgentProviderPreset?>(null) }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AgentKeyProbeResult?>(null) }
    var connecting by remember { mutableStateOf<Job?>(null) }
    var connectProblem by remember { mutableStateOf<OpenRouterConnectResult?>(null) }

    val selected = preset
    // NIP-FA: https, unless the host is this device or there is no key to leak.
    val urlValid = AgentProviderPresets.isValidUrl(url, key.trim())
    val urlNeedsHttps = !urlValid && AgentProviderPresets.isValidUrl(url)
    val canCheck = selected != null && urlValid && (!selected.needsKey || key.isNotBlank())
    // A custom endpoint is only tried when the reader asks: checking as they type would send the
    // key to every half-typed host on the way (https://api.ex, https://api.exa, …).
    val isCustom = selected == AgentProviderPresets.CUSTOM
    var testNow by remember { mutableStateOf(false) }
    val canSave = canCheck && !testing && result != null && result !is AgentKeyProbeResult.Rejected && !busy

    fun select(next: AgentProviderPreset) {
        if (next.id == RoutstrAccount.ROUTSTR_PRESET) return onRoutstr()
        preset = next
        url = next.url
        key = ""
        result = null
        connectProblem = null
        testNow = false
    }

    fun connectOpenRouter() {
        connectProblem = null
        connecting =
            scope.launch {
                try {
                    when (val outcome = onConnectOpenRouter(donePageText) { uriHandler.openUri(it) }) {
                        // The key lands in the form and checks itself; saving stays the user's call.
                        is OpenRouterConnectResult.Connected -> {
                            select(AgentProviderPresets.OPENROUTER)
                            key = outcome.key
                        }
                        else -> connectProblem = outcome
                    }
                } finally {
                    connecting = null
                }
            }
    }

    // A preset's key is checked as soon as the user stops typing: no Test button to forget. A
    // custom endpoint waits for Test.
    LaunchedEffect(url, key, selected, testNow) {
        result = null
        if (!canCheck) return@LaunchedEffect
        if (isCustom && !testNow) return@LaunchedEffect
        if (!isCustom) delay(700)
        testing = true
        try {
            result = onTest(AgentKeyTag(key.trim(), url.trim()))
        } finally {
            testing = false
        }
    }

    // Empty account: one big way in, and the rest one tap away.
    if (compactStart && selected == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectOpenRouterButton(connecting) { connectOpenRouter() }
            ConnectProblem(connectProblem)
            if (!showGrid) {
                OutlinedButton(onClick = onShowGrid, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(stringRes(Res.string.agent_keys_other_providers))
                }
            }
        }
        if (!showGrid) return
    }

    AnimatedVisibility(showGrid || selected != null, enter = fadeIn(), exit = fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // The grid folds away once a provider is picked, so the key field is right there.
            if (showGrid && selected == null) {
                Text(stringRes(Res.string.agent_keys_choose_provider), style = MaterialTheme.typography.titleMedium)
                ProviderGrid(selected) { select(it) }
            }

            if (selected != null) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected == AgentProviderPresets.CUSTOM) {
                                Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(32.dp))
                            } else {
                                ProviderAvatar(selected.id, selected.monogram, size = 32)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (selected == AgentProviderPresets.CUSTOM) stringRes(Res.string.agent_keys_custom) else selected.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                preset = null
                                key = ""
                                onShowGrid()
                            }) { Text(stringRes(Res.string.agent_keys_change)) }
                        }

                        if (selected == AgentProviderPresets.OPENROUTER) {
                            ConnectOpenRouterButton(connecting) { connectOpenRouter() }
                            ConnectProblem(connectProblem)
                            OrDivider()
                        }

                        if (selected == AgentProviderPresets.CUSTOM) {
                            PillTextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    testNow = false
                                },
                                placeholder = stringRes(Res.string.agent_keys_url),
                                leading = MaterialSymbols.Link,
                                tone = if (url.isNotBlank() && !urlValid) PillTone.Bad else PillTone.Neutral,
                                keyboardType = KeyboardType.Uri,
                            )
                            if (urlNeedsHttps) {
                                FieldCaption(PillTone.Bad) { Text(stringRes(Res.string.agent_keys_url_https)) }
                            } else if (url.isNotBlank() && !urlValid) {
                                FieldCaption(PillTone.Bad) { urlInvalidText() }
                            }
                        }

                        val tone =
                            when {
                                testing -> PillTone.Neutral
                                result is AgentKeyProbeResult.Ok && (result as AgentKeyProbeResult.Ok).keyChecked -> PillTone.Good
                                result is AgentKeyProbeResult.Rejected ||
                                    result is AgentKeyProbeResult.Failed ||
                                    result is AgentKeyProbeResult.Unreachable -> PillTone.Bad
                                else -> PillTone.Neutral
                            }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PillTextField(
                                value = key,
                                onValueChange = {
                                    key = it
                                    connectProblem = null
                                    testNow = false
                                },
                                placeholder = stringRes(if (selected.needsKey) Res.string.agent_keys_key_placeholder else Res.string.agent_keys_key_optional),
                                leading = MaterialSymbols.Key,
                                tone = tone,
                                keyboardType = KeyboardType.Password,
                                visualTransformation = if (showKey || key.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                            ) {
                                if (key.isEmpty()) {
                                    // Keys are long and pasted, never typed: make that one tap.
                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                clipboard.getText()?.trim()?.let {
                                                    key = it
                                                    testNow = false
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(36.dp),
                                    ) {
                                        Icon(MaterialSymbols.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringRes(Res.string.action_paste), style = MaterialTheme.typography.labelLarge)
                                    }
                                } else {
                                    CheckStatusIcon(testing, result)
                                    IconButton(onClick = { showKey = !showKey }, modifier = Modifier.size(40.dp)) {
                                        Icon(
                                            if (showKey) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                                            contentDescription = stringRes(if (showKey) Res.string.hide_password else Res.string.show_password),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                            checkCaption(testing, result, budgetHint = selected.needsKey)?.let { caption ->
                                FieldCaption(tone) { caption() }
                            }
                            if (isCustom && canCheck && !testNow) {
                                OutlinedButton(onClick = { testNow = true }, shape = RoundedCornerShape(14.dp)) {
                                    Text(stringRes(Res.string.agent_keys_test))
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                selected.isLocal ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(stringRes(Res.string.agent_keys_runs_locally)) },
                                    )
                                selected.keyPage != null -> {
                                    val keyPage = selected.keyPage
                                    AssistChip(
                                        onClick = { keyPage?.let { uriHandler.openUri(it) } },
                                        label = { Text(stringRes(Res.string.agent_keys_get_key)) },
                                        trailingIcon = { Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Button(
                                enabled = canSave,
                                onClick = {
                                    val saved = onSave(AgentKeyTag(key.trim(), url.trim())) ?: return@Button
                                    // The form stays until the key is saved: a failure keeps what was typed.
                                    scope.launch {
                                        if (saved.await() is AgentKeyEditState.Done) {
                                            preset = null
                                            key = ""
                                            showKey = false
                                            result = null
                                            onSaved()
                                        }
                                    }
                                },
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringRes(Res.string.agent_keys_saving))
                                } else {
                                    Text(stringRes(Res.string.save))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val urlInvalidText: @Composable () -> Unit = { Text(stringRes(Res.string.agent_keys_url_invalid)) }

@Composable
private fun FieldCaption(
    tone: PillTone,
    content: @Composable () -> Unit,
) {
    val color =
        when (tone) {
            PillTone.Good -> PillGood
            PillTone.Bad -> MaterialTheme.colorScheme.error
            PillTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(Modifier.padding(start = 16.dp)) {
        CompositionLocalProvider(LocalContentColor provides color) {
            ProvideTextStyle(MaterialTheme.typography.bodySmall) { content() }
        }
    }
}

@Composable
private fun ConnectOpenRouterButton(
    connecting: Job?,
    onConnect: () -> Unit,
) {
    if (connecting != null) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringRes(Res.string.agent_keys_openrouter_waiting), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { connecting.cancel() }) { Text(stringRes(Res.string.cancel)) }
            }
        }
    } else {
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(20.dp), filled = true)
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.agent_keys_connect_openrouter), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ConnectProblem(problem: OpenRouterConnectResult?) {
    if (problem == null) return
    Text(
        stringRes(
            when (problem) {
                OpenRouterConnectResult.Denied -> Res.string.agent_keys_openrouter_denied
                OpenRouterConnectResult.TimedOut -> Res.string.agent_keys_openrouter_timed_out
                OpenRouterConnectResult.Unsupported -> Res.string.agent_keys_openrouter_unsupported
                else -> Res.string.agent_keys_openrouter_failed
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            stringRes(Res.string.agent_keys_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

/**
 * The providers as compact rows: the main six, the rest behind "More providers", local runners
 * under their own heading, Custom last. Two columns on a phone, three on a wide pane.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderGrid(
    selected: AgentProviderPreset?,
    onSelect: (AgentProviderPreset) -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    val byGroup = AgentProviderPresets.ALL.groupBy { it.group }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 520.dp) 3 else 2
        val gap = 8.dp
        // A hair under the exact share, so rounding never wraps the last tile of a row.
        val tileWidth = (maxWidth - gap * (columns - 1)) / columns - 1.dp

        @Composable
        fun tiles(presets: List<AgentProviderPreset>) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                presets.forEach { preset -> ProviderTile(preset, preset == selected, Modifier.width(tileWidth)) { onSelect(preset) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles(byGroup[AgentProviderGroup.MAIN].orEmpty())
            TextButton(onClick = { showMore = !showMore }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(stringRes(Res.string.agent_keys_more_providers))
                Spacer(Modifier.width(4.dp))
                Icon(if (showMore) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            AnimatedVisibility(showMore) { tiles(byGroup[AgentProviderGroup.MORE].orEmpty()) }
            Text(
                stringRes(Res.string.agent_keys_on_your_computer),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            tiles(byGroup[AgentProviderGroup.LOCAL].orEmpty())
            // Custom is any URL, here or elsewhere: last, under no heading.
            tiles(byGroup[AgentProviderGroup.CUSTOM].orEmpty())
        }
    }
}

@Composable
private fun ProviderTile(
    preset: AgentProviderPreset,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isCustom = preset == AgentProviderPresets.CUSTOM
    val name = if (isCustom) stringRes(Res.string.agent_keys_custom) else preset.name
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(60.dp).clip(shape).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isCustom) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            } else {
                ProviderAvatar(preset.id, preset.monogram, size = 36)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (preset == AgentProviderPresets.OPENROUTER) {
                    Text(stringRes(Res.string.agent_keys_recommended), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                } else if (preset.id == RoutstrAccount.ROUTSTR_PRESET) {
                    Text(stringRes(Res.string.routstr_tile_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CheckStatusIcon(
    testing: Boolean,
    result: AgentKeyProbeResult?,
) {
    AnimatedContent(testing to result, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "keyCheck") { (busy, outcome) ->
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                outcome is AgentKeyProbeResult.Ok && outcome.keyChecked ->
                    Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = PillGood, filled = true)
                outcome is AgentKeyProbeResult.Ok || outcome is AgentKeyProbeResult.Unsupported ->
                    Icon(MaterialSymbols.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                outcome != null -> Icon(MaterialSymbols.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, filled = true)
            }
        }
    }
}

/** The check's outcome under the key field; before there is one, the budget advice (NIP-FA). */
private fun checkCaption(
    testing: Boolean,
    result: AgentKeyProbeResult?,
    budgetHint: Boolean,
): (@Composable () -> Unit)? {
    if (!testing && result == null && !budgetHint) return null
    return {
        Text(
            when {
                testing -> stringRes(Res.string.agent_keys_checking)
                result == null -> stringRes(Res.string.agent_keys_budget_hint)
                result is AgentKeyProbeResult.Ok && !result.keyChecked -> stringRes(Res.string.agent_keys_unverified)
                result is AgentKeyProbeResult.Ok -> {
                    val count = result.modelCount
                    if (count != null) pluralStringRes(Res.plurals.agent_keys_works_models, count, count) else stringRes(Res.string.agent_keys_works)
                }
                result is AgentKeyProbeResult.Rejected -> stringRes(Res.string.agent_keys_rejected)
                result is AgentKeyProbeResult.Failed -> stringRes(Res.string.agent_keys_failed, result.code)
                result is AgentKeyProbeResult.Unsupported -> stringRes(Res.string.agent_keys_unsupported)
                else -> stringRes(Res.string.agent_keys_unreachable)
            },
        )
    }
}

/**
 * Under a Routstr key: its balance in sats, asked for when the screen opens, a warning when it
 * may not cover the Observer's next paper, and Top up. Nothing for any other key.
 */
@Composable
private fun RoutstrKeyBalanceLine(
    routstr: RoutstrAccount?,
    key: AgentKeyTag,
    onTopUp: (AgentKeyTag) -> Unit,
) {
    val balance = rememberRoutstrBalance(routstr, key) ?: return
    var low by remember(key) { mutableStateOf(false) }
    LaunchedEffect(balance) {
        low = routstr != null && balance is RoutstrKeyBalance.Known && routstr.isLow(key, balance.balance)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (balance) {
            RoutstrKeyBalance.Loading -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            is RoutstrKeyBalance.Known ->
                Text(
                    satsText(balance.balance.balanceSats),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            is RoutstrKeyBalance.Failed ->
                Text(
                    if (balance.error.failure == RoutstrFailure.KEY_NOT_FOUND || balance.error.failure == RoutstrFailure.INVALID_KEY) routstrErrorText(balance.error) else stringRes(Res.string.routstr_balance_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
        }
        TextButton(onClick = { onTopUp(key) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringRes(Res.string.routstr_top_up), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (low) {
        Text(stringRes(Res.string.routstr_balance_low), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}
