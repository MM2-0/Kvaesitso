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
package com.vitorpamplona.amethyst.model.nip60Cashu

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.cashu.CashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.cashu.DataStoreCashuCounterStore
import com.vitorpamplona.amethyst.commons.model.preferences.CopyOnceMigration
import com.vitorpamplona.quartz.utils.cache.LargeCache

/**
 * Android's per-account NUT-13 counter store: the shared
 * [DataStoreCashuCounterStore] over a file in the app's data directory.
 *
 * Two older layers feed into it, and neither may move a counter backwards:
 *
 *  - `cashu_prefs_<npub>` SharedPreferences, copied in full on first read by
 *    [CopyOnceMigration]. The copy happens inside the same atomic DataStore
 *    write that records it happened, so a crash cannot leave the marker set
 *    with the counters missing. It is a copy, not a move: the old file stays
 *    intact, so a rolled-back build still finds its counters.
 *  - `AccountSettings.cashuKeysetCounters`, an older in-settings map, still
 *    applied per keyset through `seedIfMissing` on every read.
 *
 * Losing a counter here means restarting a keyset at zero and reusing
 * indices, which costs real ecash — so nothing on this path is best-effort.
 */
object CashuPreferences {
    private const val LEGACY_FILE_PREFIX = "cashu_prefs_"

    /** The store file name for [npub], as AppPreferenceStores takes it. */
    const val FILE_PREFIX = "cashu_"

    fun fileName(npub: String) = FILE_PREFIX + npub

    private val stores = LargeCache<String, CashuKeysetCounterStore>()

    /**
     * Per-account instance, cached: DataStore refuses two live instances over
     * one file, and a second instance would defeat the single-writer
     * serialisation that `reserve` depends on.
     */
    fun forAccount(npub: String): CashuKeysetCounterStore =
        stores.getOrCreate(npub) {
            DataStoreCashuCounterStore(Amethyst.instance.appStores.getDataStore(fileName(npub)))
        }

    /**
     * The copy out of `cashu_prefs_<npub>`, wired to the file by AppModules
     * rather than attached here.
     *
     * DataStore runs a file's migrations when that file is first opened, and
     * the holder is what opens it, so the migration has to be registered with
     * the holder or it would never run.
     */
    fun legacyMigration(
        context: Context,
        npub: String,
    ) = CopyOnceMigration("migrated.cashuCounters") { out ->
        val legacy = context.getSharedPreferences("$LEGACY_FILE_PREFIX$npub", Context.MODE_PRIVATE)
        legacy.all.forEach { (key, value) ->
            if (key.startsWith(DataStoreCashuCounterStore.COUNTER_PREFIX) && value is Long) {
                out[longPreferencesKey(key)] = value
            }
        }
    }
}
