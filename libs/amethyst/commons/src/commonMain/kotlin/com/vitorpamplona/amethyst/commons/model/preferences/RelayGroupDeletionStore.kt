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
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupDeletions
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Device-global persistence for the set of deleted NIP-29 relay-group channels ([RelayGroupDeletions]),
 * so a channel the user deleted (kind-9008) stays gone across a restart — even if the host relay keeps
 * re-announcing a stale kind-44100 for it. Mirrors [BuzzChannelStarPreferences]: app-wide (not
 * per-account), loads the saved keys into the singleton on construction, then writes every later change
 * back. Construct once, eagerly.
 */
@Stable
class RelayGroupDeletionStore(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    init {
        scope.launch {
            restoreFromDisk()
            // drop(1) skips the value present at collection start, which restoreFromDisk already wrote.
            RelayGroupDeletions.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val raw = store.data.first()[KEY] ?: return
            if (raw.isNotEmpty()) RelayGroupDeletions.restore(raw)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RelayGroupDeletionPrefs") { "Error reading deleted channels: ${e.message}" }
        }
    }

    private suspend fun persist(keys: Set<String>) {
        try {
            store.edit { prefs -> prefs[KEY] = keys }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RelayGroupDeletionPrefs") { "Error writing deleted channels: ${e.message}" }
        }
    }

    companion object {
        private val KEY = stringSetPreferencesKey("nip29.deletedChannels")
    }
}
