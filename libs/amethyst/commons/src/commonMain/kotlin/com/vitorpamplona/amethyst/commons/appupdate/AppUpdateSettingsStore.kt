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
package com.vitorpamplona.amethyst.commons.appupdate

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** What the updater keeps: the auto-check switch, a skipped version, the last check. App-wide. */
data class AppUpdateSettings(
    val autoCheck: Boolean = true,
    val skippedVersion: String? = null,
    val lastCheckAt: Long = 0L,
)

interface AppUpdateSettingsPersistence {
    suspend fun load(): AppUpdateSettings

    suspend fun save(settings: AppUpdateSettings)
}

/** [AppUpdateSettings] in the shared settings store, under the `appupdate.` prefix. */
class AppUpdateSettingsStore(
    private val store: DataStore<Preferences>,
) : AppUpdateSettingsPersistence {
    override suspend fun load(): AppUpdateSettings {
        val prefs = store.data.first()
        return AppUpdateSettings(
            autoCheck = prefs[KEY_AUTO_CHECK] ?: true,
            skippedVersion = prefs[KEY_SKIPPED],
            lastCheckAt = prefs[KEY_LAST_CHECK] ?: 0L,
        )
    }

    override suspend fun save(settings: AppUpdateSettings) {
        store.edit { prefs ->
            prefs[KEY_AUTO_CHECK] = settings.autoCheck
            val skipped = settings.skippedVersion
            if (skipped == null) prefs.remove(KEY_SKIPPED) else prefs[KEY_SKIPPED] = skipped
            prefs[KEY_LAST_CHECK] = settings.lastCheckAt
        }
    }

    companion object {
        val KEY_AUTO_CHECK = booleanPreferencesKey("appupdate.autoCheck")
        val KEY_SKIPPED = stringPreferencesKey("appupdate.skippedVersion")
        val KEY_LAST_CHECK = longPreferencesKey("appupdate.lastCheckAt")
    }
}
