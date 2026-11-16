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

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.JobBoardViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.JobState
import com.vitorpamplona.amethyst.commons.model.buzz.JobView
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.agent_work_new_task
import com.vitorpamplona.amethyst.commons.resources.agent_work_upvote
import com.vitorpamplona.amethyst.commons.resources.buzz_job_board_title
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_cancelled
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_failed
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_queued
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_shipped
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_pill_working
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_section_closed
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_section_shipped
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_view_pr
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.job_board_ask_agent_title
import com.vitorpamplona.amethyst.commons.resources.job_board_cancelled_reason
import com.vitorpamplona.amethyst.commons.resources.job_board_compose_description
import com.vitorpamplona.amethyst.commons.resources.job_board_empty_description
import com.vitorpamplona.amethyst.commons.resources.job_board_empty_title
import com.vitorpamplona.amethyst.commons.resources.job_board_example_add_setting
import com.vitorpamplona.amethyst.commons.resources.job_board_example_fix_bug
import com.vitorpamplona.amethyst.commons.resources.job_board_example_improve_screen
import com.vitorpamplona.amethyst.commons.resources.job_board_file_task
import com.vitorpamplona.amethyst.commons.resources.job_board_pill_picked_up
import com.vitorpamplona.amethyst.commons.resources.job_board_section_up_next
import com.vitorpamplona.amethyst.commons.resources.job_board_section_working
import com.vitorpamplona.amethyst.commons.resources.job_board_task_hint
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
import org.jetbrains.compose.resources.StringResource

/**
 * The shared **backlog** of one Buzz channel — where a team drives an AI agent together.
 *
 * Every member sees the same board: file a task (kind-43001), upvote to reprioritize (kind-7),
 * and watch the workspace bot work items back through accept → progress → result/error
 * (43002-43006). A live [RelayStatusBar] pins the relay's health up top; jobs are grouped by
 * lifecycle with the **active work** as the visual hero, the queue ordered by the group's
 * upvotes. Correlation/state/priority is the shared
 * [com.vitorpamplona.amethyst.commons.model.buzz.BuzzJobAggregator]; this screen renders it and
 * routes file/upvote/cancel through [JobBoardViewModel].
 *
 * Merge is deliberately NOT here — a shipped job's result is its PR; the merge happens on GitHub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobBoardScreen(
    channelId: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val me = accountViewModel.account.userProfile().pubkeyHex
    val viewModel: JobBoardViewModel = rememberViewModel(key = "JobBoard-$relayUrl-$channelId") { JobBoardViewModel() }
    viewModel.bind(accountViewModel.account, channelId, relayUrl)

    DisposableEffect(channelId, relayUrl) {
        viewModel.startWatching()
        onDispose { viewModel.stopWatching() }
    }

    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) }

    var composing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.buzz_job_board_title), nav) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { composing = true },
                icon = { Icon(symbol = MaterialSymbols.Add, contentDescription = null) },
                text = { Text(stringRes(Res.string.agent_work_new_task)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            relay?.let { RelayStatusBar(it, accountViewModel) }

            Box(modifier = Modifier.fillMaxSize()) {
                // Bucketed once per new backlog, not on every recomposition (e.g. isLoading toggles).
                val groups = remember(jobs) { JobGroups.from(jobs) }
                // Section titles resolved here — `section()` runs in LazyListScope, not a composable scope.
                val titleWorking = stringRes(Res.string.job_board_section_working)
                val titleUpNext = stringRes(Res.string.job_board_section_up_next)
                val titleShipped = stringRes(Res.string.buzz_workflow_section_shipped)
                val titleClosed = stringRes(Res.string.buzz_workflow_section_closed)

                if (jobs.isEmpty() && !isLoading) {
                    EmptyBoard()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    ) {
                        section(titleWorking, groups.running, JobStyle.HERO, me, accountViewModel, nav, viewModel)
                        section(titleUpNext, groups.queued, JobStyle.QUEUED, me, accountViewModel, nav, viewModel)
                        section(titleShipped, groups.done, JobStyle.SHIPPED, me, accountViewModel, nav, viewModel)
                        section(titleClosed, groups.closed, JobStyle.CLOSED, me, accountViewModel, nav, viewModel)
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
            onFile = { text ->
                viewModel.file(text)
                composing = false
            },
        )
    }
}

private enum class JobStyle { HERO, QUEUED, SHIPPED, CLOSED }

/** The backlog bucketed by lifecycle; the queue is ordered by the group's upvotes. */
private class JobGroups(
    val running: List<JobView>,
    val queued: List<JobView>,
    val done: List<JobView>,
    val closed: List<JobView>,
) {
    companion object {
        fun from(jobs: List<JobView>) =
            JobGroups(
                running = jobs.filter { it.state == JobState.IN_PROGRESS || it.state == JobState.ACCEPTED },
                queued =
                    jobs
                        .filter { it.state == JobState.REQUESTED }
                        .sortedWith(compareByDescending<JobView> { it.upvotes }.thenBy { it.createdAt }),
                done = jobs.filter { it.state == JobState.COMPLETED },
                closed = jobs.filter { it.state == JobState.FAILED || it.state == JobState.CANCELLED },
            )
    }
}

private fun LazyListScope.section(
    title: String,
    jobs: List<JobView>,
    style: JobStyle,
    me: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    viewModel: JobBoardViewModel,
) {
    if (jobs.isEmpty()) return
    item(key = "header-$title") {
        val accent = if (style == JobStyle.HERO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = jobs.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        }
    }
    items(jobs, key = { it.jobId }) { job ->
        JobCard(job, style, me, accountViewModel, nav, viewModel)
    }
}

@Composable
private fun LazyItemScope.JobCard(
    job: JobView,
    style: JobStyle,
    me: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    viewModel: JobBoardViewModel,
) {
    val (container, accent) = styleColors(style)
    val elevation =
        if (style == JobStyle.HERO) {
            4.dp
        } else if (style == JobStyle.SHIPPED) {
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
                .alpha(if (style == JobStyle.CLOSED) 0.7f else 1f),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left status rail — a quick-scan colour strip.
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(accent))

            Column(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatePill(job.state, accent)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TimeAgo(time = job.createdAt, style = TimeAgoStyle.Short)
                        UpvoteChip(job.upvotes) { viewModel.upvote(job.jobId, job.requester) }
                    }
                }

                Text(
                    text = job.request?.takeIf { it.isNotBlank() } ?: "(no description)",
                    style = if (style == JobStyle.HERO) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (style == JobStyle.HERO) FontWeight.SemiBold else FontWeight.Normal,
                )

                // Who's involved — real avatars + names, not hex.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Person("by", job.requester, accountViewModel, nav)
                    if (job.agent != null && job.agent != job.requester) Person("agent", job.agent, accountViewModel, nav)
                }

                when (style) {
                    JobStyle.HERO -> {
                        job.lastProgress?.takeIf { it.isNotBlank() }?.let { WorkingLine(it, accent) }
                        if (job.state == JobState.IN_PROGRESS) {
                            LinearProgressIndicator(
                                color = accent,
                                trackColor = accent.copy(alpha = 0.18f),
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                    JobStyle.SHIPPED ->
                        job.result?.takeIf { it.isNotBlank() }?.let { ResultLine(it) }
                    JobStyle.CLOSED ->
                        (job.error ?: job.cancelReason?.let { stringRes(Res.string.job_board_cancelled_reason, it) })?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = if (job.state == JobState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    JobStyle.QUEUED -> Unit
                }

                if (!job.isTerminal && job.requester == me) {
                    TextButton(onClick = { viewModel.cancel(job.jobId) }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringRes(Res.string.cancel))
                    }
                }
            }
        }
    }
}

/** The active-work pulse: the streaming progress line breathes while the agent runs. */
@Composable
private fun WorkingLine(
    text: String,
    accent: Color,
) {
    val pulse = rememberInfiniteTransition(label = "working")
    val a by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "workingAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.alpha(a)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Medium)
    }
}

/** A shipped result — if it carries a URL, offer a prominent "View PR" instead of raw text. */
@Composable
private fun ResultLine(result: String) {
    val url = remember(result) { Regex("https?://\\S+").find(result)?.value }
    if (url != null) {
        val uriHandler = LocalUriHandler.current
        Button(onClick = { uriHandler.openUri(url) }) {
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
    state: JobState,
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

@Composable
private fun UpvoteChip(
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp).animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(symbol = MaterialSymbols.ThumbUp, contentDescription = stringRes(Res.string.agent_work_upvote), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(text = count.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onFile: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(Res.string.job_board_ask_agent_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringRes(Res.string.job_board_compose_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringRes(Res.string.job_board_example_fix_bug),
                    stringRes(Res.string.job_board_example_add_setting),
                    stringRes(Res.string.job_board_example_improve_screen),
                ).forEach { example ->
                    SuggestionChip(onClick = { if (text.isBlank()) text = "$example: " }, label = { Text(example) })
                }
            }
            SpellCheckedOutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringRes(Res.string.job_board_task_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = { onFile(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(symbol = MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.job_board_file_task))
            }
        }
    }
}

@Composable
private fun EmptyBoard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(symbol = MaterialSymbols.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(stringRes(Res.string.job_board_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringRes(Res.string.job_board_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun styleColors(style: JobStyle): Pair<Color, Color> =
    when (style) {
        JobStyle.HERO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        JobStyle.QUEUED -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
        JobStyle.SHIPPED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        JobStyle.CLOSED -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.outline
    }

private fun pillContent(state: JobState): Pair<StringResource, MaterialSymbol> =
    when (state) {
        JobState.REQUESTED -> Res.string.buzz_workflow_pill_queued to MaterialSymbols.Schedule
        JobState.ACCEPTED -> Res.string.job_board_pill_picked_up to MaterialSymbols.Bolt
        JobState.IN_PROGRESS -> Res.string.buzz_workflow_pill_working to MaterialSymbols.Bolt
        JobState.COMPLETED -> Res.string.buzz_workflow_pill_shipped to MaterialSymbols.CheckCircle
        JobState.FAILED -> Res.string.buzz_workflow_pill_failed to MaterialSymbols.Error
        JobState.CANCELLED -> Res.string.buzz_workflow_pill_cancelled to MaterialSymbols.Cancel
    }
