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
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedFilter
import com.vitorpamplona.amethyst.commons.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Stable
abstract class FeedViewModel(
    localFilter: FeedFilter<Note>,
    val cacheProvider: ICacheProvider,
) : ViewModel(),
    InvalidatableContent {
    val feedState = FeedContentState(localFilter, viewModelScope, cacheProvider)

    override val isRefreshing = feedState.isRefreshing

    fun sendToTop() = feedState.sendToTop()

    suspend fun sentToTop() = feedState.sentToTop()

    override fun invalidateData(ignoreIfDoing: Boolean) = feedState.invalidateData(ignoreIfDoing)

    init {
        Log.d("Init") { "Starting new Model: ${this::class.simpleName}" }
        viewModelScope.launch(Dispatchers.IO) {
            cacheProvider.getEventStream().newEventBundles.collect { newNotes ->
                Log.d("Rendering Metrics") { "Update feeds: ${this@FeedViewModel::class.simpleName} with ${newNotes.size}" }
                feedState.updateFeedWith(newNotes)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            cacheProvider.getEventStream().deletedEventBundles.collect { newNotes ->
                Log.d("Rendering Metrics") { "Delete from feeds: ${this@FeedViewModel::class.simpleName} with ${newNotes.size}" }
                feedState.deleteFromFeed(newNotes)
            }
        }
    }

    override fun onCleared() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
    }
}
