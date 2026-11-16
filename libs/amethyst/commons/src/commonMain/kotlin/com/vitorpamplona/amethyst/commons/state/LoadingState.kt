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

/**
 * Generic loading state representation for async operations.
 *
 * Provides type-safe state management for loading, success, error, and empty states.
 * Eliminates the need for multiple boolean flags (isLoading, hasError, etc.).
 *
 * @param T The type of data when successfully loaded
 *
 * Usage example:
 * ```
 * val feedState: StateFlow<LoadingState<List<Event>>> = ...
 *
 * when (val state = feedState.collectAsState().value) {
 *     is LoadingState.Idle -> { /* Not started yet */ }
 *     is LoadingState.Loading -> CircularProgressIndicator()
 *     is LoadingState.Success -> LazyColumn { items(state.data) { ... } }
 *     is LoadingState.Error -> ErrorMessage(state.message)
 *     is LoadingState.Empty -> EmptyPlaceholder()
 * }
 * ```
 */
sealed class LoadingState<out T> {
    /**
     * Initial state - operation has not started yet.
     * Useful for actions (like follow/unfollow) that haven't been triggered.
     */
    object Idle : LoadingState<Nothing>()

    /**
     * Operation is in progress.
     */
    object Loading : LoadingState<Nothing>()

    /**
     * Operation completed successfully with data.
     *
     * @param data The loaded data
     */
    data class Success<T>(
        val data: T,
    ) : LoadingState<T>()

    /**
     * Operation failed with an error.
     *
     * @param message Human-readable error message
     * @param throwable Optional exception for debugging/logging
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null,
    ) : LoadingState<Nothing>()

    /**
     * Operation completed successfully but returned no data.
     * Useful for feeds/lists that have no items.
     */
    object Empty : LoadingState<Nothing>()

    /**
     * Returns true if this state represents a successful load (Success or Empty).
     */
    val isSuccessful: Boolean
        get() = this is Success || this is Empty

    /**
     * Returns true if this state represents a failure.
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Returns true if this state is currently loading.
     */
    val isLoading: Boolean
        get() = this is Loading

    /**
     * Returns the data if this is a Success state, null otherwise.
     */
    fun dataOrNull(): T? = if (this is Success) data else null

    /**
     * Returns the error message if this is an Error state, null otherwise.
     */
    fun errorOrNull(): String? = if (this is Error) message else null
}
