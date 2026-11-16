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
package com.vitorpamplona.amethyst.commons.keystorage

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared UI needs from a platform's key storage: its status, the questions it is asking,
 * and the actions Key Backup offers. A platform without user-facing key storage
 * (Android keeps keys in its Keystore without asking) provides none.
 */
interface KeyStorageController {
    val status: StateFlow<KeyStorageStatus>

    val prompts: KeyStoragePrompts

    /**
     * Opens the OS keyring again after it was unreachable, and logs the default account in. An
     * account already open without its key loads again with it, on [returnTo] (null: its home).
     */
    suspend fun retry(returnTo: Route? = null)

    /** Asks for the key file's password now, instead of when a key is next needed; [returnTo] as in [retry]. */
    suspend fun unlock(returnTo: Route? = null)

    /**
     * Takes back the user's "Don't save" from this run ([KeyStorageStatus.NotKept]): the next login
     * that keeps its key asks for the key-file password again.
     */
    suspend fun saveKeys()
}
