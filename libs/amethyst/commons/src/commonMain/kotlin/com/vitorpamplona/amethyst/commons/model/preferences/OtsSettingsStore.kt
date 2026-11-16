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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.OtsSettings
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persistent storage for [OtsSettings] — which blockchain explorer the user
 * has pointed OpenTimestamps at.
 *
 * App-wide, not per-account, and shares [AppPreferenceStores.SHARED_SETTINGS]
 * with the other global settings groups under its own `ots.` key prefix.
 *
 * Lives in `jvmAndroid` rather than `commonMain` only because [OtsSettings]
 * does: it names OkHttp's explorer constants, and OkHttp is JVM-bound. Android
 * and Desktop still share it.
 *
 * [initial] is taken rather than read here because the current value has to be
 * available synchronously from [current] — a resolver builder reads it from a
 * non-suspending lambda. The caller loads it with [load] and decides how to
 * wait; commonMain has no `runBlocking` to hide that decision behind.
 */
@Stable
class OtsSettingsStore(
    private val store: DataStore<Preferences>,
    initial: OtsSettings,
) {
    companion object {
        val KEY_CUSTOM_EXPLORER_URL = stringPreferencesKey("ots.customExplorerUrl")

        /** The stored settings, or [OtsSettings.DEFAULT] if unset or unreadable. */
        suspend fun load(store: DataStore<Preferences>): OtsSettings =
            try {
                val url = store.data.first()[KEY_CUSTOM_EXPLORER_URL]?.takeIf { it.isNotBlank() }
                OtsSettings(customExplorerUrl = url)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("OtsSettingsStore") { "Error reading DataStore: ${e.message}" }
                OtsSettings.DEFAULT
            }
    }

    private val _settings = MutableStateFlow(initial)
    val settings: StateFlow<OtsSettings> = _settings

    /** Synchronous snapshot — safe to call from resolver builder lambdas. */
    val current: OtsSettings get() = _settings.value

    suspend fun setCustomExplorerUrl(url: String?) {
        val normalized = url?.trim()?.takeIf { it.isNotBlank() }
        persist(current.copy(customExplorerUrl = normalized))
    }

    suspend fun reset() {
        persist(OtsSettings.DEFAULT)
    }

    private suspend fun persist(settings: OtsSettings) {
        _settings.value = settings
        try {
            store.edit { prefs ->
                val customExplorerUrl = settings.customExplorerUrl
                if (customExplorerUrl != null) {
                    prefs[KEY_CUSTOM_EXPLORER_URL] = customExplorerUrl
                } else {
                    prefs.remove(KEY_CUSTOM_EXPLORER_URL)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("OtsSettingsStore") { "Error writing DataStore: ${e.message}" }
        }
    }
}
