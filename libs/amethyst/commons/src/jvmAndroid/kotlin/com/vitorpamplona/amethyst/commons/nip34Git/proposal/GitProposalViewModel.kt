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
package com.vitorpamplona.amethyst.commons.nip34Git.proposal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Where opening, updating or proposing is. */
sealed interface GitProposalUiState {
    object Idle : GitProposalUiState

    object Working : GitProposalUiState

    class Done(
        val result: GitProposalResult,
    ) : GitProposalUiState
}

/**
 * A source's branches: loading, loaded (name and tip, with the clone URL they were read from, so a
 * tip is never published with another server's URL), or why they couldn't be read.
 */
sealed interface GitBranchesState {
    object Idle : GitBranchesState

    object Loading : GitBranchesState

    class Loaded(
        val cloneUrl: String,
        val branches: List<Pair<String, String>>,
    ) : GitBranchesState

    class Failed(
        val reason: String,
    ) : GitBranchesState
}

/**
 * Drives the pull-request screens: reading a source's branches, then opening a PR, updating one or
 * proposing an edit. The publishing runs in [workScope] (the account's), so leaving the screen
 * while commits are pushed does not drop the event that should follow them.
 */
class GitProposalViewModel(
    private val proposals: GitProposals,
    private val workScope: CoroutineScope,
    private val reportSignerErrors: suspend (suspend () -> Unit) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<GitProposalUiState>(GitProposalUiState.Idle)
    val state: StateFlow<GitProposalUiState> = _state.asStateFlow()

    private val _branches = MutableStateFlow<GitBranchesState>(GitBranchesState.Idle)
    val branches: StateFlow<GitBranchesState> = _branches.asStateFlow()

    fun target(repository: AddressableNote): GitProposalTarget? = proposals.targetFor(repository)

    private var branchesJob: Job? = null

    /** The editor's text while it differs from the file: kept here, not in saved state, since a file can be large. */
    var editDraft: String? = null

    /** The commit [editDraft] was written against: a draft of an older tree is not replayed on a newer one. */
    var editDraftParent: String? = null

    fun loadBranches(cloneUrl: String) {
        val url = cloneUrl.trim()
        // A slower answer for the previous URL must not land after this one.
        branchesJob?.cancel()
        _branches.value = GitBranchesState.Loading
        branchesJob =
            viewModelScope.launch(Dispatchers.IO) {
                _branches.value =
                    try {
                        GitBranchesState.Loaded(url, proposals.branches(url))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        GitBranchesState.Failed(e.message ?: e::class.simpleName ?: "could not read the branches")
                    }
            }
    }

    fun openPullRequest(
        target: GitProposalTarget,
        sourceClone: String,
        tip: String,
        sourceBranch: String,
        targetBranch: String?,
        title: String,
        description: String,
        labels: List<String>,
    ) = launchWork {
        proposals.openPullRequest(target, listOf(sourceClone.trim()), tip, title, description, labels, branchName = sourceBranch, targetBranch = targetBranch)
    }

    fun updatePullRequest(
        pullRequest: Note,
        target: GitProposalTarget,
        sourceClone: String,
        tip: String,
    ) = launchWork { proposals.updatePullRequest(pullRequest, target, listOf(sourceClone.trim()), tip) }

    fun proposeEdit(
        target: GitProposalTarget,
        sourceClones: List<String>,
        branch: String?,
        parent: String,
        path: List<String>,
        content: String,
        message: String,
    ) = launchWork { proposals.proposeEdit(target, sourceClones, branch, parent, mapOf(path to content.encodeToByteArray()), message, "") }

    fun reset() {
        if (_state.value !is GitProposalUiState.Working) _state.value = GitProposalUiState.Idle
    }

    private fun launchWork(work: suspend () -> GitProposalResult) {
        // One at a time: a second tap while the first runs, from any thread, does nothing.
        val current = _state.value
        if (current is GitProposalUiState.Working || !_state.compareAndSet(current, GitProposalUiState.Working)) return
        workScope.launch(Dispatchers.IO) {
            var result: GitProposalResult? = null
            try {
                reportSignerErrors { result = work() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = GitProposalResult.Failed(e.message ?: e::class.simpleName ?: "failed")
            }
            _state.value = result?.let { GitProposalUiState.Done(it) } ?: GitProposalUiState.Idle
        }
    }
}
