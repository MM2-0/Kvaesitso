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

import com.vitorpamplona.amethyst.commons.model.preferences.AccountRosterStore
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The slice of the roster store this needs.
 *
 * An interface so the fallback decisions below are testable without an
 * AndroidKeyStore, which no unit test can reach.
 */
interface RosterStorage {
    suspend fun hasMigrated(): Boolean

    suspend fun markMigrated()

    suspend fun currentAccount(): String?

    suspend fun setCurrentAccount(npub: String?)

    suspend fun allAccountInfoJson(): String?

    suspend fun setAllAccountInfoJson(json: String?)

    suspend fun clear()
}

/** [RosterStorage] over the real encrypted store. */
class EncryptedRosterStorage(
    private val store: AccountRosterStore,
) : RosterStorage {
    override suspend fun hasMigrated() = store.hasMigrated()

    override suspend fun markMigrated() = store.markMigrated()

    override suspend fun currentAccount() = store.currentAccount()

    override suspend fun setCurrentAccount(npub: String?) = store.setCurrentAccount(npub)

    override suspend fun allAccountInfoJson() = store.allAccountInfoJson()

    override suspend fun setAllAccountInfoJson(json: String?) = store.setAllAccountInfoJson(json)

    override suspend fun clear() = store.clear()
}

/**
 * Moves the account index — which accounts exist, which one is in front — out
 * of the global `secret_keeper` EncryptedSharedPreferences file, on the same
 * terms as the keys and secrets before it: both stores written, new store
 * preferred on read, nothing deleted.
 *
 * This one is the most consequential to get wrong. Every private key can be
 * perfectly intact and, if the roster reads empty, the app still opens as a
 * fresh install with no way back to the accounts that are sitting on disk.
 * So a read that fails or comes back empty falls through to the legacy file
 * rather than being taken at face value.
 *
 * The legacy reader stays for good; see [EncryptedStorage] for why a lazy
 * migration cannot have its source deleted.
 */
class AccountRoster(
    private val store: RosterStorage,
) {
    companion object {
        private const val TAG = "AccountRoster"
    }

    /**
     * Runs the one-off copy if it has not run, and reports whether the new
     * store can be trusted for this read.
     *
     * Returns false when anything goes wrong, which sends the caller to the
     * legacy file.
     */
    private suspend fun ready(
        legacyCurrent: () -> String?,
        legacyAll: () -> String?,
    ): Boolean =
        try {
            if (!store.hasMigrated()) {
                store.setCurrentAccount(legacyCurrent())
                store.setAllAccountInfoJson(legacyAll())
                // Marker last: a crash midway leaves this unmigrated, so the
                // next read copies again rather than trusting a half-written
                // roster.
                store.markMigrated()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not prepare the roster store; using the legacy file", e)
            false
        }

    suspend fun currentAccount(
        legacyCurrent: () -> String?,
        legacyAll: () -> String?,
    ): String? {
        if (!ready(legacyCurrent, legacyAll)) return legacyCurrent()

        return try {
            store.currentAccount() ?: legacyCurrent()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the current account; using the legacy file", e)
            legacyCurrent()
        }
    }

    /**
     * The saved-account list as JSON.
     *
     * An empty or absent value falls through to the legacy file rather than
     * being reported as "no accounts": the two are indistinguishable here, and
     * only one of them is safe to act on.
     */
    suspend fun allAccountInfoJson(
        legacyCurrent: () -> String?,
        legacyAll: () -> String?,
    ): String? {
        if (!ready(legacyCurrent, legacyAll)) return legacyAll()

        return try {
            store.allAccountInfoJson()?.takeIf { it.isNotBlank() && it != "[]" } ?: legacyAll()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the saved accounts; using the legacy file", e)
            legacyAll()
        }
    }

    suspend fun mirrorCurrentAccount(npub: String?) = guard { store.setCurrentAccount(npub) }

    suspend fun mirrorAllAccountInfoJson(json: String?) = guard { store.setAllAccountInfoJson(json) }

    /** Matches the legacy `clear()` on the global file when the last account goes. */
    suspend fun clear() = guard { store.clear() }

    private suspend fun guard(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            // Never fatal: the legacy file is still written and still read.
            Log.w(TAG, "Could not write the roster store", e)
        }
    }
}

val accountRoster: AccountRoster by lazy {
    AccountRoster(
        EncryptedRosterStorage(
            AccountRosterStore(
                // Through the holder rather than a DataStore built here: it is the
                // one registry that knows which files already have a live store,
                // and `roster` sits in the same directory as every other one.
                Amethyst.instance.appStores.getDataStore(ROSTER_FILE_NAME),
                SecretEncryption(),
                Amethyst.instance.applicationIOScope,
            ),
        ),
    )
}

private const val ROSTER_FILE_NAME = "roster"
