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

import com.vitorpamplona.amethyst.commons.util.WeakReference
import com.vitorpamplona.quartz.utils.cache.CacheOperations
import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.BiConsumer

/**
 * A `ConcurrentSkipListMap` gives the two properties the expect declares in one
 * structure: keys stay in natural order (so `subMap` serves the ranged scans)
 * and reads are lock-free.
 */
actual class LargeSoftCache<K : Any, V : Any> : CacheOperations<K, V> {
    private val cache = ConcurrentSkipListMap<K, WeakReference<V>>()

    actual fun keys(): Set<K> = cache.keys

    actual fun get(key: K): V? {
        val softRef = cache.get(key) ?: return null
        val value = softRef.get()

        return if (value != null) {
            value
        } else {
            cache.remove(key, softRef)
            null
        }
    }

    actual fun remove(key: K) {
        cache.remove(key)
    }

    actual fun removeIf(
        key: K,
        value: WeakReference<V>,
    ): Boolean = cache.remove(key, value)

    actual override fun size() = cache.size

    actual fun isEmpty(): Boolean = cache.isEmpty()

    actual fun clear() {
        cache.clear()
    }

    actual fun containsKey(key: K): Boolean = cache.containsKey(key)

    /**
     * Puts an object into the cache with a specified key.
     * The object is stored as a WeakReference.
     *
     * @param key The key to associate with the object.
     * @param value The object to cache.
     */
    actual fun put(
        key: K,
        value: V,
    ) {
        cache.put(key, WeakReference(value))
    }

    /**
     * Retrieves an object from the cache using its key.
     * Returns the object if it's still available (not garbage collected),
     * otherwise returns null. If the object has been garbage collected,
     * its entry is also removed from the cache.
     *
     * @param key The key of the object to retrieve.
     * @return The cached object, or null if it's no longer available.
     */
    actual fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V {
        val softRef = cache[key]

        return if (softRef == null) {
            val newObject = builder(key)
            cache.putIfAbsent(key, WeakReference(newObject))?.get() ?: newObject
        } else {
            val value = softRef.get()
            if (value != null) {
                value
            } else {
                // removes first to make sure the putIfAbsent works.
                // another thread may put in between
                cache.remove(key, softRef)
                val newObject = builder(key)
                cache.putIfAbsent(key, WeakReference(newObject))?.get() ?: newObject
            }
        }
    }

    /**
     * Proactively cleans up the cache by removing entries whose weakly referenced
     * objects have been garbage collected. Single-pass iterator for efficiency.
     */
    actual fun cleanUp() {
        val iter = cache.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.get() == null) {
                iter.remove()
            }
        }
    }

    override fun forEach(consumer: BiConsumer<K, V>) {
        cache.forEach(BiConsumerWrapper(this, consumer))
    }

    override fun forEach(
        from: K,
        to: K,
        consumer: BiConsumer<K, V>,
    ) {
        cache
            .subMap(from, true, to, true)
            .forEach(BiConsumerWrapper(this, consumer))
    }

    private class BiConsumerWrapper<K : Any, V : Any>(
        val cache: LargeSoftCache<K, V>,
        val inner: BiConsumer<K, V>,
    ) : BiConsumer<K, WeakReference<V>> {
        override fun accept(
            k: K,
            ref: WeakReference<V>,
        ) {
            val value = ref.get()
            if (value == null) {
                cache.removeIf(k, ref)
            } else {
                inner.accept(k, value)
            }
        }
    }
}
