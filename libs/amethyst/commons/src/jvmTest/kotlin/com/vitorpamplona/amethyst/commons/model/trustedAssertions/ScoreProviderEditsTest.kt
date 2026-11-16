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
package com.vitorpamplona.amethyst.commons.model.trustedAssertions

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreProviderEditsTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val relay = RelayUrlNormalizer.normalize("wss://scores.example.com")
    private val oldProvider = "a".repeat(64)
    private val newProvider = "b".repeat(64)
    private val eventProvider = ServiceProviderTag(ServiceType(30383, "rank"), "c".repeat(64), relay)

    private suspend fun existing() =
        TrustProviderListEvent.create(
            publicProviders = listOf(ServiceProviderTag(ProviderTypes.rank, oldProvider, relay), eventProvider),
            privateProviders = listOf(ServiceProviderTag(ProviderTypes.followerCount, oldProvider, relay)),
            signer = signer,
        )

    @Test
    fun replacesTheScoreProviderAndKeepsEverythingElse() =
        runBlocking {
            val updated = withScoreProvider(existing(), newProvider, relay, isPrivate = false, signer = signer)

            val public = updated.serviceProviders()
            assertTrue(eventProvider in public)
            assertEquals(newProvider, public.single { it.service == ProviderTypes.rank }.pubkey)
            assertEquals(newProvider, public.single { it.service == ProviderTypes.followerCount }.pubkey)
            // The old private followers entry is gone; nothing else was private.
            assertTrue(updated.privateTags(signer).orEmpty().isEmpty())
            assertEquals(newProvider, updated.rankProvider(signer)?.pubkey)
        }

    @Test
    fun aPrivateProviderIsKnownOnlyOnceItsPartIsRead() =
        runBlocking {
            val list = withScoreProvider(null, newProvider, relay, isPrivate = true, signer = signer)
            assertEquals(
                newProvider,
                list
                    .knownProviders { it.privateTags(signer) }
                    ?.rankChoice()
                    ?.provider
                    ?.pubkey,
            )
            // A signer that is not there yet: unknown, not "no provider".
            assertNull(list.knownProviders { null })
            // Content that does not decrypt or parse: unknown too, never an exception that ends the
            // flow resolving it.
            assertNull(list.knownProviders { throw IllegalStateException("bad private content") })
            // Someone else's list: only its public rows count, so it names no provider.
            assertNull(list.knownProviders { emptyArray() }?.rankChoice()?.provider)
        }

    @Test
    fun aPublicRankDecidesWithoutDecrypting() =
        runBlocking {
            val providers = existing().knownProviders { error("must not decrypt: the public rank row decides") }
            assertEquals(oldProvider, providers?.rankChoice()?.provider?.pubkey)
        }

    @Test
    fun privateEntriesStayPrivate() =
        runBlocking {
            val updated = withScoreProvider(null, newProvider, relay, isPrivate = true, signer = signer)
            assertTrue(updated.serviceProviders().isEmpty())
            assertNull(updated.rankProvider(null))
            assertEquals(newProvider, updated.rankProvider(signer)?.pubkey)
        }

    @Test
    fun removingTheProviderKeepsOtherEntries() =
        runBlocking {
            val removed = assertNotNull(withoutScoreProvider(existing(), signer))
            assertEquals(listOf(eventProvider), removed.serviceProviders())
            assertNull(removed.rankProvider(signer))
            assertNull(withoutScoreProvider(removed, signer))
        }

    @Test
    fun aProvidersRowsReplaceTheSameNamesAndTheOldScoreProvider() =
        runBlocking {
            // Another provider's list, a Trusted Lists row and a client tag are already there.
            val lists = arrayOf("30392", "d".repeat(64), relay.url)
            val base = existing()
            val withExtras =
                TrustProviderListEvent.resign(
                    tags = base.tags + arrayOf(lists, arrayOf("client", "Other")),
                    privateTags = base.privateTags(signer)!!,
                    signer = signer,
                )
            val rows =
                listOf(
                    TrustProviderRow("30382:rank", newProvider, relay),
                    TrustProviderRow("30382:hops", newProvider, relay),
                    TrustProviderRow("30392", newProvider, relay),
                )
            val updated = withProviderRows(withExtras, rows, isPrivate = false, signer = signer)

            val names = updated.tags.map { it[0] }
            assertEquals(1, names.count { it == "30392" })
            assertEquals(newProvider, updated.tags.single { it[0] == "30392" }[1])
            assertTrue(arrayOf("client", "Other").contentEquals(updated.tags.single { it[0] == "client" }))
            assertTrue(eventProvider in updated.serviceProviders())
            // The old provider's followers row is gone even though the new rows have none.
            assertTrue(updated.serviceProviders().none { it.pubkey == oldProvider })
            assertTrue(updated.privateTags(signer).orEmpty().isEmpty())
            assertEquals(newProvider, updated.rankProvider(signer)?.pubkey)
        }

    @Test
    fun stoppingTheProviderRemovesEveryRowItsKeyServes() =
        runBlocking {
            val rows = listOf("30382:rank", "30382:followers", "30382:hops", "30392").map { TrustProviderRow(it, newProvider, relay) }
            val set = withProviderRows(existing(), rows, isPrivate = false, signer = signer)
            val removed = assertNotNull(withoutScoreProvider(set, signer))
            assertTrue(removed.tags.none { it.size > 1 && it[1] == newProvider })
            assertEquals(listOf(eventProvider), removed.serviceProviders())
        }

    @Test
    fun rowsAreValidatedWhenParsed() {
        assertNotNull(TrustProviderRow.parse(listOf("30382:rank", "A".repeat(64), "wss://x.com")))
        assertNotNull(TrustProviderRow.parse(listOf("30392", "a".repeat(64), "wss://x.com")))
        assertNull(TrustProviderRow.parse(listOf("client", "a".repeat(64), "wss://x.com")))
        assertNull(TrustProviderRow.parse(listOf("30382:", "a".repeat(64), "wss://x.com")))
        assertNull(TrustProviderRow.parse(listOf("30382:rank", "abc", "wss://x.com")))
        assertNull(TrustProviderRow.parse(listOf("30382:rank", "a".repeat(64))))
    }

    @Test
    fun switchingProviderDropsEveryRowTheOldOneServed() =
        runBlocking {
            val brainstorm = listOf("30382:rank", "30382:followers", "30382:hops", "30392").map { TrustProviderRow(it, oldProvider, relay) }
            val first = withProviderRows(null, brainstorm, isPrivate = false, signer = signer)
            val switched = withProviderRows(first, listOf(TrustProviderRow("30382:rank", newProvider, relay)), isPrivate = false, signer = signer)
            assertTrue(switched.tags.none { it.size > 1 && it[1] == oldProvider }, "tags=${switched.tags.map { it.toList() }}")
            assertEquals(newProvider, switched.rankProvider(signer)?.pubkey)
        }
}
