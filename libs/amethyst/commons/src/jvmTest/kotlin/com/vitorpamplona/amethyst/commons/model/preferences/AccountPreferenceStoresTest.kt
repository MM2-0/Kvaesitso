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

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AccountPreferenceStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    /**
     * The root is resolved once, outside the lambda: [AccountPreferenceStores]
     * calls `rootFilesDir()` on every file() and a lambda that allocated a new
     * directory each time would hand out a different path per call.
     */
    private fun stores(): AccountPreferenceStores {
        val root = File(folder.root, "r${seq++}").apply { mkdirs() }
        return AccountPreferenceStores(rootFilesDir = { root.toOkioPath() })
    }

    private val key = stringPreferencesKey("k")

    @Test
    fun valuesRoundTripPerAccount() =
        runTest {
            val subject = stores()

            subject.getDataStore("npub1a").edit { it[key] = "a" }
            subject.getDataStore("npub1b").edit { it[key] = "b" }

            assertEquals("a", subject.getDataStore("npub1a").data.first()[key])
            assertEquals("b", subject.getDataStore("npub1b").data.first()[key])
        }

    @Test
    fun removingAnAccountDeletesItsFile() =
        runTest {
            val subject = stores()
            subject.getDataStore("npub1a").edit { it[key] = "a" }

            assertTrue(subject.removeAccount("npub1a"))
            assertFalse(subject.file("npub1a").toFile().exists())
        }

    /**
     * Deleting an account and adding it again in the same session.
     *
     * DataStore keeps a process-wide registry keyed by file path and only
     * releases an entry when its scope ends, so a store that is merely dropped
     * from the cache keeps the path claimed — and this throws "multiple
     * DataStores active for the same file".
     */
    @Test
    fun anAccountCanBeAddedAgainAfterRemoval() =
        runTest {
            val subject = stores()
            subject.getDataStore("npub1a").edit { it[key] = "before" }
            subject.removeAccount("npub1a")

            val reopened = subject.getDataStore("npub1a")

            assertNull("the old value is gone", reopened.data.first()[key])
            reopened.edit { it[key] = "after" }
            assertEquals("after", subject.getDataStore("npub1a").data.first()[key])
        }

    @Test
    fun removingAnUnknownAccountReportsNothingDeleted() =
        runTest {
            assertFalse(stores().removeAccount("npub1missing"))
        }
}
