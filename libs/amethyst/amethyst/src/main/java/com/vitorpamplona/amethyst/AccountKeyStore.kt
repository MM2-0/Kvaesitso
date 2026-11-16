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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.commons.keystorage.PrivateKeyVault
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorageVault
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Moves account private keys off `androidx.security.crypto` without ever
 * leaving one only in a place the running build cannot read.
 *
 * The old home is `secret_keeper_<npub>`, an EncryptedSharedPreferences file.
 * The new one is [SecureKeyStorage], which on Android is now an encrypted
 * DataStore sealed by the AndroidKeyStore directly. Both are written on every
 * save, and reads prefer the new store but fall back to the old one, so:
 *
 *  - an install that has never run this build still finds its key, and is
 *    migrated the first time the account loads;
 *  - a build rolled back to reading only the old store still finds every key,
 *    including ones added after the upgrade;
 *  - a new store that cannot be read — a wiped AndroidKeyStore after a device
 *    credential reset, say — falls back rather than presenting the account as
 *    having no key, which would silently demote it to read-only.
 *
 * Nothing is deleted here, and the legacy *reader* is permanent — see
 * [EncryptedStorage]. The migration is lazy, so an install that skips the
 * release introducing this store still needs the old file readable when it
 * finally arrives. What a later release can drop is the legacy **write**, once
 * every key in that file has a new home; several still do not.
 *
 * The two stores cannot legitimately disagree: an npub is derived from its
 * private key, so the key for a given npub never changes. A mismatch means
 * corruption, and is resolved in favour of the older, proven store.
 */
class AccountKeyStore(
    private val vault: PrivateKeyVault,
) {
    companion object {
        private const val TAG = "AccountKeyStore"
    }

    /**
     * The account's private key, or null when it genuinely has none — an
     * external-signer account, or a watch-only npub.
     *
     * @param legacyValue what the legacy store holds, read by the caller that
     *   already has the file open.
     */
    suspend fun read(
        npub: String,
        legacyValue: String?,
    ): String? {
        val fromSecure =
            try {
                vault.get(npub)
            } catch (e: Exception) {
                // Unreadable, not absent. Fall back, and do not migrate into a
                // store that just failed.
                Log.w(TAG, "Could not read the key store for $npub; using the legacy store", e)
                return legacyValue
            }

        if (fromSecure != null) {
            if (legacyValue != null && legacyValue != fromSecure) {
                Log.e(TAG, "Key mismatch for $npub between the legacy and current stores; keeping the legacy value", null)
                return legacyValue
            }
            return fromSecure
        }

        // Absent from the new store: first load since the upgrade.
        if (legacyValue != null) migrate(npub, legacyValue)
        return legacyValue
    }

    private suspend fun migrate(
        npub: String,
        privKeyHex: String,
    ) {
        try {
            vault.save(npub, privKeyHex)
            Log.i(TAG) { "Migrated the private key for $npub into the current store" }
        } catch (e: Exception) {
            // The legacy store still has it and is still read, so this is
            // recoverable — the next load tries again.
            Log.w(TAG, "Could not migrate the private key for $npub; it stays in the legacy store", e)
        }
    }

    /**
     * Mirrors a save into the new store. The legacy write stays where it is,
     * inside the caller's existing edit block, so a rollback keeps working.
     *
     * The three cases match the legacy write exactly, including the one that is
     * easy to get wrong: with no external signer and no private key in hand,
     * the legacy store *leaves the stored key alone* rather than clearing it,
     * so this must not clear it either. Deleting here would drop the key on
     * every save from a session that never decrypted it.
     */
    suspend fun mirrorSave(
        npub: String,
        usesExternalSigner: Boolean,
        privKeyHex: String?,
    ) {
        try {
            when {
                usesExternalSigner -> vault.delete(npub)
                privKeyHex != null -> vault.save(npub, privKeyHex)
                else -> Unit
            }
        } catch (e: Exception) {
            // Never fatal: the legacy store still loads the account, and the
            // next save or load repairs this one.
            Log.w(TAG, "Could not write the private key for $npub to the current store", e)
        }
    }

    /**
     * What the current store holds, with no fallback to the legacy value.
     *
     * For [LegacyPreferenceCleanup]; [read] deliberately hides this distinction.
     */
    suspend fun stored(npub: String): String? = vault.get(npub)

    /** Drops the key from the new store; the caller clears the legacy file itself. */
    suspend fun delete(npub: String) {
        try {
            vault.delete(npub)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Could not delete the private key for $npub from the current store", e)
        }
    }
}

/** The production instance, over the app's [SecureKeyStorage]. */
val accountKeyStore: AccountKeyStore by lazy {
    AccountKeyStore(SecureKeyStorageVault(SecureKeyStorage.create(Amethyst.instance.appContext)))
}
