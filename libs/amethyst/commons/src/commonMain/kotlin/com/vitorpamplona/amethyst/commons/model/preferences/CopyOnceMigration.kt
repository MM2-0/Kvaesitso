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

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Copies values from an older store into this one, once, the first time the
 * DataStore is read.
 *
 * Deliberately a copy and not a move. `SharedPreferencesMigration`, the stock
 * implementation, deletes each key it migrates — which is how it knows not to
 * run twice, and which makes the migration a one-way door: a build that rolls
 * back to reading the old store finds the user's settings gone. Here a marker
 * key in the *destination* records that the copy happened, so the source is
 * left untouched and a rollback still works.
 *
 * Deleting the legacy data is a separate and later decision, and a narrower one
 * than it looks: because this runs lazily — on the first read of the
 * destination, not at install time — the source has to stay *readable*
 * indefinitely for installs that skip the release which introduced the
 * destination. What can be retired is writing to the source, once nothing
 * still reads a key only it holds.
 *
 * [copy] receives the destination and writes whatever it has, so a migration
 * can carry strings, booleans, int and string sets alike. It is only called
 * when the migration actually runs, so opening the legacy store is not a cost
 * paid on every launch. A key it does not write is left absent rather than
 * written blank, so it keeps reading as "unset" and falls back to its default.
 *
 * @param markerName key recording, in this store, that the copy has run.
 *   Distinct per migration, so several can run against the same store.
 */
class CopyOnceMigration(
    markerName: String,
    private val copy: suspend (MutablePreferences) -> Unit,
) : DataMigration<Preferences> {
    private val marker = booleanPreferencesKey(markerName)

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[marker] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val updated = currentData.toMutablePreferences()

        copy(updated)
        updated[marker] = true

        return updated.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
