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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzJobAggregator
import com.vitorpamplona.amethyst.commons.model.buzz.JobView
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.util.logSignerErrors
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Backing ViewModel for the [JobBoardScreen] — the shared backlog of one Buzz channel.
 *
 * A Buzz "job" is the agent-job protocol (kinds 43001-43006): a member files a request, the
 * workspace bot works it, and every step lands as a signed event the whole room sees. This VM
 * folds those events (plus their kind-7 upvotes) into per-job [JobView] records via the shared
 * [BuzzJobAggregator] and exposes the backlog as a [StateFlow]. It also drives the three write
 * actions the board offers: file, upvote, cancel.
 *
 * **Aggregated from the live subscription, not `LocalCache`:** the job kinds 43001-43006 are neither
 * regular (`< 10_000`) nor addressable, so `LocalCache.filter` can't serve them (its note branch only
 * matches `kind.isRegular()`) — reading them back from cache returned nothing and the board stayed
 * empty. We consume the events straight off [subscribeAsFlow], which accumulates the channel's stored
 * + live events (deduped) and re-emits the list. The kind-7 upvotes ride the same `#h` subscription.
 */
class JobBoardViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private var relay: NormalizedRelayUrl? = null
    private var channelId: String? = null

    private val _jobs = MutableStateFlow<List<JobView>>(emptyList())
    val jobs: StateFlow<List<JobView>> = _jobs.asStateFlow()

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

    /** Keep the board live while it's on screen: the subscription backfills then streams job/reaction events. */
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
                account.client
                    .taggedAs(SubPurpose.RELAY_GROUPS)
                    .subscribeAsFlow(relay, boardFilters(channelId))
                    .onStart { emit(emptyList()) }
                    .collect { events ->
                        if (events.isNotEmpty()) {
                            loadingTimeout.cancel()
                            _isLoading.value = false
                        }
                        _jobs.value = BuzzJobAggregator.aggregate(events)
                    }
            }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    fun file(request: String) =
        act { account, relay, channelId ->
            account.relayGroups.fileBuzzJob(relay, channelId, request)
        }

    fun upvote(
        jobId: String,
        jobAuthor: String?,
    ) = act { account, relay, channelId ->
        account.relayGroups.upvoteBuzzJob(relay, channelId, jobId, jobAuthor)
    }

    fun cancel(jobId: String) =
        act { account, relay, channelId ->
            account.relayGroups.cancelBuzzJob(relay, channelId, jobId)
        }

    private inline fun act(crossinline block: suspend (Account, NormalizedRelayUrl, String) -> Unit) {
        val account = account ?: return
        val relay = relay ?: return
        val channelId = channelId ?: return
        // The live subscription picks up the relay's echo of our own write, so no local re-derive here.
        viewModelScope.launch(Dispatchers.IO) { logSignerErrors("JobBoard") { block(account, relay, channelId) } }
    }

    override fun onCleared() {
        stopWatching()
    }

    companion object {
        private const val LOADING_TIMEOUT_MS = 6_000L

        // The Buzz agent-job protocol (request/accepted/progress/result/cancel/error) plus the
        // kind-7 upvotes that prioritize it.
        private val JOB_KINDS = (43001..43006).toList()

        private fun boardFilters(channelId: String) =
            listOf(
                Filter(kinds = JOB_KINDS, tags = mapOf("h" to listOf(channelId))),
                Filter(kinds = listOf(ReactionEvent.KIND), tags = mapOf("h" to listOf(channelId))),
            )
    }
}
