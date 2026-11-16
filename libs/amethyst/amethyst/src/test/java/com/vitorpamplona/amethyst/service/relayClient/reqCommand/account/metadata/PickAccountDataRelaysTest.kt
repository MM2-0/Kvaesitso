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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.metadata

import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.metadata.pickAccountDataRelays
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An account whose kind:10002 lists no write relays has no home relays, and this subscription is
 * keyed on them. Keyed on them *alone*, such an account was asked about on no relay at all, so its
 * own profile, follow list and every backed-up list silently stopped updating.
 */
class PickAccountDataRelaysTest {
    private fun relays(vararg urls: String) = urls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

    private val home = relays("wss://home.example.com")
    private val inbox = relays("wss://inbox.example.com")
    private val defaults = relays("wss://default1.example.com", "wss://default2.example.com")

    @Test
    fun homeRelaysWinWhenThereAreAny() {
        assertEquals(home, pickAccountDataRelays(home, inbox, defaults))
    }

    @Test
    fun theInboxIsUsedWhenNothingIsPublishedAnywhere() {
        assertEquals(inbox, pickAccountDataRelays(emptySet(), inbox, defaults))
    }

    /** The case that broke: everything the account declares is empty, and the answer was nothing. */
    @Test
    fun defaultsRatherThanNothing() {
        assertEquals(defaults, pickAccountDataRelays(emptySet(), emptySet(), defaults))
    }

    @Test
    fun theAnswerIsNeverEmptyWhileThereAreDefaults() {
        val cases =
            listOf(
                Triple(home, inbox, defaults),
                Triple(emptySet(), inbox, defaults),
                Triple(emptySet(), emptySet(), defaults),
            )
        cases.forEach { (h, i, d) ->
            assert(pickAccountDataRelays(h, i, d).isNotEmpty()) { "empty for home=$h inbox=$i" }
        }
    }

    @Test
    fun theInboxDoesNotWidenAnAccountThatAlreadyPublishesSomewhere() {
        // Reading its own events from where it publishes them is the normal path; the inbox is a
        // fallback, not an addition, so a working account's relay set is unchanged by this rule.
        assertEquals(home, pickAccountDataRelays(home, inbox, defaults))
        assertEquals(home, pickAccountDataRelays(home, emptySet(), defaults))
    }
}
