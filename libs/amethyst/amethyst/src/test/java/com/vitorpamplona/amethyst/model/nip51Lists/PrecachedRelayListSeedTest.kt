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
package com.vitorpamplona.amethyst.model.nip51Lists

import com.vitorpamplona.amethyst.commons.model.nip51Lists.searchRelays.SearchRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the non-suspending seed that [SearchRelayListState.flow] is initialized with.
 *
 * The seed exists so `.value` is never empty before the first async emission lands (see the
 * KDoc on `flow`). For it to be an improvement over just using the curated defaults, reading an
 * account's *public* relays must work with no signer involvement at all — otherwise a NIP-46
 * account would still block. These tests pin that property.
 */
class PrecachedRelayListSeedTest {
    private val relayA = RelayUrlNormalizer.normalize("wss://relay.example.com")
    private val relayB = RelayUrlNormalizer.normalize("wss://other.example.com")

    /**
     * The load-bearing claim: public relay tags are readable synchronously. `cachedRelays` is
     * non-suspending, so if this returned empty the seed would silently degrade to the defaults
     * for every account.
     */
    @Test
    fun cachedRelays_readsPublicRelaysWithoutDecrypting() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val event = SearchRelayListEvent.create(relays = listOf(relayA, relayB), signer = signer)

            val relays = SearchRelayListDecryptionCache(signer).cachedRelays(event)

            assertEquals(setOf(relayA, relayB), relays)
        }

    /**
     * A kind:10007 with no relays must read as empty here, so the seed's `?.ifEmpty { null }`
     * arm hands over to the curated defaults rather than seeding an empty set.
     */
    @Test
    fun cachedRelays_emptyListReadsEmptySoTheSeedCanFallBack() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val event = SearchRelayListEvent.create(relays = emptyList(), signer = signer)

            val relays = SearchRelayListDecryptionCache(signer).cachedRelays(event)

            assertTrue(relays.isEmpty())
        }

    /**
     * Reading another account's list must not blow up or leak a decrypt attempt — the private
     * cache refuses to build for a foreign pubkey, so only the public tags come back.
     */
    @Test
    fun cachedRelays_foreignAuthorStillYieldsPublicRelays() =
        runTest {
            val author = NostrSignerInternal(KeyPair())
            val reader = NostrSignerInternal(KeyPair())
            val event = SearchRelayListEvent.create(relays = listOf(relayA), signer = author)

            val relays = SearchRelayListDecryptionCache(reader).cachedRelays(event)

            assertEquals(setOf(relayA), relays)
        }
}
