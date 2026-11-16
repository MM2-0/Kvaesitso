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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The members of every list the user has picked in a top bar, on any screen, whose own relays those
 * feeds read ([IFeedTopNavFilter.outboxAuthors]). Each screen's top-nav flow is [register]ed once
 * as the account builds it; [flow] is the union of their authors and changes whenever any
 * selection, or a selected list, changes.
 *
 * A member nobody follows has no other loader asking for their relay list, so the app watches this
 * set as feed authors. Swapping a selection swaps the set, which starts and stops that watch.
 */
class SelectedListAuthorsState(
    scope: CoroutineScope,
) {
    private val selections = MutableStateFlow<List<StateFlow<IFeedTopNavFilter>>>(emptyList())

    fun register(selection: StateFlow<IFeedTopNavFilter>) = selections.update { it + selection }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<HexKey>> =
        selections
            .flatMapLatest { flows ->
                if (flows.isEmpty()) {
                    MutableStateFlow(emptySet())
                } else {
                    combine(flows) { filters -> mergeOutboxAuthors(filters.asList()) }
                }
            }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())
}
