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
 * A **sorted**, thread-safe cache whose values are held by [WeakReference], so an
 * entry no other part of the app references is free to be collected.
 *
 * Two properties are load-bearing and any `actual` must preserve both:
 *
 * 1. **Weak values.** The cache alone never keeps an object alive. Callers that
 *    need a value to survive must hold it themselves — see [getOrCreate], which
 *    returns the live instance and re-creates it after a collection.
 * 2. **Key ordering.** Iteration follows the natural order of [K], and the
 *    ranged `from`/`to` operations inherited from [ICacheOperations] (the
 *    `filter(from, to, …)` family behind `LargeSoftCacheAddressExt.kt`) must scan
 *    only that sub-range. Backing this with a hash map would still *compile*,
 *    but would silently turn every bounded scan — the kind lookups the feeds
 *    run constantly — into a full scan of every entry.
 *
 * The JVM/Android `actual` is a `ConcurrentSkipListMap` of weak references,
 * which gives both for free. iOS has neither a sorted concurrent map nor a
 * weak-valued one in its standard library, so its actual is a
 * [SortedConcurrentMap] (our own skip list) of weak references — see
 * `LargeSoftCache.ios.kt`.
 */
expect class LargeSoftCache<K : Any, V : Any>() : ICacheOperations<K, V> {
    fun keys(): Set<K>

    fun get(key: K): V?

    fun remove(key: K)

    /** Removes [key] only while it still maps to [value]; returns true if it did. */
    fun removeIf(
        key: K,
        value: WeakReference<V>,
    ): Boolean

    fun isEmpty(): Boolean

    fun clear()

    fun containsKey(key: K): Boolean

    /** Puts [value] under [key], held weakly. */
    fun put(
        key: K,
        value: V,
    )

    /**
     * Returns the live value for [key], building and caching a new one when the
     * key is absent or its referent has already been collected.
     */
    fun getOrCreate(
        key: K,
        builder: (key: K) -> V,
    ): V

    /** Drops every entry whose referent has already been collected. */
    fun cleanUp()

    // Read-side operations inherited from ICacheOperations. An expect class has
    // to declare the abstract members it inherits, same as quartz's LargeCache.
    override fun size(): Int

    override fun forEach(consumer: ICacheBiConsumer<K, V>): Unit

    override fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V>

    override fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V>

    override fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R>

    override fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R>

    override fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R>

    override fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R>

    override fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R>

    override fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V?

    override fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int

    override fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long

    override fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>>

    override fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int>

    override fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long>

    override fun count(consumer: CacheCollectors.BiFilter<K, V>): Int

    override fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U>

    override fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?>

    override fun filter(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): List<V>

    override fun filterIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Set<V>

    override fun <R> map(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): List<R>

    override fun <R> mapNotNull(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): List<R>

    override fun <R> mapNotNullIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): Set<R>

    override fun <R> mapFlatten(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): List<R>

    override fun <R> mapFlattenIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): Set<R>

    override fun maxOrNullOf(
        from: K,
        to: K,
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V?

    override fun sumOf(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOf<K, V>,
    ): Int

    override fun sumOfLong(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOfLong<K, V>,
    ): Long

    override fun <R> groupBy(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, List<V>>

    override fun <R> countByGroup(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, Int>

    override fun <R> sumByGroup(
        from: K,
        to: K,
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long>

    override fun count(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Int

    override fun <T, U> associate(
        from: K,
        to: K,
        transform: (K, V) -> Pair<T, U>,
    ): Map<T, U>

    override fun <U> associateWith(
        from: K,
        to: K,
        transform: (K, V) -> U?,
    ): Map<K, U?>

    override fun joinToString(
        separator: CharSequence,
        prefix: CharSequence,
        postfix: CharSequence,
        limit: Int,
        truncated: CharSequence,
        transform: ((K, V) -> CharSequence)?,
    ): String
}
