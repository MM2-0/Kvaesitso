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
package com.vitorpamplona.amethyst.commons.wot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WoTServiceTest {
    private lateinit var scope: CoroutineScope
    private lateinit var svc: WoTService

    // Fixed test pubkeys for readability.
    private val me = "self".padEnd(64, '0')
    private val a = "aaaa".padEnd(64, '0')
    private val b = "bbbb".padEnd(64, '0')
    private val c = "cccc".padEnd(64, '0')
    private val d = "dddd".padEnd(64, '0')
    private val e = "eeee".padEnd(64, '0')

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        svc = WoTService(scope, writerDispatcher = Dispatchers.Unconfined)
    }

    @After
    fun teardown() {
        scope.cancel()
    }

    /**
     * With `Dispatchers.Unconfined` + `Channel.UNLIMITED`, `trySend` from the
     * test thread synchronously resumes the writer coroutine — so no explicit
     * wait is needed. This helper is a no-op we keep for future scheduler
     * changes.
     */
    private fun drain() = Unit

    @Test
    fun emptyGraphYieldsEmptyScores() {
        svc.onFollowSetChange(emptySet(), me)
        drain()
        assertEquals(emptyMap<String, Int>(), svc.scoresSnapshot())
    }

    @Test
    fun singleFollowerCreditsTargets() {
        svc.onFollowSetChange(setOf(a), me)
        svc.applyKind3(a, setOf(c, d))
        drain()
        assertEquals(1, svc.scoresSnapshot()[c])
        assertEquals(1, svc.scoresSnapshot()[d])
    }

    @Test
    fun overlappingFollowersSumScores() {
        svc.onFollowSetChange(setOf(a, b), me)
        svc.applyKind3(a, setOf(c, d))
        svc.applyKind3(b, setOf(c, e))
        drain()
        assertEquals(2, svc.scoresSnapshot()[c])
        assertEquals(1, svc.scoresSnapshot()[d])
        assertEquals(1, svc.scoresSnapshot()[e])
    }

    @Test
    fun removingFollowerDecrementsAllContributions() {
        svc.onFollowSetChange(setOf(a, b), me)
        svc.applyKind3(a, setOf(c, d))
        svc.applyKind3(b, setOf(c, e))
        drain()
        // A drops out.
        svc.onFollowSetChange(setOf(b), me)
        drain()
        assertEquals(1, svc.scoresSnapshot()[c])
        // d had only A crediting it — should be gone.
        assertFalse(c in svc.scoresSnapshot() && d in svc.scoresSnapshot() && svc.scoresSnapshot()[d] == null)
        assertEquals(null, svc.scoresSnapshot()[d])
        assertEquals(1, svc.scoresSnapshot()[e])
    }

    @Test
    fun kind3ChurnAppliesDiff() {
        svc.onFollowSetChange(setOf(a), me)
        svc.applyKind3(a, setOf(c, d))
        drain()
        assertEquals(1, svc.scoresSnapshot()[c])
        assertEquals(1, svc.scoresSnapshot()[d])
        // A republishes with a different set — d removed, e added.
        svc.applyKind3(a, setOf(c, e))
        drain()
        assertEquals(1, svc.scoresSnapshot()[c])
        assertEquals(null, svc.scoresSnapshot()[d])
        assertEquals(1, svc.scoresSnapshot()[e])
    }

    @Test
    fun selfInclusionInKind3IsExcluded() {
        svc.onFollowSetChange(setOf(a), me)
        // A's kind-3 includes self (me) — must not inflate self's score.
        svc.applyKind3(a, setOf(c, me))
        drain()
        assertEquals(1, svc.scoresSnapshot()[c])
        assertEquals(null, svc.scoresSnapshot()[me])
    }

    @Test
    fun followerSelfInclusionIsExcluded() {
        svc.onFollowSetChange(setOf(a), me)
        // A's kind-3 includes A itself — must not inflate A's own score.
        svc.applyKind3(a, setOf(c, a))
        drain()
        assertEquals(1, svc.scoresSnapshot()[c])
        assertEquals(null, svc.scoresSnapshot()[a])
    }

    @Test
    fun kind3FromNonFollowerIsIgnored() {
        svc.onFollowSetChange(setOf(a), me)
        // e is NOT in my follow set — their kind-3 shouldn't credit anyone.
        svc.applyKind3(e, setOf(c, d))
        drain()
        assertEquals(emptyMap<String, Int>(), svc.scoresSnapshot())
    }

    @Test
    fun sparseMapDropsZeroCounts() {
        svc.onFollowSetChange(setOf(a), me)
        svc.applyKind3(a, setOf(c))
        drain()
        assertTrue(c in svc.scoresSnapshot())
        // A republishes with an empty follow set.
        svc.applyKind3(a, emptySet())
        drain()
        // c dropped to 0 → removed from map, not stored as 0.
        assertFalse(c in svc.scoresSnapshot())
    }

    private fun fakePubkey(seed: Int): String = seed.toString(16).padStart(64, '0')

    @Test
    fun guardrailSkipsHugeFollowSets() {
        val hugeFollows = (0..WoTService.MAX_FOLLOWS + 1).map { fakePubkey(it) }.toSet()
        svc.onFollowSetChange(hugeFollows, me)
        drain()
        assertEquals(emptyMap<String, Int>(), svc.scoresSnapshot())
        assertTrue(runBlocking { svc.isReady.first() })
        assertTrue(runBlocking { svc.isDisabled.first() })
    }

    /**
     * Regression for PR #3483 review finding 2: even after the guardrail
     * trips, applyKind3 for a follower in the huge follow set used to
     * repopulate reverseIndex/_scores because myFollows had already been
     * assigned. Fix clears myFollows AND sets a disabled flag; both gate
     * handleKind3 so the guardrail actually holds under sustained pump.
     */
    @Test
    fun guardrailIgnoresApplyKind3AfterTrip() {
        val huge = (0..WoTService.MAX_FOLLOWS + 1).map { fakePubkey(it) }.toSet()
        svc.onFollowSetChange(huge, me)
        drain()

        val anyFollower = huge.first()
        svc.applyKind3(anyFollower, setOf(c, d, e))
        drain()

        assertEquals(
            "Guardrail must block score repopulation via applyKind3",
            emptyMap<String, Int>(),
            svc.scoresSnapshot(),
        )
    }

    @Test
    fun guardrailReleasesWhenFollowSetShrinksBack() {
        val huge = (0..WoTService.MAX_FOLLOWS + 1).map { fakePubkey(it) }.toSet()
        svc.onFollowSetChange(huge, me)
        drain()
        assertTrue(runBlocking { svc.isDisabled.first() })

        // User trims their follow list — dispatcher should re-engage.
        svc.onFollowSetChange(setOf(a, b), me)
        drain()
        assertFalse(runBlocking { svc.isDisabled.first() })

        // And WoT scoring resumes normally.
        svc.applyKind3(a, setOf(c, d))
        drain()
        assertEquals(1, svc.scoresSnapshot()[c])
    }

    @Test
    fun closeStopsAcceptingOps() {
        svc.onFollowSetChange(setOf(a), me)
        svc.applyKind3(a, setOf(c))
        drain()
        assertEquals(1, svc.scoresSnapshot()[c])

        svc.close()
        drain()

        // Post-close writes are dropped silently.
        svc.applyKind3(a, setOf(d))
        drain()
        assertEquals(null, svc.scoresSnapshot()[d])
        // State observed before close remains readable.
        assertEquals(1, svc.scoresSnapshot()[c])
    }

    @Test
    fun closeIsIdempotent() {
        svc.close()
        svc.close() // should not throw
    }

    @Test
    fun maxFollowsPerEventCap() {
        svc.onFollowSetChange(setOf(a), me)
        val huge = (0..WoTService.MAX_FOLLOWS_PER_EVENT + 100).map { fakePubkey(it) }.toSet()
        svc.applyKind3(a, huge)
        drain()
        // Cap kicks in after MAX_FOLLOWS_PER_EVENT — no crash, score map bounded.
        assertTrue(svc.scoresSnapshot().size <= WoTService.MAX_FOLLOWS_PER_EVENT)
    }

    @Test
    fun markReadyOnceFiresReady() {
        assertFalse(runBlocking { svc.isReady.first() })
        svc.markReadyOnce()
        drain()
        assertTrue(runBlocking { svc.isReady.first() })
    }

    @Test
    fun clearResetsEverything() {
        svc.onFollowSetChange(setOf(a), me)
        svc.applyKind3(a, setOf(c, d))
        svc.markReadyOnce()
        drain()
        svc.clear()
        drain()
        assertEquals(emptyMap<String, Int>(), svc.scoresSnapshot())
        assertFalse(runBlocking { svc.isReady.first() })
    }

    @Test
    fun scoresSnapshotIsHashMapCopy() {
        svc.onFollowSetChange(setOf(a), me)
        svc.applyKind3(a, setOf(c))
        drain()
        val snap = svc.scoresSnapshot()
        assertEquals(1, snap[c])
        // Modifying the snapshot must not affect the service.
        (snap as MutableMap<String, Int>).clear()
        assertEquals(1, svc.scoresSnapshot()[c])
    }
}
