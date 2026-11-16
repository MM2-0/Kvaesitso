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
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope

/**
 * Which accounts this installation holds, and which one is in front.
 *
 * Not per-account — this is the index that has to be read before any account
 * can be. Losing it is the worst failure in the preference layer: every key
 * survives untouched, and the app still opens as if freshly installed.
 *
 * Encrypted because it was encrypted before. The contents are npubs and two
 * booleans rather than key material, but they are the list of identities on
 * this device, and moving them to a plain store would be a quiet downgrade.
 *
 * Accessors are per-field rather than one group: the app writes the current
 * account and the account list on separate paths, and a group save would make
 * each one clobber the other's value.
 */
class AccountRosterStore(
    store: DataStore<Preferences>,
    encryption: SecretEncryption,
    scope: CoroutineScope,
) {
    companion object {
        private val currentAccountKey = stringPreferencesKey("currently_logged_in_account")
        private val allAccountInfoKey = stringPreferencesKey("all_saved_accounts_info")
        private val migratedKey = stringPreferencesKey("migrated.roster")
    }

    private val encrypted = EncryptedDataStore(store, encryption, scope)

    /** True once the one-off copy out of the legacy encrypted file has run. */
    suspend fun hasMigrated(): Boolean = encrypted.get(migratedKey) != null

    suspend fun markMigrated() {
        encrypted.save(migratedKey, "true")
    }

    suspend fun currentAccount(): String? = encrypted.get(currentAccountKey)

    suspend fun setCurrentAccount(npub: String?) {
        if (npub != null) encrypted.save(currentAccountKey, npub) else encrypted.remove(currentAccountKey)
    }

    /** The account list as the JSON the app already stores; parsing stays at the call site. */
    suspend fun allAccountInfoJson(): String? = encrypted.get(allAccountInfoKey)

    suspend fun setAllAccountInfoJson(json: String?) {
        if (json != null) encrypted.save(allAccountInfoKey, json) else encrypted.remove(allAccountInfoKey)
    }

    /**
     * Wipes the roster, matching the legacy `clear()` on the global file when
     * the last account is removed.
     *
     * The migrated marker goes too: with the legacy file cleared as well, there
     * is nothing left to copy, and leaving the marker set would be a claim
     * about a migration whose source no longer exists.
     */
    suspend fun clear() {
        encrypted.remove(currentAccountKey)
        encrypted.remove(allAccountInfoKey)
        encrypted.remove(migratedKey)
    }
}
