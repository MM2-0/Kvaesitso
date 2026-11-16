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

import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecretsEncryptedStores
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * A failed read of the secrets store falls back to the legacy file, which has no remote-signer
 * fields. Writing that fallback back would delete a NIP-46 login's bunker address and transport
 * key for good, demoting the account to read-only on every later launch.
 */
class AccountSecretsStoreFallbackTest {
    private val npub = "npub1xxxx"
    private val legacy = AccountSecrets(nwcWalletsJson = "[]")
    private val stored =
        AccountSecrets(
            remoteSignerBunkerUri = "bunker://abc?relay=wss%3A%2F%2Frelay.example.com",
            remoteSignerTransportKey = "11".repeat(32),
        )

    @Test
    fun aRunThatCouldNotReadTheStoreNeverWritesIt() =
        runTest {
            val stores = mockk<AccountSecretsEncryptedStores>(relaxed = true)
            coEvery { stores.loadSecrets(npub) } throws IOException("Keystore busy")
            val store = AccountSecretsStore(stores)

            assertEquals(legacy, store.read(npub, legacy))
            store.mirror(npub, legacy)

            coVerify(exactly = 0) { stores.saveSecrets(any(), any()) }
        }

    @Test
    fun aRunThatReadTheStoreWritesIt() =
        runTest {
            val stores = mockk<AccountSecretsEncryptedStores>(relaxed = true)
            coEvery { stores.loadSecrets(npub) } returns stored
            val store = AccountSecretsStore(stores)

            assertEquals(stored, store.read(npub, legacy))
            store.mirror(npub, stored)

            coVerify(exactly = 1) { stores.saveSecrets(npub, stored) }
        }
}
