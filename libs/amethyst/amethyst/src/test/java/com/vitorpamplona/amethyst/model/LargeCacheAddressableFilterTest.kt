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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LargeSoftCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.cache.mapNotNullIntoSet
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import junit.framework.TestCase.assertEquals
import org.junit.Test

class LargeCacheAddressableFilterTest {
    companion object {
        fun mock(
            kind: Int,
            pubkey: HexKey,
            dTag: String = "",
        ) = AddressableNote(Address(kind, pubkey, dTag))

        // LargeSoftCache stores values as WeakReferences, so the cache alone does not keep
        // the mock notes alive. Without a strong referent they are cleared at the next GC
        // and every query returns 0 — an order/GC-dependent flake. This list is the fixture's
        // source of truth and holds them strongly for the lifetime of the test class.
        val notes =
            listOf(
                mock(32000, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad"),
                mock(32000, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43"),
                mock(32000, "3064bf97800a4b04b612fc0fd498936eae75fffbdca5bbd09d19a6dc598530ab"),
                mock(32000, "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a"),
                mock(32001, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad"),
                mock(32001, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad", "a"),
                mock(32001, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad", "z"),
                mock(32001, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad", "askldfjaljksdflkaj"),
                mock(32001, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43"),
                mock(32001, "3064bf97800a4b04b612fc0fd498936eae75fffbdca5bbd09d19a6dc598530ab"),
                mock(32001, "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a"),
                mock(32002, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad"),
                mock(32002, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43"),
                mock(32002, "3064bf97800a4b04b612fc0fd498936eae75fffbdca5bbd09d19a6dc598530ab"),
                mock(32002, "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a"),
                mock(32003, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad"),
                mock(32003, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43"),
                mock(32003, "3064bf97800a4b04b612fc0fd498936eae75fffbdca5bbd09d19a6dc598530ab"),
                mock(32003, "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a"),
            )

        val cache =
            LargeSoftCache<Address, AddressableNote>().apply {
                notes.forEach { put(it.address, it) }
            }
    }

    @Test
    fun survivesGarbageCollection() {
        // Regression guard for the flaky-fixture root cause: the mock notes must stay
        // reachable across a GC. Before `notes` held them strongly, the WeakReference-backed
        // entries were cleared here and every query returned 0.
        System.gc()

        assertEquals(notes.size, cache.filterIntoSet(listOf(32_000, 32_001, 32_002, 32_003)).size)
    }

    @Test
    fun filterIntoSet() {
        val query0 = cache.filterIntoSet(32_000)
        val query1 = cache.filterIntoSet(32_001)
        val query2 = cache.filterIntoSet(32_002)
        val query3 = cache.filterIntoSet(32_003)

        assertEquals(4, query0.size)
        assertEquals(7, query1.size)
        assertEquals(4, query2.size)
        assertEquals(4, query3.size)
    }

    @Test
    fun mapIntoSet() {
        val query0 = cache.mapNotNullIntoSet(32_000) { key, note -> note }
        val query1 = cache.mapNotNullIntoSet(32_001) { key, note -> note }
        val query2 = cache.mapNotNullIntoSet(32_002) { key, note -> note }
        val query3 = cache.mapNotNullIntoSet(32_003) { key, note -> note }

        assertEquals(4, query0.size)
        assertEquals(7, query1.size)
        assertEquals(4, query2.size)
        assertEquals(4, query3.size)
    }

    @Test
    fun filterListIntoSet() {
        val query = cache.filterIntoSet(listOf(32_000, 32_001, 32_002, 32_003))

        assertEquals(19, query.size)

        val query2 = cache.filterIntoSet(listOf(32_000, 32_002, 32_003))

        assertEquals(12, query2.size)

        val query3 = cache.filterIntoSet(listOf(32_000, 32_001, 32_003))

        assertEquals(15, query3.size)
    }

    @Test
    fun filterKindKeyIntoSet() {
        val query0 = cache.filterIntoSet(32_000, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43")
        val query1 = cache.filterIntoSet(32_001, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43")
        val query2 = cache.filterIntoSet(32_002, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43")
        val query3 = cache.filterIntoSet(32_003, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43")

        assertEquals(1, query0.size)
        assertEquals(1, query1.size)
        assertEquals(1, query2.size)
        assertEquals(1, query3.size)

        val query4 = cache.filterIntoSet(32_000, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad")
        val query5 = cache.filterIntoSet(32_001, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad")
        val query6 = cache.filterIntoSet(32_002, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad")
        val query7 = cache.filterIntoSet(32_003, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad")

        assertEquals(1, query4.size)
        assertEquals(4, query5.size)
        assertEquals(1, query6.size)
        assertEquals(1, query7.size)
    }

    @Test
    fun mapKindKeyIntoSet() {
        val query0 = cache.mapNotNullIntoSet(32_000, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43") { key, note -> note }
        val query1 = cache.mapNotNullIntoSet(32_001, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43") { key, note -> note }
        val query2 = cache.mapNotNullIntoSet(32_002, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43") { key, note -> note }
        val query3 = cache.mapNotNullIntoSet(32_003, "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43") { key, note -> note }

        assertEquals(1, query0.size)
        assertEquals(1, query1.size)
        assertEquals(1, query2.size)
        assertEquals(1, query3.size)

        val query4 = cache.mapNotNullIntoSet(32_000, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad") { key, note -> note }
        val query5 = cache.mapNotNullIntoSet(32_001, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad") { key, note -> note }
        val query6 = cache.mapNotNullIntoSet(32_002, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad") { key, note -> note }
        val query7 = cache.mapNotNullIntoSet(32_003, "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad") { key, note -> note }

        assertEquals(1, query4.size)
        assertEquals(4, query5.size)
        assertEquals(1, query6.size)
        assertEquals(1, query7.size)
    }
}
