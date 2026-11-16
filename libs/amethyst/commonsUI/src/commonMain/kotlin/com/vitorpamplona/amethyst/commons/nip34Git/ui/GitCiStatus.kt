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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiRollup
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiRunAttempt
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiState
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiStatusIndex
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiSummary
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiWorkflowRuns
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.git_ci_cancelled
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_cancelled
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_failure
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_neutral
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_skipped
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_startup_failure
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_success
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_timed_out
import com.vitorpamplona.amethyst.commons.resources.git_ci_conclusion_unknown
import com.vitorpamplona.amethyst.commons.resources.git_ci_count
import com.vitorpamplona.amethyst.commons.resources.git_ci_earlier_attempts
import com.vitorpamplona.amethyst.commons.resources.git_ci_failure
import com.vitorpamplona.amethyst.commons.resources.git_ci_full_log
import com.vitorpamplona.amethyst.commons.resources.git_ci_in_progress
import com.vitorpamplona.amethyst.commons.resources.git_ci_job_loading
import com.vitorpamplona.amethyst.commons.resources.git_ci_neutral
import com.vitorpamplona.amethyst.commons.resources.git_ci_no_jobs
import com.vitorpamplona.amethyst.commons.resources.git_ci_pending
import com.vitorpamplona.amethyst.commons.resources.git_ci_queued
import com.vitorpamplona.amethyst.commons.resources.git_ci_runs_disclaimer
import com.vitorpamplona.amethyst.commons.resources.git_ci_runs_title
import com.vitorpamplona.amethyst.commons.resources.git_ci_signed_by
import com.vitorpamplona.amethyst.commons.resources.git_ci_success
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.StatusPill
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobResultQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowStatus
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource

private val CardShape = RoundedCornerShape(8.dp)
private const val LOG_TAIL_LINES = 12

private class CiStyle(
    val label: StringResource,
    val symbol: MaterialSymbol,
    val container: Color,
    val content: Color,
)

@Composable
private fun ciStyle(state: CiState): CiStyle =
    when (state) {
        CiState.SUCCESS -> CiStyle(Res.string.git_ci_success, ciSymbol(state), Color(0xFF1F883D), Color.White)
        CiState.FAILURE -> CiStyle(Res.string.git_ci_failure, ciSymbol(state), Color(0xFFCF222E), Color.White)
        CiState.PENDING -> CiStyle(Res.string.git_ci_pending, ciSymbol(state), Color(0xFF9A6700), Color.White)
        CiState.CANCELLED -> CiStyle(Res.string.git_ci_cancelled, ciSymbol(state), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        CiState.NEUTRAL -> CiStyle(Res.string.git_ci_neutral, ciSymbol(state), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

/** The glyph of a CI state, shared by the PR badge and the CI cards in the feed. */
internal fun ciSymbol(state: CiState): MaterialSymbol =
    when (state) {
        CiState.SUCCESS -> MaterialSymbols.CheckCircle
        CiState.FAILURE -> MaterialSymbols.Cancel
        CiState.PENDING -> MaterialSymbols.HourglassTop
        CiState.CANCELLED -> MaterialSymbols.Block
        CiState.NEUTRAL -> MaterialSymbols.RemoveCircleOutline
    }

/** The lines of a job's log tail without the runner's "[log-tail omitted=…]" marker. */
internal fun ciLogTailLines(logTail: String): List<String> = logTail.lines().dropWhile { it.startsWith("[log-tail omitted=") }

internal fun conclusionLabel(conclusion: CiConclusion?): StringResource =
    when (conclusion) {
        CiConclusion.SUCCESS -> Res.string.git_ci_conclusion_success
        CiConclusion.FAILURE -> Res.string.git_ci_conclusion_failure
        CiConclusion.NEUTRAL -> Res.string.git_ci_conclusion_neutral
        CiConclusion.CANCELLED -> Res.string.git_ci_conclusion_cancelled
        CiConclusion.SKIPPED -> Res.string.git_ci_conclusion_skipped
        CiConclusion.TIMED_OUT -> Res.string.git_ci_conclusion_timed_out
        CiConclusion.STARTUP_FAILURE -> Res.string.git_ci_conclusion_startup_failure
        null -> Res.string.git_ci_conclusion_unknown
    }

/**
 * The CI summary of the PR or patch [targetIdHex], read from the process-wide [CiStatusIndex] and
 * recomputed when the earliest live Workflow Progress marker it relies on expires, so a "running"
 * badge clears by itself when a coordinator stops renewing its marker. Null while nothing ran.
 */
@Composable
fun rememberCiSummary(targetIdHex: HexKey): CiSummary? {
    val index by CiStatusIndex.byItem.collectAsStateWithLifecycle()
    val item = index?.get(targetIdHex) ?: return null

    var tick by remember { mutableIntStateOf(0) }
    val summary = remember(item, tick) { item.summarize(TimeUtils.now()) }

    val next = summary.nextExpiration
    if (next != null) {
        LaunchedEffect(next, tick) {
            delay(((next - TimeUtils.now()).coerceAtLeast(0) + 1) * 1000)
            tick++
        }
    }

    return summary.takeIf { it.state != null }
}

/**
 * A GitHub-style CI badge for a NIP-34 pull request or patch: the combined state of the current
 * attempt of every workflow that ran for it, with "passed / total" when more than one ran. Hidden
 * when no CI event names the item. Tapping it opens [GitCiRunsDialog].
 */
@Composable
fun GitCiStatusBadge(
    targetIdHex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val summary = rememberCiSummary(targetIdHex) ?: return
    val state = summary.state ?: return
    var showRuns by remember { mutableStateOf(false) }

    val style = ciStyle(state)
    val label =
        if (summary.workflows.size > 1) {
            stringRes(style.label) + " " + stringRes(Res.string.git_ci_count, summary.passed, summary.workflows.size)
        } else {
            stringRes(style.label)
        }

    StatusPill(
        label = label,
        symbol = style.symbol,
        container = style.container,
        content = style.content,
        modifier = modifier.clip(CardShape).clickable { showRuns = true },
    )

    if (showRuns) {
        GitCiRunsDialog(targetIdHex, accountViewModel, nav) { showRuns = false }
    }
}

/**
 * The CI mark a list row ends with: the state's glyph in its color, with "2/3" when more than one
 * workflow ran. The full [GitCiStatusBadge] stays for the item's own header. Tapping it opens
 * [GitCiRunsDialog].
 */
@Composable
fun GitCiStatusIcon(
    targetIdHex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val summary = rememberCiSummary(targetIdHex) ?: return
    val state = summary.state ?: return
    var showRuns by remember { mutableStateOf(false) }
    val tint = stateTint(state)
    val label = stringRes(ciStyle(state).label)

    Row(
        modifier = modifier.clip(CardShape).clickable { showRuns = true }.padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(symbol = ciSymbol(state), contentDescription = label, modifier = Modifier.size(16.dp), tint = tint)
        if (summary.workflows.size > 1) {
            Text(
                text = stringRes(Res.string.git_ci_count, summary.passed, summary.workflows.size),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }

    if (showRuns) {
        GitCiRunsDialog(targetIdHex, accountViewModel, nav) { showRuns = false }
    }
}

/**
 * Every workflow run attached to [targetIdHex], grouped by (workflow, coordinator) with the current
 * attempt open and earlier attempts behind a toggle. Each attempt lists only the Job Results it
 * quotes — with name, conclusion, signer, log tail and the full-log link — and the coordinator that
 * signed it, since there is no trust model and the reader has to judge.
 */
@Composable
fun GitCiRunsDialog(
    targetIdHex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val summary = rememberCiSummary(targetIdHex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringRes(Res.string.git_ci_runs_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.close))
                    }
                }

                Text(
                    text = stringRes(Res.string.git_ci_runs_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )

                Spacer(Modifier.padding(top = 8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(summary?.workflows ?: emptyList(), key = { it.coordinator + (it.workflowPath ?: "") }) { runs ->
                        WorkflowRunsCard(runs, accountViewModel, nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRunsCard(
    runs: CiWorkflowRuns,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showEarlier by rememberSaveable(runs.coordinator, runs.workflowPath) { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = runs.workflowPath ?: "?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        SignerRow(runs.coordinator, accountViewModel, nav)

        AttemptBlock(runs.current, accountViewModel, nav)

        val earlier = runs.attempts.drop(1)
        if (earlier.isNotEmpty()) {
            TextButton(onClick = { showEarlier = !showEarlier }) {
                Text(pluralStringRes(Res.plurals.git_ci_earlier_attempts, earlier.size, earlier.size))
            }
            if (showEarlier) {
                earlier.forEach { attempt ->
                    HorizontalDivider()
                    AttemptBlock(attempt, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun AttemptBlock(
    attempt: CiRunAttempt,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val style = ciStyle(attempt.state)
            Icon(symbol = style.symbol, contentDescription = null, modifier = Modifier.size(16.dp), tint = stateTint(attempt.state))
            Text(
                text = attemptLabel(attempt),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            TimeAgo(attempt.sortTime)
        }

        val jobs = attempt.jobs
        val running = attempt.inProgressJobs
        if (jobs.isEmpty() && running.isEmpty()) {
            Text(
                text = stringRes(Res.string.git_ci_no_jobs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        jobs.forEach { quote -> JobRow(quote, accountViewModel, nav) }
        running.forEach { jobId -> RunningJobRow(jobId) }
    }
}

@Composable
private fun attemptLabel(attempt: CiRunAttempt): String =
    when {
        attempt.result != null -> stringRes(conclusionLabel(attempt.conclusion))
        attempt.progress?.status() == CiWorkflowStatus.QUEUED -> stringRes(Res.string.git_ci_queued)
        attempt.progress?.status() == CiWorkflowStatus.IN_PROGRESS -> stringRes(Res.string.git_ci_in_progress)
        else -> stringRes(conclusionLabel(attempt.conclusion))
    }

@Composable
private fun stateTint(state: CiState): Color =
    when (state) {
        CiState.SUCCESS -> gitPalette().open
        CiState.FAILURE -> gitPalette().closed
        CiState.PENDING -> gitPalette().pending
        CiState.CANCELLED, CiState.NEUTRAL -> MaterialTheme.colorScheme.grayText
    }

@Composable
private fun SignerRow(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringRes(Res.string.git_ci_signed_by),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.grayText,
        )
        UserPicture(userHex = pubKey, size = Size20dp, accountViewModel = accountViewModel, nav = nav)
        LoadUser(baseUserHex = pubKey) { user ->
            if (user != null) {
                UsernameDisplay(user, Modifier.weight(1f, fill = false), fontWeight = FontWeight.Normal, accountViewModel = accountViewModel)
            }
        }
    }
}

/** One quoted Job Result. It is fetched by id when missing; only quoted jobs are ever shown. */
@Composable
private fun JobRow(
    quote: CiJobResultQuote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = quote.eventId) { note ->
        if (note == null) {
            JobLoadingRow(quote.jobId)
        } else {
            val job by observeNoteEvent<CiJobResultEvent>(note, accountViewModel)
            val event = job
            if (event == null) {
                JobLoadingRow(quote.jobId)
            } else {
                JobResultBody(event, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun JobLoadingRow(jobId: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(symbol = MaterialSymbols.HourglassEmpty, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.grayText)
        Text(text = jobId, style = MaterialTheme.typography.bodySmall)
        Text(text = stringRes(Res.string.git_ci_job_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
    }
}

@Composable
private fun RunningJobRow(jobId: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(symbol = MaterialSymbols.HourglassTop, contentDescription = null, modifier = Modifier.size(14.dp), tint = stateTint(CiState.PENDING))
        Text(text = jobId, style = MaterialTheme.typography.bodySmall)
        Text(text = stringRes(Res.string.git_ci_in_progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
    }
}

@Composable
private fun JobResultBody(
    job: CiJobResultEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var expanded by rememberSaveable(job.id) { mutableStateOf(false) }
    val state = remember(job) { CiRollup.stateOf(job.conclusion()) }
    val tail =
        remember(job) {
            ciLogTailLines(job.logTail())
                .takeLast(LOG_TAIL_LINES)
                .joinToString("\n")
                .trim()
        }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(start = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable(enabled = tail.isNotEmpty()) { expanded = !expanded },
        ) {
            Icon(symbol = ciStyle(state).symbol, contentDescription = null, modifier = Modifier.size(14.dp), tint = stateTint(state))
            Text(
                text = job.name() ?: job.jobId() ?: "?",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringRes(conclusionLabel(job.conclusion())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
            )
            if (tail.isNotEmpty()) {
                Icon(
                    symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        SignerRow(job.pubKey, accountViewModel, nav)

        if (expanded && tail.isNotEmpty()) {
            Text(
                text = tail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(8.dp),
            )
        }

        job.logsUrl()?.let { url ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(symbol = MaterialSymbols.AutoMirrored.Article, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.grayText)
                ClickableUrlOrBlossom(urlText = stringRes(Res.string.git_ci_full_log), url = url, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
