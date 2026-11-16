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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilterNewVersionsOfAddressesTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://one.example")!!
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    private fun header(
        author: String,
        d: String,
        createdAt: Long,
    ) = AddressableNote(Address(AddressableListHeaderEvent.KIND, author, d)).apply {
        addRelaySync(relay)
        event = AddressableListHeaderEvent("c".repeat(64), author, createdAt, arrayOf(arrayOf("d", d)), "", "d".repeat(128))
    }

    @Test
    fun asksForNewerVersionsOfWhatIsHeldPerAuthor() {
        val filters = filterNewVersionsOfAddresses(listOf(header(alice, "github-accounts", 200), header(bob, "books", 100)))!!

        assertEquals(2, filters.size)
        assertEquals(setOf(relay), filters.map { it.relay }.toSet())
        assertEquals(listOf(listOf(alice), listOf(bob)), filters.map { it.filter.authors }.sortedBy { it!!.first() })
        assertEquals(setOf(listOf("github-accounts"), listOf("books")), filters.map { it.filter.tags!!.getValue("d") }.toSet())
        assertTrue(filters.all { it.filter.kinds == listOf(AddressableListHeaderEvent.KIND) })
        // Only what is newer than the version held here.
        assertEquals(mapOf(alice to 201L, bob to 101L), filters.associate { it.filter.authors!!.single() to it.filter.since })
        assertTrue(filters.all { it.filter.limit == 1 })
    }

    /** Addresses that share a filter are asked from the oldest version among them, so none is missed. */
    @Test
    fun aSharedFilterStartsAtItsOldestVersion() {
        val filter = filterNewVersionsOfAddresses(listOf(header(alice, "github-accounts", 200), header(alice, "books", 100)))!!.single().filter

        assertEquals(listOf("books", "github-accounts"), filter.tags!!.getValue("d"))
        assertEquals(101L, filter.since)
    }

    @Test
    fun oneFilterPerKind() {
        val article =
            AddressableNote(Address(LongFormContentEvent.KIND, alice, "post")).apply {
                addRelaySync(relay)
                event = LongFormContentEvent("e".repeat(64), alice, 300, arrayOf(arrayOf("d", "post")), "", "f".repeat(128))
            }

        val filters = filterNewVersionsOfAddresses(listOf(header(alice, "github-accounts", 200), article))!!

        assertEquals(setOf(listOf(LongFormContentEvent.KIND), listOf(AddressableListHeaderEvent.KIND)), filters.map { it.filter.kinds }.toSet())
    }

    /** A replaceable below 30000 has no `d`: asked by kind and author, never with an empty `#d`. */
    @Test
    fun plainReplaceablesAreAskedWithoutD() {
        val list =
            AddressableNote(Address(TrustProviderListEvent.KIND, alice, "")).apply {
                addRelaySync(relay)
                event = TrustProviderListEvent("e".repeat(64), alice, 300, emptyArray(), "", "f".repeat(128))
            }

        val filter = filterNewVersionsOfAddresses(listOf(list))!!.single().filter

        assertEquals(listOf(TrustProviderListEvent.KIND), filter.kinds)
        assertEquals(listOf(alice), filter.authors)
        assertNull(filter.tags)
        assertEquals(301L, filter.since)
    }

    /** As many addresses as a long publication has sections: split where a relay would cap them. */
    @Test
    fun manyAddressesAreChunked() {
        val sections = (1..240).map { header(alice, "section-$it", it.toLong()) }

        val filters = filterNewVersionsOfAddresses(sections)!!

        assertTrue(filters.size > 1)
        assertEquals(
            240,
            filters.sumOf {
                it.filter.tags!!
                    .getValue("d")
                    .size
            },
        )
    }

    /** An address with no version yet is the loader's to find. */
    @Test
    fun skipsAddressesNotYetLoaded() {
        val missing = AddressableNote(Address(AddressableListHeaderEvent.KIND, alice, "x")).apply { addRelaySync(relay) }

        assertNull(filterNewVersionsOfAddresses(listOf(missing)))
    }
}
