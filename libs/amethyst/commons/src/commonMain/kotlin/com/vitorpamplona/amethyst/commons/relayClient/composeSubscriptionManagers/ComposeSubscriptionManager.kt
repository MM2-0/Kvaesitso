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
package com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock

/**
 *  This allows composables to directly register their queries
 *  to relays. There may be multiple duplications in these
 *  subscriptions since we do not control when screens are removed.
 */
@Stable
abstract class ComposeSubscriptionManager<T> :
    ComposeSubscriptionManagerControls,
    Subscribable<T> {
    // T has no Comparable bound — see the note in MutableComposeSubscriptionManager
    // for why LargeCache isn't used here.
    private val lock = KmpLock()
    private val composeSubscriptions = mutableMapOf<T, T>()

    // This is called by main. Keep it really fast.
    override fun subscribe(query: T?) {
        if (query == null) return

        lock.withLock { composeSubscriptions[query] = query }

        invalidateKeys()
    }

    // This is called by main. Keep it really fast.
    override fun unsubscribe(query: T?) {
        if (query == null) return

        lock.withLock { composeSubscriptions.remove(query) }

        invalidateKeys()
    }

    override fun subscribe(query: List<T>) {
        if (query.isEmpty()) return

        lock.withLock {
            query.forEach { composeSubscriptions[it] = it }
        }

        invalidateKeys()
    }

    // This is called by main. Keep it really fast.
    override fun unsubscribe(query: List<T>) {
        if (query.isEmpty()) return

        lock.withLock {
            query.forEach { composeSubscriptions.remove(it) }
        }

        invalidateKeys()
    }

    fun allKeys(): Set<T> = lock.withLock { composeSubscriptions.keys.toSet() }
}

interface Subscribable<T> {
    // This is called by main. Keep it really fast.
    fun subscribe(query: T?)

    fun unsubscribe(query: T?)

    fun subscribe(query: List<T>)

    fun unsubscribe(query: List<T>)
}
