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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persistent storage for [NamecoinSettings] — which ElectrumX servers and
 * backend resolve `.bit` names, and the certificates the user has pinned.
 *
 * App-wide, not per-account, and shares [AppPreferenceStores.SHARED_SETTINGS]
 * with the other global settings groups under its own `namecoin.` key prefix.
 *
 * The current settings are available synchronously via [settings] (a
 * [StateFlow]) and can be read in non-suspend contexts (e.g. in a
 * `serverListProvider` lambda).
 */
@Stable
class NamecoinSettingsStore(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val KEY_ENABLED = booleanPreferencesKey("namecoin.enabled")
        val KEY_CUSTOM_SERVERS = stringPreferencesKey("namecoin.customServers")
        val KEY_PINNED_CERTS = stringPreferencesKey("namecoin.pinnedCerts")
        val KEY_BACKEND = stringPreferencesKey("namecoin.backend")
        val KEY_CORE_RPC = stringPreferencesKey("namecoin.coreRpc")
        val KEY_FALLBACK_CUSTOM_EX = booleanPreferencesKey("namecoin.fallback.customElectrumx")
        val KEY_FALLBACK_DEFAULT_EX = booleanPreferencesKey("namecoin.fallback.defaultElectrumx")
    }

    /**
     * Current settings, loaded synchronously at init to avoid races.
     */
    private val _settings = MutableStateFlow(NamecoinSettings.DEFAULT)
    val settings: StateFlow<NamecoinSettings> = _settings

    init {
        scope.launch {
            _settings.tryEmit(loadFromDisk() ?: NamecoinSettings.DEFAULT)
        }
    }

    /** Synchronous snapshot — safe to call from `serverListProvider` lambdas. */
    val current: NamecoinSettings get() = _settings.value

    /**
     * Parsed [ElectrumxServer] list from current custom settings, or `null`
     * if the user hasn't configured any (meaning "use defaults").
     */
    val customServersOrNull: List<ElectrumxServer>?
        get() = current.toElectrumxServers()

    // ── Mutators ───────────────────────────────────────────────────────

    suspend fun setEnabled(enabled: Boolean) {
        val updated = current.copy(enabled = enabled)
        persist(updated)
    }

    suspend fun addServer(server: String) {
        if (server.isBlank() || server in current.customServers) return
        val updated = current.copy(customServers = current.customServers + server)
        persist(updated)
    }

    suspend fun removeServer(server: String) {
        val updated = current.copy(customServers = current.customServers - server)
        persist(updated)
    }

    suspend fun setBackend(backend: NamecoinBackend) {
        val updated = current.copy(backend = backend)
        persist(updated)
    }

    suspend fun setCoreRpcConfig(cfg: NamecoinCoreRpcConfig) {
        val updated = current.copy(namecoinCoreRpc = cfg)
        persist(updated)
    }

    suspend fun setFallbackToCustomElectrumx(enabled: Boolean) {
        val updated = current.copy(fallbackToCustomElectrumx = enabled)
        persist(updated)
    }

    suspend fun setFallbackToDefaultElectrumx(enabled: Boolean) {
        val updated = current.copy(fallbackToDefaultElectrumx = enabled)
        persist(updated)
    }

    suspend fun reset() {
        persist(NamecoinSettings.DEFAULT)
        clearPinnedCerts()
    }

    /**
     * Store a PEM-encoded certificate that the user accepted via Test Connection.
     * The cert is appended to the existing list and synced to the ElectrumXClient.
     */
    suspend fun addPinnedCert(pem: String) {
        val existing = loadPinnedCertsFromDisk()
        val updated = (existing + pem).distinct()
        savePinnedCerts(updated)
    }

    /** Load all user-pinned certs from disk (for startup sync). */
    suspend fun loadPinnedCerts(): List<String> = loadPinnedCertsFromDisk()

    private suspend fun clearPinnedCerts() = savePinnedCerts(emptyList())

    private suspend fun savePinnedCerts(certs: List<String>) {
        try {
            store.edit { prefs ->
                prefs[KEY_PINNED_CERTS] = json.encodeToString(certs)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NamecoinSettingsStore") { "Error writing pinned certs: ${e.message}" }
        }
    }

    private suspend fun loadPinnedCertsFromDisk(): List<String> =
        try {
            val prefs = store.data.first()
            val certsJson = prefs[KEY_PINNED_CERTS]
            if (certsJson != null) {
                json.decodeFromString<List<String>>(certsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }

    // ── Internal ───────────────────────────────────────────────────────

    private suspend fun persist(settings: NamecoinSettings) {
        _settings.value = settings
        try {
            store.edit { prefs ->
                prefs[KEY_ENABLED] = settings.enabled
                prefs[KEY_CUSTOM_SERVERS] =
                    json.encodeToString(
                        settings.customServers.filter { it.isNotBlank() },
                    )
                prefs[KEY_BACKEND] = settings.backend.name
                prefs[KEY_CORE_RPC] = json.encodeToString(settings.namecoinCoreRpc)
                prefs[KEY_FALLBACK_CUSTOM_EX] = settings.fallbackToCustomElectrumx
                prefs[KEY_FALLBACK_DEFAULT_EX] = settings.fallbackToDefaultElectrumx
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NamecoinSettingsStore") { "Error writing DataStore: ${e.message}" }
        }
    }

    private suspend fun loadFromDisk(): NamecoinSettings? =
        try {
            val prefs = store.data.first()
            val enabled = prefs[KEY_ENABLED] ?: true
            val serversJson = prefs[KEY_CUSTOM_SERVERS]
            val servers =
                if (serversJson != null) {
                    try {
                        json.decodeFromString<List<String>>(serversJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            val backend =
                prefs[KEY_BACKEND]?.let {
                    try {
                        NamecoinBackend.valueOf(it)
                    } catch (_: Exception) {
                        NamecoinBackend.ELECTRUMX
                    }
                } ?: NamecoinBackend.ELECTRUMX
            val coreRpc =
                prefs[KEY_CORE_RPC]?.let { raw ->
                    try {
                        json.decodeFromString<NamecoinCoreRpcConfig>(raw)
                    } catch (_: Exception) {
                        null
                    }
                } ?: NamecoinCoreRpcConfig()
            val fallbackCustom = prefs[KEY_FALLBACK_CUSTOM_EX] ?: false
            val fallbackDefault = prefs[KEY_FALLBACK_DEFAULT_EX] ?: false
            NamecoinSettings(
                enabled = enabled,
                customServers = servers,
                backend = backend,
                namecoinCoreRpc = coreRpc,
                fallbackToCustomElectrumx = fallbackCustom,
                fallbackToDefaultElectrumx = fallbackDefault,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NamecoinSettingsStore") { "Error reading DataStore: ${e.message}" }
            null
        }
}
