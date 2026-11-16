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
import com.vitorpamplona.amethyst.commons.feeds.ChangesFlowFilter
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.commons.model.ListChange
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Stable
abstract class ListChangeFeedViewModel(
    localFilter: ChangesFlowFilter<Note>,
    cacheProvider: ICacheProvider,
) : ViewModel(),
    InvalidatableContent {
    val feedState = FeedContentState(localFilter, viewModelScope, cacheProvider)

    override val isRefreshing = feedState.isRefreshing

    override fun invalidateData(ignoreIfDoing: Boolean) = feedState.invalidateData(ignoreIfDoing)

    init {
        Log.d("Init") { "Starting new Model: ${this::class.simpleName}" }
        // Trigger initial load so empty rooms show Empty instead of Loading
        viewModelScope.launch(Dispatchers.IO) {
            feedState.invalidateData(ignoreIfDoing = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            localFilter.changesFlow().collect {
                Log.d("Init") { "Collecting changes to: ${this@ListChangeFeedViewModel::class.simpleName}" }
                when (it) {
                    is ListChange.Addition -> feedState.updateFeedWith(setOf(it.item))
                    is ListChange.Deletion -> feedState.deleteFromFeed(setOf(it.item))
                    is ListChange.SetAddition -> feedState.updateFeedWith(it.item)
                    is ListChange.SetDeletion -> feedState.deleteFromFeed(it.item)
                }
            }
        }
    }
}
