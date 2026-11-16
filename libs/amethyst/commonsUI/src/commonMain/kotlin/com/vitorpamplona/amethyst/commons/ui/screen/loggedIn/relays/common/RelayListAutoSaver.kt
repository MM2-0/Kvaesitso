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

import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pacing for a relay-list editor that signs and publishes as the user edits, with no Save button.
 *
 * Edits are debounced so a drag that crosses several slots, or a "Default relays" reset that
 * clears and re-adds every entry, publishes one event instead of one per step. A list equal to
 * [persisted] (a drag dropped where it started, a relay added and removed again) is not published
 * at all. Saves run one at a time and each starts in a later second than the previous one
 * finished: these lists are replaceable events, and two versions sharing a `created_at`
 * tie-break on id, so the newer list could lose to the older one.
 *
 * [T] is the editor's snapshot of what it publishes, compared with `==`.
 */
class RelayListAutoSaver<T>(
    private val scope: CoroutineScope,
) {
    private var pending: Job? = null
    private val saving = Mutex()
    private var lastSavedAt = 0L
    private val inFlight = MutableStateFlow(0)

    /**
     * The list the account holds, as far as this editor knows: set when the editor loads it from
     * the account, and after each save that went through. A failed save leaves it alone, so the
     * next edit publishes the whole list again.
     */
    var persisted: T? = null

    /** No edit is waiting for its quiet period and no save is queued or being signed. */
    val isIdle: Boolean get() = pending == null && inFlight.value == 0

    /** (Re)starts the quiet period; [flush] runs once edits stop for [DEBOUNCE_MS]. */
    fun schedule(flush: () -> Unit) {
        pending?.cancel()
        pending =
            scope.launch {
                delay(DEBOUNCE_MS)
                pending = null
                flush()
            }
    }

    fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    /**
     * Publishes [snapshot] through [publish] unless it equals [persisted]. [launch] runs the work
     * on a scope that outlives the editor (the signer's); the save counts as in flight from this
     * call on, so [isIdle] covers the hop to that scope too.
     */
    fun save(
        snapshot: T,
        launch: (suspend () -> Unit) -> Unit,
        publish: suspend (T) -> Unit,
    ) {
        inFlight.update { it + 1 }
        launch {
            try {
                saving.withLock {
                    if (snapshot == persisted) return@withLock

                    val wait = lastSavedAt + 1 - TimeUtils.now()
                    if (wait > 0) delay(wait * 1000)
                    try {
                        publish(snapshot)
                        persisted = snapshot
                    } finally {
                        lastSavedAt = TimeUtils.now()
                    }
                }
            } finally {
                inFlight.update { it - 1 }
            }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 1000L
    }
}
