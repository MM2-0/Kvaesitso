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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Delegate for searching users via relays (NIP-50 or other mechanism).
 * Platform-specific: desktop uses relay manager, Android could use its own.
 */
interface RelayUserSearchDelegate {
    fun searchPeople(
        query: String,
        limit: Int,
        onResult: (User) -> Unit,
        onComplete: () -> Unit,
    ): Job
}

/**
 * Reusable user search engine that combines local cache search with optional relay search.
 * Platform-agnostic — lives in commons, relay interaction via delegate.
 *
 * Usage:
 * ```
 * val engine = UserSearchEngine(cache, scope)
 * engine.relayDelegate = myRelayDelegate // optional
 * engine.search("fiatjaf")
 * // collect engine.results, engine.isSearching
 * ```
 */
class UserSearchEngine(
    private val cache: ICacheProvider,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300L,
    private val localLimit: Int = 10,
    private val relayLimit: Int = 10,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _localResults = MutableStateFlow<List<User>>(emptyList())
    val localResults: StateFlow<List<User>> = _localResults.asStateFlow()

    private val _relayResults = MutableStateFlow<List<User>>(emptyList())
    val relayResults: StateFlow<List<User>> = _relayResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    var relayDelegate: RelayUserSearchDelegate? = null

    private var relaySearchJob: Job? = null

    init {
        setupDebouncedSearch()
    }

    fun search(text: String) {
        _query.value = text
        _relayResults.value = emptyList()
        relaySearchJob?.cancel()

        if (text.length < 2) {
            _localResults.value = emptyList()
            _isSearching.value = false
        }
    }

    fun clear() = search("")

    /**
     * Resolves an input string to a hex pubkey.
     * Handles npub, nprofile, and raw hex.
     */
    fun resolveToHex(input: String): String = decodePublicKeyAsHexOrNull(input.trim()) ?: input.trim()

    @OptIn(FlowPreview::class)
    private fun setupDebouncedSearch() {
        _query
            .debounce(debounceMs)
            .onEach { text ->
                if (text.length >= 2) {
                    // Local cache search (instant)
                    _localResults.value = cache.findUsersStartingWith(text, localLimit)

                    // Relay search (async, via delegate)
                    startRelaySearch(text)
                } else {
                    _localResults.value = emptyList()
                    _isSearching.value = false
                }
            }
            // findUsersStartingWith walks the whole user cache; callers pass a
            // Compose scope, so keep the scan off the main dispatcher.
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    private fun startRelaySearch(text: String) {
        val delegate = relayDelegate ?: return
        relaySearchJob?.cancel()
        _isSearching.value = true
        _relayResults.value = emptyList()

        relaySearchJob =
            delegate.searchPeople(
                query = text,
                limit = relayLimit,
                onResult = { user ->
                    // Deduplicate against local results and existing relay results
                    val isDuplicate =
                        _localResults.value.any { it.pubkeyHex == user.pubkeyHex } ||
                            _relayResults.value.any { it.pubkeyHex == user.pubkeyHex }
                    if (!isDuplicate) {
                        _relayResults.value = _relayResults.value + user
                    }
                },
                onComplete = {
                    _isSearching.value = false
                },
            )
    }
}
