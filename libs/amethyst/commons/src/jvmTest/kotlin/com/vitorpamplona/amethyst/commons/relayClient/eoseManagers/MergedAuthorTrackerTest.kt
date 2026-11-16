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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that keeps a merged subscription from silently withholding history.
 *
 * One EOSE cursor per relay is right while the covered set is stable and wrong the moment it grows:
 * whoever joins inherits a cursor they never earned, so `since` skips everything older and the app
 * never asks for it. These pin *when* the cursor has to be dropped — over-dropping costs a refetch,
 * under-dropping loses data silently, so the asymmetry is deliberate.
 */
class MergedAuthorTrackerTest {
    private val relayA = NormalizedRelayUrl("wss://a.example/")
    private val relayB = NormalizedRelayUrl("wss://b.example/")

    private val vitor = "aa".repeat(32)
    private val edo = "bb".repeat(32)
    private val liz = "cc".repeat(32)

    @Test
    fun `the first sight of a relay never drops a cursor`() {
        val tracker = MergedAuthorTracker()

        // Nothing has EOSE'd yet, so there is nothing to invalidate.
        assertFalse(tracker.gainedAuthors(relayA, listOf(vitor)))
    }

    @Test
    fun `an account joining a relay drops that relay's cursor`() {
        val tracker = MergedAuthorTracker()
        tracker.gainedAuthors(relayA, listOf(vitor))

        // The real startup path: the screen's account mounts and EOSEs, then the registry brings the
        // rest in a second later. Without this, Edo and Liz get `since = <Vitor's EOSE>` forever.
        assertTrue(tracker.gainedAuthors(relayA, listOf(vitor, edo, liz)))
    }

    @Test
    fun `the same set again is not growth`() {
        val tracker = MergedAuthorTracker()
        tracker.gainedAuthors(relayA, listOf(vitor, edo))

        // updateFilter runs on every invalidation — order must not matter, and a no-op must stay one.
        assertFalse(tracker.gainedAuthors(relayA, listOf(vitor, edo)))
        assertFalse(tracker.gainedAuthors(relayA, listOf(edo, vitor)))
    }

    @Test
    fun `an account leaving is not growth`() {
        val tracker = MergedAuthorTracker()
        tracker.gainedAuthors(relayA, listOf(vitor, edo, liz))

        // Backgrounding drops the accounts that did not opt in. The ones that remain already have
        // their events, so their cursor is still honest — refetching would be pure waste.
        assertFalse(tracker.gainedAuthors(relayA, listOf(vitor)))
    }

    @Test
    fun `a swap counts as growth`() {
        val tracker = MergedAuthorTracker()
        tracker.gainedAuthors(relayA, listOf(vitor))

        // Same size, different member: Edo is new here even though nothing grew in count.
        assertTrue(tracker.gainedAuthors(relayA, listOf(edo)))
    }

    @Test
    fun `relays are tracked independently`() {
        val tracker = MergedAuthorTracker()
        tracker.gainedAuthors(relayA, listOf(vitor))
        tracker.gainedAuthors(relayB, listOf(vitor))

        assertTrue(tracker.gainedAuthors(relayA, listOf(vitor, edo)))
        // B's set did not change, so B's cursor must survive A's churn.
        assertFalse(tracker.gainedAuthors(relayB, listOf(vitor)))
    }

    @Test
    fun `clearing forgets everything, so the next sight is a first sight`() {
        val tracker = MergedAuthorTracker()
        tracker.gainedAuthors(relayA, listOf(vitor, edo))
        tracker.clear()

        assertFalse(tracker.gainedAuthors(relayA, listOf(vitor)))
    }
}
