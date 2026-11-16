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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 *  This allows composables to directly register their queries
 *  to relays. There may be multiple duplications in these
 *  subscriptions since we do not control when screens are removed.
 *
 *  This is similar to QueryBasedSubscriptionOrchestrator, but it
 *  also allows the subscription itself to change over time as a
 *  flow, which trigger an update on the relay subscriptions
 */
@Stable
abstract class MutableComposeSubscriptionManager<T : MutableQueryState>(
    val scope: CoroutineScope,
) : ComposeSubscriptionManagerControls {
    // T is generic and not required to be Comparable, so this can't use
    // LargeCache (ConcurrentSkipListMap-backed on JVM). A plain map guarded by
    // KmpLock gives the same atomicity guarantees ConcurrentHashMap did
    // previously, with no Comparable requirement.
    private val lock = KmpLock()
    private val composeSubscriptions = mutableMapOf<T, Job>()

    // This is called by main. Keep it really fast.
    fun subscribe(query: T?) {
        if (query == null) return

        lock.withLock {
            composeSubscriptions[query]?.cancel()
            composeSubscriptions[query] =
                scope.launch {
                    query.flow().collectLatest {
                        invalidateKeys()
                    }
                }
        }

        invalidateKeys()
    }

    // This is called by main. Keep it really fast.
    fun unsubscribe(query: T?) {
        if (query == null) return

        lock.withLock {
            composeSubscriptions[query]?.cancel()
            composeSubscriptions.remove(query)
        }

        invalidateKeys()
    }

    fun allKeys(): Set<T> = lock.withLock { composeSubscriptions.keys.toSet() }

    fun forEachSubscriber(action: (T) -> Unit) {
        val snapshot = lock.withLock { composeSubscriptions.keys.toList() }
        snapshot.forEach(action)
    }
}

interface MutableQueryState {
    fun flow(): Flow<*>
}
