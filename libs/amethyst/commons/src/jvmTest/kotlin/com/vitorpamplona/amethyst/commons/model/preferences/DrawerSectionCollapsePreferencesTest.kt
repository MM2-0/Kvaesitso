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
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The whole point of the feature is that a collapsed heading survives a restart, and nothing about
 * that is visible within one process: the write happens in a collector on the app scope, and the read
 * happens in the *next* process's constructor. [session] drives both halves against a real DataStore
 * on a temp file, so a test reads as "one launch, then another".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerSectionCollapsePreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("shared_settings.preferences_pb") }

    /**
     * One app launch over the shared temp file: restores, runs [taps], flushes the writes, then shuts
     * down as a process death would. Returns what that launch ended up holding.
     */
    private fun TestScope.session(taps: DrawerSectionCollapsePreferences.() -> Unit = {}): Set<DrawerSectionId> {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val prefs = DrawerSectionCollapsePreferences(store(scope), scope)
        advanceUntilIdle()
        prefs.taps()
        advanceUntilIdle()
        // On the test dispatcher advanceUntilIdle is the join: cancel() only
        // asks, and the next session() opens the same file, which DataStore
        // refuses while the previous instance is still winding down.
        scope.cancel()
        advanceUntilIdle()
        return prefs.flow.value
    }

    @Test
    fun aCollapsedHeadingComesBackCollapsedInTheNextLaunch() =
        runTest(StandardTestDispatcher()) {
            assertEquals("nothing stored yet, so the stock drawer", emptySet<DrawerSectionId>(), session())

            session { toggle(DrawerSectionId.FEEDS) }

            assertEquals(setOf(DrawerSectionId.FEEDS), session())
        }

    @Test
    fun expandingAgainClearsItFromDisk() =
        runTest(StandardTestDispatcher()) {
            session {
                toggle(DrawerSectionId.FEEDS)
                toggle(DrawerSectionId.FEEDS)
            }

            assertEquals(emptySet<DrawerSectionId>(), session())
        }

    @Test
    fun eachHeadingIsRememberedIndependently() =
        runTest(StandardTestDispatcher()) {
            session {
                toggle(DrawerSectionId.FEEDS)
                toggle(DrawerSectionId.SYSTEM)
                toggle(DrawerSectionId.YOU)
                toggle(DrawerSectionId.YOU)
            }

            assertEquals(setOf(DrawerSectionId.FEEDS, DrawerSectionId.SYSTEM), session())
        }

    @Test
    fun theStoredKeyIsTheOneTheDrawerReadsBack() =
        runTest(StandardTestDispatcher()) {
            // Pins the on-disk key name: renaming it silently resets everyone's folded headings.
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val store = store(scope)
            val prefs = DrawerSectionCollapsePreferences(store, scope)
            advanceUntilIdle()

            prefs.toggle(DrawerSectionId.NAVIGATE)
            advanceUntilIdle()

            val written =
                store.data
                    .first()
                    .asMap()
                    .mapKeys { it.key.name }
            assertEquals(setOf("NAVIGATE"), written["ui.drawer.collapsedSections"])
            scope.cancel()
        }
}
