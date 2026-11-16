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
package com.vitorpamplona.amethyst.commons.connectedApps

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreNostrSignerPermissionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun stores() = AppPreferenceStores(rootFilesDir = { folder.root.toOkioPath() })

    /**
     * The hash is half the file name, so it has to keep producing exactly what
     * `MessageDigest.getInstance("SHA-256")` truncated to 8 bytes and formatted
     * with `"%02x"` produced on Android. Get it wrong and the old file is simply
     * never opened again — every permission the user granted that app is gone,
     * silently. The expected values are SHA-256 prefixes computed outside this
     * codebase.
     */
    @Test
    fun theFileNameMatchesTheAndroidImplementation() {
        assertEquals("nsp_12bafbcaa8bb08b6", DataStoreNostrSignerPermissionStore.nameFor("31990:abc:def"))
        assertEquals("nsp_12f134c5dae480dc", DataStoreNostrSignerPermissionStore.nameFor("wss://relay.example.com"))
        assertEquals("nsp_e3b0c44298fc1c14", DataStoreNostrSignerPermissionStore.nameFor(""))
    }

    /**
     * allPolicies enumerates the datastore directory, which used to be
     * `File.listFiles` and is now AppPreferenceStores.names. It must see only
     * the signer files — the shared settings and every other store live in the
     * same directory.
     */
    @Test
    fun namesSeesOnlyTheSignerFilesAndSurvivesAnEmptyDirectory() =
        runTest {
            val subject = stores()

            assertTrue("nothing written yet", subject.names("nsp_").isEmpty())

            // a read does not create the file, only a write does
            listOf("shared_settings", "search_history", "nsp_deadbeefdeadbeef", "nsp_0011223344556677").forEach {
                subject.getDataStore(it).edit { prefs -> prefs[booleanPreferencesKey("touch")] = true }
            }

            assertEquals(
                listOf("nsp_0011223344556677", "nsp_deadbeefdeadbeef"),
                subject.names("nsp_").sorted(),
            )
        }

    /**
     * A pairing check for a key that was never paired must not open a store: every distinct key a
     * stranger sends would otherwise leave a DataStore and its scope open for the life of the process.
     */
    @Test
    fun readingAnUnknownCoordinateOpensNoStore() =
        runTest {
            val stores = stores()
            val subject = DataStoreNostrSignerPermissionStore(stores)
            val unknown = "nip46:aaaa:bbbb"
            val name = DataStoreNostrSignerPermissionStore.nameFor(unknown)

            assertNull(subject.loadPolicy(unknown))
            assertNull(subject.loadOpDecision(unknown, NostrSignerOp.Encrypt))
            assertNull(subject.loadLastUsed(unknown))
            assertTrue(subject.allOpDecisions(unknown).isEmpty())
            assertFalse(stores.isOpen(name))

            subject.storePolicy(unknown, AppSignerPolicy.REASONABLE)
            assertEquals(AppSignerPolicy.REASONABLE, subject.loadPolicy(unknown))
            assertTrue(stores.isOpen(name))
        }

    /** A store written earlier (e.g. before an app restart) is found by its file, not only when open. */
    @Test
    fun aCoordinateWrittenBeforeIsReadBackAfterRelease() =
        runTest {
            val stores = stores()
            val subject = DataStoreNostrSignerPermissionStore(stores)
            val known = "nip46:aaaa:cccc"

            subject.storePolicy(known, AppSignerPolicy.REASONABLE)
            stores.release(DataStoreNostrSignerPermissionStore.nameFor(known))

            assertEquals(AppSignerPolicy.REASONABLE, subject.loadPolicy(known))
        }
}
