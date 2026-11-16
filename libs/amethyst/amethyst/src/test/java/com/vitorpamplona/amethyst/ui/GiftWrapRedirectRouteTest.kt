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
package com.vitorpamplona.amethyst.ui

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Why a screen waiting on a gift wrap has to be woken a second time.
 *
 * `LocalCache.consumeRegularEvent` emits the wrap note the moment the wrap is cached — which
 * is before anything has decrypted it, so `innerEventId` is still null and the wrap can say
 * nothing about where it leads. Everything that links the chain afterwards is plain assignment
 * on the note, which emits nothing. So that first emission is the only one a
 * `Route.EventRedirect` parked on a wrap ever sees, and the case below is what it sees: no
 * route, nothing to navigate to, wait forever — while the message it wanted sits decrypted in
 * the cache a few milliseconds later. `DecryptAndIndexProcessor` re-emits the wrap and seal
 * notes once the chain is linked, which is what turns the second case into the live one.
 */
class GiftWrapRedirectRouteTest {
    private val account = mockk<Account>()
    private val signer = NostrSignerSync(KeyPair())

    private fun wrap(): GiftWrapEvent = GiftWrapEvent.create(signer.sign(TextNoteEvent.build("psst")), signer.pubKey)

    @Test
    fun anUnopenedWrapHasNowhereToGo() {
        assertNull(routeFor(wrap(), account))
    }

    @Test
    fun anOpenedWrapLeadsToWhatItCarried() {
        val sealId = "5".repeat(64)
        val opened = wrap().apply { innerEventId = sealId }

        // The seal itself has not been cached in this test, so the walk stops there and hands
        // back a redirect — the point is that it moves at all, which it cannot do until the
        // wrap has been opened.
        assertEquals(Route.EventRedirect(sealId), routeFor(opened, account))
    }
}
