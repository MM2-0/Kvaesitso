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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * Who this account is, and the handful of per-account settings that sat beside
 * that in the legacy file.
 *
 * [pubKeyHex] is the one value in the whole preference layer that the account
 * cannot be loaded without: the loader returns null the moment it is missing,
 * and the account disappears from the app even with its private key safe in the
 * key store. Everything here is public — a pubkey, a signer's package name, the
 * relay URLs the user typed — and the store file is already named after the
 * account's npub, so keeping it in the plain per-account store reveals nothing
 * the file name does not.
 *
 * `hasBackedUpKeys` is deliberately *not* a field here. It is written on its
 * own, by the key-backup nudge, at moments unrelated to any of these; folding
 * it into the group would mean every [AccountIdentityStore.save] carried a
 * value its caller never knew about and would flip the nudge back on. It gets
 * its own accessors below.
 */
data class AccountIdentity(
    val pubKeyHex: String? = null,
    val loginWithExternalSigner: Boolean = false,
    val externalSignerPackageName: String? = null,
    val localRelayServers: Set<String> = emptySet(),
    val openBackupConflictsJson: String? = null,
)

/** Reads and writes [AccountIdentity] in the account's DataStore. */
class AccountIdentityStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        val pubKeyHex = stringPreferencesKey("nostr_pubkey")
        val loginWithExternalSigner = booleanPreferencesKey("login_with_external_signer")
        val externalSignerPackageName = stringPreferencesKey("signer_package_name")
        val localRelayServers = stringSetPreferencesKey("localRelayServers")
        val openBackupConflictsJson = stringPreferencesKey("openBackupConflicts")
        val hasBackedUpKeys = booleanPreferencesKey("has_backed_up_keys")

        /**
         * What the `secret_keeper_<npub>` file called these, for the one-shot copy.
         *
         * `has_backed_up_keys` is carried here even though it is not part of
         * [AccountIdentity]: the copy is per *key*, not per group, and losing
         * it would put the "back up your key" nudge back in front of every
         * user who had already dismissed it.
         */
        val legacyTable =
            LegacyKeyTable(
                "migrated.identity",
                listOf(
                    LegacyStringKey("nostr_pubkey", pubKeyHex),
                    LegacyBooleanKey("login_with_external_signer", loginWithExternalSigner),
                    LegacyStringKey("signer_package_name", externalSignerPackageName),
                    LegacyStringSetKey("localRelayServers", localRelayServers),
                    LegacyStringKey("openBackupConflicts", openBackupConflictsJson),
                    LegacyBooleanKey("has_backed_up_keys", hasBackedUpKeys),
                ),
            )
    }

    private suspend fun read(): Preferences =
        store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

    suspend fun load(): AccountIdentity {
        val prefs = read()

        return AccountIdentity(
            pubKeyHex = prefs[pubKeyHex],
            loginWithExternalSigner = prefs[loginWithExternalSigner] ?: false,
            externalSignerPackageName = prefs[externalSignerPackageName],
            localRelayServers = prefs[localRelayServers] ?: emptySet(),
            openBackupConflictsJson = prefs[openBackupConflictsJson],
        )
    }

    /**
     * Writes the whole group in one edit, so a crash cannot half-apply it.
     *
     * The removes matter as much as the puts and mirror the legacy block
     * exactly: an account that drops its external signer, clears its local
     * relays or answers its last backup conflict has to end up with those keys
     * *absent*, not holding yesterday's value.
     */
    suspend fun save(value: AccountIdentity) {
        store.edit { prefs ->
            value.pubKeyHex.let { if (it != null) prefs[pubKeyHex] = it else prefs.remove(pubKeyHex) }
            prefs[loginWithExternalSigner] = value.loginWithExternalSigner
            value.externalSignerPackageName.let { if (it != null) prefs[externalSignerPackageName] = it else prefs.remove(externalSignerPackageName) }
            value.localRelayServers.let { if (it.isNotEmpty()) prefs[localRelayServers] = it else prefs.remove(localRelayServers) }
            value.openBackupConflictsJson.let { if (it != null) prefs[openBackupConflictsJson] = it else prefs.remove(openBackupConflictsJson) }
        }
    }

    /**
     * True unless a freshly generated account still has its key only in the
     * app. Absent means true: every account logged in from an existing nsec,
     * bunker or external signer already holds its key elsewhere and must not
     * be nudged.
     */
    suspend fun hasBackedUpKeys(): Boolean = read()[hasBackedUpKeys] ?: true

    suspend fun setHasBackedUpKeys(value: Boolean) {
        store.edit { prefs -> prefs[hasBackedUpKeys] = value }
    }
}

/**
 * Falls back to [legacy] as a whole when this identity cannot be used.
 *
 * The test is [AccountIdentity.pubKeyHex], and the fallback is all-or-nothing
 * on purpose. A missing pubkey means the store answered from
 * `emptyPreferences()` — its file is unreadable, or the one-shot copy never
 * ran — and in that state the other fields are equally untrustworthy: an
 * absent boolean and a `false` one are the same value here, so merging field
 * by field would quietly report an external-signer account as a local one.
 * A pubkey present means the store is live and authoritative.
 */
fun AccountIdentity.orIfUnusable(legacy: () -> AccountIdentity): AccountIdentity = if (pubKeyHex != null) this else legacy()
