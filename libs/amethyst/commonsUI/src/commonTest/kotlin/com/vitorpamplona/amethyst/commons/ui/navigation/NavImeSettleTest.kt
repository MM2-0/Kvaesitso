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
package com.vitorpamplona.amethyst.commons.ui.navigation

import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.ImeSettler
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Leaving a screen while the soft keyboard is still animating strands `imePadding()` at keyboard
 * height for the whole app, because `WindowInsets.ime` is a single shared holder. The fix is that
 * [Nav] waits for the IME to be gone before it moves, so these assert the ordering rather than any
 * visual result: every transition must settle the keyboard *first*.
 *
 * Each settle records the screen on top when it runs; the navigation must not have happened yet.
 *
 * This is the prevention half. The system's own back gesture never reaches [Nav] — the first back
 * press with a keyboard up is consumed by the IME — so it can still cancel an animation and freeze
 * the inset. `SafeImeInsets` is the backstop for that; see `SafeImeInsetsTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavImeSettleTest {
    /** A stack sitting on a pushed note, and a log of the screen on top at each settle. */
    private fun TestScope.navOnANote(
        settled: MutableList<Route>,
        slowBy: Long = 0,
    ): Nav {
        val stacks = NavBackStacks()
        stacks.push(Route.Note("n"))
        return Nav(
            stacks,
            this,
            ImeSettler {
                if (slowBy > 0) delay(slowBy)
                settled.add(stacks.topRoute)
            },
        )
    }

    @Test
    fun popBackSettlesTheKeyboardBeforeNavigating() =
        runTest {
            val settled = mutableListOf<Route>()
            val nav = navOnANote(settled)

            nav.popBack()
            advanceUntilIdle()

            assertEquals(listOf<Route>(Route.Note("n")), settled)
            assertEquals(Route.Home, nav.currentRoute)
        }

    @Test
    fun bottomBarSettlesTheKeyboardBeforeNavigating() =
        runTest {
            // The search tab focuses its field on arrival, so the keyboard is already up when the
            // user taps another tab — the exit that has no BackHandler and no top bar to guard it.
            val settled = mutableListOf<Route>()
            val nav = navOnANote(settled)

            nav.navBottomBar(Route.Message)
            advanceUntilIdle()

            assertEquals(listOf<Route>(Route.Note("n")), settled)
            assertEquals(Route.Message, nav.currentRoute)
        }

    @Test
    fun newStackSettlesTheKeyboardBeforeNavigating() =
        runTest {
            val settled = mutableListOf<Route>()
            val nav = navOnANote(settled)

            nav.newStack(Route.Profile("p"))
            advanceUntilIdle()

            assertEquals(listOf<Route>(Route.Note("n")), settled)
            assertEquals(Route.Profile("p"), nav.currentRoute)
        }

    @Test
    fun aSlowKeyboardStillHoldsTheNavigationBack() =
        runTest {
            // The real settler suspends for the length of the IME close animation. Navigation must
            // wait for it, not fire alongside it — that overlap is the bug.
            val settled = mutableListOf<Route>()
            val nav = navOnANote(settled, slowBy = 250)

            nav.popBack()
            assertEquals(Route.Note("n"), nav.currentRoute)

            advanceUntilIdle()
            assertEquals(listOf<Route>(Route.Note("n")), settled)
            assertEquals(Route.Home, nav.currentRoute)
        }
}
