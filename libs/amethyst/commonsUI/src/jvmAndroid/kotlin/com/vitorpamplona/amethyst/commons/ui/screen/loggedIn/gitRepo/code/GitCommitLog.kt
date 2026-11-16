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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.code

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitLoadingBox
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitMessageBox
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitMetaStyle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_pr_no_changes
import com.vitorpamplona.amethyst.commons.resources.git_repo_code_error
import com.vitorpamplona.amethyst.commons.resources.git_repo_code_loading
import com.vitorpamplona.amethyst.commons.resources.git_repo_commit
import com.vitorpamplona.amethyst.commons.resources.git_repo_commits
import com.vitorpamplona.amethyst.commons.resources.git_repo_file_load_error
import com.vitorpamplona.amethyst.commons.resources.git_repo_no_commits
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffView
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip34Git.git.GitCommit
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Commit history for the current branch: a `git log`-style list. Tapping a commit
 * loads the diff it introduced (vs its first parent) and renders it with the
 * shared [GitDiffView].
 */
@Composable
fun GitCommitLog(
    snapshot: GitRepoSnapshot,
    viewModel: GitRepositoryBrowserViewModel,
    onBack: () -> Unit,
    /** A file's history instead of the branch's: [title] heads it, [path] picks the commits. */
    title: String? = null,
    path: List<String>? = null,
) {
    var openCommit by rememberSaveable(snapshot.headCommit, path) { mutableStateOf<String?>(null) }

    val history by
        produceState<Result<List<GitCommit>>?>(null, snapshot.headCommit, path) {
            value =
                try {
                    Result.success(if (path != null) viewModel.fileHistory(snapshot, path) else viewModel.loadHistory(snapshot))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
        }

    val commits = history?.getOrNull()

    if (openCommit != null) {
        val commit = remember(openCommit, commits) { commits?.firstOrNull { it.oid == openCommit } }
        PlatformBackHandler { openCommit = null }
        Column(Modifier.fillMaxSize()) {
            // The summary heads the diff below, so the bar only says where the reader is.
            LogHeader(title = stringRes(Res.string.git_repo_commit), onBack = { openCommit = null })
            HorizontalDivider(thickness = 0.5.dp)
            if (commit == null) {
                GitMessageBox(MaterialSymbols.ErrorOutline, stringRes(Res.string.git_repo_file_load_error))
            } else {
                CommitDiff(snapshot, viewModel, commit)
            }
        }
        return
    }

    PlatformBackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        LogHeader(title = title ?: stringRes(Res.string.git_repo_commits), onBack = onBack)
        HorizontalDivider(thickness = 0.5.dp)
        when {
            history == null -> GitLoadingBox(stringRes(Res.string.git_repo_code_loading))
            history!!.isFailure -> GitMessageBox(MaterialSymbols.ErrorOutline, stringRes(Res.string.git_repo_code_error))
            commits.isNullOrEmpty() -> GitMessageBox(MaterialSymbols.History, stringRes(Res.string.git_repo_no_commits))
            else ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(commits, key = { it.oid }) { commit ->
                        CommitRow(commit) { openCommit = commit.oid }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.subtleBorder,
                        )
                    }
                }
        }
    }
}

@Composable
private fun CommitDiff(
    snapshot: GitRepoSnapshot,
    viewModel: GitRepositoryBrowserViewModel,
    commit: GitCommit,
) {
    val result by
        produceState<Result<ParsedPatch>?>(null, commit.oid) {
            value =
                try {
                    Result.success(viewModel.commitDiff(snapshot, commit))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
        }

    when (val r = result) {
        null -> GitLoadingBox(stringRes(Res.string.git_repo_code_loading))
        else ->
            if (r.isFailure || r.getOrThrow().files.isEmpty()) {
                GitMessageBox(MaterialSymbols.Code, stringRes(Res.string.git_pr_no_changes))
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    if (commit.summary.isNotBlank()) {
                        Text(commit.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = commit.authorName + timeAgo(commit.authorTimeSec, prefix = " · "),
                        style = gitMetaStyle(),
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    )
                    GitDiffView(r.getOrThrow(), Modifier.fillMaxWidth())
                }
            }
    }
}

@Composable
private fun CommitRow(
    commit: GitCommit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = commit.summary.ifBlank { commit.shortOid },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = commit.authorName + timeAgo(commit.authorTimeSec, prefix = " · "),
                    style = gitMetaStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LogHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) { ArrowBackIcon() }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
