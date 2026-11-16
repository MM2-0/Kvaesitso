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
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import okio.Path

/**
 * The per-account, non-secret preference store: one DataStore file per npub,
 * at `<root>/datastore/<npub>.preferences_pb`.
 *
 * The root directory is injected rather than discovered so every front end can
 * say where its data lives — `filesDir` on Android, the app data directory on
 * desktop, a temp folder in tests — and so this class needs no platform API of
 * its own.
 *
 * Built on [PreferenceDataStoreFactory.createWithPath], the okio-based factory,
 * because the `java.io.File` overloads are absent on Apple targets.
 *
 * [migrations] runs once per account, before that account's store answers its
 * first read. Android supplies one that copies out of the legacy
 * SharedPreferences; front ends with no history supply none.
 */
class AccountPreferenceStores(
    val rootFilesDir: () -> Path,
    private val migrations: (npub: String) -> List<DataMigration<Preferences>> = { emptyList() },
) {
    /**
     * One store per account, each on a scope this class can cancel.
     *
     * DataStore keeps a process-wide registry keyed by file path and only
     * releases an entry when the owning scope ends. Left to create its own
     * internal scope, a store is never released, and deleting an account then
     * adding it again in the same session throws "multiple DataStores active
     * for the same file".
     */
    private class Entry(
        val scope: CoroutineScope,
        val store: DataStore<Preferences>,
    )

    private val storeCache = LargeCache<String, Entry>()

    fun file(npub: String): Path = rootFilesDir() / "datastore" / "$npub.preferences_pb"

    fun getDataStore(npub: String): DataStore<Preferences> =
        storeCache
            .getOrCreate(npub) {
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                Entry(
                    scope,
                    PreferenceDataStoreFactory.createWithPath(
                        scope = scope,
                        migrations = migrations(npub),
                        produceFile = { file(npub) },
                    ),
                )
            }.store

    /**
     * Drops the account's stored preferences.
     *
     * The live store is shut down first: deleting the file underneath one
     * would leave it writing the account's settings back out on the next edit,
     * re-creating what this call is meant to erase — and would keep the path
     * registered, so the same account could not be added again.
     *
     * Cancelling is not enough on its own, and this is suspend for that
     * reason. `cancel()` only *asks*; DataStore releases the path when the
     * scope's job actually completes, so a store opened on it before then
     * still throws "multiple DataStores active for the same file". The window
     * is small enough to pass locally and fail on a loaded CI runner.
     */
    suspend fun removeAccount(npub: String): Boolean {
        storeCache.get(npub)?.scope?.let {
            it.cancel()
            it.coroutineContext.job.join()
        }
        storeCache.remove(npub)
        val path = file(npub)
        if (!platformFileSystem.exists(path)) return false
        platformFileSystem.delete(path)
        return true
    }
}
