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
package com.vitorpamplona.amethyst.commons.service.pow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoWPolicyTest {
    val allCategories = PoWCategory.entries.toSet()

    @Test
    fun minerWorkersIsHalfTheCoresButNeverZero() {
        assertEquals(1, PoWPolicy.minerWorkers(1))
        assertEquals(1, PoWPolicy.minerWorkers(2))
        assertEquals(2, PoWPolicy.minerWorkers(4))
        assertEquals(3, PoWPolicy.minerWorkers(6))
        assertEquals(4, PoWPolicy.minerWorkers(8))
        assertEquals(1, PoWPolicy.minerWorkers(0))
    }

    @Test
    fun desktopMinerWorkersLeavesTwoCoresButNeverZero() {
        assertEquals(1, PoWPolicy.desktopMinerWorkers(0))
        assertEquals(1, PoWPolicy.desktopMinerWorkers(2))
        assertEquals(1, PoWPolicy.desktopMinerWorkers(3))
        assertEquals(6, PoWPolicy.desktopMinerWorkers(8))
        assertEquals(10, PoWPolicy.desktopMinerWorkers(12))
    }

    @Test
    fun difficultyOffMinesNothing() {
        assertNull(PoWPolicy.shouldMine(1, 0, allCategories))
        assertNull(PoWPolicy.shouldMine(1, -5, allCategories))
    }

    @Test
    fun defaultCategoriesMinePrimarySpamSurfaces() {
        val defaults = PoWCategory.DEFAULT_ENABLED
        // ON by default
        assertEquals(20, PoWPolicy.shouldMine(1, 20, defaults))
        assertEquals(20, PoWPolicy.shouldMine(1111, 20, defaults))
        assertEquals(20, PoWPolicy.shouldMine(1984, 20, defaults))
        assertEquals(20, PoWPolicy.shouldMine(30023, 20, defaults))
        assertEquals(20, PoWPolicy.shouldMine(9802, 20, defaults))
        assertEquals(20, PoWPolicy.shouldMine(1222, 20, defaults))
        assertEquals(20, PoWPolicy.shouldMine(1244, 20, defaults))
        // OFF by default (opt-in toggles)
        assertNull(PoWPolicy.shouldMine(6, 20, defaults))
        assertNull(PoWPolicy.shouldMine(16, 20, defaults))
        assertNull(PoWPolicy.shouldMine(7, 20, defaults))
        assertNull(PoWPolicy.shouldMine(42, 20, defaults))
        assertNull(PoWPolicy.shouldMine(1311, 20, defaults))
        assertNull(PoWPolicy.shouldMine(1059, 20, defaults))
        assertNull(PoWPolicy.shouldMine(1068, 20, defaults))
    }

    @Test
    fun optInCategoriesMineWhenEnabled() {
        assertEquals(16, PoWPolicy.shouldMine(7, 16, allCategories))
        assertEquals(16, PoWPolicy.shouldMine(6, 16, allCategories))
        assertEquals(16, PoWPolicy.shouldMine(1059, 16, allCategories))
        assertEquals(16, PoWPolicy.shouldMine(42, 16, allCategories))
        // long tail routes through OTHER_PUBLIC
        assertEquals(16, PoWPolicy.shouldMine(1068, 16, allCategories))
        assertEquals(16, PoWPolicy.shouldMine(30315, 16, allCategories))
    }

    @Test
    fun neverListWinsOverEverySetting() {
        val neverKinds =
            listOf(
                0, // metadata
                3, // contact list
                9734, // zap request
                1040, // OTS attestation
                31234, // NIP-37 draft wrap
                30024, // long-form draft
                22242, // relay auth
                13194, // NWC info
                23194, // NWC request
                23195, // NWC response
                23196, // NWC notification
                24133, // NIP-46 bunker
                27235, // HTTP auth
                24242, // Blossom auth
                10002, // relay list
                10000, // mute list
                30000, // follow sets
                30078, // app-specific data
            )

        neverKinds.forEach { kind ->
            assertNull(PoWPolicy.shouldMine(kind, 28, allCategories), "kind $kind must never be mined")
            assertTrue(PoWPolicy.neverMine(kind), "kind $kind must be in the NEVER list")
        }
    }

    @Test
    fun minedContentIsNotInTheNeverList() {
        listOf(1, 1111, 1984, 30023, 9802, 1222, 1244, 6, 16, 7, 42, 1311, 1059, 1068).forEach { kind ->
            assertTrue(!PoWPolicy.neverMine(kind), "kind $kind must be minable")
        }
    }

    @Test
    fun categoryIdsRoundTrip() {
        val ids = PoWCategory.entries.map { it.id }
        assertEquals(PoWCategory.entries.toSet(), PoWCategory.fromIds(ids))
        assertEquals(emptySet(), PoWCategory.fromIds(listOf("bogus")))
    }
}
