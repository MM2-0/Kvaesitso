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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Events of a kind quartz has no class for are kept so the fallback card can draw them: a regular
 * one by its id, a replaceable or addressable one at its address (newest version wins), and an
 * ephemeral one not at all.
 */
class EventCacheUnknownKindTest {
    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    private fun event(
        idSeed: Char,
        kind: Int,
        createdAt: Long,
        tags: Array<Array<String>> = emptyArray(),
    ): Event = EventFactory.create(idSeed.toString().repeat(64), alice, createdAt, kind, tags, "", "0".repeat(128))

    @Test
    fun regularUnknownKindIsKeptById() {
        val cache = EventCache()
        val e = event('1', 4_242, 1_000)
        assertTrue(cache.justConsume(e, null, true))
        assertEquals(e, cache.getNoteIfExists(e.id)?.event)
    }

    @Test
    fun addressableUnknownKindLivesAtItsAddressAndTheNewestWins() {
        val cache = EventCache()
        val v1 = event('1', 35_123, 1_000, arrayOf(arrayOf("d", "bread")))
        val v2 = event('2', 35_123, 2_000, arrayOf(arrayOf("d", "bread")))
        cache.justConsume(v2, null, true)
        cache.justConsume(v1, null, true)

        val address = Address(35_123, alice, "bread")
        assertEquals(v2.id, cache.getAddressableNoteIfExists(address)?.event?.id)
        // Each version is still reachable by its id, as an nevent link would ask for it.
        assertEquals(v1.id, cache.getNoteIfExists(v1.id)?.event?.id)
    }

    @Test
    fun replaceableUnknownKindLivesAtItsAddress() {
        val cache = EventCache()
        val e = event('3', 17_777, 1_000)
        cache.justConsume(e, null, true)
        assertEquals(e.id, cache.getAddressableNoteIfExists(Address(17_777, alice, ""))?.event?.id)
    }

    @Test
    fun ephemeralUnknownKindIsNotKept() {
        val cache = EventCache()
        val e = event('4', 24_242, 1_000)
        assertFalse(cache.justConsume(e, null, true))
        assertNull(cache.getNoteIfExists(e.id)?.event)
    }
}
