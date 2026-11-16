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
package com.vitorpamplona.amethyst.commons.service.pow

import com.vitorpamplona.crypto.sha2.Sha256Midstate
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.indexOf
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import kotlin.test.Test
import kotlin.test.assertEquals

class PoWEstimatorTest {
    private val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    private fun note(
        content: String,
        tags: Array<Array<String>> = emptyArray(),
    ) = EventTemplate<TextNoteEvent>(1683596206, TextNoteEvent.KIND, tags, content)

    private val replyTags =
        arrayOf(
            arrayOf("e", "27ac621d7dc4a932e1a79f984308e7d20656dd6fddb2ce9cdfcb6a67b9a7bcc3", "", "root"),
            arrayOf("e", "be7245af96210a0dd048cab4ad38e52dbd6c09a53ea21a7edb6be8898e5727cc", "", "reply"),
            arrayOf("p", "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"),
        )

    @Test
    fun shapeMatchesWhatTheMidstateHasherRedoes() {
        for (template in listOf(note("gm"), note("A long form thought. ".repeat(100)), note("+1", replyTags))) {
            val nonce = "q7Xk2"
            val bytes = EventHasherSerializer.fastMakeJsonForId(pubKey, template.createdAt, template.kind, template.tags + PoWTag.assemble(nonce, 20), template.content)
            val midstate = Sha256Midstate(bytes, bytes.indexOf(nonce.encodeToByteArray()))

            val shape = PoWEventShape.of(template, pubKey, 20)
            assertEquals(midstate.tail.size / Sha256Midstate.BLOCK, shape.hashedBlocks)
            assertEquals(PoWEventShape.blocksFor(bytes.size), shape.fullBlocks)
        }
    }

    @Test
    fun replyTagsComeBeforeTheNonce() {
        assertEquals(PoWEventShape(fullBlocks = 2, hashedBlocks = 1), PoWEventShape.of(note("gm"), pubKey, 20))
        assertEquals(PoWEventShape(fullBlocks = 6, hashedBlocks = 1), PoWEventShape.of(note("+1", replyTags), pubKey, 20))
    }

    @Test
    fun zerosElsewhereInTheEventAreNotTheNonce() {
        // a reply to a mined event cites an id that starts with zeros
        val minedRoot = arrayOf(arrayOf("e", "000000006d9f2bd7ab6d5c6a3f9b8a3ce1b5fde20e1d2a9ef2f0b03b51eb7d22", "", "root")) + replyTags.copyOfRange(1, 3)
        assertEquals(PoWEventShape(fullBlocks = 6, hashedBlocks = 1), PoWEventShape.of(note("00000", minedRoot), pubKey, 20))
    }

    // full: 300 ns per call + 100 ns per block; midstate: 200 ns per block
    private val costs = PoWHashCosts(fullBaseNanos = 300.0, fullPerBlockNanos = 100.0, midstateBaseNanos = 0.0, midstatePerBlockNanos = 200.0)

    @Test
    fun ratesFollowTheFasterHasher() {
        // long note: full 300 + 35 × 100 = 3800 ns beats midstate 34 × 200 = 6800 ns
        assertEquals(1e9 / 3800, PoWHashRate(listOf(costs)).hashesPerSecond(PoWEventShape(35, 34)), 1e-6)
        // reply: midstate 1 × 200 = 200 ns beats full 300 + 6 × 100 = 900 ns
        assertEquals(1e9 / 200, PoWHashRate(listOf(costs)).hashesPerSecond(PoWEventShape(6, 1)), 1e-6)
    }

    @Test
    fun workersAddUp() {
        val slower = costs.copy(midstatePerBlockNanos = 400.0)
        assertEquals(1e9 / 200 + 1e9 / 400, PoWHashRate(listOf(costs, slower)).hashesPerSecond(PoWEventShape(6, 1)), 1e-6)
    }

    @Test
    fun liveRateCarriesOverToOtherShapesByTheModelsRatio() {
        val model = PoWHashRate(listOf(costs))
        val reply = PoWEventShape(6, 1)
        val longNote = PoWEventShape(35, 34)

        // the event being mined gets the live rate exactly
        assertEquals(3e6, PoWRateEstimate(3e6, model).hashesPerSecond(reply, miningShape = reply), 1e-6)
        // another one gets it scaled: the model says the long note is 3800 / 200 = 19x slower
        assertEquals(3e6 * 200 / 3800, PoWRateEstimate(3e6, model).hashesPerSecond(longNote, miningShape = reply), 1e-6)
        // idle: the model alone; no model yet: the live rate alone
        assertEquals(1e9 / 3800, PoWRateEstimate(null, model).hashesPerSecond(longNote, miningShape = reply), 1e-6)
        assertEquals(3e6, PoWRateEstimate(3e6, null).hashesPerSecond(longNote, miningShape = reply), 1e-6)
    }
}
