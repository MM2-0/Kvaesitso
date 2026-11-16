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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The read/write decisions that move account keys off the deprecated store.
 *
 * Every branch here can cost someone their account: reading a working store as
 * empty demotes a signing account to read-only, and clearing a key that was
 * only temporarily unreadable destroys it. The AndroidKeyStore itself cannot be
 * reached from a unit test, so [PrivateKeyVault] is faked and the logic above
 * it is what gets exercised.
 */
class AccountKeyStoreTest {
    private val npub = "npub1xxxx"
    private val key = "e5e2b1d3f6a94c8d7b0e1f2a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d"

    private class FakeVault(
        var stored: MutableMap<String, String> = mutableMapOf(),
        var failReads: Boolean = false,
        var failWrites: Boolean = false,
    ) : PrivateKeyVault {
        var saves = 0
        var deletes = 0

        override suspend fun get(npub: String): String? {
            if (failReads) throw IllegalStateException("store unreadable")
            return stored[npub]
        }

        override suspend fun save(
            npub: String,
            privKeyHex: String,
        ) {
            saves++
            if (failWrites) throw IllegalStateException("store unwritable")
            stored[npub] = privKeyHex
        }

        override suspend fun delete(npub: String) {
            deletes++
            if (failWrites) throw IllegalStateException("store unwritable")
            stored.remove(npub)
        }
    }

    // ── reads ─────────────────────────────────────────────────────────

    /** First load after the upgrade: the key is only in the legacy store, and moves across. */
    @Test
    fun aLegacyOnlyKeyIsReturnedAndMigrated() =
        runTest {
            val vault = FakeVault()

            val read = AccountKeyStore(vault).read(npub, legacyValue = key)

            assertEquals(key, read)
            assertEquals("migrated into the new store", key, vault.stored[npub])
        }

    @Test
    fun anAlreadyMigratedKeyIsReadFromTheNewStore() =
        runTest {
            val vault = FakeVault(mutableMapOf(npub to key))

            assertEquals(key, AccountKeyStore(vault).read(npub, legacyValue = key))
            assertEquals("already there, so not rewritten", 0, vault.saves)
        }

    /**
     * The dangerous one. A store that cannot be read must not look like an
     * account with no key — that would silently turn a signing account into a
     * read-only one.
     */
    @Test
    fun anUnreadableStoreFallsBackToLegacyRatherThanReportingNoKey() =
        runTest {
            val vault = FakeVault(failReads = true)

            assertEquals(key, AccountKeyStore(vault).read(npub, legacyValue = key))
        }

    /** And it must not try to migrate into a store that just failed. */
    @Test
    fun anUnreadableStoreIsNotWrittenTo() =
        runTest {
            val vault = FakeVault(failReads = true)

            AccountKeyStore(vault).read(npub, legacyValue = key)

            assertEquals(0, vault.saves)
        }

    /** An account genuinely without a key — external signer, or watch-only. */
    @Test
    fun noKeyAnywhereReadsAsNull() =
        runTest {
            val vault = FakeVault()

            assertNull(AccountKeyStore(vault).read(npub, legacyValue = null))
            assertEquals("nothing to migrate", 0, vault.saves)
        }

    /** A key added after the upgrade exists only in the new store. */
    @Test
    fun aNewStoreOnlyKeyIsReturned() =
        runTest {
            val vault = FakeVault(mutableMapOf(npub to key))

            assertEquals(key, AccountKeyStore(vault).read(npub, legacyValue = null))
        }

    /**
     * An npub is derived from its key, so the two stores disagreeing means
     * corruption. The older, proven store wins.
     */
    @Test
    fun aMismatchPrefersTheLegacyValue() =
        runTest {
            val vault = FakeVault(mutableMapOf(npub to "deadbeef"))

            assertEquals(key, AccountKeyStore(vault).read(npub, legacyValue = key))
        }

    /** A failed migration must not fail the account load; the legacy store still has it. */
    @Test
    fun aFailedMigrationStillReturnsTheKey() =
        runTest {
            val vault = FakeVault(failWrites = true)

            assertEquals(key, AccountKeyStore(vault).read(npub, legacyValue = key))
        }

    // ── writes ────────────────────────────────────────────────────────

    @Test
    fun aSaveMirrorsTheKey() =
        runTest {
            val vault = FakeVault()

            AccountKeyStore(vault).mirrorSave(npub, usesExternalSigner = false, privKeyHex = key)

            assertEquals(key, vault.stored[npub])
        }

    @Test
    fun anExternalSignerAccountClearsTheKey() =
        runTest {
            val vault = FakeVault(mutableMapOf(npub to key))

            AccountKeyStore(vault).mirrorSave(npub, usesExternalSigner = true, privKeyHex = null)

            assertTrue(vault.stored.isEmpty())
        }

    /**
     * The case that is easy to get wrong. With no external signer and no key in
     * hand, the legacy store leaves the stored key alone — so this must too.
     * Deleting here would drop the key on every save from a session that never
     * decrypted it.
     */
    @Test
    fun aSaveWithoutAKeyInHandLeavesTheStoredKeyAlone() =
        runTest {
            val vault = FakeVault(mutableMapOf(npub to key))

            AccountKeyStore(vault).mirrorSave(npub, usesExternalSigner = false, privKeyHex = null)

            assertEquals(key, vault.stored[npub])
            assertEquals(0, vault.deletes)
            assertEquals(0, vault.saves)
        }

    /** A write failure must never fail the save: the legacy store is still written. */
    @Test
    fun aWriteFailureIsSwallowed() =
        runTest {
            val vault = FakeVault(failWrites = true)

            AccountKeyStore(vault).mirrorSave(npub, usesExternalSigner = false, privKeyHex = key)
        }

    @Test
    fun deleteRemovesFromTheNewStore() =
        runTest {
            val vault = FakeVault(mutableMapOf(npub to key))

            AccountKeyStore(vault).delete(npub)

            assertTrue(vault.stored.isEmpty())
        }
}
