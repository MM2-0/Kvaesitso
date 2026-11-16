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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.vitorpamplona.amethyst.commons.buzz.WorkflowDefOption
import com.vitorpamplona.amethyst.commons.buzz.WorkflowRunBoardViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRun
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_approve_body
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_approve_open_pr
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_approve_title
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_approved_toast
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_by
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_cancel
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_closed_cancelled
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_closed_denied
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_closed_failed
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_closed_failed_prefix
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_create_definition
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_decision_failed_toast
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_def_desc
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_def_name
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_def_name_hint
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_def_publish_failed
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_def_title
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_def_yaml
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_denied_toast
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_deny
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_deny_body
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_deny_title
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_empty_desc
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_empty_title
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_gate_awaiting
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_gate_needs_you
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_id_prefix
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_new_definition
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_new_run
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_no_defs_hint
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_no_description
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_picker_choose
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_picker_empty
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_picker_label
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_approved
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_cancelled
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_denied
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_failed
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_needs_approval
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_queued
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_shipped
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_working
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_publishing
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_readonly_approver
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_runs_title
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_section_active
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_section_awaiting
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_section_closed
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_section_shipped
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_task_label
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_this_run
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_trigger_desc
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_trigger_failed_toast
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_trigger_run
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_trigger_title
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_triggered_toast
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_view_pr
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_waiting_for_approval
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_waiting_on
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
 * The shared **workflow run board** of one Buzz channel — where a team drives an AI agent under a
 * human-approval gate.
 *
 * Every member sees the same runs: trigger a workflow (kind-46020), watch the runner work it through
 * triggered → step → the **approval gate** (46010), and — if the run named you as approver — grant or
 * deny it right here (46030/46031). A granted run ships and lands as a shipped result (its PR);
 * merge stays on GitHub. Runs awaiting a human are the visual hero — an elevated, glowing card sorted
 * to the top — because they're the ones blocking. Correlation/state is the shared
 * [com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunAggregator]; this screen renders it and
 * routes trigger/approve/deny through [WorkflowRunBoardViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunBoardScreen(
    channelId: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val me = remember(accountViewModel) { accountViewModel.account.userProfile().pubkeyHex }
    val canWrite = remember(accountViewModel) { accountViewModel.account.isWriteable() }
    val viewModel: WorkflowRunBoardViewModel = rememberViewModel(key = "WorkflowRunBoard-$relayUrl-$channelId") { WorkflowRunBoardViewModel() }
    viewModel.bind(accountViewModel.account, channelId, relayUrl)

    DisposableEffect(channelId, relayUrl) {
        viewModel.startWatching()
        onDispose { viewModel.stopWatching() }
    }

    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val definitions by viewModel.definitions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) }

    var composing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingDecision?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Resolve snackbar copy here (composable scope) so the coroutine lambdas below don't call stringRes.
    val msgTriggered = stringRes(Res.string.buzz_workflow_triggered_toast)
    val msgTriggerFailed = stringRes(Res.string.buzz_workflow_trigger_failed_toast)
    val msgApproved = stringRes(Res.string.buzz_workflow_approved_toast)
    val msgDenied = stringRes(Res.string.buzz_workflow_denied_toast)
    val msgDecisionFailed = stringRes(Res.string.buzz_workflow_decision_failed_toast)

    // Section titles resolved here too — `section()` runs in LazyListScope, not a composable scope.
    val titleAwaiting = stringRes(Res.string.buzz_workflow_section_awaiting)
    val titleActive = stringRes(Res.string.buzz_workflow_section_active)
    val titleShipped = stringRes(Res.string.buzz_workflow_section_shipped)
    val titleClosed = stringRes(Res.string.buzz_workflow_section_closed)

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.buzz_workflow_runs_title), nav) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (canWrite) {
                ExtendedFloatingActionButton(
                    onClick = { composing = true },
                    icon = { Icon(symbol = MaterialSymbols.Add, contentDescription = null) },
                    text = { Text(stringRes(Res.string.buzz_workflow_new_run)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            relay?.let { RelayStatusBar(it, accountViewModel) }

            Box(modifier = Modifier.fillMaxSize()) {
                val groups = remember(runs) { RunGroups.from(runs) }

                if (runs.isEmpty() && !isLoading) {
                    EmptyRunBoard()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
                    ) {
                        section(titleAwaiting, groups.awaiting, RunStyle.GATE, me, canWrite, accountViewModel, nav) { run, grant ->
                            pending = PendingDecision(run, grant)
                        }
                        section(titleActive, groups.active, RunStyle.ACTIVE, me, canWrite, accountViewModel, nav)
                        section(titleShipped, groups.done, RunStyle.SHIPPED, me, canWrite, accountViewModel, nav)
                        section(titleClosed, groups.closed, RunStyle.CLOSED, me, canWrite, accountViewModel, nav)
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
        NewRunSheet(
            sheetState = sheetState,
            definitions = definitions,
            onDismiss = { composing = false },
            onDefine = { name, yaml, onResult -> viewModel.defineWorkflow(name, yaml, onResult) },
            onTrigger = { workflowId, task ->
                // Only close the sheet on a confirmed publish; on failure keep it open (task text intact)
                // and tell the user, instead of silently swallowing a read-only / rejected write.
                viewModel.trigger(workflowId, task) { ok ->
                    if (ok) {
                        composing = false
                        scope.launch { snackbar.showSnackbar(msgTriggered) }
                    } else {
                        scope.launch { snackbar.showSnackbar(msgTriggerFailed) }
                    }
                }
            },
        )
    }

    // Approving pushes code and opens a PR; denying discards it — both consequential enough to confirm.
    pending?.let { decision ->
        ConfirmDecisionDialog(
            decision = decision,
            onDismiss = { pending = null },
            onConfirm = {
                val grant = decision.grant
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
                if (grant) viewModel.approve(decision.run.runId, onResult) else viewModel.deny(decision.run.runId, onResult)
                pending = null
            },
        )
    }
}

private enum class RunStyle { GATE, ACTIVE, SHIPPED, CLOSED }

/** A grant/deny the approver has tapped but not yet confirmed. */
private data class PendingDecision(
    val run: WorkflowRun,
    val grant: Boolean,
)

/** The runs bucketed by lifecycle; awaiting-a-human first (those block the room). */
private class RunGroups(
    val awaiting: List<WorkflowRun>,
    val active: List<WorkflowRun>,
    val done: List<WorkflowRun>,
    val closed: List<WorkflowRun>,
) {
    companion object {
        fun from(runs: List<WorkflowRun>) =
            RunGroups(
                awaiting = runs.filter { it.state == WorkflowRunState.AWAITING_APPROVAL },
                active =
                    runs.filter {
                        it.state == WorkflowRunState.TRIGGERED ||
                            it.state == WorkflowRunState.RUNNING ||
                            it.state == WorkflowRunState.APPROVED
                    },
                done = runs.filter { it.state == WorkflowRunState.COMPLETED },
                closed =
                    runs.filter {
                        it.state == WorkflowRunState.FAILED ||
                            it.state == WorkflowRunState.CANCELLED ||
                            it.state == WorkflowRunState.DENIED
                    },
            )
    }
}

private fun LazyListScope.section(
    title: String,
    runs: List<WorkflowRun>,
    style: RunStyle,
    me: String,
    canWrite: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDecide: (WorkflowRun, Boolean) -> Unit = { _, _ -> },
) {
    if (runs.isEmpty()) return
    item(key = "header-$title", contentType = "header") {
        SectionHeader(title, runs.size, style)
    }
    items(runs, key = { it.runId }, contentType = { style }) { run ->
        if (style == RunStyle.GATE) {
            GateCard(run, me, canWrite, accountViewModel, nav, onDecide)
        } else {
            RunCard(run, style, accountViewModel, nav)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    style: RunStyle,
) {
    val accent = if (style == RunStyle.GATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
        Surface(color = accent.copy(alpha = 0.14f), shape = CircleShape) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * The centerpiece: a run paused on its approval gate. Elevated and softly glowing so the one thing
 * that needs a human reads as the hero of the board. If I'm the named approver it carries the
 * grant/deny actions; otherwise it shows who the room is waiting on.
 */
@Composable
private fun LazyItemScope.GateCard(
    run: WorkflowRun,
    me: String,
    canWrite: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDecide: (WorkflowRun, Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glowT = rememberInfiniteTransition(label = "gate")
    val glow by glowT.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "gateGlow",
    )
    val mine = run.pendingApprover == me
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(scheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(symbol = MaterialSymbols.Gavel, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        text = if (mine) stringRes(Res.string.buzz_workflow_gate_needs_you) else stringRes(Res.string.buzz_workflow_gate_awaiting),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TimeAgo(time = run.createdAt, style = TimeAgoStyle.Short)
            }

            Text(
                text = runHeadline(run),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Person(stringRes(Res.string.buzz_workflow_by), run.requester, accountViewModel, nav)
            }

            if (mine && canWrite) {
                ApprovalActions(
                    onApprove = { onDecide(run, true) },
                    onDeny = { onDecide(run, false) },
                )
            } else if (mine) {
                // Named approver, but this login can't sign (read-only / remote signer w/o write).
                Text(
                    text = stringRes(Res.string.buzz_workflow_readonly_approver),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WaitingPill(run.pendingApprover, accountViewModel, nav)
            }
        }
    }
}

/** The grant/deny buttons the named approver sees — Approve dominates, Deny stays a quiet exit. */
@Composable
private fun ApprovalActions(
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onDeny,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringRes(Res.string.buzz_workflow_deny), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onApprove,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Icon(symbol = MaterialSymbols.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.buzz_workflow_approve_open_pr), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The confirm step before a decision is published. Approving is a real commitment (it authorizes a
 * push + PR), so it gets a filled confirm; denying discards work, so it gets an error-toned confirm.
 * The copy states the boundary plainly: approving never merges or deploys.
 */
@Composable
private fun ConfirmDecisionDialog(
    decision: PendingDecision,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val task = decision.run.task?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.buzz_workflow_this_run)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                symbol = if (decision.grant) MaterialSymbols.Gavel else MaterialSymbols.Close,
                contentDescription = null,
                tint = if (decision.grant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(if (decision.grant) stringRes(Res.string.buzz_workflow_approve_title) else stringRes(Res.string.buzz_workflow_deny_title)) },
        text = {
            Text(
                if (decision.grant) {
                    stringRes(Res.string.buzz_workflow_approve_body, task)
                } else {
                    stringRes(Res.string.buzz_workflow_deny_body, task)
                },
            )
        },
        confirmButton = {
            if (decision.grant) {
                Button(onClick = onConfirm) {
                    Icon(symbol = MaterialSymbols.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(Res.string.buzz_workflow_approve_open_pr))
                }
            } else {
                TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringRes(Res.string.buzz_workflow_deny), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.buzz_workflow_cancel)) } },
    )
}

/** Who the room is waiting on, for members who aren't the approver. */
@Composable
private fun WaitingPill(
    approver: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(symbol = MaterialSymbols.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            if (approver != null) {
                Person(stringRes(Res.string.buzz_workflow_waiting_on), approver, accountViewModel, nav)
            } else {
                Text(stringRes(Res.string.buzz_workflow_waiting_for_approval), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Active / shipped / closed runs — a colour-railed card with real depth and a state-tuned accent. */
@Composable
private fun LazyItemScope.RunCard(
    run: WorkflowRun,
    style: RunStyle,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, accent) = styleColors(style)
    val elevation =
        if (style == RunStyle.ACTIVE) {
            4.dp
        } else if (style == RunStyle.SHIPPED) {
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
                .alpha(if (style == RunStyle.CLOSED) 0.75f else 1f),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(5.dp).background(accent))

            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatePill(run.state, accent)
                    TimeAgo(time = run.createdAt, style = TimeAgoStyle.Short)
                }

                Text(
                    text = runHeadline(run),
                    style = if (style == RunStyle.ACTIVE) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (style == RunStyle.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
                )

                Person(stringRes(Res.string.buzz_workflow_by), run.requester, accountViewModel, nav)

                when (style) {
                    RunStyle.ACTIVE -> {
                        run.lastStep?.takeIf { it.isNotBlank() }?.let { WorkingLine(it, accent) }
                        if (run.state == WorkflowRunState.RUNNING || run.state == WorkflowRunState.APPROVED) RunningBar(accent)
                    }
                    RunStyle.SHIPPED ->
                        run.result?.takeIf { it.isNotBlank() }?.let { ResultLine(it) }
                    RunStyle.CLOSED -> ClosedLine(run, scheme)
                    RunStyle.GATE -> Unit
                }
            }
        }
    }
}

/** A thin indeterminate bar so a run that's actively working *looks* alive. */
@Composable
private fun RunningBar(accent: Color) {
    LinearProgressIndicator(
        color = accent,
        trackColor = accent.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun ClosedLine(
    run: WorkflowRun,
    scheme: ColorScheme,
) {
    val text =
        when (run.state) {
            WorkflowRunState.DENIED -> stringRes(Res.string.buzz_workflow_closed_denied)
            WorkflowRunState.CANCELLED -> stringRes(Res.string.buzz_workflow_closed_cancelled)
            else -> run.error?.let { stringRes(Res.string.buzz_workflow_closed_failed_prefix, it) } ?: stringRes(Res.string.buzz_workflow_closed_failed)
        }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (run.state == WorkflowRunState.FAILED) scheme.error else scheme.onSurfaceVariant,
    )
}

/** The active-work pulse: the current step breathes while the runner works. */
@Composable
private fun WorkingLine(
    text: String,
    accent: Color,
) {
    val pulse = rememberInfiniteTransition(label = "working")
    // Keep the State (no `by`) and read it inside graphicsLayer so the pulse redraws, not recomposes.
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

/** Matches the first URL in a shipped-run result; compiled once for the process, not per result. */
private val URL_REGEX = Regex("https?://\\S+")

/** A shipped result — if it carries a URL, offer a prominent "View PR" instead of raw text. */
@Composable
private fun ResultLine(result: String) {
    val url = remember(result) { URL_REGEX.find(result)?.value }
    if (url != null) {
        val uriHandler = LocalUriHandler.current
        Button(onClick = { uriHandler.openUri(url) }, shape = RoundedCornerShape(12.dp)) {
            Icon(symbol = MaterialSymbols.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.buzz_workflow_view_pr))
        }
    } else {
        Text(result, style = MaterialTheme.typography.bodyMedium)
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
            if (user != null) {
                UsernameDisplay(baseUser = user, fontWeight = FontWeight.Medium, accountViewModel = accountViewModel)
            }
        }
    }
}

@Composable
private fun StatePill(
    state: WorkflowRunState,
    accent: Color,
) {
    val (labelRes, symbol) = pillContent(state)
    Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(symbol = symbol, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            Text(text = stringRes(labelRes), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

/** The task/workflow-id/placeholder headline shown for a run, resolved for the current locale. */
@Composable
private fun runHeadline(run: WorkflowRun): String =
    run.task?.takeIf { it.isNotBlank() }
        ?: run.workflowId?.let { stringRes(Res.string.buzz_workflow_id_prefix, it) }
        ?: stringRes(Res.string.buzz_workflow_no_description)

/**
 * Trigger a run: pick one of the channel's published **workflow definitions** (kind-30620) from the
 * dropdown — or define a new one inline (name + YAML) — then describe the task. The picker shows
 * definitions by name; the free-text id field is gone. Defining a workflow publishes a 30620 and
 * auto-selects it once it lands in the channel's definition list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewRunSheet(
    sheetState: SheetState,
    definitions: List<WorkflowDefOption>,
    onDismiss: () -> Unit,
    onDefine: (String, String, (String?) -> Unit) -> Unit,
    onTrigger: (String, String) -> Unit,
) {
    var task by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<WorkflowDefOption?>(null) }
    var defining by remember { mutableStateOf(false) }
    var pendingSelectId by remember { mutableStateOf<String?>(null) }

    // Once a just-published definition lands in the channel list, select it and leave the editor.
    LaunchedEffect(definitions, pendingSelectId) {
        val id = pendingSelectId ?: return@LaunchedEffect
        definitions.firstOrNull { it.id == id }?.let {
            selected = it
            defining = false
            pendingSelectId = null
        }
    }
    // Keep the selection valid if the list changes underneath us; default to the sole definition.
    LaunchedEffect(definitions) {
        if (selected != null && definitions.none { it.id == selected!!.id }) selected = null
        if (selected == null && definitions.size == 1) selected = definitions.first()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringRes(Res.string.buzz_workflow_trigger_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringRes(Res.string.buzz_workflow_trigger_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (defining) {
                DefinitionEditor(
                    onCancel = { defining = false },
                    onCreate = { name, yaml, onResult ->
                        onDefine(name, yaml) { newId ->
                            pendingSelectId = newId // non-null → the effect below selects it and closes the editor
                            onResult(newId != null) // null → the editor shows its error and re-enables
                        }
                    },
                )
            } else {
                WorkflowPicker(
                    definitions = definitions,
                    selected = selected,
                    onSelect = { selected = it },
                    onNewDefinition = { defining = true },
                )
                if (definitions.isEmpty()) {
                    // Don't leave a first-time user staring at a disabled Trigger button and an empty
                    // dropdown — point them at the way forward.
                    Text(
                        stringRes(Res.string.buzz_workflow_no_defs_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SpellCheckedOutlinedTextField(
                    value = task,
                    onValueChange = { task = it },
                    label = { Text(stringRes(Res.string.buzz_workflow_task_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Button(
                    onClick = { onTrigger(selected!!.id, task.trim()) },
                    enabled = selected != null && task.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(symbol = MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(Res.string.buzz_workflow_trigger_run))
                }
            }
        }
    }
}

/** The definition dropdown: pick a published 30620 by name, with a trailing "new definition" entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkflowPicker(
    definitions: List<WorkflowDefOption>,
    selected: WorkflowDefOption?,
    onSelect: (WorkflowDefOption) -> Unit,
    onNewDefinition: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringRes(Res.string.buzz_workflow_picker_label)) },
            placeholder = { Text(if (definitions.isEmpty()) stringRes(Res.string.buzz_workflow_picker_empty) else stringRes(Res.string.buzz_workflow_picker_choose)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            definitions.forEach { def ->
                DropdownMenuItem(
                    text = { Text(def.label) },
                    onClick = {
                        onSelect(def)
                        expanded = false
                    },
                )
            }
            if (definitions.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.buzz_workflow_new_definition), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) },
                leadingIcon = { Icon(symbol = MaterialSymbols.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    onNewDefinition()
                },
            )
        }
    }
}

/**
 * Inline editor to publish a new kind-30620 definition: a name and its YAML recipe. [onCreate] hands
 * back a success flag; on failure the editor stays open, shows an error, and re-enables the button so
 * the work isn't lost and the user isn't nudged into publishing a duplicate.
 */
@Composable
private fun DefinitionEditor(
    onCancel: () -> Unit,
    onCreate: (String, String, (Boolean) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var yaml by remember { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val publishFailedMsg = stringRes(Res.string.buzz_workflow_def_publish_failed)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringRes(Res.string.buzz_workflow_def_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            stringRes(Res.string.buzz_workflow_def_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringRes(Res.string.buzz_workflow_def_name)) },
            placeholder = { Text(stringRes(Res.string.buzz_workflow_def_name_hint)) },
            singleLine = true,
            enabled = !publishing,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = yaml,
            onValueChange = { yaml = it },
            label = { Text(stringRes(Res.string.buzz_workflow_def_yaml)) },
            enabled = !publishing,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !publishing) { Text(stringRes(Res.string.buzz_workflow_cancel)) }
            Button(
                onClick = {
                    error = null
                    publishing = true
                    onCreate(name.trim(), yaml) { ok ->
                        publishing = false
                        if (!ok) error = publishFailedMsg
                    }
                },
                enabled = !publishing && name.isNotBlank() && yaml.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(symbol = MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (publishing) stringRes(Res.string.buzz_workflow_publishing) else stringRes(Res.string.buzz_workflow_create_definition))
            }
        }
    }
}

@Composable
private fun EmptyRunBoard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(symbol = MaterialSymbols.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(stringRes(Res.string.buzz_workflow_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringRes(Res.string.buzz_workflow_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun styleColors(style: RunStyle): Pair<Color, Color> =
    when (style) {
        RunStyle.GATE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        RunStyle.ACTIVE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        RunStyle.SHIPPED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        RunStyle.CLOSED -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.outline
    }

private fun pillContent(state: WorkflowRunState): Pair<StringResource, MaterialSymbol> =
    when (state) {
        WorkflowRunState.TRIGGERED -> Res.string.buzz_workflow_pill_queued to MaterialSymbols.Schedule
        WorkflowRunState.RUNNING -> Res.string.buzz_workflow_pill_working to MaterialSymbols.Bolt
        WorkflowRunState.AWAITING_APPROVAL -> Res.string.buzz_workflow_pill_needs_approval to MaterialSymbols.Gavel
        WorkflowRunState.APPROVED -> Res.string.buzz_workflow_pill_approved to MaterialSymbols.CheckCircle
        WorkflowRunState.COMPLETED -> Res.string.buzz_workflow_pill_shipped to MaterialSymbols.CheckCircle
        WorkflowRunState.FAILED -> Res.string.buzz_workflow_pill_failed to MaterialSymbols.Error
        WorkflowRunState.CANCELLED -> Res.string.buzz_workflow_pill_cancelled to MaterialSymbols.Cancel
        WorkflowRunState.DENIED -> Res.string.buzz_workflow_pill_denied to MaterialSymbols.Close
    }
