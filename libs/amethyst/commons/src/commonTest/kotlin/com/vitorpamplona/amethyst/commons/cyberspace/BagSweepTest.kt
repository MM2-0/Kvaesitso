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
package com.vitorpamplona.amethyst.commons.cyberspace

import com.vitorpamplona.quartz.cyberspace.CyberspaceBagContents
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The budget around §7.7's sweep, on a bag the reference implementation sealed.
 *
 * The payload and the `d` tag below are the london height-4 vector that
 * `CyberspaceBagEventTest` pins against `cyberspace-cli`'s own cipher, so a
 * sweep that finds it here has reproduced the hider's region key from nothing
 * but a box — which is the whole claim §7.7 makes.
 */
class BagSweepTest {
    /** `cyberspace-cli`'s AES-256-GCM over a plain text note, at the london height-4 key. */
    private val payload = "AAECAwQFBgcICQoLu08nGyGW/7Vdw9GJIb/FveUqGhVW7YsSZuQ83LcizhHAJWNnQG/CHIcDKA=="

    /** The aligned bases of the boxes around that coordinate, from the reference's own interleave. */
    private val box444 = "c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c8000"
    private val box666 = "c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e3380000"
    private val box121212 = "c492492492492492492492edf5bee7267451c787d95ba4d7840c76c000000000"

    private fun bag(vararg extra: Array<String>): CyberspaceBagEvent =
        CyberspaceBagEvent(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = 1700000000,
            tags =
                arrayOf(
                    arrayOf("d", "a1d82532c354e690c6bffdb1fb20ccda716e037586feaf70092cbc442a635916"),
                    arrayOf("version", "2"),
                    arrayOf("h", "4"),
                    arrayOf("encrypted", "aes-256-gcm", payload),
                ) + extra,
            content = "",
            sig = "0".repeat(128),
        )

    private fun hint(
        base: String,
        h: Int,
    ) = arrayOf("hint", base, h.toString(), h.toString(), h.toString())

    @Test
    fun aBoxIsPricedFromTwoTagsAndNothingElse() {
        val quote = BagSweep.quote(bag(hint(box666, 6)))
        assertIs<BagSweepQuote.Searchable>(quote)
        assertEquals(6, quote.gapBits, "(6-4) three times")
        assertEquals(64, quote.candidates)
        assertEquals(false, quote.destination)
    }

    @Test
    fun aHintThatNamesTheRegionIsStillOfferedAsASearch() {
        // §7.7 calls three heights equal to the bag's "a destination the seeker
        // can compute or walk to directly, not a search". It is still a tap: a
        // reader who learned that some bags open themselves would have learned
        // an expectation a hostile bag could hide inside.
        val quote = BagSweep.quote(bag(hint(box444, 4)))
        assertIs<BagSweepQuote.Searchable>(quote)
        assertEquals(0, quote.gapBits)
        assertEquals(1, quote.candidates)
        assertTrue(quote.destination)
    }

    @Test
    fun aBoxPastTheCapIsNeverOfferedAsAButton() {
        // 3 x (12 - 4) = 24, which §7.7's own table calls "hours".
        val quote = BagSweep.quote(bag(hint(box121212, 12)))
        assertIs<BagSweepQuote.OutOfReach>(quote)
        assertEquals(24, quote.gapBits)
    }

    @Test
    fun aBagWithNoUsableHintIsHiddenRatherThanBroken() {
        // §7.7: a malformed hint "MUST be treated as absent", and §7.6 says a
        // bag nobody can find is not an error in the bag.
        assertIs<BagSweepQuote.Hidden>(BagSweep.quote(bag()))
        // Two hint tags is one of §7.7's malformed cases.
        assertIs<BagSweepQuote.Hidden>(BagSweep.quote(bag(hint(box666, 6), hint(box444, 4))))
        // And a box smaller than the region it claims to hold is another.
        assertIs<BagSweepQuote.Hidden>(BagSweep.quote(bag(hint(box444, 3))))
    }

    @Test
    fun aBagWhoseVersionIsUnknownIsNotSwept() {
        // §8.6: "A reader MUST ignore a bag whose version it does not know."
        val future =
            CyberspaceBagEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1700000000,
                tags =
                    arrayOf(
                        arrayOf("d", "a1d82532c354e690c6bffdb1fb20ccda716e037586feaf70092cbc442a635916"),
                        arrayOf("version", "3"),
                        arrayOf("h", "4"),
                        arrayOf("encrypted", "aes-256-gcm", payload),
                        hint(box666, 6),
                    ),
                content = "",
                sig = "0".repeat(128),
            )
        assertIs<BagSweepQuote.Hidden>(BagSweep.quote(future))
    }

    @Test
    fun sweepingTheBoxFindsTheRegionAndOpensTheBag() =
        runTest {
            val states = BagSweep.sweep(bag(hint(box666, 6))).toList()

            assertIs<BagSweepState.Measuring>(states.first(), "priced on this device before the rest is spent")
            val opened = assertIs<BagSweepState.Opened>(states.last())
            val contents = assertIs<CyberspaceBagContents.Opaque>(opened.contents)
            assertEquals("just some words, not a list", contents.bytes.decodeToString())
        }

    @Test
    fun aDestinationHintOpensOnTheOneCandidateItNames() =
        runTest {
            // The measurement is never wasted: the box's own base region is the
            // first candidate either way, so a gap-0 hint is answered by it.
            val states = BagSweep.sweep(bag(hint(box444, 4))).toList()
            assertEquals(2, states.size, "measure, then open")
            assertIs<BagSweepState.Opened>(states.last())
        }

    @Test
    fun aBoxTheHintWasWrongAboutIsSweptToTheEndAndSaysSo() =
        runTest {
            // The same box, against a bag addressed to a region that is not in
            // it. Every candidate is derived and none matches, which is the
            // honest outcome of a hint that was wrong or was bait.
            val elsewhere =
                CyberspaceBagEvent(
                    id = "a".repeat(64),
                    pubKey = "b".repeat(64),
                    createdAt = 1700000000,
                    tags =
                        arrayOf(
                            arrayOf("d", "f".repeat(64)),
                            arrayOf("version", "2"),
                            arrayOf("h", "4"),
                            arrayOf("encrypted", "aes-256-gcm", payload),
                            hint(box666, 6),
                        ),
                    content = "",
                    sig = "0".repeat(128),
                )

            val states = BagSweep.sweep(elsewhere).toList()
            val notFound = assertIs<BagSweepState.NotFound>(states.last())
            assertEquals(64, notFound.examined, "the whole box, and not one region more")
        }

    @Test
    fun aQuoteThatWasDeclinedEmitsNothingAtAll() =
        runTest {
            // Nothing is built for a box the card already refused, which is the
            // point of pricing from the tags: the refusal costs no arithmetic.
            assertTrue(BagSweep.sweep(bag(hint(box121212, 12))).toList().isEmpty())
            assertTrue(BagSweep.sweep(bag()).toList().isEmpty())
        }
}
