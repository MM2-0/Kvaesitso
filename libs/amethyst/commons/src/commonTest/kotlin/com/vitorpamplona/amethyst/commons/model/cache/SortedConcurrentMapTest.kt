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

import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SortedConcurrentMapTest {
    private fun SortedConcurrentMap<String, String>.entries(
        from: String? = null,
        to: String? = null,
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        forEachInRange(from, to) { k, v -> out.add(k to v) }
        return out
    }

    @Test
    fun iteratesInKeyOrder() {
        val map = SortedConcurrentMap<String, String>()
        listOf("m", "c", "x", "a", "q").forEach { map.put(it, it.uppercase()) }

        assertEquals(listOf("a", "c", "m", "q", "x"), map.entries().map { it.first })
        assertEquals(5, map.size())
    }

    @Test
    fun rangeIsInclusiveOnBothEnds() {
        val map = SortedConcurrentMap<String, String>()
        listOf("a", "b", "c", "d", "e").forEach { map.put(it, it) }

        assertEquals(listOf("b", "c", "d"), map.entries("b", "d").map { it.first })
        assertEquals(listOf("c", "d"), map.entries("bb", "dd").map { it.first })
        assertEquals(emptyList(), map.entries("f", "z"))
    }

    @Test
    fun overwriteKeepsOneEntry() {
        val map = SortedConcurrentMap<String, String>()
        map.put("k", "1")
        map.put("k", "2")

        assertEquals("2", map.get("k"))
        assertEquals(1, map.size())
    }

    @Test
    fun putIfAbsentReturnsTheWinner() {
        val map = SortedConcurrentMap<String, String>()

        assertNull(map.putIfAbsent("k", "first"))
        assertEquals("first", map.putIfAbsent("k", "second"))
        assertEquals("first", map.get("k"))
    }

    @Test
    fun conditionalRemoveComparesByIdentity() {
        val map = SortedConcurrentMap<String, StringBuilder>()
        val stored = StringBuilder("v")
        map.put("k", stored)

        assertFalse(map.remove("k", StringBuilder("v")))
        assertTrue(map.containsKey("k"))
        assertTrue(map.remove("k", stored))
        assertFalse(map.containsKey("k"))
        assertTrue(map.isEmpty())
    }

    @Test
    fun clearEmptiesTheMapAndItStaysUsable() {
        val map = SortedConcurrentMap<String, String>()
        (0 until 100).forEach { map.put("k$it", "v") }
        map.clear()

        assertTrue(map.isEmpty())
        assertEquals(emptyList(), map.entries())
        map.put("again", "v")
        assertEquals(listOf("again"), map.entries().map { it.first })
    }

    @Test
    fun scanCallbackMayWriteBackIntoTheMap() {
        val map = SortedConcurrentMap<String, String>()
        (0 until 50).forEach { map.put("k" + it.toString().padStart(3, '0'), "v") }

        map.forEachInRange(null, null) { k, _ -> map.remove(k) }

        assertTrue(map.isEmpty())
    }

    @Test
    fun addressRangesSelectOneKind() {
        val map = SortedConcurrentMap<Address, String>()
        val pub = "a".repeat(64)
        map.put(Address(30023, pub, "post"), "article")
        map.put(Address(30000, pub, "list"), "list")
        map.put(Address(30023, pub, ""), "empty-d")
        map.put(Address(30024, pub, "draft"), "draft")

        val found = ArrayList<String>()
        map.forEachInRange(kindStart(30023), kindEnd(30023)) { _, v -> found.add(v) }

        assertEquals(setOf("article", "empty-d"), found.toSet())
    }

    @Test
    fun matchesAReferenceMapUnderRandomOperations() {
        val map = SortedConcurrentMap<String, String>()
        val reference = HashMap<String, String>()
        val random = Random(42)

        repeat(20_000) {
            val key = "k" + random.nextInt(2_000).toString().padStart(4, '0')
            when (random.nextInt(4)) {
                0, 1 -> {
                    val value = random.nextInt().toString()
                    map.put(key, value)
                    reference[key] = value
                }
                2 -> assertEquals(reference.remove(key), map.remove(key))
                else -> assertEquals(reference[key], map.get(key))
            }
        }

        assertEquals(reference.size, map.size())
        assertEquals(reference.entries.sortedBy { it.key }.map { it.key to it.value }, map.entries())

        val from = "k0500"
        val to = "k1499"
        val expected = reference.keys.filter { it >= from && it <= to }.sorted()
        assertEquals(expected, map.entries(from, to).map { it.first })
    }

    @Test
    fun concurrentWritersLoseNothingAndScansStaySorted() =
        runTest {
            val map = SortedConcurrentMap<String, String>()
            val writers = 4
            val perWriter = 2_000

            withContext(Dispatchers.Default) {
                val jobs =
                    (0 until writers).map { w ->
                        async {
                            repeat(perWriter) { i ->
                                map.put("w$w-" + i.toString().padStart(5, '0'), "v")
                            }
                        }
                    } +
                        (0 until 2).map {
                            async {
                                repeat(50) {
                                    var last: String? = null
                                    map.forEachInRange(null, null) { k, _ ->
                                        last?.let { prev -> assertTrue(prev < k, "$prev then $k") }
                                        last = k
                                    }
                                }
                            }
                        }
                jobs.awaitAll()
            }

            assertEquals(writers * perWriter, map.size())
            assertEquals(writers * perWriter, map.entries().size)
        }
}
