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
package com.vitorpamplona.amethyst.commons.viewmodels.thread

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.feeds.FeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ThreadLevelCalculator
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.viewmodels.FeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

@Stable
abstract class LevelFeedViewModel(
    localFilter: FeedFilter<Note>,
    cacheProvider: ICacheProvider,
) : FeedViewModel(localFilter, cacheProvider) {
    var llState: LazyListState by mutableStateOf(LazyListState(0, 0))

    val hasDragged = mutableStateOf(false)

    /**
     * Reply notes (by id hex) the user has collapsed in the thread view. A collapsed note
     * renders only a short preview of itself and hides all of its descendants. Backed by a
     * snapshot map so reads in composition recompose when the set changes.
     */
    val collapsedReplies = mutableStateMapOf<String, Boolean>()

    /**
     * Whether a reply is collapsed: the user's own choice when they made one (true or false),
     * otherwise [collapsedByDefault] (replies from outside the Web of Trust network start
     * collapsed).
     */
    fun isCollapsed(
        idHex: String,
        collapsedByDefault: Set<String> = emptySet(),
    ): Boolean = collapsedReplies[idHex] ?: (idHex in collapsedByDefault)

    fun toggleCollapsed(
        idHex: String,
        collapsedByDefault: Set<String> = emptySet(),
    ) {
        collapsedReplies[idHex] = !isCollapsed(idHex, collapsedByDefault)
        // Collapsing is an explicit interaction, so stop the thread from auto-scrolling
        // to the focused note afterwards.
        hasDragged.value = true
    }

    val selectedIDHex =
        llState.interactionSource.interactions
            .onEach {
                if (it is DragInteraction.Start) {
                    hasDragged.value = true
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null,
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val levelCacheFlow: StateFlow<Map<Note, Int>> =
        feedState.feedContent
            .transformLatest { feed ->
                emitAll(
                    if (feed is FeedState.Loaded) {
                        feed.feed.map {
                            val cache = mutableMapOf<Note, Int>()
                            it.list.forEach {
                                ThreadLevelCalculator.replyLevel(it, cache)
                            }
                            cache
                        }
                    } else {
                        MutableStateFlow(mapOf())
                    },
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                mapOf(),
            )

    fun levelFlowForItem(note: Note) =
        levelCacheFlow
            .map {
                it[note] ?: 0
            }.distinctUntilChanged()
}
