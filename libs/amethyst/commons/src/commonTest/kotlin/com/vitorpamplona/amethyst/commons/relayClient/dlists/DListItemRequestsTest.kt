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
package com.vitorpamplona.amethyst.commons.relayClient.dlists

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class DListItemRequestsTest {
    private val relayA = NormalizedRelayUrl("wss://a.test/")
    private val relayB = NormalizedRelayUrl("wss://b.test/")
    private val author = "b8".repeat(32)

    private fun header(
        d: String,
        vararg extra: Array<String>,
    ) = AddressableListHeaderEvent(
        id = d.padEnd(64, '0'),
        pubKey = author,
        createdAt = 1_791_000_000,
        tags = arrayOf(arrayOf("d", d), arrayOf("names", d, d + "s"), *extra),
        content = "",
        sig = "00".repeat(64),
    )

    private fun on(
        event: AddressableListHeaderEvent,
        vararg relays: NormalizedRelayUrl,
    ) = DListHeaderOnRelays(event.addressTag(), event, relays.toList())

    @Test
    fun headersOnTheSameRelayShareOneFilter() {
        // Each card on screen used to add its own filter to the REQ, one per header per relay.
        val food = header("food")
        val books = header("book")

        val requests = planDListItemRequests(listOf(on(food, relayA), on(books, relayA, relayB)))

        assertEquals(2, requests.size)
        val onA = requests.single { it.relay == relayA }
        assertEquals(listOf(food.addressTag(), books.addressTag()), onA.pointers)
        assertEquals(listOf(food.addressTag(), books.addressTag()), onA.headerIds)
        assertEquals(listOf(9999, 39999), onA.kinds)

        val onB = requests.single { it.relay == relayB }
        assertEquals(listOf(books.addressTag()), onB.pointers)
    }

    @Test
    fun headersWithOtherItemKindsGetTheirOwnFilter() {
        val standard = header("food")
        val custom = header("book", arrayOf("item-kind", "30023"))

        val requests = planDListItemRequests(listOf(on(standard, relayA), on(custom, relayA)))

        assertEquals(2, requests.size)
        assertEquals(listOf(standard.addressTag()), requests.single { it.kinds == listOf(9999, 39999) }.pointers)
        assertEquals(listOf(custom.addressTag()), requests.single { it.kinds == listOf(30023) }.pointers)
    }

    @Test
    fun aHeaderSeenNowhereAsksNothing() {
        assertEquals(emptyList(), planDListItemRequests(listOf(on(header("food")))))
    }

    @Test
    fun sharedPointersAreAskedOnce() {
        val original = header("food")
        val copy = header("food-copy", arrayOf("b", original.addressTag(), "pointer"))

        val request = planDListItemRequests(listOf(on(original, relayA), on(copy, relayA))).single()

        assertEquals(listOf(original.addressTag(), copy.addressTag()), request.pointers)
    }
}
