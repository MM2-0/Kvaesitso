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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.AgentWorkBoardViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.AgentWorkItem
import com.vitorpamplona.amethyst.commons.model.buzz.AgentWorkKind
import com.vitorpamplona.amethyst.commons.model.buzz.AgentWorkState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.agent_work_compose_description
import com.vitorpamplona.amethyst.commons.resources.agent_work_deny
import com.vitorpamplona.amethyst.commons.resources.agent_work_empty_description
import com.vitorpamplona.amethyst.commons.resources.agent_work_empty_title
import com.vitorpamplona.amethyst.commons.resources.agent_work_file_direct
import com.vitorpamplona.amethyst.commons.resources.agent_work_file_with_gate
import com.vitorpamplona.amethyst.commons.resources.agent_work_new_task
import com.vitorpamplona.amethyst.commons.resources.agent_work_require_approval
import com.vitorpamplona.amethyst.commons.resources.agent_work_require_approval_off
import com.vitorpamplona.amethyst.commons.resources.agent_work_require_approval_on
import com.vitorpamplona.amethyst.commons.resources.agent_work_source_direct
import com.vitorpamplona.amethyst.commons.resources.agent_work_source_gated
import com.vitorpamplona.amethyst.commons.resources.agent_work_toast_decision_failed
import com.vitorpamplona.amethyst.commons.resources.agent_work_toast_file_failed
import com.vitorpamplona.amethyst.commons.resources.agent_work_toast_task_filed
import com.vitorpamplona.amethyst.commons.resources.agent_work_toast_task_filed_gated
import com.vitorpamplona.amethyst.commons.resources.agent_work_upvote
import com.vitorpamplona.amethyst.commons.resources.agent_work_waiting_on_you
import com.vitorpamplona.amethyst.commons.resources.buzz_agent_work_title
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_approve_open_pr
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_approved_toast
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_denied_toast
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_gate_awaiting
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_needs_approval
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_queued
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_shipped
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_working
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_readonly_approver
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_section_closed
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_task_label
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_view_pr
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_waiting_for_approval
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_waiting_on
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * The merged **Agent work** board: one channel, one list, both agent protocols (ungated jobs and
 * gated workflow runs) folded into the single [AgentWorkItem] vocabulary and sorted needs-approval
 * first. The human-approval gate — the thing that used to be its own screen — is now just the
 * elevated card at the top; everything else (queued, working, shipped, closed) reads the same
 * whether it came from a job or a workflow.
 *
 * Prototype note: strings are inline pending adoption; the standalone [WorkflowRunBoardScreen]'s
 * confirm-before-approve dialog and full localization are the carry-over follow-ups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentWorkBoardScreen(
    channelId: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val me = remember(accountViewModel) { accountViewModel.account.userProfile().pubkeyHex }
    val canWrite = remember(accountViewModel) { accountViewModel.account.isWriteable() }
    val viewModel: AgentWorkBoardViewModel = rememberViewModel(key = "AgentWorkBoard-$relayUrl-$channelId") { AgentWorkBoardViewModel() }
    viewModel.bind(accountViewModel.account, channelId, relayUrl)

    DisposableEffect(channelId, relayUrl) {
        viewModel.startWatching()
        onDispose { viewModel.stopWatching() }
    }

    val itemsList by viewModel.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) }

    var composing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Snackbar copy resolved here (composable scope) so the coroutine lambdas below don't call stringRes.
    val msgApproved = stringRes(Res.string.buzz_workflow_approved_toast)
    val msgDenied = stringRes(Res.string.buzz_workflow_denied_toast)
    val msgDecisionFailed = stringRes(Res.string.agent_work_toast_decision_failed)
    val msgFiledGated = stringRes(Res.string.agent_work_toast_task_filed_gated)
    val msgFiled = stringRes(Res.string.agent_work_toast_task_filed)
    val msgFileFailed = stringRes(Res.string.agent_work_toast_file_failed)

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.buzz_agent_work_title), nav) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (canWrite) {
                ExtendedFloatingActionButton(
                    onClick = { composing = true },
                    icon = { Icon(symbol = MaterialSymbols.Add, contentDescription = null) },
                    text = { Text(stringRes(Res.string.agent_work_new_task)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            relay?.let { RelayStatusBar(it, accountViewModel) }

            Box(modifier = Modifier.fillMaxSize()) {
                if (itemsList.isEmpty() && !isLoading) {
                    EmptyAgentWork()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
                    ) {
                        items(itemsList, key = { it.id }, contentType = { it.state }) { item ->
                            if (item.state == AgentWorkState.NEEDS_APPROVAL) {
                                GateCard(item, me, canWrite, accountViewModel, nav) { grant ->
                                    val onResult: (Boolean) -> Unit = { ok ->
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                when {
                                                    ok && grant -> msgApproved
                                                    ok -> msgDenied
                                                    else -> msgDecisionFailed
                                                },
                                            )
                                        }
                                    }
                                    if (grant) viewModel.approve(item.id, onResult) else viewModel.deny(item.id, onResult)
                                }
                            } else {
                                WorkCard(
                                    item = item,
                                    accountViewModel = accountViewModel,
                                    nav = nav,
                                    onUpvote = { viewModel.upvote(item.id, item.requester) },
                                    onCancel = { viewModel.cancel(item.id) },
                                    canWrite = canWrite,
                                )
                            }
                        }
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(24.dp),
                    )
                }
            }
        }
    }

    if (composing) {
        val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
        NewTaskSheet(
            sheetState = sheetState,
            onDismiss = { composing = false },
            onSubmit = { text, requireApproval ->
                viewModel.newTask(text, requireApproval) { ok ->
                    if (ok) {
                        composing = false
                        scope.launch { snackbar.showSnackbar(if (requireApproval) msgFiledGated else msgFiled) }
                    } else {
                        scope.launch { snackbar.showSnackbar(msgFileFailed) }
                    }
                }
            },
        )
    }
}

/** The one elevated, glowing card: a workflow run parked on its approval gate — the room's blocker. */
@Composable
private fun LazyItemScope.GateCard(
    item: AgentWorkItem,
    me: String,
    canWrite: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDecide: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glowT = rememberInfiniteTransition(label = "gate")
    val glow by glowT.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "gateGlow",
    )
    val mine = item.pendingApprover == me
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surface,
        shadowElevation = 10.dp,
        border = BorderStroke(1.5.dp, scheme.primary.copy(alpha = glow)),
        modifier = Modifier.fillMaxWidth().animateItem(),
    ) {
        Column(
            modifier =
                Modifier
                    .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.85f), scheme.surface)))
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                        Icon(symbol = MaterialSymbols.Gavel, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        text = if (mine) stringRes(Res.string.agent_work_waiting_on_you) else stringRes(Res.string.buzz_workflow_gate_awaiting),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TimeAgo(time = item.createdAt, style = TimeAgoStyle.Short)
            }

            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Person("by", item.requester, accountViewModel, nav)

            if (mine && canWrite) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onDecide(false) }, colors = ButtonDefaults.textButtonColors(contentColor = scheme.error)) {
                        Text(stringRes(Res.string.agent_work_deny), fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = { onDecide(true) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(symbol = MaterialSymbols.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringRes(Res.string.buzz_workflow_approve_open_pr), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (mine) {
                Text(stringRes(Res.string.buzz_workflow_readonly_approver), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            } else {
                WaitingPill(item.pendingApprover, accountViewModel, nav)
            }
        }
    }
}

/** Every other state — queued / working / shipped / closed — as a colour-railed card with its actions. */
@Composable
private fun LazyItemScope.WorkCard(
    item: AgentWorkItem,
    accountViewModel: AccountViewModel,
    nav: INav,
    onUpvote: () -> Unit,
    onCancel: () -> Unit,
    canWrite: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = accentFor(item.state)
    val container = containerFor(item.state)
    val elevation =
        if (item.state == AgentWorkState.WORKING) {
            4.dp
        } else if (item.state == AgentWorkState.SHIPPED) {
            3.dp
        } else {
            1.dp
        }
    Surface(
        color = container,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = elevation,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .animateItem()
                .alpha(if (item.state == AgentWorkState.CLOSED) 0.75f else 1f),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(5.dp).background(accent))
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatePill(item.state, accent)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceTag(item.source)
                        TimeAgo(time = item.createdAt, style = TimeAgoStyle.Short)
                    }
                }

                Text(
                    text = item.title,
                    style = if (item.state == AgentWorkState.WORKING) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.state == AgentWorkState.WORKING) FontWeight.SemiBold else FontWeight.Normal,
                )
                Person("by", item.requester, accountViewModel, nav)

                when (item.state) {
                    AgentWorkState.WORKING -> {
                        item.detail?.takeIf { it.isNotBlank() }?.let { WorkingLine(it, accent) }
                        RunningBar(accent)
                    }
                    AgentWorkState.SHIPPED -> ResultLine(item.result)
                    AgentWorkState.CLOSED -> item.detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                    AgentWorkState.QUEUED -> Unit
                    AgentWorkState.NEEDS_APPROVAL -> Unit
                }

                // Jobs carry upvote priority + a cancel affordance; workflow runs don't.
                if (item.source == AgentWorkKind.JOB && canWrite && !item.isTerminal()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UpvoteChip(item.upvotes ?: 0, onUpvote)
                        TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurfaceVariant)) {
                            Text(stringRes(Res.string.cancel), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun AgentWorkItem.isTerminal() = state == AgentWorkState.SHIPPED || state == AgentWorkState.CLOSED

@Composable
private fun UpvoteChip(
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(symbol = MaterialSymbols.ThumbUp, contentDescription = stringRes(Res.string.agent_work_upvote), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RunningBar(accent: Color) {
    LinearProgressIndicator(
        color = accent,
        trackColor = accent.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun WorkingLine(
    text: String,
    accent: Color,
) {
    val pulse = rememberInfiniteTransition(label = "working")
    val a =
        pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "workingAlpha",
        )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.graphicsLayer { alpha = a.value }) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Medium)
    }
}

private val URL_REGEX = Regex("https?://\\S+")

@Composable
private fun ResultLine(result: String?) {
    val url = remember(result) { result?.let { URL_REGEX.find(it)?.value } }
    if (url != null) {
        val uriHandler = LocalUriHandler.current
        Button(onClick = { uriHandler.openUri(url) }, shape = RoundedCornerShape(12.dp)) {
            Icon(symbol = MaterialSymbols.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.buzz_workflow_view_pr))
        }
    } else if (!result.isNullOrBlank()) {
        Text(result, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A quiet, secondary marker of which protocol an item came from — transparent, never a nav choice. */
@Composable
private fun SourceTag(source: AgentWorkKind) {
    val (label, symbol) = if (source == AgentWorkKind.WORKFLOW) stringRes(Res.string.agent_work_source_gated) to MaterialSymbols.Lock else stringRes(Res.string.agent_work_source_direct) to MaterialSymbols.LockOpen
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(symbol = symbol, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Person(
    prefix: String,
    hex: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (hex == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(prefix, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        UserPicture(userHex = hex, size = Size20dp, accountViewModel = accountViewModel, nav = nav)
        LoadUser(baseUserHex = hex) { user ->
            if (user != null) UsernameDisplay(baseUser = user, fontWeight = FontWeight.Medium, accountViewModel = accountViewModel)
        }
    }
}

@Composable
private fun WaitingPill(
    approver: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(symbol = MaterialSymbols.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            if (approver != null) Person(stringRes(Res.string.buzz_workflow_waiting_on), approver, accountViewModel, nav) else Text(stringRes(Res.string.buzz_workflow_waiting_for_approval), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatePill(
    state: AgentWorkState,
    accent: Color,
) {
    val (labelRes, symbol) = pillContent(state)
    Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(symbol = symbol, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            Text(text = stringRes(labelRes), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSubmit: (String, Boolean) -> Unit,
) {
    var task by remember { mutableStateOf("") }
    var requireApproval by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringRes(Res.string.agent_work_new_task), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringRes(Res.string.agent_work_compose_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpellCheckedOutlinedTextField(
                value = task,
                onValueChange = { task = it },
                label = { Text(stringRes(Res.string.buzz_workflow_task_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringRes(Res.string.agent_work_require_approval), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            if (requireApproval) stringRes(Res.string.agent_work_require_approval_on) else stringRes(Res.string.agent_work_require_approval_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = requireApproval, onCheckedChange = { requireApproval = it })
                }
            }
            Button(onClick = { onSubmit(task.trim(), requireApproval) }, enabled = task.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(symbol = if (requireApproval) MaterialSymbols.Gavel else MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (requireApproval) stringRes(Res.string.agent_work_file_with_gate) else stringRes(Res.string.agent_work_file_direct))
            }
        }
    }
}

@Composable
private fun EmptyAgentWork() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(symbol = MaterialSymbols.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(stringRes(Res.string.agent_work_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringRes(Res.string.agent_work_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun accentFor(state: AgentWorkState): Color =
    when (state) {
        AgentWorkState.NEEDS_APPROVAL -> MaterialTheme.colorScheme.primary
        AgentWorkState.WORKING -> MaterialTheme.colorScheme.secondary
        AgentWorkState.QUEUED -> MaterialTheme.colorScheme.tertiary
        AgentWorkState.SHIPPED -> MaterialTheme.colorScheme.tertiary
        AgentWorkState.CLOSED -> MaterialTheme.colorScheme.outline
    }

@Composable
private fun containerFor(state: AgentWorkState): Color =
    when (state) {
        AgentWorkState.NEEDS_APPROVAL -> MaterialTheme.colorScheme.primaryContainer
        AgentWorkState.WORKING -> MaterialTheme.colorScheme.secondaryContainer
        AgentWorkState.QUEUED -> MaterialTheme.colorScheme.surfaceContainerLow
        AgentWorkState.SHIPPED -> MaterialTheme.colorScheme.tertiaryContainer
        AgentWorkState.CLOSED -> MaterialTheme.colorScheme.surfaceContainerLow
    }

private fun pillContent(state: AgentWorkState): Pair<StringResource, MaterialSymbol> =
    when (state) {
        AgentWorkState.NEEDS_APPROVAL -> Res.string.buzz_workflow_pill_needs_approval to MaterialSymbols.Gavel
        AgentWorkState.WORKING -> Res.string.buzz_workflow_pill_working to MaterialSymbols.Bolt
        AgentWorkState.QUEUED -> Res.string.buzz_workflow_pill_queued to MaterialSymbols.Schedule
        AgentWorkState.SHIPPED -> Res.string.buzz_workflow_pill_shipped to MaterialSymbols.CheckCircle
        AgentWorkState.CLOSED -> Res.string.buzz_workflow_section_closed to MaterialSymbols.Cancel
    }
