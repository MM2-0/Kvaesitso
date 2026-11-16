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
package com.vitorpamplona.amethyst.commons.feeds

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Base interface for notification cards.
 *
 * Cards represent grouped or individual notification items in the feed.
 * Platform implementations provide concrete card types (NoteCard, MultiSetCard, etc.).
 */
@Immutable
interface Card {
    /**
     * Returns the creation timestamp of this card.
     * Used for sorting cards in chronological order.
     */
    fun createdAt(): Long

    /**
     * Returns a unique identifier for this card.
     * Used for list diffing and deduplication.
     */
    fun id(): String
}

/**
 * Feed state for notification cards.
 *
 * Mirrors FeedState but specifically typed for Card content.
 */
@Stable
sealed class CardFeedState {
    @Immutable
    object Loading : CardFeedState()

    @Stable
    class Loaded(
        val feed: MutableStateFlow<LoadedFeedState<Card>>,
    ) : CardFeedState()

    @Immutable
    object Empty : CardFeedState()

    @Immutable
    class FeedError(
        val errorMessage: String,
    ) : CardFeedState()
}

/**
 * Comparator for sorting cards by creation time (newest first).
 */
val DefaultCardComparator: Comparator<Card> =
    compareByDescending<Card> { it.createdAt() }
        .thenBy { it.id() }
