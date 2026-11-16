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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitBranchesState
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalResult
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalTarget
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalUiState
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposalViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.proposal.GitProposals
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitBranchField
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitShape
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitTextEditor
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitTextEditorState
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitPalette
import com.vitorpamplona.amethyst.commons.nip34Git.ui.normalizeLabel
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitSignedState
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.LoadGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_edit_message
import com.vitorpamplona.amethyst.commons.resources.git_edit_propose
import com.vitorpamplona.amethyst.commons.resources.git_edit_tap_hint
import com.vitorpamplona.amethyst.commons.resources.git_edit_title
import com.vitorpamplona.amethyst.commons.resources.git_merge_cancel
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_subject
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_base
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_branch
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_create
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_description
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_labels
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_load_branches
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_source
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_source_hint
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_title
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_working
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_action
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_submit
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_title
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.reflect.KClass

private class GitProposalViewModelFactory(
    private val accountViewModel: AccountViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras,
    ): T =
        GitProposalViewModel(
            proposals = GitProposals(accountViewModel.account, accountViewModel.httpClientBuilder::okHttpClientForPreview),
            workScope = accountViewModel.account.scope,
            reportSignerErrors = { accountViewModel.reportSignerErrors(it) },
        ) as T
}

@Composable
fun rememberGitProposals(
    key: String,
    accountViewModel: AccountViewModel,
): GitProposalViewModel = viewModel(key = key + "GitProposals", factory = GitProposalViewModelFactory(accountViewModel))

/** Once a proposal is published, opens it in place of the screen that made it. */
@Composable
private fun OpenWhenPublished(
    viewModel: GitProposalViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    leaveFirst: () -> Unit = { nav.popBack() },
    onFailed: @Composable (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is GitProposalUiState.Working -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is GitProposalUiState.Done ->
            when (val result = s.result) {
                is GitProposalResult.Published ->
                    LaunchedEffect(result.event.id) {
                        viewModel.reset()
                        leaveFirst()
                        routeFor(LocalCache.getOrCreateNote(result.event.id), accountViewModel.account)?.let { nav.nav(it) }
                    }
                is GitProposalResult.Failed -> onFailed(result.reason)
            }
        else -> {}
    }
}

/**
 * The repository a proposal goes into, recomputed as its announcements, their memberships and the
 * maintainers' signed state load, so a screen opened cold does not keep the first, empty answer.
 */
@Composable
private fun rememberGitProposalTarget(
    repoNote: AddressableNote,
    viewModel: GitProposalViewModel,
    accountViewModel: AccountViewModel,
): GitProposalTarget? {
    val event by observeNoteEvent<GitRepositoryEvent>(repoNote, accountViewModel)
    // Every maintainer's announcement, from relays as well: opened cold, the cache may hold only the
    // owner's, and a PR must name each maintainer's coordinate and push to each one's GRASP servers.
    LoadGitRepositoryAnnouncements(remember(repoNote) { listOf(repoNote.address) })
    val announcements = observeGitRepositoryAnnouncements(repoNote.address.dTag)
    val signedState = observeGitSignedState(repoNote.address, accountViewModel)
    return remember(event, announcements, signedState) { viewModel.target(repoNote) }
}

@Composable
fun GitNewPullRequestScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let { GitNewPullRequestForm(it, accountViewModel, nav) }
    }
}

/**
 * "New pull request": a branch (of this repository, or of a fork at any clone URL) proposed into
 * one of this repository's branches. Its commits are pushed to the repository's GRASP servers and
 * the PR published once they hold them, as ngit opens one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitNewPullRequestForm(
    repoNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel = rememberGitProposals(repoNote.idHex, accountViewModel)
    val target = rememberGitProposalTarget(repoNote, viewModel, accountViewModel)
    val branchesState by viewModel.branches.collectAsStateWithLifecycle()
    val working = viewModel.state.collectAsStateWithLifecycle().value is GitProposalUiState.Working

    var source by rememberSaveable { mutableStateOf("") }
    var sourceBranch by rememberSaveable { mutableStateOf<String?>(null) }
    var targetBranch by rememberSaveable { mutableStateOf<String?>(null) }
    // Opened cold, the announcement and its signed state arrive after the first frame: fill the
    // defaults in when they do, without overwriting what the user already chose.
    LaunchedEffect(target) {
        if (source.isBlank()) {
            target
                ?.targets
                ?.grasp
                ?.firstOrNull()
                ?.cloneUrl
                ?.let { source = it }
        }
        if (targetBranch == null) targetBranch = target?.defaultBranch
    }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var labels by rememberSaveable { mutableStateOf("") }

    // Branches read from another URL than the one in the field are not offered: their tip would be
    // published with a clone URL that may not serve it.
    val loadedState = (branchesState as? GitBranchesState.Loaded)?.takeIf { it.cloneUrl == source.trim() }
    val loaded = loadedState?.branches.orEmpty()
    val tip = loaded.firstOrNull { it.first == sourceBranch }?.second
    val targetBranches =
        remember(target) {
            target
                ?.signedState
                ?.event
                ?.branches()
                ?.map { it.shortName }
                .orEmpty()
        }
    val canCreate = target != null && tip != null && loadedState != null && title.isNotBlank() && !working && accountViewModel.account.isWriteable()
    PlatformBackHandler(enabled = working) {}

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = { Text(stringRes(Res.string.git_pr_new_title), fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    // No arrow in a tab or a window of its own: nothing to go back to.
                    if (nav.canPop()) {
                        Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                            // Leaving while the commits are pushed would hide the result it waits for.
                            IconButton(onClick = nav::popBack, enabled = !working) { ArrowBackIcon() }
                        }
                    }
                },
                actions = {
                    Button(
                        enabled = canCreate,
                        onClick = {
                            val branch = sourceBranch
                            if (target != null && tip != null && branch != null && loadedState != null) {
                                viewModel.openPullRequest(
                                    target,
                                    loadedState.cloneUrl,
                                    tip,
                                    branch,
                                    targetBranch,
                                    title.trim(),
                                    description.trim(),
                                    labels.split(',').map(::normalizeLabel).filter { it.isNotEmpty() },
                                )
                            }
                        },
                        shape = GitShape,
                        colors = ButtonDefaults.buttonColors(containerColor = gitPalette().open, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 10.dp).height(34.dp),
                    ) {
                        Text(stringRes(Res.string.git_pr_new_create), style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(LocalDisappearingScaffoldPadding.current)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OpenWhenPublished(viewModel, accountViewModel, nav) { reason -> Text(reason, color = MaterialTheme.colorScheme.error) }
            if (working) Text(stringRes(Res.string.git_pr_new_working), color = MaterialTheme.colorScheme.grayText)

            SourceBranchSection(
                source = source,
                onSource = { source = it },
                showHint = true,
                branchesState = branchesState,
                branches = loaded.map { it.first },
                branch = sourceBranch,
                onBranch = {
                    sourceBranch = it
                    if (title.isBlank()) title = it.replace('-', ' ').replace('_', ' ')
                },
                onLoad = { viewModel.loadBranches(source) },
            )
            if (targetBranches.isNotEmpty()) {
                GitBranchField(stringRes(Res.string.git_pr_new_base), targetBranches, targetBranch) { targetBranch = it }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringRes(Res.string.git_new_issue_subject)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringRes(Res.string.git_pr_new_description)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = labels,
                onValueChange = { labels = it },
                label = { Text(stringRes(Res.string.git_pr_new_labels)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Where a pull request's commits come from: a clone URL, its branches once loaded, and the branch picked. */
@Composable
private fun SourceBranchSection(
    source: String,
    onSource: (String) -> Unit,
    showHint: Boolean,
    branchesState: GitBranchesState,
    branches: List<String>,
    branch: String?,
    onBranch: (String) -> Unit,
    onLoad: () -> Unit,
) {
    OutlinedTextField(
        value = source,
        onValueChange = onSource,
        label = { Text(stringRes(Res.string.git_pr_new_source)) },
        supportingText = if (showHint) ({ Text(stringRes(Res.string.git_pr_new_source_hint)) }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(onClick = onLoad, enabled = source.isNotBlank()) {
        Text(stringRes(Res.string.git_pr_new_load_branches))
    }
    when (branchesState) {
        is GitBranchesState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is GitBranchesState.Failed -> Text(branchesState.reason, color = MaterialTheme.colorScheme.error)
        else -> {}
    }
    if (branches.isNotEmpty()) GitBranchField(stringRes(Res.string.git_pr_new_branch), branches, branch, onBranch)
}

/**
 * For a pull request's author: point it at a newer tip (a kind 1619), its commits pushed to the
 * repository's GRASP servers first. The source defaults to where the PR's commits are served.
 */
@Composable
fun GitPullRequestUpdateButton(
    pullRequest: Note,
    repository: AddressableNote?,
    currentTip: String?,
    /** Where the PR's newest commits are served: its latest update's clone URLs first, then its own. */
    currentClones: List<String>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = pullRequest.event as? GitPullRequestEvent ?: return
    val account = accountViewModel.account
    if (event.pubKey != account.signer.pubKey || repository == null || !account.isWriteable()) return
    val viewModel = rememberGitProposals(pullRequest.idHex, accountViewModel)
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = {
        // A result that arrived after the dialog was last closed is not this attempt's.
        viewModel.reset()
        open = true
    }) { Text(stringRes(Res.string.git_pr_update_action)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if ((state as? GitProposalUiState.Done)?.result is GitProposalResult.Published) {
            viewModel.reset()
            open = false
        }
    }
    if (!open) return

    val target = rememberGitProposalTarget(repository, viewModel, accountViewModel)
    val branchesState by viewModel.branches.collectAsStateWithLifecycle()
    var source by rememberSaveable {
        mutableStateOf(
            currentClones.firstOrNull() ?: event.cloneUrls().firstOrNull() ?: target
                ?.targets
                ?.grasp
                ?.firstOrNull()
                ?.cloneUrl ?: "",
        )
    }
    var branch by rememberSaveable { mutableStateOf(event.branchName()) }
    val loadedState = (branchesState as? GitBranchesState.Loaded)?.takeIf { it.cloneUrl == source.trim() }
    val loaded = loadedState?.branches.orEmpty()
    val tip = loaded.firstOrNull { it.first == branch }?.second
    val working = state is GitProposalUiState.Working

    AlertDialog(
        onDismissRequest = { if (state !is GitProposalUiState.Working) open = false },
        title = { Text(stringRes(Res.string.git_pr_update_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceBranchSection(
                    source = source,
                    onSource = { source = it },
                    showHint = false,
                    branchesState = branchesState,
                    branches = loaded.map { it.first },
                    branch = branch,
                    onBranch = { branch = it },
                    onLoad = { viewModel.loadBranches(source) },
                )
                when (val s = state) {
                    is GitProposalUiState.Working -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is GitProposalUiState.Done -> (s.result as? GitProposalResult.Failed)?.let { Text(it.reason, color = MaterialTheme.colorScheme.error) }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                // The tip it already points at (its latest update's, else its own) is no update.
                enabled = target != null && tip != null && loadedState != null && tip != (currentTip ?: event.currentCommit()) && !working,
                onClick = { if (target != null && tip != null && loadedState != null) viewModel.updatePullRequest(pullRequest, target, loadedState.cloneUrl, tip) },
            ) { Text(stringRes(Res.string.git_pr_update_submit)) }
        },
        dismissButton = { TextButton(onClick = { open = false }, enabled = !working) { Text(stringRes(Res.string.git_merge_cancel)) } },
    )
}

/**
 * Editing a file in the app: its text, then a commit message, proposed as a pull request into the
 * branch being browsed ([GitProposals.proposeEdit]). Opens the pull request once it is published.
 */
@Composable
fun GitFileEditor(
    repository: AddressableNote,
    sourceClones: List<String>,
    branch: String?,
    parent: String,
    path: List<String>,
    initial: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    onCancel: () -> Unit,
) {
    val viewModel = rememberGitProposals(repository.idHex + path.joinToString("/"), accountViewModel)
    val target = rememberGitProposalTarget(repository, viewModel, accountViewModel)
    // The text lives in the view model, not saved state: a file of a few hundred KB would overflow
    // Android's saved-instance bundle. It is edited a block of lines at a time, so a keystroke
    // costs the block's layout, not the file's.
    // A draft is replayed only on the commit it was written on: on a newer tip it would revert
    // whatever changed upstream in the file.
    val draft = remember { viewModel.editDraft?.takeIf { viewModel.editDraftParent == parent } }
    val editor = remember { GitTextEditorState(draft ?: initial) }
    // Cancelled or published, the draft is done; left any other way (back, a reload), it is kept.
    val done = remember { mutableStateOf(false) }
    DisposableEffect(editor) {
        onDispose {
            val draft = editor.text().takeIf { !done.value && it != initial }
            viewModel.editDraft = draft
            viewModel.editDraftParent = draft?.let { parent }
        }
    }
    val finish = {
        done.value = true
        onCancel()
    }
    val changed = editor.isChanged || draft != null
    var message by rememberSaveable { mutableStateOf("Update ${path.lastOrNull() ?: ""}") }
    val working = viewModel.state.collectAsStateWithLifecycle().value is GitProposalUiState.Working
    // A result left from an earlier visit (published or failed after the editor closed) is not this one's.
    LaunchedEffect(Unit) { viewModel.reset() }
    // While the commit is pushed, back does nothing: leaving would hide the result it is waiting for.
    PlatformBackHandler(enabled = working) {}

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(Res.string.git_edit_title, path.joinToString("/")), style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
        OpenWhenPublished(viewModel, accountViewModel, nav, leaveFirst = finish) { reason -> Text(reason, color = MaterialTheme.colorScheme.error) }
        Text(stringRes(Res.string.git_edit_tap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
        GitTextEditor(editor, readOnly = working, modifier = Modifier.fillMaxWidth().weight(1f))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(stringRes(Res.string.git_edit_message)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = finish, enabled = !working) { Text(stringRes(Res.string.git_merge_cancel)) }
            Button(
                enabled = target != null && message.isNotBlank() && changed && !working && accountViewModel.account.isWriteable(),
                onClick = {
                    val text = editor.text()
                    if (target != null && text != initial) viewModel.proposeEdit(target, sourceClones, branch, parent, path, text, message.trim())
                },
            ) { Text(stringRes(Res.string.git_edit_propose)) }
        }
    }
}
