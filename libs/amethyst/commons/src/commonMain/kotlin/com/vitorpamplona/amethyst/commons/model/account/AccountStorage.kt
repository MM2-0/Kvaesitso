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
package com.vitorpamplona.amethyst.commons.model.account

/**
 * Platform-agnostic interface for persisting account metadata.
 * Implementations:
 * - Android: LocalPreferences (EncryptedSharedPreferences)
 * - Desktop: DesktopAccountStorage (encrypted JSON file)
 *
 * Does NOT handle private key storage — that's SecureKeyStorage's job.
 * Constructor-injected, not expect/actual.
 */
interface AccountStorage {
    /** Load all saved account metadata */
    suspend fun loadAccounts(): List<AccountInfo>

    /** Save or update account metadata */
    suspend fun saveAccount(info: AccountInfo)

    /** Delete account metadata by npub */
    suspend fun deleteAccount(npub: String)

    /** Get the npub of the currently active account, or null if none */
    suspend fun currentAccount(): String?

    /** Set which account is currently active */
    suspend fun setCurrentAccount(npub: String)
}
