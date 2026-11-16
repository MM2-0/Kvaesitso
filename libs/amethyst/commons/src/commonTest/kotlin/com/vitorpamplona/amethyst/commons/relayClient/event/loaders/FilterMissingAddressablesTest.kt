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
package com.vitorpamplona.amethyst.commons.relayClient.event.loaders

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterMissingAddressablesTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://one.example")!!
    private val author = "a".repeat(64)
    private val other = "b".repeat(64)

    private fun sections(
        count: Int,
        pubkey: String = author,
        kind: Int = 30041,
    ) = (1..count).map { Address(kind, pubkey, "chapter-$it") }.toSet()

    @Test
    fun oneAuthorAndKindCollapseIntoASingleFilter() {
        val filters = filterMissingAddressables(mapOf(relay to sections(34)))

        assertEquals(1, filters.size)
        val filter = filters.single().filter
        assertEquals(listOf(30041), filter.kinds)
        assertEquals(listOf(author), filter.authors)
        assertEquals(34, filter.tags?.get("d")?.size)
        // Replaceable: the relay holds exactly one event per coordinate, so that is the ceiling.
        assertEquals(34, filter.limit)
    }

    @Test
    fun aBookLargerThanTheChunkSplitsRatherThanTruncating() {
        val filters = filterMissingAddressables(mapOf(relay to sections(240)))

        assertEquals(3, filters.size)
        assertEquals(listOf(100, 100, 40), filters.map { it.filter.tags!!["d"]!!.size })
        // Every coordinate asked for survives the split.
        assertEquals(240, filters.flatMap { it.filter.tags!!["d"]!! }.distinct().size)
    }

    @Test
    fun differentAuthorsAndKindsStaySeparate() {
        val mixed = sections(2) + sections(2, pubkey = other) + sections(2, kind = 30040)

        val filters = filterMissingAddressables(mapOf(relay to mixed))

        assertEquals(3, filters.size)
        assertTrue(filters.all { it.filter.authors!!.size == 1 })
    }

    @Test
    fun aReplaceableWithNoIdentifierIsAskedByKindAndAuthor() {
        val filters = filterMissingAddressables(mapOf(relay to setOf(Address(10002, author, ""))))

        val filter = filters.single().filter
        assertEquals(listOf(10002), filter.kinds)
        assertEquals(listOf(author), filter.authors)
        assertEquals(null, filter.tags?.get("d"))
        assertEquals(1, filter.limit)
    }

    @Test
    fun aThreadLargerThanTheChunkSplitsItsIdsToo() {
        val ids = (1..250).map { it.toString().padStart(64, '0') }.toSet()

        val filters = filterMissingEvents(mapOf(relay to ids))

        assertEquals(3, filters.size)
        assertEquals(listOf(100, 100, 50), filters.map { it.filter.ids!!.size })
        assertEquals(250, filters.flatMap { it.filter.ids!! }.distinct().size)
    }

    @Test
    fun noEventIdsMeansNoFilters() {
        assertEquals(emptyList(), filterMissingEvents(emptyMap()))
    }

    @Test
    fun noAddressesMeansNoFilters() {
        assertEquals(emptyList(), filterMissingAddressables(emptyMap()))
    }
}
