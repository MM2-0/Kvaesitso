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
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Ports the coverage the Android CalendarReminderPrefsTest had, onto the DataStore stores. */
class CalendarReminderStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun raw(): DataStore<Preferences> {
        val file = File(folder.root, "cal_${seq++}.preferences_pb")
        return PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file.toOkioPath() },
        )
    }

    // ── settings ──────────────────────────────────────────────────────

    /** Reminders are ON out of the box; a wrong default here silently stops everyone's reminders. */
    @Test
    fun defaultsAreEnabledAtFifteenMinutes() =
        runTest {
            val loaded = CalendarReminderSettingsStore(raw()).load()

            assertEquals(true, loaded.enabled)
            assertEquals(15, loaded.leadMinutes)
        }

    @Test
    fun settingsRoundTrip() =
        runTest {
            val store = CalendarReminderSettingsStore(raw())

            store.setEnabled(false)
            store.setLeadMinutes(60)

            assertEquals(CalendarReminderSettings(enabled = false, leadMinutes = 60), store.load())
        }

    /** The choices are anchored to the worker cadence — below 15 min cannot be honoured. */
    @Test
    fun leadTimeChoicesAreUnchanged() {
        assertEquals(listOf(5, 15, 30, 60), CalendarReminderSettings.LEAD_TIME_CHOICES)
        assertTrue(CalendarReminderSettings.DEFAULT_LEAD_MINUTES in CalendarReminderSettings.LEAD_TIME_CHOICES)
    }

    // ── log ───────────────────────────────────────────────────────────

    @Test
    fun anUnknownEventWasNotNotified() =
        runTest {
            assertFalse(CalendarReminderLogStore(raw()).wasNotified("abc", 1_000L))
        }

    @Test
    fun markingMakesItNotified() =
        runTest {
            val store = CalendarReminderLogStore(raw())

            store.markNotified("abc", 1_000L)

            assertTrue(store.wasNotified("abc", 1_000L))
        }

    /**
     * The stored value is the event-start the reminder fired for, not a flag:
     * a moved meeting must fire again rather than be silently skipped.
     */
    @Test
    fun aMovedEventIsNotifiedAgain() =
        runTest {
            val store = CalendarReminderLogStore(raw())
            store.markNotified("abc", 1_000L)

            assertFalse("the new start time has not been notified", store.wasNotified("abc", 2_000L))
        }

    @Test
    fun forgetBeforeDropsOnlyOlderEntries() =
        runTest {
            val store = CalendarReminderLogStore(raw())
            store.markNotified("old", 100L)
            store.markNotified("new", 900L)

            store.forgetBefore(500L)

            assertFalse(store.wasNotified("old", 100L))
            assertTrue(store.wasNotified("new", 900L))
        }

    /** forgetBefore must only prune its own keys, not anything else sharing the store. */
    @Test
    fun forgetBeforeLeavesForeignKeysAlone() =
        runTest {
            val foreignKey = longPreferencesKey("someone_elses_counter")
            val raw = raw()
            val log = CalendarReminderLogStore(raw)
            log.markNotified("old", 100L)
            raw.updateData { prefs -> prefs.toMutablePreferences().apply { this[foreignKey] = 1L } }

            log.forgetBefore(500L)

            assertEquals(1L, raw.data.first()[foreignKey])
        }
}
