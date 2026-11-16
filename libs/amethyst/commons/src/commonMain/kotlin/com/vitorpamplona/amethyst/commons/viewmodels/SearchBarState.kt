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

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.search.SearchResult
import com.vitorpamplona.amethyst.commons.search.parseSearchInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * State holder for search bar functionality.
 * Shared between Android and Desktop search screens.
 *
 * Handles:
 * - Search text input
 * - Bech32/hex parsing
 * - Local cache search (with debounce)
 * - Relay search results aggregation
 *
 * Platform-specific concerns (relay subscriptions, navigation) remain in the UI layer.
 */
class SearchBarState(
    private val cache: ICacheProvider,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300L,
) {
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _bech32Results = MutableStateFlow<List<SearchResult>>(emptyList())
    val bech32Results: StateFlow<List<SearchResult>> = _bech32Results.asStateFlow()

    private val _cachedUserResults = MutableStateFlow<List<User>>(emptyList())
    val cachedUserResults: StateFlow<List<User>> = _cachedUserResults.asStateFlow()

    private val _relaySearchResults = MutableStateFlow<List<User>>(emptyList())
    val relaySearchResults: StateFlow<List<User>> = _relaySearchResults.asStateFlow()

    private val _isSearchingRelays = MutableStateFlow(false)
    val isSearchingRelays: StateFlow<Boolean> = _isSearchingRelays.asStateFlow()

    val hasResults: Boolean
        get() =
            _bech32Results.value.isNotEmpty() ||
                _cachedUserResults.value.isNotEmpty() ||
                _relaySearchResults.value.isNotEmpty()

    val shouldSearchRelays: Boolean
        get() =
            _searchText.value.length >= 2 &&
                _bech32Results.value.isEmpty() &&
                _cachedUserResults.value.size < 5

    init {
        setupSearchTextObserver()
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchTextObserver() {
        // Debounced cache search
        _searchText
            .debounce(debounceMs)
            .onEach { query ->
                if (query.length >= 2 && _bech32Results.value.isEmpty()) {
                    _cachedUserResults.value = cache.findUsersStartingWith(query, 20)
                } else {
                    _cachedUserResults.value = emptyList()
                }
            }
            // The scan walks every cached user; callers hand us a Compose scope,
            // so keep it off the main dispatcher.
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    fun updateSearchText(text: String) {
        _searchText.value = text
        _relaySearchResults.value = emptyList()
        _isSearchingRelays.value = false

        // Parse Bech32/hex immediately (no debounce)
        _bech32Results.value = parseSearchInput(text)

        // Clear cached results if query too short or is bech32
        if (text.length < 2 || _bech32Results.value.isNotEmpty()) {
            _cachedUserResults.value = emptyList()
        }
    }

    fun clearSearch() {
        updateSearchText("")
    }

    fun startRelaySearch() {
        _isSearchingRelays.value = true
    }

    fun endRelaySearch() {
        _isSearchingRelays.value = false
    }

    fun addRelaySearchResult(user: User) {
        if (!_relaySearchResults.value.any { it.pubkeyHex == user.pubkeyHex }) {
            _relaySearchResults.value = _relaySearchResults.value + user
        }
    }
}
