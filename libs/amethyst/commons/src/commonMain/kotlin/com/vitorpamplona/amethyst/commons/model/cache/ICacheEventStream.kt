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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.Note
import kotlinx.coroutines.flow.SharedFlow

/**
 * Event stream interface for cache updates.
 *
 * Abstracts the real-time event notification system used by ViewModels
 * to react to new notes and deletions. Platform implementations
 * (Android LocalCache, Desktop cache) provide these streams.
 *
 * ViewModels collect these flows to incrementally update feed state
 * without full refresh.
 */
interface ICacheEventStream {
    /**
     * Flow of new note bundles added to the cache.
     * Emits sets of Note objects when new events arrive from relays.
     */
    val newEventBundles: SharedFlow<Set<Note>>

    /**
     * Flow of deleted note bundles removed from the cache.
     * Emits sets of Note objects when deletion events are processed.
     */
    val deletedEventBundles: SharedFlow<Set<Note>>
}
