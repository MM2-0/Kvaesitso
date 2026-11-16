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

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupAdminCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Device-global persistence for [RelayGroupAdminCache] — the last-known admins of the NIP-29 groups
 * the user is in — so the migration/fork check can read those admins' kind-10009 lists even after a
 * restart while the group's host relay is down. Mirrors [RelayGroupDeletionStore]: loads the saved
 * map into the singleton on construction, then writes every later change back. Construct once.
 */
@Stable
class RelayGroupAdminCacheStore(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    init {
        scope.launch {
            // What the disk holds; only a map that differs from it is written back. Comparing instead
            // of dropping the flow's first value also persists a remember() that raced the restore.
            var onDisk = restoreFromDisk()
            RelayGroupAdminCache.flow.collect {
                if (it != onDisk) {
                    persist(it)
                    onDisk = it
                }
            }
        }
    }

    /** Merges the saved map into the cache (in-memory entries win) and returns what the disk held. */
    private suspend fun restoreFromDisk(): Map<String, Set<HexKey>> {
        val fromDisk =
            try {
                store.data.first()[KEY]?.let(::decode) ?: emptyMap()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("RelayGroupAdminCache") { "Error reading cached group admins: ${e.message}" }
                emptyMap()
            }
        RelayGroupAdminCache.restore(fromDisk)
        return fromDisk
    }

    private suspend fun persist(map: Map<String, Set<HexKey>>) {
        try {
            store.edit { prefs -> prefs[KEY] = encode(map) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RelayGroupAdminCache") { "Error writing cached group admins: ${e.message}" }
        }
    }

    companion object {
        private val KEY = stringSetPreferencesKey("nip29.groupAdmins")

        // One entry per group: "<group key>\t<pubkey>,<pubkey>…". Group keys are `id@relay-url`
        // and pubkeys are hex, so neither contains a tab or a comma.
        private const val SEP = '\t'

        fun encode(map: Map<String, Set<HexKey>>): Set<String> = map.entries.mapTo(HashSet()) { (key, admins) -> key + SEP + admins.joinToString(",") }

        fun decode(raw: Set<String>): Map<String, Set<HexKey>> =
            raw
                .mapNotNull { entry ->
                    val idx = entry.lastIndexOf(SEP)
                    if (idx <= 0) return@mapNotNull null
                    val admins = entry.substring(idx + 1).split(',').filterTo(HashSet()) { it.length == 64 }
                    if (admins.isEmpty()) null else entry.substring(0, idx) to admins
                }.toMap()
    }
}
