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
package com.vitorpamplona.amethyst.commons.relayClient.paging

import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * State-machine tests for [BackwardRelayPager]: drive its relay callbacks directly (no network) and
 * assert the cursor / done / stalled / exhausted bookkeeping — the logic that backed the "All caught up
 * while messages missing" and the stalled-vs-done bugs. The relay's own `until`+`limit`+EOSE wire
 * behaviour is covered separately against the in-process relay in `UntilLimitPagingRelayTest`.
 *
 * The pager is the **single-active orchestrator**: its per-relay cursors live on a separate
 * [RelayLoadingCursors] (in production, on a `Chatroom` / `ChatroomList`), bound in via [bind]. These tests
 * supply their own cursor object so they can rebind a previously-paged scope and assert what persists
 * (the cursors) versus what is transient and recomputed (the stalled set, the live flows).
 */
class BackwardRelayPagerTest {
    private val r1 = NormalizedRelayUrl("wss://r1.example/")
    private val r2 = NormalizedRelayUrl("wss://r2.example/")
    private val r3 = NormalizedRelayUrl("wss://r3.example/")

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    // A pager bound to a fresh scope of [relays]; returns both so tests can read the pinned cursor floor.
    private fun pagerOf(vararg relays: NormalizedRelayUrl): Pair<BackwardRelayPager, RelayLoadingCursors> {
        val cursors = RelayLoadingCursors()
        val p = BackwardRelayPager("test")
        p.bind(cursors, scope) { relays.toList() }
        return p to cursors
    }

    @Test
    fun firstPageRequestsTheFloorAndAnEmptyPageIsCaughtUp() {
        val (p, cursors) = pagerOf(r1)
        assertFalse(p.status.value.exhausted)

        assertTrue(p.advance(r1))
        // The very first page asks `until = floor` (pinned on the bound cursors).
        assertEquals(cursors.floor, p.requestedUntilFor(r1))

        // Empty page + EOSE → that relay is done; the only relay is done → genuinely caught up.
        assertTrue(p.onEose(r1))
        assertTrue(
            p.status.value.relayProgress
                .getValue(r1)
                .done,
        )
        assertTrue(p.status.value.exhausted)
        assertEquals(0, p.status.value.stalledCount)
    }

    @Test
    fun aDisabledScopeNeitherAdvancesNorShowsRelays() {
        var enabled = false
        val cursors = RelayLoadingCursors()
        val p = BackwardRelayPager("test")
        p.bind(cursors, scope, isEnabled = { enabled }) { listOf(r1, r2) }

        // Off (e.g. NIP-04 turned off in Settings › Messages): nothing to page, nothing to show.
        assertTrue(p.status.value.exhausted)
        assertTrue(
            p.status.value.relayProgress
                .isEmpty(),
        )
        assertFalse(p.advance(r1))
        assertFalse(p.advanceAll())
        assertFalse(p.loadingMore.value)
        assertTrue(p.armedRelays(listOf(r1, r2)).isEmpty())
        // The cursors were not touched while off.
        assertEquals(null, cursors.requestedUntilFor(r1))

        // Back on: the relays come back and paging resumes.
        enabled = true
        p.onEnabledChanged()
        assertFalse(p.status.value.exhausted)
        assertEquals(setOf(r1, r2), p.status.value.relayProgress.keys)
        assertTrue(p.advance(r1))
        assertEquals(listOf(r1), p.armedRelays(listOf(r1, r2)))
    }

    @Test
    fun turningAScopeOffMidPageDropsTheSpinnerAndKeepsTheCursors() {
        var enabled = true
        val cursors = RelayLoadingCursors()
        val p = BackwardRelayPager("test")
        p.bind(cursors, scope, isEnabled = { enabled }) { listOf(r1) }

        assertTrue(p.advance(r1))
        assertTrue(p.loadingMore.value)
        val requested = cursors.requestedUntilFor(r1)

        enabled = false
        p.onEnabledChanged()
        assertFalse(p.loadingMore.value)
        assertTrue(p.status.value.exhausted)
        assertTrue(
            p.status.value.relayProgress
                .isEmpty(),
        )
        assertEquals(requested, cursors.requestedUntilFor(r1))
    }

    @Test
    fun aLatePageAfterTurningOffCannotMarkTheFreshCursorsDone() {
        var enabled = true
        val cursors = RelayLoadingCursors()
        val p = BackwardRelayPager("test")
        p.bind(cursors, scope, isEnabled = { enabled }) { listOf(r1) }
        assertTrue(p.advance(r1))

        // Turned off mid-page: the owner resets the cursors, then the in-flight page answers.
        enabled = false
        p.onEnabledChanged()
        cursors.reset()
        p.onEvent(r1, 500)
        assertFalse(p.onEose(r1))
        p.onClosed(r1, "auth-required")

        // Back on: the relay is untouched — not done, nothing reached, not stalled — and pages again.
        enabled = true
        p.onEnabledChanged()
        assertFalse(cursors.isDone(r1))
        assertEquals(0, p.status.value.stalledCount)
        assertTrue(p.advance(r1))
        assertEquals(cursors.floor, p.requestedUntilFor(r1))
    }

    @Test
    fun nonEmptyPageMovesTheCursorThenBottomsOut() {
        val (p, _) = pagerOf(r1)
        p.advance(r1)

        // A page of three events; the oldest is 80, so the reached cursor drops to 80 (not done).
        p.onEvent(r1, 100)
        p.onEvent(r1, 80)
        p.onEvent(r1, 90)
        assertFalse(p.onEose(r1))
        assertFalse(
            p.status.value.relayProgress
                .getValue(r1)
                .done,
        )
        assertEquals(80L, p.status.value.reachedBack)
        assertFalse(p.status.value.exhausted)

        // The next page must start strictly below the oldest reached (80 → until 79).
        assertTrue(p.advance(r1))
        assertEquals(79L, p.requestedUntilFor(r1))

        // Empty page now → done → caught up.
        assertTrue(p.onEose(r1))
        assertTrue(p.status.value.exhausted)
        assertEquals(0, p.status.value.stalledCount)
    }

    @Test
    fun aStalledRelayMakesExhaustionIncompleteNotCaughtUp() {
        val (p, _) = pagerOf(r1, r2)
        p.advance(r1)
        p.advance(r2)

        // r1 genuinely bottoms out; r2 is still pending, so not exhausted yet.
        p.onEose(r1)
        assertFalse(p.status.value.exhausted)

        // r2 auth-walls the REQ → stalled (kept, not done).
        p.onClosed(r2, "auth-required")
        assertTrue(
            p.status.value.relayProgress
                .getValue(r2)
                .stalled,
        )
        assertFalse(
            p.status.value.relayProgress
                .getValue(r2)
                .done,
        )

        // Every relay is now done-or-stalled → exhausted, but it is INCOMPLETE: one relay unreachable.
        assertTrue(p.status.value.exhausted)
        assertEquals(1, p.status.value.stalledCount)
    }

    @Test
    fun cannotConnectAlsoStalls() {
        val (p, _) = pagerOf(r1)
        p.advance(r1)
        p.onCannotConnect(r1, "offline")
        assertTrue(
            p.status.value.relayProgress
                .getValue(r1)
                .stalled,
        )
        assertTrue(p.status.value.exhausted)
        assertEquals(1, p.status.value.stalledCount)
    }

    @Test
    fun reAdvancingAStalledRelayClearsTheStallAndUnExhausts() {
        val (p, _) = pagerOf(r1)
        p.advance(r1)
        p.onClosed(r1, "auth-required")
        assertTrue(p.status.value.exhausted)
        assertEquals(1, p.status.value.stalledCount)

        // Retrying it re-arms the relay: no longer stalled, no longer exhausted.
        assertTrue(p.advance(r1))
        assertFalse(
            p.status.value.relayProgress
                .getValue(r1)
                .stalled,
        )
        assertFalse(p.status.value.exhausted)
        assertEquals(0, p.status.value.stalledCount)
    }

    @Test
    fun reachedBackIsTheDeepestCursorAcrossRelays() {
        val (p, _) = pagerOf(r1, r2)
        p.advance(r1)
        p.advance(r2)

        p.onEvent(r1, 500)
        p.onEose(r1) // r1 reached 500

        p.onEvent(r2, 300)
        p.onEose(r2) // r2 reached 300

        // Deepest = the oldest point any relay has reached.
        assertEquals(300L, p.status.value.reachedBack)
    }

    @Test
    fun aDoneRelayWillNotAdvanceAgain() {
        val (p, _) = pagerOf(r1)
        p.advance(r1)
        p.onEose(r1) // empty → done
        assertFalse(p.advance(r1))
    }

    @Test
    fun advanceAllArmsEveryNotDoneRelay() {
        val (p, _) = pagerOf(r1, r2, r3)
        // r2 already finished; advanceAll should arm only r1 and r3.
        p.advance(r2)
        p.onEose(r2)

        assertTrue(p.advanceAll())
        assertEquals(setOf(r1, r3), p.armedRelays(listOf(r1, r2, r3)).toSet())
    }

    @Test
    fun rebindingRepointsFlowsKeepingDoneCursorsButDroppingTransientStalls() {
        val cursorsA = RelayLoadingCursors()
        val cursorsB = RelayLoadingCursors()
        val p = BackwardRelayPager("test")

        // Scope A: r1 bottoms out (done — a persistent cursor fact); r2 auth-walls (stalled — transient).
        p.bind(cursorsA, scope) { listOf(r1, r2) }
        p.advance(r1)
        p.advance(r2)
        p.onEose(r1)
        p.onClosed(r2, "auth-required")
        assertTrue(p.status.value.exhausted)
        assertEquals(1, p.status.value.stalledCount)

        // Bind to a fresh scope B: the flows reflect B's own (empty) state — nothing stalled, and its
        // reach sits at B's floor (no history fetched yet — markers start at the live-tail boundary).
        p.bind(cursorsB, scope) { listOf(r3) }
        assertFalse(p.status.value.exhausted)
        assertEquals(0, p.status.value.stalledCount)
        assertEquals(cursorsB.floor, p.status.value.reachedBack)

        // Rebind to A: r1 is still DONE (its cursor persisted on cursorsA), but r2's stall is gone — stall
        // is transient, so r2 is pending again and A is no longer exhausted (it will retry the auth relay).
        p.bind(cursorsA, scope) { listOf(r1, r2) }
        assertTrue(
            p.status.value.relayProgress
                .getValue(r1)
                .done,
        )
        assertEquals(0, p.status.value.stalledCount)
        assertFalse(p.status.value.exhausted)
    }

    @Test
    fun unbindLetsGoOfTheEndedSubscriptionsScope() {
        val cursors = RelayLoadingCursors()
        val p = BackwardRelayPager("test")
        p.bind(cursors, scope, owner = "alice") { listOf(r1, r2) }
        p.advance(r1)
        assertTrue(p.loadingMore.value)

        p.unbind("alice")

        // Nothing of the scope is left: no cursors, no relays, no spinner, an empty status.
        assertFalse(p.isBoundTo(cursors))
        assertFalse(p.loadingMore.value)
        assertEquals(PagingStatus(), p.status.value)
        assertFalse(p.advance(r2))
        assertFalse(p.advanceAll())
        // A late page of the ended subscription lands nowhere.
        assertFalse(p.onEose(r1))
        assertEquals(emptyList(), p.armedRelays(listOf(r1, r2)))
    }

    @Test
    fun unbindFromAnOlderSubscriptionLeavesTheNewerBinding() {
        val cursorsA = RelayLoadingCursors()
        val cursorsB = RelayLoadingCursors()
        val p = BackwardRelayPager("test")
        p.bind(cursorsA, scope, owner = "alice") { listOf(r1) }
        p.bind(cursorsB, scope, owner = "bob") { listOf(r3) }

        p.unbind("alice")

        assertTrue(p.isBoundTo(cursorsB))
        assertTrue(p.advance(r3))
    }

    @Test
    fun aScopeBoundAgainAfterUnbindKeepsItsProgress() {
        val cursors = RelayLoadingCursors()
        val p = BackwardRelayPager("test")
        p.bind(cursors, scope, owner = "alice") { listOf(r1, r2) }
        p.advance(r1)
        p.onEose(r1) // an empty page: r1 is done

        p.unbind("alice")
        p.bind(cursors, scope, owner = "alice") { listOf(r1, r2) }

        assertTrue(
            p.status.value.relayProgress
                .getValue(r1)
                .done,
        )
        assertFalse(p.advance(r1))
        assertTrue(p.advance(r2))
    }
}
