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

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRun
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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
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

/** One selectable workflow definition (kind-30620) — its UUID `d` tag, optional name, and YAML recipe. */
@Immutable
data class WorkflowDefOption(
    val id: String,
    val name: String?,
    val yaml: String,
) {
    /** What the picker shows: the name if it has one, else a short form of the id. */
    val label: String get() = name ?: "Workflow ${id.take(8)}"
}

/**
 * Backing ViewModel for the [WorkflowRunBoardScreen] — the shared workflow runs of one Buzz channel.
 *
 * A Buzz "workflow" is the source-confirmed structured-work primitive (30620 def, 46020 trigger,
 * 46001-46007 lifecycle, 46010 approval gate, 46030/46031 grant/deny): a member triggers a run, the
 * runner does the work and pauses on a **human-approval gate**, and only ships after someone grants.
 * This VM folds those events into per-run [WorkflowRun] records via the shared [WorkflowRunAggregator]
 * and exposes them, awaiting-approval first, as a [StateFlow]; it drives trigger + approve + deny.
 *
 * **Why we aggregate from the live subscription, not `LocalCache`:** the run/lifecycle/gate kinds are
 * 46001-46031, which are neither regular (`< 10_000`) nor addressable, so `LocalCache.filter` cannot
 * serve them — its note branch only matches `kind.isRegular()` events. Reading them back from the
 * cache always returned nothing, so the board never showed a run. Instead we consume the events
 * straight off [subscribeAsFlow], which accumulates the channel's stored + live events (deduped by id)
 * and re-emits the growing list — exactly the data the aggregator needs.
 *
 * Two subscription realities (both mirrored from `amy buzz workflow`): the trigger + lifecycle + gate
 * + the addressable definitions (30620) are `#h`-scoped to the channel — one subscription, one `#h`
 * value, so Buzz's relay keeps it channel-scoped and delivers live (a multi-`#h` filter would be
 * forced global and go deaf). The grant/deny decisions (46030/46031) carry only a `d` tag, so they
 * ride a second subscription filtered by **author** — every 46010 gate names its approver in a `p`
 * tag, giving us exactly those authors. The two streams are merged and folded on every emission.
 */
class WorkflowRunBoardViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private var relay: NormalizedRelayUrl? = null
    private var channelId: String? = null

    private val _runs = MutableStateFlow<List<WorkflowRun>>(emptyList())
    val runs: StateFlow<List<WorkflowRun>> = _runs.asStateFlow()

    private val _definitions = MutableStateFlow<List<WorkflowDefOption>>(emptyList())

    /** The channel's published workflow definitions (kind-30620), name-sorted — the picker's options. */
    val definitions: StateFlow<List<WorkflowDefOption>> = _definitions.asStateFlow()

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

    /**
     * Open the channel's workflow subscriptions and fold every emission into the board. The base
     * `#h` subscription backfills stored runs then streams live ones; a nested by-author subscription
     * (rebuilt only when the approver set actually changes) streams the grant/deny decisions.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startWatching() {
        val account = account ?: return
        val relay = relay ?: return
        val channelId = channelId ?: return
        if (watchJob != null) return

        watchJob =
            viewModelScope.launch(Dispatchers.IO) {
                _isLoading.value = true
                // Fall out of the loading state even if the channel is genuinely empty (no EOSE signal).
                val loadingTimeout =
                    launch {
                        delay(LOADING_TIMEOUT_MS)
                        _isLoading.value = false
                    }

                val baseFilter = Filter(kinds = WORKFLOW_H_KINDS + WorkflowDefEvent.KIND, tags = mapOf("h" to listOf(channelId)))
                // `onStart(emptyList)` so the merged flow produces a first value even before any event
                // arrives — otherwise `combine` would never emit for an empty channel and the board
                // would sit on the spinner forever.
                val baseFlow =
                    account.client
                        .taggedAs(SubPurpose.RELAY_GROUPS)
                        .subscribeAsFlow(relay, listOf(baseFilter))
                        .onStart { emit(emptyList()) }

                val decisionFlow =
                    baseFlow
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

                combine(baseFlow, decisionFlow) { base, decisions -> base + decisions }
                    .collect { events ->
                        if (events.isNotEmpty()) {
                            loadingTimeout.cancel()
                            _isLoading.value = false
                        }
                        derive(events)
                    }
            }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    /** Fold the merged event list into the definitions picker + the prioritized run board. */
    private fun derive(events: List<Event>) {
        _definitions.value =
            events
                .filterIsInstance<WorkflowDefEvent>()
                .map { WorkflowDefOption(it.workflowId(), it.name()?.takeIf { n -> n.isNotBlank() }, it.yaml()) }
                .distinctBy { it.id }
                .sortedBy { (it.name ?: it.id).lowercase() }
        _runs.value = WorkflowRunAggregator.byPriority(WorkflowRunAggregator.aggregate(events))
    }

    fun trigger(
        workflowId: String,
        task: String,
        onResult: (Boolean) -> Unit,
    ) = act(onResult) { account, relay, channelId ->
        account.relayGroups.triggerBuzzWorkflow(relay, channelId, workflowId, task) != null
    }

    fun approve(
        runId: HexKey,
        onResult: (Boolean) -> Unit,
    ) = act(onResult) { account, relay, _ ->
        account.relayGroups.approveBuzzWorkflowRun(relay, runId) != null
    }

    fun deny(
        runId: HexKey,
        onResult: (Boolean) -> Unit,
    ) = act(onResult) { account, relay, _ ->
        account.relayGroups.denyBuzzWorkflowRun(relay, runId) != null
    }

    /**
     * Publish a new workflow definition (kind-30620) for this channel and hand its freshly-minted id
     * back on [onResult] (on the main thread) — or `null` if the account can't write / the publish
     * failed, so the caller can surface an error instead of hanging the editor open silently.
     */
    fun defineWorkflow(
        name: String,
        yaml: String,
        onResult: (String?) -> Unit,
    ) {
        val account = account
        val relay = relay
        val channelId = channelId
        if (account == null || relay == null || channelId == null) {
            onResult(null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val newId = logSignerErrors("WorkflowRunBoard") { account.relayGroups.publishBuzzWorkflowDef(relay, channelId, name, yaml) }
            withContext(Dispatchers.Main) { onResult(newId) }
        }
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
            val ok = logSignerErrors("WorkflowRunBoard") { block(account, relay, channelId) } ?: false
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    override fun onCleared() {
        stopWatching()
    }

    companion object {
        private const val LOADING_TIMEOUT_MS = 6_000L

        // The `#h`-scoped workflow kinds the aggregator can actually fold: trigger (46020), run/step
        // lifecycle (46001-46003, 46005-46007) and the relay-signed approval-requested gate (46010).
        // Deliberately excluded: the client-signed grant/deny commands (46030/46031) carry no `h` tag
        // (fetched by author instead), and 46004/46011/46012 carry no correlatable run id, so the
        // aggregator ignores them — fetching them would be pure cost.
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
