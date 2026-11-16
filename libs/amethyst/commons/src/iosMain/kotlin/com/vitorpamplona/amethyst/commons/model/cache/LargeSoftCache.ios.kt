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
import com.vitorpamplona.quartz.utils.cache.CacheCollectors
import com.vitorpamplona.quartz.utils.cache.ICacheBiConsumer
import com.vitorpamplona.quartz.utils.cache.ICacheOperations

/**
 * The iOS actual: a [SortedConcurrentMap] (a skip list with lock-free reads) of
 * [WeakReference]s, the same shape as the JVM's `ConcurrentSkipListMap` of weak
 * references. Kotlin/Native ships neither a sorted concurrent map nor a weak-valued
 * one, so the skip list is ours.
 *
 * Every operation is written once, over an optional key range, and both the unranged
 * and the `from`/`to` overloads delegate to it — so the ranged scans behind
 * `LargeSoftCacheAddressExt.kt` walk only their sub-range, as on the JVM. Like the
 * JVM actual, a scan that meets a collected referent drops that entry.
 */
actual class LargeSoftCache<K : Any, V : Any> : ICacheOperations<K, V> {
    private val cache = SortedConcurrentMap<K, WeakReference<V>>()

    /** Visits the live entries in key order within `[from, to]`; a null bound is open. */
    private inline fun scan(
        from: K?,
        to: K?,
        crossinline action: (K, V) -> Unit,
    ) {
        cache.forEachInRange(from, to) { key, ref ->
            val value = ref.get()
            if (value == null) {
                cache.remove(key, ref)
            } else {
                action(key, value)
            }
        }
    }

    actual fun keys(): Set<K> {
        val results = LinkedHashSet<K>()
        scan(null, null) { key, _ -> results.add(key) }
        return results
    }

    actual fun get(key: K): V? {
        val ref = cache.get(key) ?: return null
        val value = ref.get()
        if (value == null) cache.remove(key, ref)
        return value
    }

    actual fun remove(key: K) {
        cache.remove(key)
    }

    actual fun removeIf(
        key: K,
        value: WeakReference<V>,
    ): Boolean = cache.remove(key, value)

    actual fun isEmpty(): Boolean = cache.isEmpty()

    actual fun clear() {
        cache.clear()
    }

    actual fun containsKey(key: K): Boolean = cache.containsKey(key)

    actual fun put(
        key: K,
        value: V,
    ) {
        cache.put(key, WeakReference(value))
    }

    /**
     * [builder] runs outside the lock, at most once. The insert loops because the entry
     * that beat us to [key] may itself already be collected: returning [newObject]
     * without storing it would let the next caller build a second live instance.
     */
    actual fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V {
        val ref = cache.get(key)
        if (ref != null) {
            val value = ref.get()
            if (value != null) return value
            cache.remove(key, ref)
        }
        val newObject = builder(key)
        val newRef = WeakReference(newObject)
        while (true) {
            val existing = cache.putIfAbsent(key, newRef) ?: return newObject
            existing.get()?.let { return it }
            cache.remove(key, existing)
        }
    }

    actual fun cleanUp() {
        cache.forEachInRange(null, null) { key, ref ->
            if (ref.get() == null) cache.remove(key, ref)
        }
    }

    actual override fun size(): Int = cache.size()

    actual override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        scan(null, null) { key, value -> consumer.accept(key, value) }
    }

    // Range-parameterized bodies. The public overloads below pass null bounds or their own.

    private fun filterIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): List<V> {
        val results = ArrayList<V>()
        scan(from, to) { key, value -> if (consumer.filter(key, value)) results.add(value) }
        return results
    }

    private fun filterIntoSetIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Set<V> {
        val results = LinkedHashSet<V>()
        scan(from, to) { key, value -> if (consumer.filter(key, value)) results.add(value) }
        return results
    }

    private fun <R> mapIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): List<R> {
        val results = ArrayList<R>()
        scan(from, to) { key, value -> results.add(consumer.map(key, value)) }
        return results
    }

    private fun <R> mapNotNullIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): List<R> {
        val results = ArrayList<R>()
        scan(from, to) { key, value -> consumer.map(key, value)?.let { results.add(it) } }
        return results
    }

    private fun <R> mapNotNullIntoSetIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): Set<R> {
        val results = LinkedHashSet<R>()
        scan(from, to) { key, value -> consumer.map(key, value)?.let { results.add(it) } }
        return results
    }

    private fun <R> mapFlattenIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): List<R> {
        val results = ArrayList<R>()
        scan(from, to) { key, value -> consumer.map(key, value)?.let { results.addAll(it) } }
        return results
    }

    private fun <R> mapFlattenIntoSetIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): Set<R> {
        val results = LinkedHashSet<R>()
        scan(from, to) { key, value -> consumer.map(key, value)?.let { results.addAll(it) } }
        return results
    }

    private fun maxOrNullOfIn(
        from: K?,
        to: K?,
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
        var maxV: V? = null
        scan(from, to) { key, value ->
            if (filter.filter(key, value)) {
                val current = maxV
                if (current == null || comparator.compare(value, current) > 0) {
                    maxV = value
                }
            }
        }
        return maxV
    }

    private fun sumOfIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiSumOf<K, V>,
    ): Int {
        var sum = 0
        scan(from, to) { key, value -> sum += consumer.map(key, value) }
        return sum
    }

    private fun sumOfLongIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiSumOfLong<K, V>,
    ): Long {
        var sum = 0L
        scan(from, to) { key, value -> sum += consumer.map(key, value) }
        return sum
    }

    private fun <R> groupByIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, List<V>> {
        val results = HashMap<R, ArrayList<V>>()
        scan(from, to) { key, value ->
            results.getOrPut(consumer.map(key, value)) { ArrayList() }.add(value)
        }
        return results
    }

    private fun <R> countByGroupIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, Int> {
        val results = HashMap<R, Int>()
        scan(from, to) { key, value ->
            val group = consumer.map(key, value)
            results[group] = (results[group] ?: 0) + 1
        }
        return results
    }

    private fun <R> sumByGroupIn(
        from: K?,
        to: K?,
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> {
        val results = HashMap<R, Long>()
        scan(from, to) { key, value ->
            val group = groupMap.map(key, value)
            results[group] = (results[group] ?: 0L) + sumOf.map(key, value)
        }
        return results
    }

    private fun countIn(
        from: K?,
        to: K?,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Int {
        var count = 0
        scan(from, to) { key, value -> if (consumer.filter(key, value)) count++ }
        return count
    }

    private fun <T, U> associateIn(
        from: K?,
        to: K?,
        transform: (K, V) -> Pair<T, U>,
    ): Map<T, U> {
        val results = LinkedHashMap<T, U>()
        scan(from, to) { key, value ->
            val pair = transform(key, value)
            results[pair.first] = pair.second
        }
        return results
    }

    private fun <U> associateWithIn(
        from: K?,
        to: K?,
        transform: (K, V) -> U?,
    ): Map<K, U?> {
        val results = LinkedHashMap<K, U?>()
        scan(from, to) { key, value -> results[key] = transform(key, value) }
        return results
    }

    actual override fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V> = filterIn(null, null, consumer)

    actual override fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> = filterIntoSetIn(null, null, consumer)

    actual override fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R> = mapIn(null, null, consumer)

    actual override fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> = mapNotNullIn(null, null, consumer)

    actual override fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R> = mapNotNullIntoSetIn(null, null, consumer)

    actual override fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R> = mapFlattenIn(null, null, consumer)

    actual override fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R> = mapFlattenIntoSetIn(null, null, consumer)

    actual override fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? = maxOrNullOfIn(null, null, filter, comparator)

    actual override fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int = sumOfIn(null, null, consumer)

    actual override fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long = sumOfLongIn(null, null, consumer)

    actual override fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> = groupByIn(null, null, consumer)

    actual override fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> = countByGroupIn(null, null, consumer)

    actual override fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> = sumByGroupIn(null, null, groupMap, sumOf)

    actual override fun count(consumer: CacheCollectors.BiFilter<K, V>): Int = countIn(null, null, consumer)

    actual override fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> = associateIn(null, null, transform)

    actual override fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> = associateWithIn(null, null, transform)

    actual override fun filter(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): List<V> = filterIn(from, to, consumer)

    actual override fun filterIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Set<V> = filterIntoSetIn(from, to, consumer)

    actual override fun <R> map(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): List<R> = mapIn(from, to, consumer)

    actual override fun <R> mapNotNull(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): List<R> = mapNotNullIn(from, to, consumer)

    actual override fun <R> mapNotNullIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): Set<R> = mapNotNullIntoSetIn(from, to, consumer)

    actual override fun <R> mapFlatten(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): List<R> = mapFlattenIn(from, to, consumer)

    actual override fun <R> mapFlattenIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): Set<R> = mapFlattenIntoSetIn(from, to, consumer)

    actual override fun maxOrNullOf(
        from: K,
        to: K,
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? = maxOrNullOfIn(from, to, filter, comparator)

    actual override fun sumOf(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOf<K, V>,
    ): Int = sumOfIn(from, to, consumer)

    actual override fun sumOfLong(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOfLong<K, V>,
    ): Long = sumOfLongIn(from, to, consumer)

    actual override fun <R> groupBy(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, List<V>> = groupByIn(from, to, consumer)

    actual override fun <R> countByGroup(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, Int> = countByGroupIn(from, to, consumer)

    actual override fun <R> sumByGroup(
        from: K,
        to: K,
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> = sumByGroupIn(from, to, groupMap, sumOf)

    actual override fun count(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Int = countIn(from, to, consumer)

    actual override fun <T, U> associate(
        from: K,
        to: K,
        transform: (K, V) -> Pair<T, U>,
    ): Map<T, U> = associateIn(from, to, transform)

    actual override fun <U> associateWith(
        from: K,
        to: K,
        transform: (K, V) -> U?,
    ): Map<K, U?> = associateWithIn(from, to, transform)

    actual override fun joinToString(
        separator: CharSequence,
        prefix: CharSequence,
        postfix: CharSequence,
        limit: Int,
        truncated: CharSequence,
        transform: ((K, V) -> CharSequence)?,
    ): String {
        val buffer = StringBuilder()
        buffer.append(prefix)
        var count = 0
        scan(null, null) { key, value ->
            val str = if (transform != null) transform(key, value) else ""
            if (str.isNotEmpty()) {
                if (++count > 1) buffer.append(separator)
                if (limit < 0 || count <= limit) {
                    when {
                        transform != null -> buffer.append(str)
                        else -> buffer.append("$key $value")
                    }
                }
            }
        }
        if (limit >= 0 && count > limit) buffer.append(truncated)
        buffer.append(postfix)
        return buffer.toString()
    }
}
