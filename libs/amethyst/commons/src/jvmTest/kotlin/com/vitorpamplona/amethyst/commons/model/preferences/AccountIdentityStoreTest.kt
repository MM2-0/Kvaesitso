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

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AccountIdentityStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun raw(migrations: List<DataMigration<Preferences>> = emptyList()): DataStore<Preferences> {
        val file = File(folder.root, "identity_${seq++}.preferences_pb")
        return PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            migrations = migrations,
            produceFile = { file.toOkioPath() },
        )
    }

    @Test
    fun defaultsMatchTheLegacyOnes() =
        runTest {
            val loaded = AccountIdentityStore(raw()).load()

            assertNull(loaded.pubKeyHex)
            assertEquals(false, loaded.loginWithExternalSigner)
            assertNull(loaded.externalSignerPackageName)
            assertEquals(emptySet<String>(), loaded.localRelayServers)
            assertNull(loaded.openBackupConflictsJson)
        }

    @Test
    fun roundTrip() =
        runTest {
            val store = AccountIdentityStore(raw())
            val value =
                AccountIdentity(
                    pubKeyHex = "aabbcc",
                    loginWithExternalSigner = true,
                    externalSignerPackageName = "com.greenart7c3.nostrsigner",
                    localRelayServers = setOf("ws://localhost:4869"),
                    openBackupConflictsJson = """[["a","b","c"]]""",
                )

            store.save(value)

            assertEquals(value, store.load())
        }

    /**
     * Dropping an external signer, clearing the local relays or answering the
     * last backup conflict has to leave those keys absent — not holding
     * yesterday's value.
     */
    @Test
    fun savingEmptyValuesClearsWhatWasThere() =
        runTest {
            val store = AccountIdentityStore(raw())
            store.save(
                AccountIdentity(
                    pubKeyHex = "aabbcc",
                    loginWithExternalSigner = true,
                    externalSignerPackageName = "com.example.signer",
                    localRelayServers = setOf("ws://localhost:4869"),
                    openBackupConflictsJson = "[]",
                ),
            )

            store.save(AccountIdentity(pubKeyHex = "aabbcc"))

            val loaded = store.load()
            assertEquals("aabbcc", loaded.pubKeyHex)
            assertEquals(false, loaded.loginWithExternalSigner)
            assertNull(loaded.externalSignerPackageName)
            assertEquals(emptySet<String>(), loaded.localRelayServers)
            assertNull(loaded.openBackupConflictsJson)
        }

    /** Absent means "already backed up elsewhere", so the nudge stays off. */
    @Test
    fun hasBackedUpKeysDefaultsToTrue() =
        runTest {
            assertEquals(true, AccountIdentityStore(raw()).hasBackedUpKeys())
        }

    /**
     * The nudge writes this on its own. A group save must not carry a value
     * its caller never knew about and flip the nudge back on.
     */
    @Test
    fun aGroupSaveLeavesHasBackedUpKeysAlone() =
        runTest {
            val store = AccountIdentityStore(raw())
            store.setHasBackedUpKeys(false)

            store.save(AccountIdentity(pubKeyHex = "aabbcc", localRelayServers = setOf("ws://x")))

            assertEquals(false, store.hasBackedUpKeys())
        }

    @Test
    fun theLegacyCopyCarriesEveryFieldIncludingTheBackupFlag() =
        runTest {
            val legacy =
                FakeLegacySource(
                    mapOf(
                        "nostr_pubkey" to "aabbcc",
                        "login_with_external_signer" to true,
                        "signer_package_name" to "com.example.signer",
                        "localRelayServers" to setOf("ws://localhost:4869"),
                        "openBackupConflicts" to """[["a","b","c"]]""",
                        "has_backed_up_keys" to false,
                    ),
                )

            val store = AccountIdentityStore(raw(listOf(AccountIdentityStore.legacyTable.migration { legacy })))

            assertEquals(
                AccountIdentity(
                    pubKeyHex = "aabbcc",
                    loginWithExternalSigner = true,
                    externalSignerPackageName = "com.example.signer",
                    localRelayServers = setOf("ws://localhost:4869"),
                    openBackupConflictsJson = """[["a","b","c"]]""",
                ),
                store.load(),
            )
            assertEquals(false, store.hasBackedUpKeys())
        }

    /**
     * The fallback is all-or-nothing, and that is the point: with no pubkey
     * the store answered from `emptyPreferences()`, where an absent boolean and
     * a false one are the same value. Merging field by field would report an
     * external-signer account as a local one — and a local one has no signer,
     * so the account would go read-only.
     */
    @Test
    fun anIdentityWithoutAPubKeyFallsBackWholesale() {
        val legacy =
            AccountIdentity(
                pubKeyHex = "aabbcc",
                loginWithExternalSigner = true,
                externalSignerPackageName = "com.example.signer",
            )

        assertEquals(legacy, AccountIdentity().orIfUnusable { legacy })
    }

    @Test
    fun anIdentityWithAPubKeyIsAuthoritative() {
        val stored = AccountIdentity(pubKeyHex = "aabbcc")
        var called = false

        val result =
            stored.orIfUnusable {
                called = true
                AccountIdentity(pubKeyHex = "ddeeff")
            }

        assertSame(stored, result)
        assertTrue(!called)
    }
}
