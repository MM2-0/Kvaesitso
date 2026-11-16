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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettings
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettingsState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-account persistence for [NowPlayingSettings]. Keys are namespaced by pubkey because the
 * status is published by that account. Loads the saved settings into [state] on construction, then
 * writes every later change back. Construct once per account, eagerly.
 */
@Stable
class NowPlayingSettingsStore(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
    pubKeyHex: HexKey,
    private val state: NowPlayingSettingsState,
) {
    private val shareInApp = booleanPreferencesKey("$KEY_PREFIX$pubKeyHex.shareInApp")
    private val shareOtherApps = booleanPreferencesKey("$KEY_PREFIX$pubKeyHex.shareOtherApps")
    private val blockedApps = stringSetPreferencesKey("$KEY_PREFIX$pubKeyHex.blockedApps")
    private val knownApps = stringSetPreferencesKey("$KEY_PREFIX$pubKeyHex.knownApps")

    init {
        scope.launch {
            restoreFromDisk()
            // drop(1) skips the value present at collection start, which restoreFromDisk already wrote.
            state.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val prefs = store.data.first()
            state.restore(
                NowPlayingSettings(
                    shareInApp = prefs[shareInApp] ?: false,
                    shareOtherApps = prefs[shareOtherApps] ?: false,
                    blockedApps = prefs[blockedApps] ?: emptySet(),
                    knownApps = decodeApps(prefs[knownApps] ?: emptySet()),
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NowPlayingSettingsStore", "Error reading now playing settings", e)
            // Carry on with the defaults, so whatever waits for the saved settings does not wait forever.
            state.restore(state.flow.value)
        }
    }

    private suspend fun persist(value: NowPlayingSettings) {
        try {
            store.edit { prefs ->
                prefs[shareInApp] = value.shareInApp
                prefs[shareOtherApps] = value.shareOtherApps
                prefs[blockedApps] = value.blockedApps
                prefs[knownApps] = encodeApps(value.knownApps)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NowPlayingSettingsStore", "Error writing now playing settings", e)
        }
    }

    companion object {
        private const val KEY_PREFIX = "nowPlaying."
        private const val SEPARATOR = '\t'

        fun encodeApps(apps: Map<String, String>): Set<String> = apps.mapTo(mutableSetOf()) { (id, label) -> "$id$SEPARATOR$label" }

        fun decodeApps(raw: Set<String>): Map<String, String> =
            raw
                .mapNotNull { entry ->
                    val split = entry.indexOf(SEPARATOR)
                    if (split <= 0) null else entry.substring(0, split) to entry.substring(split + 1)
                }.toMap()
    }
}
