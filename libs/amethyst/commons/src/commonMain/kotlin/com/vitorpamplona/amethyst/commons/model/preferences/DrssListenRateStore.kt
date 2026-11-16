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
import com.vitorpamplona.amethyst.commons.drss.DrssListenRates
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-account persistence for the playback speed of each drss feed ([DrssListenRates]), saved as
 * `pubkey=rate` strings. Loads this account's speeds on construction, then writes every change.
 */
@Stable
class DrssListenRateStore(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val pubKeyHex: HexKey,
    private val rates: DrssListenRates,
) {
    private val key = stringSetPreferencesKey("$KEY_PREFIX$pubKeyHex")

    init {
        scope.launch {
            restoreFromDisk()
            rates.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val raw = store.data.first()[key] ?: return
            rates.restore(
                raw
                    .mapNotNull { entry ->
                        val feed = entry.substringBefore('=', "")
                        val rate = entry.substringAfter('=', "").toFloatOrNull()
                        if (feed.isEmpty() || rate == null) null else feed to rate
                    }.toMap(),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error reading drss playback speeds", e)
        }
    }

    private suspend fun persist(map: Map<HexKey, Float>) {
        try {
            store.edit { prefs -> prefs[key] = map.mapTo(mutableSetOf()) { (feed, rate) -> "$feed=$rate" } }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error writing drss playback speeds", e)
        }
    }

    companion object {
        private const val TAG = "DrssListenRateStore"
        private const val KEY_PREFIX = "drss.listenRates."
    }
}
