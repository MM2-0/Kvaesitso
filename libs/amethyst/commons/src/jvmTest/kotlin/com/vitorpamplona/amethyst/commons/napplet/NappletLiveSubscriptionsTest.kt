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
package com.vitorpamplona.amethyst.commons.napplet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class NappletLiveSubscriptionsTest {
    // With no account, an open answers at once with an empty EOSE: what this test counts.
    private val subscriptions = NappletLiveSubscriptions<String>(CoroutineScope(Dispatchers.Unconfined), NappletAttendance())
    private val pushed = mutableListOf<String>()

    private fun open(
        owner: String,
        subId: String,
        arrivedAt: Long,
    ) = subscriptions.open(owner, subId, emptyList(), null, arrivedAt) { pushed += it }

    @Test
    fun aSubscribeThatArrivedBeforeItsCloseNeverOpens() {
        // The page sent subscribe s1 (1) then close s1 (2); the close was answered first, while the
        // subscribe waited on consent.
        val subscribe = subscriptions.arrival()
        val close = subscriptions.arrival()
        subscriptions.close("page", "s1", close)
        open("page", "s1", subscribe)
        assertEquals(0, pushed.size, "the page closed it; nothing opens behind its back")

        // A new subscribe with the same id, sent after the close, opens as usual.
        open("page", "s1", subscriptions.arrival())
        assertEquals(1, pushed.size)
        // Another page's s1 is its own.
        open("other", "s1", subscribe)
        assertEquals(2, pushed.size)
    }

    @Test
    fun nothingOpensForASurfaceThatWentAway() {
        val subscribe = subscriptions.arrival()
        subscriptions.closeAllFor("page")
        open("page", "s1", subscribe)
        open("page", "s2", subscriptions.arrival())
        assertEquals(0, pushed.size)
    }
}
