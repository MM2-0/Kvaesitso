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
package com.vitorpamplona.amethyst.commons.nip34Git.ci

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache.observeEvents
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Workflow Results and Progress markers that name one PR or patch as their `E` root. */
@Immutable
class CiItemEvents(
    val results: List<CiWorkflowResultEvent>,
    val progress: List<CiWorkflowProgressEvent>,
) {
    fun summarize(now: Long) = CiRollup.summarize(results, progress, now)
}

/**
 * Cross-screen index of the Nostr CI runs attached to each PR / patch, keyed by the root item id the
 * runs carry in their NIP-22 `E` tag. It reduces the kind-indexed [observeEvents] stream, so a list
 * row reads its badge without a cache scan. Only 9842 and 39842 are indexed: Job Results (9841) are read on demand, inside the attempt
 * that quotes them. Push-triggered runs carry no `E` and are left out.
 *
 * Eager, never `WhileSubscribed`: rows read `.value` synchronously. `null` means "not loaded yet".
 */
object CiStatusIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val byItem: StateFlow<Map<HexKey, CiItemEvents>?> =
        LocalCache
            .observeEvents<Event>(Filter(kinds = listOf(CiWorkflowResultEvent.KIND, CiWorkflowProgressEvent.KIND)))
            .map { groupByItem(it) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)

    fun groupByItem(events: List<Event>): Map<HexKey, CiItemEvents> {
        val results = HashMap<HexKey, MutableList<CiWorkflowResultEvent>>()
        val progress = HashMap<HexKey, MutableList<CiWorkflowProgressEvent>>()
        for (event in events) {
            when (event) {
                is CiWorkflowResultEvent -> {
                    val item = event.pullRequest()?.pullRequestId ?: continue
                    results.getOrPut(item) { mutableListOf() }.add(event)
                }

                is CiWorkflowProgressEvent -> {
                    val item = event.pullRequest()?.pullRequestId ?: continue
                    progress.getOrPut(item) { mutableListOf() }.add(event)
                }
            }
        }
        return (results.keys + progress.keys).associateWith { CiItemEvents(results[it] ?: emptyList(), progress[it] ?: emptyList()) }
    }
}
