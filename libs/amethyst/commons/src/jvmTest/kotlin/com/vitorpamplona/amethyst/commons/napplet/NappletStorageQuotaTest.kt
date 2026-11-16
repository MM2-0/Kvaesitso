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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NappletStorageQuotaTest {
    @get:Rule val folder = TemporaryFolder()

    private fun storage() = DataStoreNappletStorage(AppPreferenceStores(rootFilesDir = { folder.root.absolutePath.toPath() }).getDataStore("napplet_storage")) { "ab".repeat(32) }

    // Every inline Run is a new instance id. A quota per instance let an app write 512 KB more on
    // every run, without end, into a store the app keeps in memory.
    @Test
    fun instancesShareTheAppsQuota() =
        runBlocking {
            val store = storage()
            val app = NappletIdentity(authorPubKey = "cd".repeat(32), identifier = "game", aggregateHash = "ef".repeat(32))
            val chunk = "x".repeat(200 * 1024)

            var refused = 0
            for (run in 1..6) {
                val instance = app.copy(instanceId = "run-$run").instanceStorageCoordinate
                if (runCatching { store.set(instance, "blob", chunk, quotaCoordinate = app.storageCoordinate) }.isFailure) refused++
            }

            assertEquals("only two 200 KB writes fit in one 512 KB quota", 4, refused)
        }

    @Test
    fun aNewKeyIsChargedForItsName() =
        runBlocking {
            val store = storage()
            val coordinate = "app"
            val nameBytes = 100
            store.set(coordinate, "a", "x".repeat(DataStoreNappletStorage.MAX_STORAGE_BYTES - 1 - nameBytes))
            val tooBig = runCatching { store.set(coordinate, "k".repeat(nameBytes), "y") }

            assertTrue("a new key's own name must count against the quota", tooBig.isFailure)
        }

    @Test
    fun replacingAValueIsChargedOnlyForTheDifference() =
        runBlocking {
            val store = storage()
            val big = "x".repeat(DataStoreNappletStorage.MAX_STORAGE_BYTES - 10)
            store.set("app", "k", big)
            store.set("app", "k", big)

            assertEquals(big, store.get("app", "k"))
        }

    // An app whose namespace happens to start with another's (same author, a `d` tag that extends the
    // other's, no artifact hash) must not spend that app's quota.
    @Test
    fun anAppWhoseNameExtendsAnothersHasItsOwnQuota() =
        runBlocking {
            val store = storage()
            val game = NappletIdentity(authorPubKey = "cd".repeat(32), identifier = "game")
            val other = NappletIdentity(authorPubKey = "cd".repeat(32), identifier = "game:x")
            val nearlyFull = "x".repeat(DataStoreNappletStorage.MAX_STORAGE_BYTES - 1_000)
            store.set(other.storageCoordinate, "k", nearlyFull, quotaCoordinate = other.storageCoordinate)

            val ok = runCatching { store.set(game.storageCoordinate, "k", "y".repeat(10_000), quotaCoordinate = game.storageCoordinate) }

            assertTrue("game's quota is its own: ${ok.exceptionOrNull()}", ok.isSuccess)
        }
}
