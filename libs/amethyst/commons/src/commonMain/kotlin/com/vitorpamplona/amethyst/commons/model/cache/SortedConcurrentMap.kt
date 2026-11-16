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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicArray
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A key-sorted map with **lock-free reads** and **serialized writes**: a skip list,
 * standing in for `java.util.concurrent.ConcurrentSkipListMap` on targets that do not
 * ship one (Kotlin/Native). It backs the iOS `LargeSoftCache`; it lives in commonMain
 * only so the JVM test suite can exercise it.
 *
 * Keys must implement [Comparable] against each other, exactly as
 * `ConcurrentSkipListMap` requires without a comparator; the cast is checked at use.
 *
 * ## Concurrency contract
 *
 * - **Readers never block.** [get], [containsKey], [size] and [forEachInRange] take no
 *   lock. Level 0 is the authoritative sorted list; upper levels are only shortcuts
 *   into it.
 * - **Writers take one lock**, held only for an O(log n) walk plus a few pointer
 *   stores. No caller code runs while it is held, so a scan's callback may write back
 *   into the map.
 * - A new node's own `next` links are all set before it is published, so a reader that
 *   reaches it always sees a well-formed tail.
 * - A removed node is unlinked but its own `next` links are never touched again, and
 *   its value is nulled first. A reader standing on it keeps walking forward through
 *   strictly greater keys and skips it, so iteration always terminates and stays in
 *   order.
 * - Iteration is **weakly consistent**, like `ConcurrentSkipListMap`'s: it may or may
 *   not observe writes that land while it runs, and never throws.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SortedConcurrentMap<K : Any, V : Any> {
    /** [key] is null only on the head sentinel. A null [value] marks a removed node. */
    private class Node<K, V>(
        val key: K?,
        @Volatile var value: V?,
        height: Int,
    ) {
        /**
         * Level 0 gets its own field rather than a slot in [upper]: every scan walks it,
         * and three nodes in four (p = 1/4) have no other level, so those allocate no
         * array at all.
         */
        @Volatile var next0: Node<K, V>? = null

        /** Levels `1 until height`; null on nodes of height 1. */
        val upper: AtomicArray<Node<K, V>?>? = if (height > 1) AtomicArray(height - 1) { null } else null

        val height: Int get() = (upper?.size ?: 0) + 1

        fun next(level: Int): Node<K, V>? = if (level == 0) next0 else upper!!.loadAt(level - 1)

        fun setNext(
            level: Int,
            node: Node<K, V>?,
        ) {
            if (level == 0) next0 = node else upper!!.storeAt(level - 1, node)
        }
    }

    private val head = Node<K, V>(null, null, MAX_HEIGHT)
    private val writeLock = KmpLock()

    // Written only under [writeLock].
    @Volatile private var count = 0

    @Volatile private var height = 1
    private var seed = 0x2545F491
    private val preds = arrayOfNulls<Node<K, V>>(MAX_HEIGHT)

    @Suppress("UNCHECKED_CAST")
    private fun compare(
        a: K,
        b: K,
    ): Int = (a as Comparable<K>).compareTo(b)

    /** The first node whose key is `>= key`, or null. Lock-free. */
    private fun ceilingNode(key: K): Node<K, V>? {
        var x = head
        var level = height - 1
        while (level >= 0) {
            var n = x.next(level)
            while (n != null && compare(n.key!!, key) < 0) {
                x = n
                n = x.next(level)
            }
            level--
        }
        return x.next0
    }

    private fun nodeFor(key: K): Node<K, V>? {
        val n = ceilingNode(key) ?: return null
        return if (compare(n.key!!, key) == 0) n else null
    }

    /**
     * Fills [preds] with the last node before [key] at every level. Levels at or above
     * [height] are empty, so their predecessor is [head]. Call under [writeLock].
     */
    private fun findPredecessors(key: K): Node<K, V>? {
        val top = height
        for (level in top until MAX_HEIGHT) preds[level] = head
        var x = head
        for (level in top - 1 downTo 0) {
            var n = x.next(level)
            while (n != null && compare(n.key!!, key) < 0) {
                x = n
                n = x.next(level)
            }
            preds[level] = x
        }
        val candidate = x.next0
        return if (candidate != null && compare(candidate.key!!, key) == 0) candidate else null
    }

    /** Xorshift32, under [writeLock]: each extra level has probability 1/4. */
    private fun randomHeight(): Int {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        var h = 1
        var bits = x
        while (h < MAX_HEIGHT && (bits and 3) == 0) {
            h++
            bits = bits ushr 2
        }
        return h
    }

    /** Links a new node after [preds]. Call under [writeLock], after [findPredecessors]. */
    private fun insert(
        key: K,
        value: V,
    ) {
        val h = randomHeight()
        val node = Node(key, value, h)
        for (level in 0 until h) {
            node.setNext(level, preds[level]!!.next(level))
        }
        for (level in 0 until h) {
            preds[level]!!.setNext(level, node)
        }
        if (h > height) height = h
        count++
    }

    /** Unlinks [node], found by [findPredecessors]. Call under [writeLock]. */
    private fun unlink(node: Node<K, V>) {
        node.value = null
        for (level in node.height - 1 downTo 0) {
            val pred = preds[level]!!
            if (pred.next(level) === node) {
                pred.setNext(level, node.next(level))
            }
        }
        count--
    }

    private fun clearPredecessors() {
        preds.fill(null)
    }

    fun size(): Int = count

    fun isEmpty(): Boolean = count == 0

    fun get(key: K): V? = nodeFor(key)?.value

    fun containsKey(key: K): Boolean = get(key) != null

    fun put(
        key: K,
        value: V,
    ) {
        writeLock.withLock {
            val existing = findPredecessors(key)
            if (existing != null) {
                existing.value = value
            } else {
                insert(key, value)
            }
            clearPredecessors()
        }
    }

    /** Inserts only when [key] is absent; returns the value already there, or null if this call inserted. */
    fun putIfAbsent(
        key: K,
        value: V,
    ): V? =
        writeLock.withLock {
            val existing = findPredecessors(key)
            val result =
                if (existing != null) {
                    existing.value
                } else {
                    insert(key, value)
                    null
                }
            clearPredecessors()
            result
        }

    fun remove(key: K): V? =
        writeLock.withLock {
            val existing = findPredecessors(key)
            val old = existing?.value
            if (existing != null) unlink(existing)
            clearPredecessors()
            old
        }

    /** Removes [key] only while it still maps to [expected] (by identity); returns true if it did. */
    fun remove(
        key: K,
        expected: V,
    ): Boolean =
        writeLock.withLock {
            val existing = findPredecessors(key)
            val matches = existing != null && existing.value === expected
            if (matches) unlink(existing)
            clearPredecessors()
            matches
        }

    fun clear() {
        writeLock.withLock {
            var n = head.next0
            while (n != null) {
                n.value = null
                n = n.next0
            }
            for (level in 0 until MAX_HEIGHT) head.setNext(level, null)
            height = 1
            count = 0
        }
    }

    /**
     * Visits, in ascending key order, every entry whose key is within `[from, to]`
     * (both inclusive, as `subMap(from, true, to, true)`); a null bound is open.
     * Lock-free: [action] may write back into this map.
     */
    fun forEachInRange(
        from: K?,
        to: K?,
        action: (K, V) -> Unit,
    ) {
        var n = if (from == null) head.next0 else ceilingNode(from)
        while (n != null) {
            val key = n.key!!
            if (to != null && compare(key, to) > 0) return
            val value = n.value
            if (value != null) action(key, value)
            n = n.next0
        }
    }

    private companion object {
        /** p = 1/4 per level: 16 levels index ~4^16 entries before the top saturates. */
        const val MAX_HEIGHT = 16
    }
}
