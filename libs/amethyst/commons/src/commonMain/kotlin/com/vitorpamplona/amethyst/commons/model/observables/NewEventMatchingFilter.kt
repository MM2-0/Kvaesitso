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
package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Emits each new event for which [predicate] returns true, one at a time,
 * as it is inserted into the cache. Unlike [EventListMatchingFilter] /
 * [NoteListMatchingFilter], this does not accumulate a list — it simply
 * calls [onNew] per matching event. Useful for reactive event-triggered
 * pipelines like notifications.
 *
 * The predicate has to be fast — it runs on every new cache insertion.
 * Callers with a Nostr [com.vitorpamplona.quartz.nip01Core.relay.filters.Filter]
 * can pass `filter::match` as the predicate and compose additional checks
 * with `&&`.
 */
class NewEventMatchingFilter<T : Event>(
    private val predicate: (Event) -> Boolean,
    private val onNew: (T) -> Unit,
) : Observable {
    @Suppress("UNCHECKED_CAST")
    override fun new(
        event: Event,
        note: Note,
    ) {
        if (predicate(event)) {
            onNew(event as T)
        }
    }

    override fun remove(note: Note) {}
}
