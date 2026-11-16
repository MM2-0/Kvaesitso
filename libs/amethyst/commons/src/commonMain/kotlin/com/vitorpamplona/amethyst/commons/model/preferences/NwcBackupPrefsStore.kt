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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/** Not secret: the backup event is public on those relays anyway. */
data class NwcBackupPrefs(
    val autoBackup: Boolean = false,
    val cardDismissed: Boolean = false,
    val relays: Set<String> = emptySet(),
    val walletIds: Set<String> = emptySet(),
)

class NwcBackupPrefsStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        val autoBackup = booleanPreferencesKey("nwc_backup_auto")
        val cardDismissed = booleanPreferencesKey("nwc_backup_card_dismissed")
        val relays = stringSetPreferencesKey("nwc_backup_relays")
        val walletIds = stringSetPreferencesKey("nwc_backup_wallet_ids")
    }

    private suspend fun read(): Preferences =
        store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

    suspend fun load(): NwcBackupPrefs {
        val prefs = read()

        return NwcBackupPrefs(
            autoBackup = prefs[autoBackup] ?: false,
            cardDismissed = prefs[cardDismissed] ?: false,
            relays = prefs[relays] ?: emptySet(),
            walletIds = prefs[walletIds] ?: emptySet(),
        )
    }

    suspend fun save(value: NwcBackupPrefs) {
        store.edit { prefs ->
            prefs[autoBackup] = value.autoBackup
            prefs[cardDismissed] = value.cardDismissed
            prefs[relays] = value.relays
            prefs[walletIds] = value.walletIds
        }
    }
}
