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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The collapse survives a restart while the fold's instant — the missed-count badge's base —
 * does not (the restore restarts the clock at "now"), and none of that is visible within one
 * process: the write happens in a collector on the app scope, and the read happens in the
 * *next* process's constructor. [session] drives both halves against a real DataStore on a temp
 * file, so a test reads as "one launch, then another".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPanelCollapsePreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("shared_settings.preferences_pb") }

    /**
     * One app launch over the shared temp file: restores, runs [taps], flushes the writes, then
     * shuts down as a process death would, returning that launch's preferences to assert on.
     */
    private fun TestScope.session(taps: NotificationPanelCollapsePreferences.() -> Unit = {}): NotificationPanelCollapsePreferences {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val prefs = NotificationPanelCollapsePreferences(store(scope), scope)
        advanceUntilIdle()
        prefs.taps()
        advanceUntilIdle()
        // On the test dispatcher advanceUntilIdle is the join: cancel() only
        // asks, and the next session() opens the same file, which DataStore
        // refuses while the previous instance is still winding down.
        scope.cancel()
        advanceUntilIdle()
        return prefs
    }

    @Test
    fun aCollapsedPanelComesBackCollapsedInTheNextLaunch() =
        runTest(StandardTestDispatcher()) {
            assertFalse("nothing stored yet, so the panel opens expanded", session().flow.value)

            session { collapse(atSeconds = 1_000) }

            assertTrue(session().flow.value)
        }

    @Test
    fun expandingAgainStaysExpandedInTheNextLaunch() =
        runTest(StandardTestDispatcher()) {
            session {
                collapse(atSeconds = 1_000)
                expand()
            }

            assertFalse(session().flow.value)
        }

    @Test
    fun theFoldInstantBasesTheMissedCountAndExpandResetsIt() =
        runTest(StandardTestDispatcher()) {
            val prefs = session { collapse(atSeconds = 1_000) }
            assertEquals(1_000L, prefs.collapsedAt.value)

            prefs.expand()
            advanceUntilIdle()
            assertNull(prefs.collapsedAt.value)

            // A new fold restamps the badge's base rather than counting from the old one.
            prefs.collapse(atSeconds = 2_000)
            advanceUntilIdle()
            assertEquals(2_000L, prefs.collapsedAt.value)
        }

    @Test
    fun aRestoredFoldStartsTheMissedCountAtTheRestore() =
        runTest(StandardTestDispatcher()) {
            session { collapse(atSeconds = 1_000) }

            val before = TimeUtils.now()
            val next = session()
            val after = TimeUtils.now()

            assertTrue("the fold itself survives", next.flow.value)
            val restoredAt = next.collapsedAt.value
            // The original fold's stamp is not on disk: what arrived while the app was closed must
            // not count, but the badge must tick from here on, so the restore restarts the clock.
            assertNotNull("the badge counts from the restore, not stuck at zero", restoredAt)
            assertTrue("the clock restamps at the restore", restoredAt!! in before..after)
        }

    @Test
    fun anExpandedRestoreHasNoFoldClock() =
        runTest(StandardTestDispatcher()) {
            val next = session()
            assertFalse(next.flow.value)
            assertNull("expanded: no fold, no clock, nothing counted", next.collapsedAt.value)
        }

    @Test
    fun theStoredKeyIsTheOneTheShellReadsBack() =
        runTest(StandardTestDispatcher()) {
            // Pins the on-disk key name: renaming it silently resets everyone's folded panel.
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val store = store(scope)
            val prefs = NotificationPanelCollapsePreferences(store, scope)
            advanceUntilIdle()

            prefs.collapse(atSeconds = 1_000)
            advanceUntilIdle()

            val written =
                store.data
                    .first()
                    .asMap()
                    .mapKeys { it.key.name }
            assertEquals(true, written["ui.notificationPanel.collapsed"])
            scope.cancel()
        }
}
