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
package com.vitorpamplona.amethyst.commons.account

import com.vitorpamplona.amethyst.commons.cashu.CashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.cashu.UnavailableCashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.StateFlow

/**
 * Where [AccountSessionManager] keeps the saved logins: the roster, which one is current, and each
 * one's settings. Android's encrypted preferences implement it.
 */
interface AccountSessionStore {
    /** The current login's settings, or null when nobody is logged in. */
    suspend fun loadAccountConfigFromEncryptedStorage(): AccountSettings?

    /** The settings of the saved login [npub], or null when there is none. */
    suspend fun loadAccountConfigFromEncryptedStorage(npub: String): AccountSettings?

    /**
     * Saves [accountSettings] and makes it the current login. Returns the settings that became
     * current, which can be an existing signing account when a read-only npub is added for a
     * pubkey it already signs for.
     */
    suspend fun setDefaultAccount(accountSettings: AccountSettings): AccountSettings

    suspend fun setHasBackedUpKeys(
        value: Boolean,
        npub: String,
    )

    /** The npub of the current login. */
    suspend fun currentAccount(): String?

    suspend fun allSavedAccounts(): List<AccountInfo>

    /** The saved logins as they change; null until [allSavedAccounts] first loads them. */
    fun accountsFlow(): StateFlow<List<AccountInfo>?>

    suspend fun switchToAccount(accountInfo: AccountInfo)

    suspend fun deleteAccount(accountInfo: AccountInfo)
}

/** The live [Account]s [AccountSessionManager] logs into: built once per pubkey and kept. */
interface AccountCache {
    fun loadAccount(accountSettings: AccountSettings): Account

    /** Drops the in-memory copy. */
    fun removeAccount(pubkey: HexKey)

    /** The in-memory copy of [pubkey]'s account, when it is loaded (on screen or in the background). */
    fun loadedAccount(pubkey: HexKey): Account? = null

    /** Deletes the account's on-disk directory, for a permanent deletion. */
    fun deleteAccountFiles(pubkey: HexKey)
}

/** What the platform does around a session change, beyond the roster and the account cache. */
interface AccountSessionHooks {
    /** The current account is about to be switched away from or logged off: end what it owns. */
    fun onSessionEnding() {}

    /** [npub] is about to be deleted. Runs first, whether or not it is the current account. */
    suspend fun onAccountRemoving(npub: String) {}

    /** [pubkey]'s account was deleted: drop what it left in the app's own pipelines. */
    suspend fun onAccountRemoved(pubkey: HexKey) {}

    /** The Cashu keyset counters a brand-new account starts with. */
    fun cashuCountersFor(npub: String): CashuKeysetCounterStore = UnavailableCashuKeysetCounterStore
}
