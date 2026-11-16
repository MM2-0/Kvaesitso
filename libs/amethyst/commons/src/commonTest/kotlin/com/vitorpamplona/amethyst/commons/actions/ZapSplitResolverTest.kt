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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZapSplitResolverTest {
    private val authorPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val splitAPriv = "000000000000000000000000000000000000000000000000000000000000000d"
    private val splitBPriv = "0000000000000000000000000000000000000000000000000000000000000011"

    private val authorSigner = NostrSignerInternal(KeyPair(authorPriv.hexToByteArray()))
    private val authorPub = xOnly(authorPriv)
    private val splitAPub = xOnly(splitAPriv)
    private val splitBPub = xOnly(splitBPriv)

    private fun xOnly(privHex: String) =
        Secp256k1Instance
            .compressedPubKeyFor(privHex.hexToByteArray())
            .copyOfRange(1, 33)
            .toHexKey()

    /** Build a kind:1 note with the given extra tags, signed by the author. */
    private suspend fun noteWithTags(vararg tags: Array<String>): Event =
        authorSigner.sign<TextNoteEvent>(
            createdAt = 1_700_000_000L,
            kind = TextNoteEvent.KIND,
            tags = arrayOf(*tags),
            content = "hello world",
        )

    // ------------------------------------------------------------------
    // shareMillisats
    // ------------------------------------------------------------------

    @Test
    fun shareMillisats_distributesProportionallyAndRoundsToSats() {
        val total = 10_000L // 10 sats — millisats
        val a = ZapSplitResolver.shareMillisats(total, weight = 1.0, totalWeight = 4.0)
        val b = ZapSplitResolver.shareMillisats(total, weight = 3.0, totalWeight = 4.0)

        // Always a multiple of 1000 (whole sats).
        assertEquals(0L, a % 1000)
        assertEquals(0L, b % 1000)
        // 1/4 + 3/4 = full sat total (within rounding).
        assertTrue(a + b in (total - 1000)..(total + 1000))
    }

    @Test
    fun shareMillisats_zeroTotalWeightReturnsZero() {
        assertEquals(0L, ZapSplitResolver.shareMillisats(1_000L, 1.0, 0.0))
    }

    @Test
    fun shareMillisats_roundsHalfUpToWholeSat() {
        // 1234 msats with weight 1/1 → 1234 msats, rounds to 1000 msats (1 sat).
        val r = ZapSplitResolver.shareMillisats(1_234L, 1.0, 1.0)
        assertEquals(1_000L, r)
    }

    // ------------------------------------------------------------------
    // resolve — author fallback
    // ------------------------------------------------------------------

    @Test
    fun resolve_authorFallbackWhenNoSplitsAndNoSpecialEventKind() =
        runTest {
            val note = noteWithTags()
            val lookup: suspend (HexKey) -> String? = { pk ->
                if (pk == authorPub) "author@wallet.example" else null
            }

            val recipients = ZapSplitResolver.resolve(note, lookup)

            assertEquals(1, recipients.size)
            assertEquals("author@wallet.example", recipients[0].lnAddress)
            assertEquals(authorPub, recipients[0].pubkey)
            assertEquals(1.0, recipients[0].weight)
        }

    @Test
    fun resolve_authorWithoutLnAddressReturnsEmpty() =
        runTest {
            val note = noteWithTags()
            val recipients = ZapSplitResolver.resolve(note) { null }
            assertTrue(recipients.isEmpty(), "author has no LN address → no recipients")
        }

    // ------------------------------------------------------------------
    // resolve — LN-address split tags (legacy variant)
    // ------------------------------------------------------------------

    @Test
    fun resolve_lnAddressSplitTagsUsedDirectlyWithoutLookup() =
        runTest {
            val note =
                noteWithTags(
                    arrayOf("zap", "carol@damus.io"),
                    arrayOf("zap", "dave@wallet.io"),
                )
            // Lookup should never be consulted for LnAddress-style splits.
            var lookupCalls = 0
            val recipients =
                ZapSplitResolver.resolve(note) { _ ->
                    lookupCalls++
                    null
                }

            assertEquals(0, lookupCalls)
            assertEquals(2, recipients.size)
            assertEquals(setOf("carol@damus.io", "dave@wallet.io"), recipients.map { it.lnAddress }.toSet())
            // LnAddress splits never carry a pubkey.
            assertTrue(recipients.all { it.pubkey == null })
            // The legacy LnAddress format is always weight 1.0 per ZapSplitSetupParser.
            assertTrue(recipients.all { it.weight == 1.0 })
        }

    // ------------------------------------------------------------------
    // resolve — pubkey split tags (current variant)
    // ------------------------------------------------------------------

    @Test
    fun resolve_pubkeySplitTagsResolvedViaLookup() =
        runTest {
            val note =
                noteWithTags(
                    arrayOf("zap", splitAPub, "", "2.0"),
                    arrayOf("zap", splitBPub, "", "3.0"),
                )
            val knownAddresses =
                mapOf(
                    splitAPub to "split-a@wallet.example",
                    splitBPub to "split-b@wallet.example",
                )

            val recipients = ZapSplitResolver.resolve(note) { pk -> knownAddresses[pk] }

            assertEquals(2, recipients.size)
            val byPub = recipients.associateBy { it.pubkey }
            assertEquals("split-a@wallet.example", byPub[splitAPub]?.lnAddress)
            assertEquals(2.0, byPub[splitAPub]?.weight)
            assertEquals("split-b@wallet.example", byPub[splitBPub]?.lnAddress)
            assertEquals(3.0, byPub[splitBPub]?.weight)
        }

    @Test
    fun resolve_pubkeySplitWithoutLnAddressIsDroppedSilently() =
        runTest {
            val note =
                noteWithTags(
                    arrayOf("zap", splitAPub, "", "1.0"),
                    arrayOf("zap", splitBPub, "", "1.0"),
                )
            // Only split A has an LN address; B is silently dropped — same
            // behavior as the in-app `mapNotNull` after error display.
            val recipients =
                ZapSplitResolver.resolve(note) { pk ->
                    if (pk == splitAPub) "split-a@wallet.example" else null
                }

            assertEquals(1, recipients.size)
            assertEquals(splitAPub, recipients[0].pubkey)
        }

    @Test
    fun resolve_pubkeySplitsDoNotFallBackToAuthor() =
        runTest {
            // When split tags are present but none resolve to an LN address,
            // we get empty — we do NOT silently bill the author.
            val note = noteWithTags(arrayOf("zap", splitAPub, "", "1.0"))

            val recipients = ZapSplitResolver.resolve(note) { null }

            assertTrue(recipients.isEmpty())
        }

    // ------------------------------------------------------------------
    // shareMillisats integration: weighted distribution sums correctly
    // ------------------------------------------------------------------

    @Test
    fun resolveAndShare_weighted2to3SplitMatchesUiBehavior() =
        runTest {
            val note =
                noteWithTags(
                    arrayOf("zap", splitAPub, "", "2.0"),
                    arrayOf("zap", splitBPub, "", "3.0"),
                )
            val recipients =
                ZapSplitResolver.resolve(note) { pk ->
                    when (pk) {
                        splitAPub -> "a@x"
                        splitBPub -> "b@x"
                        else -> null
                    }
                }
            val totalWeight = recipients.sumOf { it.weight }
            val totalMsats = 100_000L // 100 sats

            val shares = recipients.map { ZapSplitResolver.shareMillisats(totalMsats, it.weight, totalWeight) }

            // 2/5 of 100 sats = 40 sats; 3/5 = 60 sats.
            assertEquals(40_000L, shares[0])
            assertEquals(60_000L, shares[1])
            assertEquals(totalMsats, shares.sum())
        }
}
