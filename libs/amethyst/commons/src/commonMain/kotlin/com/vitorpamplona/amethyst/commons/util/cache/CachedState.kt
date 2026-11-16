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
package com.vitorpamplona.amethyst.commons.util.cache

import androidx.collection.LruCache

/** A suspend-computed value per key, with the cached hit available without suspending. */
interface CachedState<K : Any, V : Any> {
    fun cached(k: K): V?

    suspend fun update(k: K): V?
}

abstract class GenericBaseCache<K : Any, V : Any>(
    capacity: Int,
) : CachedState<K, V> {
    private val cache = LruCache<K, V>(capacity)

    override fun cached(k: K): V? = cache[k]

    override suspend fun update(k: K): V? {
        cache[k]?.let { return it }

        val v = compute(k)

        if (v != null) {
            cache.put(k, v)
        }

        return v
    }

    /** Stores [v] for subclasses that also offer a synchronous load next to [update]. */
    protected fun put(
        k: K,
        v: V,
    ) {
        cache.put(k, v)
    }

    abstract suspend fun compute(key: K): V?
}

interface AsyncCachedState<K : Any, V : Any> {
    fun cached(k: K): V?

    suspend fun update(k: K): V?
}

abstract class GenericBaseCacheAsync<K : Any, V : Any>(
    capacity: Int,
) : AsyncCachedState<K, V> {
    private val cache = LruCache<K, V>(capacity)

    override fun cached(k: K): V? = cache[k]

    override suspend fun update(k: K): V? {
        cache[k]?.let { return it }

        val newValue = compute(k)

        if (newValue != null) {
            cache.put(k, newValue)
        }

        return newValue
    }

    abstract suspend fun compute(key: K): V?
}
