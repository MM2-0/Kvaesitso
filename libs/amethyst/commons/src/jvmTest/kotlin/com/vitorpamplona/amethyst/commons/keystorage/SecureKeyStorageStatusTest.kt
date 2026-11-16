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

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.PasswordAccessException
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SecureKeyStorage.status], the denied-vs-absent split, retry, the password-file fallback, the self-test. */
class SecureKeyStorageStatusTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakeKeyring(
        override val kind: OsKeyring? = OsKeyring.SECRET_SERVICE,
    ) : KeyringHandle {
        val store = mutableMapOf<Pair<String, String>, String>()
        var writes = 0

        /** Every read fails the way a denied macOS prompt or a locked keyring does. */
        var denyReads = false

        /** How this backend words a missing item: macOS and the Secret Service by default. */
        var missingRead: (String, String) -> String = { service, account -> "No stored credentials match $service account: $account" }
        var missingDelete: (String, String) -> String = missingRead

        override fun getPassword(
            service: String,
            account: String,
        ): String {
            if (denyReads) throw PasswordAccessException("Failed to get credential. User canceled the operation.")
            return store[service to account] ?: throw PasswordAccessException(missingRead(service, account))
        }

        override fun setPassword(
            service: String,
            account: String,
            password: String,
        ) {
            writes++
            store[service to account] = password
        }

        override fun deletePassword(
            service: String,
            account: String,
        ) {
            store.remove(service to account) ?: throw PasswordAccessException(missingDelete(service, account))
        }
    }

    private val key = "5a".repeat(32)

    private fun storage(factory: () -> KeyringHandle) = SecureKeyStorage.create(folder.root).also { it.keyringFactory = factory }

    @Test
    fun openingTheKeyringPublishesWhichOne() =
        runBlocking<Unit> {
            val keyring = FakeKeyring()
            val storage = storage { keyring }
            assertEquals(KeyStorageStatus.Unknown, storage.status.value)
            storage.enableConsolidatedVault(emptyList())
            assertEquals(KeyStorageStatus.Keyring(OsKeyring.SECRET_SERVICE), storage.status.value)
        }

    @Test
    fun aDeniedVaultReadNeitherRewritesTheVaultNorAnswersAbsent() =
        runBlocking<Unit> {
            val keyring = FakeKeyring()
            keyring.store["amethyst-desktop" to "vault-v1"] = "{\"schemaVersion\":1,\"entries\":{}}"
            keyring.denyReads = true
            val storage = storage { keyring }

            storage.enableConsolidatedVault(listOf("npub1a"))

            assertEquals(0, keyring.writes)
            assertFalse(storage.isVaultActive())
            assertIs<KeyStorageStatus.KeyringUnreachable>(storage.status.value)
            // Not a silent read-only login: the strict read refuses to say "absent".
            assertFailsWith<SecureStorageException> { storage.getPrivateKeyOrThrow("npub1a") }
        }

    @Test
    fun aKWalletMissIsAbsenceNotADenial() =
        runBlocking<Unit> {
            // java-keyring's KWallet backend, on a fresh install: no vault item yet.
            val keyring = FakeKeyring(OsKeyring.KWALLET)
            keyring.missingRead = { _, _ -> "Password is not in wallet" }
            keyring.missingDelete = { _, _ -> "Password cannot be deleted, it is not in wallet" }
            val storage = storage { keyring }

            storage.enableConsolidatedVault(listOf("npub1a"))

            assertTrue(storage.isVaultActive())
            assertEquals(KeyStorageStatus.Keyring(OsKeyring.KWALLET), storage.status.value)
            assertNull(storage.getPrivateKeyOrThrow("npub1a"))
            storage.savePrivateKey("npub1a", key)
            assertTrue(storage.deletePrivateKey("npub1a"))
            assertEquals(KeyStorageStatus.Keyring(OsKeyring.KWALLET), storage.status.value)
        }

    @Test
    fun aReadRacingTryAgainStillRefusesUntilTheVaultIsReadAgain() =
        runBlocking<Unit> {
            val keyring = FakeKeyring()
            storage { keyring }.apply {
                enableConsolidatedVault(emptyList())
                savePrivateKey("npub1a", key)
            }
            keyring.denyReads = true
            val storage = storage { keyring }
            storage.enableConsolidatedVault(listOf("npub1a"))

            // Try again resets the keyring; the launch's own login reads before the vault reopens.
            keyring.denyReads = false
            storage.resetKeyring()
            assertFailsWith<SecureStorageException> { storage.getPrivateKeyOrThrow("npub1a") }

            storage.enableConsolidatedVault(listOf("npub1a"))
            assertEquals(key, storage.getPrivateKeyOrThrow("npub1a"))
        }

    @Test
    fun tryingAgainAfterADenialOpensTheVault() =
        runBlocking<Unit> {
            val keyring = FakeKeyring()
            keyring.denyReads = true
            val storage = storage { keyring }
            storage.enableConsolidatedVault(emptyList())
            assertIs<KeyStorageStatus.KeyringUnreachable>(storage.status.value)

            keyring.denyReads = false
            storage.resetKeyring()
            storage.enableConsolidatedVault(emptyList())
            assertTrue(storage.isVaultActive())
            assertEquals(KeyStorageStatus.Keyring(OsKeyring.SECRET_SERVICE), storage.status.value)
        }

    @Test
    fun aMissingItemIsAbsentNotAFailure() =
        runBlocking<Unit> {
            val storage = storage { FakeKeyring() }
            assertNull(storage.getPrivateKey("npub1missing"))
            assertEquals(KeyStorageStatus.Keyring(OsKeyring.SECRET_SERVICE), storage.status.value)
        }

    @Test
    fun withNoKeyringKeysGoToThePasswordFile() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.Password("correct horse battery"))
            val job = script.start(this)
            val storage = storage { throw BackendNotSupportedException("No available keyring backend found") }
            storage.prompts = script.prompts

            storage.savePrivateKey("npub1a", key)

            assertEquals(key, storage.getPrivateKey("npub1a"))
            assertEquals(KeyStorageStatus.PasswordFile(hasPassword = true, unlocked = true), storage.status.value)
            assertTrue(folder.root.resolve("keys.ncryptsec").exists())
            job.cancel()
        }

    /** The status the login screen and Key Backup watch: Save keys turns NotKept back into a key file. */
    @Test
    fun saveKeysTakesBackDontRemember() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.DontRemember, NewPasswordAnswer.Password("correct horse battery"))
            val job = script.start(this)
            val storage = storage { throw BackendNotSupportedException("No available keyring backend found") }
            storage.prompts = script.prompts

            assertFailsWith<KeyNotKeptException> { storage.savePrivateKey("npub1a", key) }
            assertEquals(KeyStorageStatus.NotKept, storage.status.value)

            storage.allowKeepingKeys()
            assertEquals(KeyStorageStatus.PasswordFile(hasPassword = false, unlocked = false), storage.status.value)

            storage.savePrivateKey("npub1a", key)
            assertEquals(key, storage.getPrivateKey("npub1a"))
            assertEquals(KeyStorageStatus.PasswordFile(hasPassword = true, unlocked = true), storage.status.value)
            job.cancel()
        }

    @Test
    fun aBackendThatFailsToLoadIsNoBackend() =
        runBlocking<Unit> {
            val storage = storage { throw NoClassDefFoundError("org/freedesktop/dbus/transport/jre/NativeTransportProvider") }
            // No prompts attached: nothing can ask for a password, so nothing is kept, and it says so.
            assertFailsWith<SecureStorageException> { storage.savePrivateKey("npub1a", key) }
            assertIs<KeyStorageStatus.PasswordFile>(storage.status.value)
        }

    @Test
    fun selfTestWritesReadsAndDeletesAProbe() {
        val keyring = FakeKeyring()
        val result = storage { keyring }.selfTest()
        assertTrue(result.ok, result.detail)
        assertEquals(OsKeyring.SECRET_SERVICE, result.keyring)
        assertTrue(keyring.store.isEmpty())
    }

    @Test
    fun selfTestReportsABrokenBackend() {
        val result = storage { throw NoClassDefFoundError("at/favre/lib/crypto/HKDF") }.selfTest()
        assertFalse(result.ok)
        assertTrue(result.detail.contains("HKDF"), result.detail)
    }
}
