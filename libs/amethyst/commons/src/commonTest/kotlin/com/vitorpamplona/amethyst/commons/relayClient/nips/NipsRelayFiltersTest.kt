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
package com.vitorpamplona.amethyst.commons.relayClient.nips

import com.vitorpamplona.amethyst.commons.nipsOnNostr.definesKind
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NipsRelayFiltersTest {
    private val a = RelayUrlNormalizer.normalizeOrNull("wss://a.example.com")!!
    private val b = RelayUrlNormalizer.normalizeOrNull("wss://b.example.com")!!

    @Test
    fun oneFilterPerRelayResumingFromItsLastEose() {
        val spec = NipsQuerySpec(SubPurpose.TOPIC_FEED, listOf(30817), tags = mapOf("k" to listOf("12000")), limit = 50)
        val filters = nipsRelayFilters(spec, setOf(a, b), mutableMapOf(a to MutableTime(1_700_000_000)))
        assertEquals(setOf(a, b), filters.map { it.relay }.toSet())

        val onA = filters.first { it.relay == a }.filter
        assertEquals(1_700_000_000, onA.since, "a relay that answered before is asked only for what is newer")
        assertEquals(listOf(30817), onA.kinds)
        assertEquals(mapOf("k" to listOf("12000")), onA.tags)
        assertEquals(50, onA.limit)

        assertNull(filters.first { it.relay == b }.filter.since, "a relay never asked has no window: NIPs are long-lived")
    }

    @Test
    fun definesKindReadsKTags() {
        val tags = arrayOf(arrayOf("d", "x"), arrayOf("k", " 30092 "), arrayOf("k", "abc"))
        assertTrue(definesKind(tags, 30092))
        assertFalse(definesKind(tags, 1))
    }
}
