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
 * The app-wide DataStore files — the ones that belong to the install rather
 * than to an account.
 *
 * [AccountPreferenceStores] is the same idea keyed by npub; this is keyed by
 * file name, because these stores are one-per-app and several of them share a
 * single file under different key prefixes (see [SHARED_SETTINGS]).
 *
 * # Why a holder rather than a `Context` delegate
 *
 * Android's `Context.preferencesDataStore(name)` delegate does this job, but
 * only on Android and only from a `Context`. Taking the root directory as a
 * parameter is what lets the stores themselves live in `commonMain` — every
 * front end says where its data lives: `filesDir` on Android, the app data
 * directory on desktop, a temp folder in tests.
 *
 * # The paths are the delegate's paths
 *
 * `preferencesDataStore(name = "x")` resolves to
 * `filesDir/datastore/x.preferences_pb`, and [file] reproduces that exactly.
 * Wired with `filesDir` as the root, a store moved off the delegate onto this
 * holder opens the file it was already using, so nothing has to be migrated
 * and a rollback finds its data where it left it. Changing [file]'s shape
 * would silently orphan every existing install's settings.
 *
 * @param migrations the migrations to attach to a file, by name. Taken here
 *   rather than at [getDataStore] because DataStore runs a file's migrations
 *   once, when that file is first opened — and several stores share
 *   [SHARED_SETTINGS], so which one opens it is a race. Wiring the migrations
 *   to the file rather than to a caller is what makes the copy happen no
 *   matter who wins.
 */
class AppPreferenceStores(
    val rootFilesDir: () -> Path,
    private val migrations: (String) -> List<DataMigration<Preferences>> = { emptyList() },
) {
    companion object {
        /**
         * The file that UI, Tor, OTS, Namecoin and several smaller settings
         * groups all share, each under its own key prefix (`ui.`, `tor.`, …).
         *
         * One file rather than one per group, which is how it has always been
         * on Android: these are read together at startup, and a DataStore is
         * a whole-file read.
         */
        const val SHARED_SETTINGS = "shared_settings"

        private const val SUFFIX = ".preferences_pb"
    }

    /**
     * One store per file, each on a scope this class owns.
     *
     * DataStore keeps a process-wide registry keyed by file path and refuses a
     * second store on a path that already has a live one. Handing out a new
     * store per call is therefore a crash rather than a waste — it has already
     * happened twice in this codebase — so going through the cache is the
     * point of the class, not an optimisation.
     */
    private class Entry(
        val scope: CoroutineScope,
        val store: DataStore<Preferences>,
    )

    private val storeCache = LargeCache<String, Entry>()

    fun file(name: String): Path = rootFilesDir() / "datastore" / "$name$SUFFIX"

    fun getDataStore(name: String): DataStore<Preferences> =
        storeCache
            .getOrCreate(name) {
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                Entry(
                    scope,
                    PreferenceDataStoreFactory.createWithPath(
                        scope = scope,
                        migrations = migrations(name),
                        produceFile = { file(name) },
                    ),
                )
            }.store

    /**
     * The store for [name] if it is already open or its file exists, else null — without opening one.
     *
     * For read paths keyed by untrusted input (the signer permissions look up an `nsp_<hash>` file per
     * remote client): [getDataStore] would open, and keep for the life of the process, a store and a
     * scope for every key ever asked about, even though reading never creates the file. A missing
     * file means nothing was ever written, so the caller can answer "absent" directly.
     */
    fun getDataStoreIfExists(name: String): DataStore<Preferences>? {
        storeCache.get(name)?.let { return it.store }
        return if (platformFileSystem.exists(file(name))) getDataStore(name) else null
    }

    /** Whether a store for [name] is open in this process. For tests. */
    fun isOpen(name: String): Boolean = storeCache.get(name) != null

    /** The file UI, Tor, OTS, Namecoin and friends share. */
    fun sharedSettings(): DataStore<Preferences> = getDataStore(SHARED_SETTINGS)

    /**
     * Releases the store for [name], so the file can be opened again.
     *
     * DataStore keeps a process-wide registry keyed by path and refuses a second
     * live instance, and `cancel()` only *asks* a scope to stop — the registry
     * entry survives until the owning job actually completes, which is why this
     * joins. Getting that wrong produced "there are multiple DataStores active
     * for the same file" twice in this codebase already.
     *
     * Production has no reason to call this: these stores live as long as the
     * process. It exists so a test can write a file, let go of it, and reopen it
     * to check what is actually on disk — the one thing that was impossible
     * before, and the reason the migration-guard test had to be driven against
     * the DataMigration directly instead.
     *
     * Returns false if nothing was open under that name.
     */
    suspend fun release(name: String): Boolean {
        val entry = storeCache.get(name) ?: return false

        entry.scope.cancel()
        entry.scope.coroutineContext.job
            .join()
        storeCache.remove(name)
        return true
    }

    /**
     * The names of stores already on disk whose name starts with [prefix].
     *
     * For the store families that are one file per key rather than one file —
     * the signer permissions keep an `nsp_<hash>` file per app — where the only
     * way to enumerate what exists is to look. Reads the directory, so callers
     * keep it off the main thread.
     *
     * Returns names in the form [getDataStore] takes, with the directory and
     * the `.preferences_pb` suffix stripped, and an empty list when nothing has
     * been written yet.
     */
    fun names(prefix: String): List<String> {
        val dir = rootFilesDir() / "datastore"
        if (!platformFileSystem.exists(dir)) return emptyList()

        return platformFileSystem
            .list(dir)
            .map { it.name }
            .filter { it.startsWith(prefix) && it.endsWith(SUFFIX) }
            .map { it.removeSuffix(SUFFIX) }
    }
}
