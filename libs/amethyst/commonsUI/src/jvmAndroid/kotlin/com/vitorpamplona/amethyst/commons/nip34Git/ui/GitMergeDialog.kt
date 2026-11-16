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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeBlocker
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeCheck
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeCheckStep
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeOutcome
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeStep
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeUiState
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMerger
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitPreparedMerge
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitMergeStatuses
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_merge_already
import com.vitorpamplona.amethyst.commons.resources.git_merge_already_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_no_branch
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_no_state
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_no_tip
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_not_grasp
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_not_loaded
import com.vitorpamplona.amethyst.commons.resources.git_merge_blocked_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_cancel
import com.vitorpamplona.amethyst.commons.resources.git_merge_checking_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_close
import com.vitorpamplona.amethyst.commons.resources.git_merge_closes
import com.vitorpamplona.amethyst.commons.resources.git_merge_commits
import com.vitorpamplona.amethyst.commons.resources.git_merge_commits_from
import com.vitorpamplona.amethyst.commons.resources.git_merge_confirm
import com.vitorpamplona.amethyst.commons.resources.git_merge_conflicts
import com.vitorpamplona.amethyst.commons.resources.git_merge_conflicts_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_done
import com.vitorpamplona.amethyst.commons.resources.git_merge_done_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_failed_generic
import com.vitorpamplona.amethyst.commons.resources.git_merge_failed_missing
import com.vitorpamplona.amethyst.commons.resources.git_merge_failed_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_failed_unreachable
import com.vitorpamplona.amethyst.commons.resources.git_merge_hide
import com.vitorpamplona.amethyst.commons.resources.git_merge_hide_details
import com.vitorpamplona.amethyst.commons.resources.git_merge_leave_hint
import com.vitorpamplona.amethyst.commons.resources.git_merge_more_commits
import com.vitorpamplona.amethyst.commons.resources.git_merge_not_marked
import com.vitorpamplona.amethyst.commons.resources.git_merge_other_clones
import com.vitorpamplona.amethyst.commons.resources.git_merge_partial
import com.vitorpamplona.amethyst.commons.resources.git_merge_push_failed
import com.vitorpamplona.amethyst.commons.resources.git_merge_resolved
import com.vitorpamplona.amethyst.commons.resources.git_merge_servers
import com.vitorpamplona.amethyst.commons.resources.git_merge_show_details
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_changes
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_history
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_issues
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_merging
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_push
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_state
import com.vitorpamplona.amethyst.commons.resources.git_merge_step_status
import com.vitorpamplona.amethyst.commons.resources.git_merge_strategy_apply
import com.vitorpamplona.amethyst.commons.resources.git_merge_strategy_merge_commit
import com.vitorpamplona.amethyst.commons.resources.git_merge_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_with_git
import com.vitorpamplona.amethyst.commons.resources.git_merge_with_git_patches
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_merged_with_git
import com.vitorpamplona.amethyst.commons.ui.components.CommandLine
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.grasp.GraspServer
import com.vitorpamplona.quartz.nip34Git.merge.GitMergeStrategy
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import kotlin.reflect.KClass

private class GitMergeViewModelFactory(
    private val accountViewModel: AccountViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras,
    ): T =
        GitMergeViewModel(
            merger = GitMerger(accountViewModel.account, accountViewModel.httpClientBuilder::okHttpClientForPreview),
            workScope = accountViewModel.account.scope,
            reportSignerErrors = { accountViewModel.reportSignerErrors(it) },
            onHiddenFinished = { outcome ->
                // The sheet was hidden: say how it ended wherever the person is now.
                when (outcome) {
                    is GitMergeOutcome.Merged ->
                        accountViewModel.toastManager.toast(Res.string.git_merge_done_title, Res.string.git_merge_done, outcome.branch, outcome.newTip.take(7))
                    is GitMergeOutcome.PushFailed ->
                        accountViewModel.toastManager.toast(Res.string.git_merge_failed_title, Res.string.git_merge_push_failed)
                }
            },
            onHiddenFailed = { accountViewModel.toastManager.toast(Res.string.git_merge_failed_title, Res.string.git_merge_failed_generic) },
        ) as T
}

/**
 * Merging [note] (a pull request or a patch series' root) in the app, as gitworkshop does in the
 * browser. Returns what the Merge button calls; the dialog shows while a merge is checked, made
 * or reported. [onMarkMerged] publishes a plain "merged" status, for a merge made with git.
 */
@Composable
fun rememberGitMerge(
    note: Note,
    accountViewModel: AccountViewModel,
    onMarkMerged: () -> Unit,
): () -> Unit {
    val viewModel: GitMergeViewModel = viewModel(key = note.idHex + "GitMerge", factory = GitMergeViewModelFactory(accountViewModel))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible = state !is GitMergeUiState.Idle && (state as? GitMergeUiState.Merging)?.hidden != true
    if (visible) {
        GitMergeDialog(
            state = state,
            onMerge = { viewModel.merge(note) },
            onMarkMerged = {
                viewModel.dismiss()
                onMarkMerged()
            },
            onDismiss = viewModel::dismiss,
        )
    }
    return { viewModel.check(note) }
}

@Composable
fun GitMergeDialog(
    state: GitMergeUiState,
    onMerge: () -> Unit,
    onMarkMerged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val check = (state as? GitMergeUiState.Checked)?.check
    val ready = check as? GitMergeCheck.Ready ?: (state as? GitMergeUiState.Merging)?.ready
    val merging = state is GitMergeUiState.Merging
    // A merge in flight can only be hidden: it goes on, and reports when it ends.
    val notMarked = ((state as? GitMergeUiState.Done)?.outcome as? GitMergeOutcome.Merged)?.markedMerged == false
    val canMarkMerged = check is GitMergeCheck.AlreadyMerged || check is GitMergeCheck.Conflicts || check is GitMergeCheck.Blocked || notMarked

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleOf(state, ready?.merge)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state) {
                    is GitMergeUiState.Idle -> {}
                    is GitMergeUiState.Checking -> Progress(checkStepLabel(state.step))
                    is GitMergeUiState.Merging -> {
                        MergeSummary(state.ready)
                        Progress(
                            when (state.step) {
                                GitMergeStep.PUBLISHING_STATE_AND_PUSHING -> stringRes(Res.string.git_merge_step_push)
                                GitMergeStep.PUBLISHING_STATUS -> stringRes(Res.string.git_merge_step_status)
                            },
                        )
                        Text(stringRes(Res.string.git_merge_leave_hint), color = MaterialTheme.colorScheme.grayText, style = MaterialTheme.typography.bodySmall)
                    }
                    is GitMergeUiState.Checked -> CheckBody(state.check)
                    is GitMergeUiState.Done -> DoneBody(state.outcome)
                    is GitMergeUiState.Error -> FailureBody(friendly(state.message), state.message)
                }
            }
        },
        confirmButton = {
            when {
                // Checking can take a minute on a large repository: it can always be called off.
                state is GitMergeUiState.Checking -> TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_merge_cancel)) }
                merging -> TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_merge_hide)) }
                ready != null ->
                    Button(
                        onClick = onMerge,
                        colors = ButtonDefaults.buttonColors(containerColor = gitStateColor(StatusKind.APPLIED), contentColor = Color.White),
                    ) { Text(stringRes(Res.string.git_merge_confirm, ready.merge.branch)) }
                canMarkMerged -> Button(onClick = onMarkMerged) { Text(stringRes(Res.string.git_status_mark_merged_with_git)) }
                else -> TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_merge_close)) }
            }
        },
        dismissButton = {
            if (ready != null && !merging || canMarkMerged) {
                TextButton(onClick = onDismiss) { Text(stringRes(if (notMarked) Res.string.git_merge_close else Res.string.git_merge_cancel)) }
            }
        },
    )
}

@Composable
private fun titleOf(
    state: GitMergeUiState,
    ready: GitPreparedMerge?,
): String =
    when {
        state is GitMergeUiState.Checking -> stringRes(Res.string.git_merge_checking_title)
        ready != null -> stringRes(Res.string.git_merge_title, ready.branch)
        state is GitMergeUiState.Done && state.outcome is GitMergeOutcome.Merged -> stringRes(Res.string.git_merge_done_title)
        state is GitMergeUiState.Checked ->
            when (state.check) {
                is GitMergeCheck.Conflicts -> stringRes(Res.string.git_merge_conflicts_title)
                is GitMergeCheck.AlreadyMerged -> stringRes(Res.string.git_merge_already_title)
                is GitMergeCheck.Blocked -> stringRes(Res.string.git_merge_blocked_title)
                else -> stringRes(Res.string.git_merge_failed_title)
            }
        else -> stringRes(Res.string.git_merge_failed_title)
    }

@Composable
private fun checkStepLabel(step: GitMergeCheckStep): String =
    when (step) {
        GitMergeCheckStep.STATE -> stringRes(Res.string.git_merge_step_state)
        GitMergeCheckStep.HISTORY -> stringRes(Res.string.git_merge_step_history)
        GitMergeCheckStep.CHANGES -> stringRes(Res.string.git_merge_step_changes)
        GitMergeCheckStep.MERGING -> stringRes(Res.string.git_merge_step_merging)
        GitMergeCheckStep.ISSUES -> stringRes(Res.string.git_merge_step_issues)
    }

@Composable
private fun Progress(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, color = MaterialTheme.colorScheme.grayText)
    }
}

private const val COMMITS_SHOWN = 5

/** What goes in: its commits, the issues it closes, where it lands, and the mirrors it can't reach. */
@Composable
private fun MergeSummary(ready: GitMergeCheck.Ready) {
    val merge = ready.merge
    val commits = merge.newCommits
    val from = (merge.inputs.source as? GitPullRequestEvent)?.branchName()
    Text(
        if (from != null) {
            pluralStringRes(Res.plurals.git_merge_commits_from, commits.size, commits.size, from, merge.branch)
        } else {
            pluralStringRes(Res.plurals.git_merge_commits, commits.size, commits.size, merge.branch)
        },
        style = MaterialTheme.typography.titleSmall,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        commits.takeLast(COMMITS_SHOWN).reversed().forEach { commit ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(commit.shortOid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                Text(commit.summary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (commits.size > COMMITS_SHOWN) {
            val more = commits.size - COMMITS_SHOWN
            Text(pluralStringRes(Res.plurals.git_merge_more_commits, more, more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
        }
    }
    if (merge.plan.strategy == GitMergeStrategy.APPLY_TO_TIP) {
        Text(stringRes(Res.string.git_merge_strategy_apply, merge.branch), style = MaterialTheme.typography.bodySmall)
    } else {
        Text(stringRes(Res.string.git_merge_strategy_merge_commit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
    }
    ready.resolutions.forEach { IssueLine(MaterialSymbols.CheckCircle, stringRes(Res.string.git_merge_closes, issueLabel(it))) }

    val servers =
        merge.targets
            .mapNotNull { GraspServer.normalize(it.cloneUrl) }
            .distinct()
            .joinToString(", ")
    Text(stringRes(Res.string.git_merge_servers, servers), color = MaterialTheme.colorScheme.grayText, style = MaterialTheme.typography.bodySmall)
    merge.otherClones.forEach { clone ->
        IssueLine(MaterialSymbols.Info, stringRes(Res.string.git_merge_other_clones, clone.substringAfter("://").removeSuffix(".git"), merge.oldTip.take(7)))
    }
}

/** `#1a2b3c4d Title`: how an issue is named in git messages, then its title. */
private fun issueLabel(resolution: GitMergeStatuses.Resolution): String = "#${resolution.issue.id.take(8)}" + (resolution.issue.subject()?.let { " $it" } ?: "")

@Composable
private fun IssueLine(
    symbol: MaterialSymbol,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(16.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.grayText)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CheckBody(check: GitMergeCheck) {
    when (check) {
        is GitMergeCheck.Ready -> MergeSummary(check)
        is GitMergeCheck.AlreadyMerged -> Text(stringRes(Res.string.git_merge_already))
        is GitMergeCheck.Conflicts -> {
            Text(stringRes(Res.string.git_merge_conflicts))
            check.paths.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            WithGit(check.merge)
        }
        is GitMergeCheck.Blocked -> {
            Text(
                when (check.blocker) {
                    GitMergeBlocker.REPOSITORY_NOT_LOADED -> stringRes(Res.string.git_merge_blocked_not_loaded)
                    GitMergeBlocker.NOT_ON_GRASP -> stringRes(Res.string.git_merge_blocked_not_grasp)
                    GitMergeBlocker.NO_SIGNED_STATE -> stringRes(Res.string.git_merge_blocked_no_state)
                    GitMergeBlocker.NO_BRANCH_TIP -> stringRes(Res.string.git_merge_blocked_no_branch)
                    GitMergeBlocker.NO_PULL_REQUEST_TIP -> stringRes(Res.string.git_merge_blocked_no_tip)
                },
            )
            WithGit(check.merge)
        }
        is GitMergeCheck.Failed -> FailureBody(friendly(check.message), check.message)
    }
}

/** The commands that merge it by hand: fetch the PR's tip, merge it, push. */
@Composable
private fun WithGit(hint: GitMergeCheck.Hint?) {
    hint ?: return
    if (hint.isPatchSeries) {
        Text(stringRes(Res.string.git_merge_with_git_patches), style = MaterialTheme.typography.bodySmall)
        return
    }
    val clone = hint.clone ?: return
    val tip = hint.tip ?: return
    Text(stringRes(Res.string.git_merge_with_git), style = MaterialTheme.typography.bodySmall)
    CommandLine("git fetch $clone $tip")
    CommandLine("git merge --no-ff FETCH_HEAD")
    CommandLine("git push")
}

@Composable
private fun DoneBody(outcome: GitMergeOutcome) {
    when (outcome) {
        is GitMergeOutcome.Merged -> {
            Text(stringRes(Res.string.git_merge_done, outcome.branch, outcome.newTip.take(7)))
            if (!outcome.markedMerged) Text(stringRes(Res.string.git_merge_not_marked), color = MaterialTheme.colorScheme.error)
            outcome.resolvedIssues.forEach { IssueLine(MaterialSymbols.CheckCircle, stringRes(Res.string.git_merge_resolved, issueLabel(it))) }
            if (outcome.push.result.servers
                    .any { !it.pushed }
            ) {
                Text(
                    stringRes(Res.string.git_merge_partial, outcome.push.result.describeFailures()),
                    color = MaterialTheme.colorScheme.grayText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        is GitMergeOutcome.PushFailed -> FailureBody(stringRes(Res.string.git_merge_push_failed), outcome.reason)
    }
}

/** A plain sentence for a failure, with what the servers said behind "Show details". */
@Composable
private fun FailureBody(
    sentence: String,
    details: String,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Text(sentence)
    TextButton(onClick = { open = !open }) { Text(stringRes(if (open) Res.string.git_merge_hide_details else Res.string.git_merge_show_details)) }
    if (open) Text(details, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
}

/** What a server error means for the person merging. */
@Composable
private fun friendly(message: String): String =
    remember(message) {
        when {
            "HTTP 404" in message || "not found" in message || "no server holds" in message -> Res.string.git_merge_failed_missing
            "HTTP" in message || "timeout" in message.lowercase() || "resolve host" in message || "connect" in message.lowercase() -> Res.string.git_merge_failed_unreachable
            else -> Res.string.git_merge_failed_generic
        }
    }.let { stringRes(it) }
