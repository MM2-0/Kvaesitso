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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedAutoRecovery.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAutoRecoveryTest {
    private var clock = 1_000L
    private val recovery = EmbeddedAutoRecovery { clock }

    @Test
    fun visibleTabRebuildsRightAway() {
        recovery.onShown()
        assertEquals(Decision.RECOVER_NOW, recovery.onLost())
    }

    @Test
    fun parkedTabWaitsUntilShown() {
        assertEquals(Decision.DEFERRED, recovery.onLost())
        assertTrue(recovery.onShown())
        // Only once: coming back again doesn't rebuild a live tab.
        recovery.onHidden()
        assertFalse(recovery.onShown())
    }

    @Test
    fun parkedTabRecoveredByOtherMeansDoesNotRebuild() {
        assertEquals(Decision.DEFERRED, recovery.onLost())
        recovery.clearPending()
        assertFalse(recovery.onShown())
    }

    @Test
    fun secondDeathRightAfterAnAutoRebuildGivesUp() {
        recovery.onShown()
        assertEquals(Decision.RECOVER_NOW, recovery.onLost())
        clock += 5_000
        assertEquals(Decision.GIVE_UP, recovery.onLost())
    }

    @Test
    fun deathAfterADeferredRebuildAlsoGivesUp() {
        assertEquals(Decision.DEFERRED, recovery.onLost())
        assertTrue(recovery.onShown())
        clock += 1_000
        assertEquals(Decision.GIVE_UP, recovery.onLost())
    }

    @Test
    fun laterDeathsRecoverAgain() {
        recovery.onShown()
        assertEquals(Decision.RECOVER_NOW, recovery.onLost())
        clock += EmbeddedAutoRecovery.LOOP_WINDOW_MS
        assertEquals(Decision.RECOVER_NOW, recovery.onLost())
    }
}
