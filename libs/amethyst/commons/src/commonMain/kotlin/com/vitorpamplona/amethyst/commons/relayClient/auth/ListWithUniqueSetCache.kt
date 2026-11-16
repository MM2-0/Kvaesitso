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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ListWithUniqueSetCache<T, U>(
    val key: (T) -> U,
) {
    private val list = AtomicReference(listOf<T>())
    private val cacheSet = AtomicReference<Set<U>?>(setOf())

    fun isEmpty() = list.load().isEmpty()

    // Compare-and-set: screens add and remove on the main thread while notification workers do it
    // in the background. A plain load-then-store can let an add write back a list that still holds
    // an item another thread just removed, keeping that item (and the account it holds) forever.
    fun add(item: T) = update { it + item }

    fun remove(item: T) = update { it - item }

    private inline fun update(change: (List<T>) -> List<T>) {
        while (true) {
            val current = list.load()
            if (list.compareAndSet(current, change(current))) break
        }
        cacheSet.store(null)
    }

    fun set(newList: List<T>) {
        list.store(newList)
        // Invalidate the cache - next read will recompute
        cacheSet.store(null)
    }

    fun distinct(): Set<U> {
        val currentSet = cacheSet.load()

        // Check if the cached set is based on the current list
        if (currentSet != null) {
            return currentSet
        }

        // Compute and attempt to atomically update the cache
        val newSet = list.load().mapTo(mutableSetOf(), key)
        cacheSet.compareAndSet(currentSet, newSet)
        return newSet
    }

    /** One representative [T] per unique key — the first occurrence wins. */
    fun distinctValues(): List<T> {
        val seen = HashSet<U>()
        return list.load().filter { seen.add(key(it)) }
    }

    fun forEachSubscriber(action: (T) -> Unit) {
        list.load().forEach(action)
    }

    fun forEachUniqueSubscriber(action: (U) -> Unit) {
        distinct().forEach(action)
    }
}
