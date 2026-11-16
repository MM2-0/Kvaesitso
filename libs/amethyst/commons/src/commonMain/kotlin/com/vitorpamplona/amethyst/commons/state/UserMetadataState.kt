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
package com.vitorpamplona.amethyst.commons.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.User
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Reactive state wrapper for User metadata.
 *
 * Provides a StateFlow that emits UserState when user metadata changes.
 * Used by both Android ViewModels and Desktop composables for reactive UI updates.
 *
 * Android pattern: UserBundledRefresherFlow (typealias for backwards compatibility)
 */
@Stable
class UserMetadataState(
    val user: User,
) {
    val stateFlow = MutableStateFlow(UserState(user))

    fun invalidateData() {
        stateFlow.tryEmit(UserState(user))
    }

    fun hasObservers() = stateFlow.subscriptionCount.value > 0
}

/**
 * Immutable snapshot of User state.
 *
 * Emitted by UserMetadataState.stateFlow to trigger recomposition.
 */
@Immutable
class UserState(
    val user: User,
)
