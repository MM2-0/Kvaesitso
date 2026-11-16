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
package com.vitorpamplona.amethyst.commons.connectedApps.nip46

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreNip46ClientStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { folder.root.toOkioPath() / "$name.preferences_pb" },
        )

    /**
     * The coordinate hash is a stored key, so it has to keep producing exactly
     * what the Android implementation did — `MessageDigest.getInstance("SHA-256")`
     * truncated to 8 bytes and formatted with `"%02x"`. A different digest would
     * not fail; it would quietly orphan every client the user has authorized.
     *
     * The expected values are SHA-256 prefixes computed outside this codebase, so
     * this is a cross-check rather than a restatement of the implementation.
     */
    @Test
    fun theCoordinateHashMatchesTheAndroidImplementation() {
        assertEquals("12bafbcaa8bb08b6", DataStoreNip46ClientStore.hash("31990:abc:def"))
        assertEquals("12f134c5dae480dc", DataStoreNip46ClientStore.hash("wss://relay.example.com"))
        assertEquals("e3b0c44298fc1c14", DataStoreNip46ClientStore.hash(""))
    }

    /** 16 lower-case hex characters, always — it is half a key name. */
    @Test
    fun theHashIsAlwaysSixteenLowerCaseHexChars() {
        listOf("a", "a longer coordinate with spaces", "ünïcödé", "31990:".repeat(50)).forEach {
            val h = DataStoreNip46ClientStore.hash(it)
            assertEquals("wrong length for '$it'", 16, h.length)
            assertEquals("not lower-case hex for '$it'", h, h.lowercase().filter { c -> c in "0123456789abcdef" })
        }
    }

    @Test
    fun aStoredClientComesBack() =
        runTest {
            val subject = DataStoreNip46ClientStore(store("clients"))
            val coordinate = "31990:pubkeyhex:handler"

            assertNull(subject.load(coordinate))

            subject.store(
                coordinate,
                Nip46ClientInfo(name = "Test App", url = "https://x", image = "https://x/y.png", relays = setOf("wss://a", "wss://b")),
            )

            val loaded = subject.load(coordinate)!!
            assertEquals("Test App", loaded.name)
            assertEquals("https://x", loaded.url)
            assertEquals("https://x/y.png", loaded.image)
            assertEquals(setOf("wss://a", "wss://b"), loaded.relays)

            assertEquals(mapOf(coordinate to loaded), subject.all())

            subject.remove(coordinate)
            assertNull(subject.load(coordinate))
        }
}
