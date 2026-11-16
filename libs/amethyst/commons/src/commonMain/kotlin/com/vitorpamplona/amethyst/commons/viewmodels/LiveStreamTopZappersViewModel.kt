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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.ListChange
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.LiveActivityTopZappersAggregator
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.TopZapperEntry
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ZapContribution
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.toLongValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Live-stream top-zappers leaderboard state, computed off the UI thread and maintained
 * incrementally.
 *
 * The leaderboard draws from two independent contribution sets keyed by zap-receipt id:
 *   - [streamContributions] — zaps routed into the [LiveActivitiesChannel]'s cache via the
 *     stream's `#a` subscription;
 *   - [goalContributions] — zaps attached to a NIP-75 goal note via `#e`.
 *
 * The union is flattened on each change and fed into [LiveActivityTopZappersAggregator] for
 * de-dup, anonymous bucketing, sum-by-contributor, and top-N sort, publishing a stable
 * `List<TopZapperEntry>` via [topZappers] for dumb UI consumption.
 */
@Stable
class LiveStreamTopZappersViewModel(
    private val channel: LiveActivitiesChannel,
    private val limit: Int = LiveActivityTopZappersAggregator.DEFAULT_LIMIT,
) : ViewModel() {
    private val mutex = Mutex()

    // receiptId -> ZapContribution, partitioned by source. Aggregator dedupes at merge.
    private val streamContributions = HashMap<HexKey, ZapContribution>()
    private val goalContributions = HashMap<HexKey, ZapContribution>()

    private val _topZappers = MutableStateFlow<List<TopZapperEntry>>(emptyList())
    val topZappers: StateFlow<List<TopZapperEntry>> = _topZappers.asStateFlow()

    private var goalNote: Note? = null
    private var goalObserverJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Seed from the channel cache.
            mutex.withLock {
                channel.notes.forEach { _, note ->
                    contributionFromStreamZap(note)?.let { streamContributions[it.receiptId] = it }
                }
            }
            publish()

            // Keep in sync with subsequent arrivals.
            channel.changesFlow().collect { change ->
                val mutated = applyChannelChange(change)
                if (mutated) publish()
            }
        }
    }

    /**
     * Attach (or detach) a NIP-75 goal note whose zaps should flow into the leaderboard.
     * Passing the same instance twice is a no-op; passing null clears the goal source.
     */
    fun setGoalNote(newGoalNote: Note?) {
        if (newGoalNote === goalNote) return
        goalNote = newGoalNote

        goalObserverJob?.cancel()
        goalObserverJob =
            viewModelScope.launch(Dispatchers.IO) {
                // Reset goal bucket and re-seed from the new note (if any) before subscribing.
                rebuildGoalContributions(newGoalNote)
                publish()

                if (newGoalNote == null) return@launch

                // Observe future goal-zap arrivals. Each emission rebuilds the goal bucket;
                // the aggregator already handles de-dup at merge-time.
                newGoalNote.flow().zaps.stateFlow.collect {
                    rebuildGoalContributions(newGoalNote)
                    publish()
                }
            }
    }

    private suspend fun applyChannelChange(change: ListChange<Note>): Boolean =
        mutex.withLock {
            when (change) {
                is ListChange.Addition -> upsertStream(change.item)
                is ListChange.Deletion -> removeStream(change.item.idHex)
                is ListChange.SetAddition -> change.item.fold(false) { acc, n -> upsertStream(n) || acc }
                is ListChange.SetDeletion -> change.item.fold(false) { acc, n -> removeStream(n.idHex) || acc }
            }
        }

    private fun upsertStream(note: Note): Boolean {
        val c = contributionFromStreamZap(note) ?: return false
        val prev = streamContributions[c.receiptId]
        if (prev == c) return false
        streamContributions[c.receiptId] = c
        return true
    }

    private fun removeStream(receiptId: HexKey): Boolean = streamContributions.remove(receiptId) != null

    private suspend fun rebuildGoalContributions(goal: Note?) {
        mutex.withLock {
            goalContributions.clear()
            goal?.zaps?.forEach { (zapRequestNote, receiptNote) ->
                contributionFromGoalZap(zapRequestNote, receiptNote)?.let {
                    goalContributions[it.receiptId] = it
                }
            }
            goal?.bolt12Zaps?.values?.forEach { entry ->
                contributionFromStreamZap(entry.source)?.let {
                    goalContributions[it.receiptId] = it
                }
            }
        }
    }

    private suspend fun publish() {
        // Snapshot both maps under the same mutex their writers hold; the two
        // collectors (channel changes, goal zaps) run on different coroutines.
        val merged = mutex.withLock { streamContributions.values + goalContributions.values }
        _topZappers.value = LiveActivityTopZappersAggregator.aggregate(merged, limit)
    }

    private fun contributionFromStreamZap(note: Note): ZapContribution? =
        when (val ev = note.event) {
            is ZapReceiptEvent -> {
                val request = ev.zapRequest ?: return null
                val sats = ev.amount()?.toLongValue() ?: return null
                ZapContribution(note.idHex, request.pubKey, request.isAnonTagged(), sats)
            }
            is Bolt12ZapEvent -> {
                val sats = ev.amount()?.div(1000) ?: return null
                ZapContribution(note.idHex, ev.payer() ?: ev.pubKey, ev.isAnonymous(), sats)
            }
            else -> null
        }

    private fun contributionFromGoalZap(
        zapRequestNote: Note,
        receiptNote: Note?,
    ): ZapContribution? {
        val receiptEv = receiptNote?.event as? ZapReceiptEvent ?: return null
        val request = zapRequestNote.event as? ZapRequestEvent ?: return null
        val sats = receiptEv.amount()?.toLongValue() ?: return null
        return ZapContribution(receiptNote.idHex, request.pubKey, request.isAnonTagged(), sats)
    }
}

/** True for both public anonymous and NIP-57 private zaps (any `anon` tag, empty or encrypted). */
private fun ZapRequestEvent.isAnonTagged(): Boolean = tags.any { it.isNotEmpty() && it[0] == "anon" }
