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
package com.vitorpamplona.amethyst.commons.buzz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.buzz.AgentWorkBoard
import com.vitorpamplona.amethyst.commons.model.buzz.AgentWorkItem
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzJobAggregator
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunAggregator
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.util.logSignerErrors
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * Backing ViewModel for the merged **Agent work** board — one channel, both agent protocols folded
 * into one [AgentWorkItem] list. It runs the two board subscriptions the split screens used to run
 * separately and merges their output:
 *
 * - **Jobs** (43001-43006 + kind-7 upvotes), folded by [BuzzJobAggregator].
 * - **Workflow runs** (46020 + lifecycle + the 46010 gate, plus 30620 defs) folded by
 *   [WorkflowRunAggregator]; the client-signed 46030/46031 decisions ride a by-author sub (rebuilt
 *   only when the approver set changes), so grants/denies land live.
 *
 * All read straight off [subscribeAsFlow] (not `LocalCache`, which can't serve these ≥10 000 kinds).
 * Writes reuse the same `Account` extensions the split boards used. "New task" chooses the protocol
 * from a single toggle: **require approval → a gated workflow run; otherwise → a direct job.**
 */
class AgentWorkBoardViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private var relay: NormalizedRelayUrl? = null
    private var channelId: String? = null

    private val _items = MutableStateFlow<List<AgentWorkItem>>(emptyList())
    val items: StateFlow<List<AgentWorkItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var watchJob: Job? = null

    fun bind(
        account: Account,
        channelId: String,
        relayUrl: String,
    ) {
        if (this.account != null) return
        this.account = account
        this.channelId = channelId
        this.relay = RelayUrlNormalizer.normalizeOrNull(relayUrl)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startWatching() {
        val account = account ?: return
        val relay = relay ?: return
        val channelId = channelId ?: return
        if (watchJob != null) return

        watchJob =
            viewModelScope.launch(Dispatchers.IO) {
                _isLoading.value = true
                val loadingTimeout =
                    launch {
                        delay(LOADING_TIMEOUT_MS)
                        _isLoading.value = false
                    }

                // Jobs (43xxx) + their upvotes, one #h subscription.
                val jobsFlow =
                    account.client
                        .taggedAs(SubPurpose.RELAY_GROUPS)
                        .subscribeAsFlow(relay, listOf(Filter(kinds = JOB_KINDS + ReactionEvent.KIND, tags = mapOf("h" to listOf(channelId)))))
                        .onStart { emit(emptyList()) }
                        .map { BuzzJobAggregator.aggregate(it) }

                // Workflow base (#h) + a by-author decisions sub rebuilt only when the approver set changes.
                val wfBaseFlow =
                    account.client
                        .taggedAs(SubPurpose.RELAY_GROUPS)
                        .subscribeAsFlow(relay, listOf(Filter(kinds = WORKFLOW_H_KINDS + WorkflowDefEvent.KIND, tags = mapOf("h" to listOf(channelId)))))
                        .onStart { emit(emptyList()) }
                val wfDecisionFlow =
                    wfBaseFlow
                        .map { base ->
                            base
                                .filterIsInstance<WorkflowApprovalRequestedEvent>()
                                .mapNotNull { it.approver() }
                                .distinct()
                                .sorted()
                        }.distinctUntilChanged()
                        .flatMapLatest { approvers ->
                            if (approvers.isEmpty()) {
                                flowOf(emptyList())
                            } else {
                                account.client
                                    .taggedAs(SubPurpose.RELAY_GROUPS)
                                    .subscribeAsFlow(relay, listOf(Filter(kinds = DECISION_KINDS, authors = approvers)))
                                    .onStart { emit(emptyList()) }
                            }
                        }
                val wfFlow = combine(wfBaseFlow, wfDecisionFlow) { base, decisions -> WorkflowRunAggregator.aggregate(base + decisions) }

                combine(jobsFlow, wfFlow) { jobs, runs -> AgentWorkBoard.merge(runs, jobs) }
                    .collect { merged ->
                        if (merged.isNotEmpty()) {
                            loadingTimeout.cancel()
                            _isLoading.value = false
                        }
                        _items.value = merged
                    }
            }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    /**
     * File a new piece of agent work. [requireApproval] is the whole gate/no-gate choice: on → a
     * workflow run that pauses for a human (an ad-hoc workflow id, since self-hosted the id is just a
     * label); off → a direct job that ships its PR without a gate.
     */
    fun newTask(
        text: String,
        requireApproval: Boolean,
        onResult: (Boolean) -> Unit,
    ) = act(onResult) { account, relay, channelId ->
        if (requireApproval) {
            account.relayGroups.triggerBuzzWorkflow(relay, channelId, ADHOC_WORKFLOW_ID, text) != null
        } else {
            account.relayGroups.fileBuzzJob(relay, channelId, text) != null
        }
    }

    fun approve(
        runId: HexKey,
        onResult: (Boolean) -> Unit,
    ) = act(onResult) { account, relay, _ -> account.relayGroups.approveBuzzWorkflowRun(relay, runId) != null }

    fun deny(
        runId: HexKey,
        onResult: (Boolean) -> Unit,
    ) = act(onResult) { account, relay, _ -> account.relayGroups.denyBuzzWorkflowRun(relay, runId) != null }

    fun upvote(
        jobId: HexKey,
        jobAuthor: HexKey?,
    ) = act({}) { account, relay, channelId ->
        account.relayGroups.upvoteBuzzJob(relay, channelId, jobId, jobAuthor)
        true
    }

    fun cancel(jobId: HexKey) =
        act({}) { account, relay, channelId ->
            account.relayGroups.cancelBuzzJob(relay, channelId, jobId)
            true
        }

    private inline fun act(
        crossinline onResult: (Boolean) -> Unit,
        crossinline block: suspend (Account, NormalizedRelayUrl, String) -> Boolean,
    ) {
        val account = account
        val relay = relay
        val channelId = channelId
        if (account == null || relay == null || channelId == null) {
            onResult(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = logSignerErrors("AgentWorkBoard") { block(account, relay, channelId) } ?: false
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    override fun onCleared() {
        stopWatching()
    }

    companion object {
        private const val LOADING_TIMEOUT_MS = 6_000L
        private const val ADHOC_WORKFLOW_ID = "adhoc"

        private val JOB_KINDS = (43001..43006).toList()

        // Same set the standalone workflow board folds: trigger + foldable lifecycle + the gate.
        private val WORKFLOW_H_KINDS =
            listOf(
                WorkflowTriggerEvent.KIND,
                WorkflowTriggeredEvent.KIND,
                WorkflowStepStartedEvent.KIND,
                WorkflowStepCompletedEvent.KIND,
                WorkflowCompletedEvent.KIND,
                WorkflowFailedEvent.KIND,
                WorkflowCancelledEvent.KIND,
                WorkflowApprovalRequestedEvent.KIND,
            )
        private val DECISION_KINDS = listOf(ApprovalGrantEvent.KIND, ApprovalDenyEvent.KIND)
    }
}
