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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.feeds.FeedFilter
import com.vitorpamplona.amethyst.commons.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.commons.feeds.UserFeedState
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.service.BundledUpdate
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
open class UserFeedViewModel(
    val dataSource: FeedFilter<User>,
) : ViewModel(),
    InvalidatableContent {
    private val _feedContent = MutableStateFlow<UserFeedState>(UserFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshSuspended() }
    }

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    private fun refreshSuspended() {
        try {
            isRefreshing.value = true

            val notes = dataSource.loadTop().toImmutableList()

            val oldNotesState = _feedContent.value
            if (oldNotesState is UserFeedState.Loaded) {
                // Using size as a proxy for has changed.
                if (!equalImmutableLists(notes, oldNotesState.feed.value)) {
                    updateFeed(notes)
                }
            } else {
                updateFeed(notes)
            }
        } finally {
            isRefreshing.value = false
        }
    }

    private fun updateFeed(notes: ImmutableList<User>) {
        val currentState = _feedContent.value
        if (notes.isEmpty()) {
            _feedContent.tryEmit(UserFeedState.Empty)
        } else if (currentState is UserFeedState.Loaded) {
            // updates the current list
            currentState.feed.tryEmit(notes)
        } else {
            _feedContent.tryEmit(UserFeedState.Loaded(MutableStateFlow(notes)))
        }
    }

    private val bundler = BundledUpdate(250, Dispatchers.IO)

    override fun invalidateData(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing) {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            refreshSuspended()
        }
    }

    init {
        Log.d("Init") { "${this::class.simpleName}" }
        viewModelScope.launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect { newNotes ->
                Log.d("Rendering Metrics") { "Update feeds: ${this@UserFeedViewModel::class.simpleName} with ${newNotes.size}" }
                invalidateData()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            LocalCache.live.deletedEventBundles.collect { newNotes ->
                Log.d("Rendering Metrics") { "Delete from feeds: ${this@UserFeedViewModel::class.simpleName} with ${newNotes.size}" }
                invalidateData()
            }
        }
    }

    override fun onCleared() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
        bundler.cancel()
    }
}
