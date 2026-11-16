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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Stable
class RelaySuggestionState : IRelaySuggestionState {
    val currentInput = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    override val results =
        currentInput
            .debounce(300)
            .distinctUntilChanged()
            .map { input ->
                if (input.length > 1) {
                    val lower = input.lowercase()
                    LocalCache.relayHints.relayDB
                        .filter { _, relay -> relay.url.contains(lower) }
                        .map { relaySetupInfoBuilder(it) }
                        .sortedByDescending { it.relayStat.receivedBytes }
                        .take(20)
                } else {
                    emptyList()
                }
            }.flowOn(Dispatchers.IO)

    override fun processInput(input: String) {
        currentInput.tryEmit(input)
    }

    override fun reset() {
        currentInput.tryEmit("")
    }
}

@Stable
interface IRelaySuggestionState {
    val results: Flow<List<BasicRelaySetupInfo>>

    fun processInput(input: String)

    fun reset()
}
