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
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * When this account answers a relay's NIP-42 AUTH challenge.

 * The policy key is absent for accounts saved before the setting existed; the
 * caller maps that absence to CUSTOM rather than to the constructor default, so
 * an existing user's behaviour does not change under them.
 *
 * Defaults match what the SharedPreferences implementation returned for a
 * missing key, so an account that never touched a setting behaves identically
 * before and after the migration.
 */
data class RelayAuth(
    val policyName: String? = null,
    val trustMyRelays: Boolean = true,
    val trustReadFollows: Boolean = true,
    val trustMessageFollows: Boolean = true,
    val trustMessageStrangers: Boolean = false,
)

/** Reads and writes [RelayAuth] in the account's DataStore. */
class RelayAuthStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        val policyName = stringPreferencesKey("default_relay_auth_policy")
        val trustMyRelays = booleanPreferencesKey("relay_auth_trust_my_relays")
        val trustReadFollows = booleanPreferencesKey("relay_auth_trust_read_follows")
        val trustMessageFollows = booleanPreferencesKey("relay_auth_trust_message_follows")
        val trustMessageStrangers = booleanPreferencesKey("relay_auth_trust_message_strangers")

        /**
         * What the `secret_keeper_<npub>` file called these, for the one-shot copy.
         *
         * The two "trust my relays" spellings differ: the legacy key grew a
         * `_and_venues` suffix that the new one dropped.
         */
        val legacyTable =
            LegacyKeyTable(
                "migrated.relayAuth",
                listOf(
                    LegacyStringKey("default_relay_auth_policy", policyName),
                    LegacyBooleanKey("relay_auth_trust_my_relays_and_venues", trustMyRelays),
                    LegacyBooleanKey("relay_auth_trust_read_follows", trustReadFollows),
                    LegacyBooleanKey("relay_auth_trust_message_follows", trustMessageFollows),
                    LegacyBooleanKey("relay_auth_trust_message_strangers", trustMessageStrangers),
                ),
            )
    }

    suspend fun load(): RelayAuth {
        val prefs =
            store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()

        return RelayAuth(
            policyName = prefs[policyName],
            trustMyRelays = prefs[trustMyRelays] ?: true,
            trustReadFollows = prefs[trustReadFollows] ?: true,
            trustMessageFollows = prefs[trustMessageFollows] ?: true,
            trustMessageStrangers = prefs[trustMessageStrangers] ?: false,
        )
    }

    /** Writes the whole group in one edit, so a crash cannot half-apply it. */
    suspend fun save(value: RelayAuth) {
        store.edit { prefs ->
            value.policyName.let { if (it != null) prefs[policyName] = it else prefs.remove(policyName) }
            prefs[trustMyRelays] = value.trustMyRelays
            prefs[trustReadFollows] = value.trustReadFollows
            prefs[trustMessageFollows] = value.trustMessageFollows
            prefs[trustMessageStrangers] = value.trustMessageStrangers
        }
    }
}
