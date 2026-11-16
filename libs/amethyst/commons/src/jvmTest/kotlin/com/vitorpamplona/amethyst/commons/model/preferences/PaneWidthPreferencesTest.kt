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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A dragged width survives a restart: one launch drags, the next reads it back. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaneWidthPreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("shared_settings.preferences_pb") }

    private fun TestScope.session(drags: PaneWidthPreferences.() -> Unit = {}): PaneWidths {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val prefs = PaneWidthPreferences(store(scope), scope)
        advanceUntilIdle()
        prefs.drags()
        // Past the save delay, so the debounced write lands.
        advanceUntilIdle()
        scope.cancel()
        advanceUntilIdle()
        return prefs.flow.value
    }

    @Test
    fun draggedWidthsComeBackInTheNextLaunch() =
        runTest(StandardTestDispatcher()) {
            assertEquals("never dragged: the layout's defaults", PaneWidths(), session())

            session {
                setDrawer(340f)
                setNotifications(420f)
                setChatList(310f)
            }

            assertEquals(PaneWidths(drawer = 340f, notifications = 420f, chatList = 310f), session())
        }

    @Test
    fun aDragKeepsOnlyWhereItEnded() =
        runTest(StandardTestDispatcher()) {
            session {
                for (width in 300..400) setDrawer(width.toFloat())
            }

            assertEquals(400f, session().drawer)
        }
}
