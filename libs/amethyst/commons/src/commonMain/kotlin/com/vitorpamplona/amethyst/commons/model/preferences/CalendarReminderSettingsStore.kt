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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.IOException

/** Store (and file) names, as [AppPreferenceStores] keys them. */
const val CALENDAR_REMINDER_SETTINGS_STORE = "calendar_reminder_settings"
const val CALENDAR_REMINDER_LOG_STORE = "calendar_reminder_log"

/**
 * Device-wide settings for the calendar reminder worker.
 *
 * Device scope rather than per-account, as before: the worker that consults
 * them runs globally, and multiplexing per-account settings would need
 * account-context plumbing into WorkManager that the rest of the app does not
 * have. A user who flips between accounts on one device shares one lead time.
 */
data class CalendarReminderSettings(
    val enabled: Boolean = DEFAULT_ENABLED,
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
) {
    companion object {
        const val DEFAULT_LEAD_MINUTES = 15
        const val DEFAULT_ENABLED = true

        /**
         * Choices presented in the settings UI. Anchored to the worker cadence —
         * lead times smaller than the cadence (15 min) cannot be honoured
         * reliably; 60 is the largest the UX shape supports without an extra
         * "hours" picker.
         */
        val LEAD_TIME_CHOICES = listOf(5, 15, 30, 60)
    }
}

class CalendarReminderSettingsStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        val enabled = booleanPreferencesKey("enabled")
        val leadMinutes = intPreferencesKey("lead_minutes")
    }

    private fun Flow<Preferences>.guarded() = catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    /** Observes the settings, so a screen can react to a change made elsewhere. */
    val flow: Flow<CalendarReminderSettings> =
        store.data.guarded().map { prefs ->
            CalendarReminderSettings(
                enabled = prefs[enabled] ?: CalendarReminderSettings.DEFAULT_ENABLED,
                leadMinutes = prefs[leadMinutes] ?: CalendarReminderSettings.DEFAULT_LEAD_MINUTES,
            )
        }

    suspend fun load(): CalendarReminderSettings = flow.first()

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[enabled] = value }
    }

    suspend fun setLeadMinutes(value: Int) {
        store.edit { it[leadMinutes] = value }
    }
}
