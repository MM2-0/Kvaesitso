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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class BuzzCanvasHeadTest {
    private val channel = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

    private fun canvas(
        id: String,
        createdAt: Long,
    ): Note =
        Note(id).apply {
            event = CanvasEvent(id, "a".repeat(64), createdAt, arrayOf(arrayOf("h", channel)), "rev $id", "sig")
        }

    @AfterTest
    fun tearDown() = BuzzWorkspaceStates.clearForTesting()

    @Test
    fun newerCreatedAtWins() {
        val state = BuzzWorkspaceState()
        val old = canvas("1".repeat(64), 100)
        val new = canvas("2".repeat(64), 101)
        state.updateCanvas(new)
        state.updateCanvas(old)
        assertSame(new, state.canvasNote)
    }

    /** The relay reads the head as `created_at DESC, id ASC`: a same-second tie goes to the smaller id. */
    @Test
    fun sameSecondTieGoesToTheSmallestIdNotTheLastArrival() {
        val small = canvas("1".repeat(64), 100)
        val big = canvas("f".repeat(64), 100)

        val bigFirst = BuzzWorkspaceState()
        bigFirst.updateCanvas(big)
        bigFirst.updateCanvas(small)
        assertSame(small, bigFirst.canvasNote)

        val smallFirst = BuzzWorkspaceState()
        smallFirst.updateCanvas(small)
        smallFirst.updateCanvas(big)
        assertSame(small, smallFirst.canvasNote)
        assertEquals(1, smallFirst.canvasUpdates.value)
    }

    @Test
    fun reconsumingTheHeadDoesNotBump() {
        val state = BuzzWorkspaceState()
        val head = canvas("1".repeat(64), 100)
        state.updateCanvas(head)
        state.updateCanvas(head)
        assertEquals(1, state.canvasUpdates.value)
    }

    @Test
    fun writerClassifiesRelayAnswers() {
        val event = CanvasEvent("1".repeat(64), "a".repeat(64), 100, arrayOf(arrayOf("h", channel)), "x", "sig")

        assertIs<BuzzCanvasWriter.Outcome.Saved>(BuzzCanvasWriter.classify(event, listOf(PublishResult(true, ""))))

        val conflict = BuzzCanvasWriter.classify(event, listOf(PublishResult(false, "conflict: canvas changed since it was loaded")))
        assertIs<BuzzCanvasWriter.Outcome.Conflict>(conflict)
        assertEquals("conflict: canvas changed since it was loaded", conflict.message)

        val invalid = BuzzCanvasWriter.classify(event, listOf(PublishResult(false, "invalid: canvas created_at too far in the future")))
        assertIs<BuzzCanvasWriter.Outcome.Failed>(invalid)

        val silent = BuzzCanvasWriter.classify(event, emptyList())
        assertIs<BuzzCanvasWriter.Outcome.Failed>(silent)
        assertEquals(PublishResult.NO_RESPONSE, silent.message)
    }
}
