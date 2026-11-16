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
package com.vitorpamplona.amethyst.commons.nip34Git.merge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** The merge sheet's state. */
sealed interface GitMergeUiState {
    object Idle : GitMergeUiState

    class Checking(
        val step: GitMergeCheckStep,
    ) : GitMergeUiState

    class Checked(
        val check: GitMergeCheck,
    ) : GitMergeUiState

    /** [hidden]: the person closed the sheet; the merge goes on and reports when it ends. */
    class Merging(
        val ready: GitMergeCheck.Ready,
        val step: GitMergeStep,
        val hidden: Boolean = false,
    ) : GitMergeUiState

    class Done(
        val outcome: GitMergeOutcome,
    ) : GitMergeUiState

    class Error(
        val message: String,
    ) : GitMergeUiState
}

/**
 * Drives one item's merge: [check] plans it (cancellable with [dismiss]), [merge] signs, pushes and
 * publishes. The merge runs in [workScope] (the account's), not the screen's, so leaving the screen
 * halfway does not stop a push after its state went out. A merge the person [hide]s, or leaves the
 * screen during, ends with [onHiddenFinished] (its outcome) or [onHiddenFailed] (an error) instead
 * of the sheet.
 */
class GitMergeViewModel(
    private val merger: GitMerger,
    private val workScope: CoroutineScope,
    /** Wraps the signing work so a refused or absent signer surfaces as the app reports it. */
    private val reportSignerErrors: suspend (suspend () -> Unit) -> Unit,
    private val onHiddenFinished: (GitMergeOutcome) -> Unit = {},
    private val onHiddenFailed: (String) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow<GitMergeUiState>(GitMergeUiState.Idle)
    val state: StateFlow<GitMergeUiState> = _state.asStateFlow()

    private var checking: Job? = null

    fun check(item: Note) {
        if (_state.value is GitMergeUiState.Merging) return
        checking?.cancel()
        _state.value = GitMergeUiState.Checking(GitMergeCheckStep.STATE)
        checking =
            viewModelScope.launch(Dispatchers.IO) {
                val result =
                    try {
                        GitMergeUiState.Checked(merger.check(item) { step -> if (isActive) _state.value = GitMergeUiState.Checking(step) })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        GitMergeUiState.Error(e.message ?: e::class.simpleName ?: "merge check failed")
                    }
                if (isActive) _state.value = result
            }
    }

    fun merge(item: Note) {
        val ready = (_state.value as? GitMergeUiState.Checked)?.check as? GitMergeCheck.Ready ?: return
        _state.value = GitMergeUiState.Merging(ready, GitMergeStep.PUBLISHING_STATE_AND_PUSHING)
        workScope.launch(Dispatchers.IO) {
            var outcome: GitMergeOutcome? = null
            var failure: String? = null
            try {
                reportSignerErrors {
                    outcome = merger.merge(item, ready.merge, ready.resolutions) { step -> updateMerging { GitMergeUiState.Merging(it.ready, step, it.hidden) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message ?: e::class.simpleName ?: "merge failed"
            }
            finish(ready, outcome, failure)
        }
    }

    /**
     * Ends a merge: on the sheet when it is showing; else the person hears of it where they are,
     * and the sheet goes back to idle rather than pop up again.
     */
    private fun finish(
        ready: GitMergeCheck.Ready,
        outcome: GitMergeOutcome?,
        failure: String?,
    ) {
        var wasHidden = false
        _state.update { current ->
            wasHidden = (current as? GitMergeUiState.Merging)?.hidden == true
            when {
                wasHidden && (outcome != null || failure != null) -> GitMergeUiState.Idle
                outcome != null -> GitMergeUiState.Done(outcome)
                failure != null -> GitMergeUiState.Error(failure)
                // A signer that refused before anything was pushed: ready to try again.
                else -> GitMergeUiState.Checked(ready)
            }
        }
        if (wasHidden) {
            outcome?.let(onHiddenFinished)
            failure?.let(onHiddenFailed)
        }
    }

    /** Closes the sheet while a merge goes on; it reports when it ends. */
    fun hide() = updateMerging { GitMergeUiState.Merging(it.ready, it.step, hidden = true) }

    /** Closes the sheet, cancelling a check; a merge in flight is only hidden. */
    fun dismiss() {
        if (_state.value is GitMergeUiState.Merging) return hide()
        checking?.cancel()
        _state.value = GitMergeUiState.Idle
    }

    /** The screen is gone: a merge still running reports where the person is now. */
    override fun onCleared() {
        hide()
        super.onCleared()
    }

    /** Changes a merge in flight atomically: [hide] and the merge's own steps come from different threads. */
    private inline fun updateMerging(crossinline change: (GitMergeUiState.Merging) -> GitMergeUiState.Merging) {
        _state.update { current -> (current as? GitMergeUiState.Merging)?.let(change) ?: current }
    }
}
