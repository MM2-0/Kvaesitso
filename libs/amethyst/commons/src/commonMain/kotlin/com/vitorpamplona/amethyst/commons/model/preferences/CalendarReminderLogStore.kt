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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * The "already reminded about this" log for calendar appointments.
 *
 * Without it, every worker run after a restart would re-notify for the same
 * upcoming event until it started, since LocalCache has no memory of past
 * reminders.
 *
 * Each key stores the event-start time the reminder fired for, not a bare flag.
 * That is what makes a moved meeting work: if the author changes the start, the
 * stored value no longer matches and a fresh reminder fires, rather than the
 * new time being silently skipped.
 */
class CalendarReminderLogStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        private const val KEY_PREFIX = "notified:"

        fun keyFor(eventId: String) = longPreferencesKey(KEY_PREFIX + eventId)

        internal fun isLogKey(key: Preferences.Key<*>) = key.name.startsWith(KEY_PREFIX)
    }

    private suspend fun read(): Preferences =
        store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

    suspend fun wasNotified(
        eventId: String,
        eventStartSeconds: Long,
    ): Boolean = read()[keyFor(eventId)] == eventStartSeconds

    suspend fun markNotified(
        eventId: String,
        eventStartSeconds: Long,
    ) {
        store.edit { it[keyFor(eventId)] = eventStartSeconds }
    }

    /**
     * Drops entries whose recorded event-start is older than [cutoffSeconds],
     * keeping the log bounded — an event that has long since ended cannot fire
     * a second reminder, so its entry is dead weight.
     *
     * Only keys carrying the log's own prefix are considered, so a future
     * setting sharing this store cannot be pruned away by a stale cutoff.
     */
    suspend fun forgetBefore(cutoffSeconds: Long) {
        store.edit { prefs ->
            prefs
                .asMap()
                .filter { (key, value) -> isLogKey(key) && value is Long && value < cutoffSeconds }
                .forEach { (key, _) -> prefs.remove(key) }
        }
    }
}
