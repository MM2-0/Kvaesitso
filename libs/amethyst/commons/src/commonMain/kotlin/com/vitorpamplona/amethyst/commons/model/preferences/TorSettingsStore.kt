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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.tor.TorPreferencesPort
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.cancellation.CancellationException

@Stable
class TorSettingsStore(
    prefs: TorSettings,
    val store: DataStore<Preferences>,
    val scope: CoroutineScope,
) : TorPreferencesPort {
    // Tor Preferences. Makes sure to wait for it to avoid connecting with random IPs
    val value = TorSettingsFlow.build(prefs)

    override val torType: StateFlow<TorType> get() = value.torType
    override val externalSocksPort: StateFlow<Int> get() = value.externalSocksPort

    @OptIn(FlowPreview::class)
    val saving =
        value.propertyWatchFlow
            .debounce(1000)
            .distinctUntilChanged()
            .onEach {
                save(it, store)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    override suspend fun loadLastBypassApprovalMs(): Long = loadLastBypassApprovalMs(store)

    override suspend fun saveLastBypassApprovalMs(value: Long) = saveLastBypassApprovalMs(value, store)

    companion object {
        // loads faster when individualized
        val TOR_TYPE_KEY = stringPreferencesKey("tor.torType")
        val EXTERNAL_SOCKS_PORT_KEY = intPreferencesKey("tor.externalSocksPort")
        val LAST_BYPASS_APPROVAL_MS_KEY = longPreferencesKey("tor.lastBypassApprovalMs")
        val ONION_RELAYS_VIA_TOR_KEY = booleanPreferencesKey("tor.onionRelaysViaTor")
        val DM_RELAYS_VIA_TOR_KEY = booleanPreferencesKey("tor.dmRelaysViaTor")
        val NEW_RELAYS_VIA_TOR_KEY = booleanPreferencesKey("tor.newRelaysViaTor")
        val TRUSTED_RELAYS_VIA_TOR_KEY = booleanPreferencesKey("tor.trustedRelaysViaTor")
        val URL_PREVIEWS_VIA_TOR_KEY = booleanPreferencesKey("tor.urlPreviewsViaTor")
        val PROFILE_PICS_VIA_TOR_KEY = booleanPreferencesKey("tor.profilePicsViaTor")
        val IMAGES_VIA_TOR_KEY = booleanPreferencesKey("tor.imagesViaTor")
        val VIDEOS_VIA_TOR_KEY = booleanPreferencesKey("tor.videosViaTor")
        val MONEY_OPERATIONS_VIA_TOR_KEY = booleanPreferencesKey("tor.moneyOperationsViaTor")
        val NIP05_VERIFICATIONS_VIA_TOR_KEY = booleanPreferencesKey("tor.nip05VerificationsViaTor")
        val MEDIA_UPLOADS_VIA_TOR_KEY = booleanPreferencesKey("tor.mediaUploadsViaTor")
        val APPLETS_AND_WEBSITES_VIA_TOR_KEY = booleanPreferencesKey("tor.appletsAndWebsitesViaTor")
        val PROGRESS_NOTIFICATION_KEY = booleanPreferencesKey("tor.progressNotification")

        suspend fun torPreferences(store: DataStore<Preferences>): TorSettings? =
            try {
                // Get the preference flow and take the first value.
                val preferences = store.data.first()
                TorSettings(
                    torType = preferences[TOR_TYPE_KEY]?.let { TorType.valueOf(it) } ?: TorType.INTERNAL,
                    externalSocksPort = preferences[EXTERNAL_SOCKS_PORT_KEY] ?: 9050,
                    onionRelaysViaTor = preferences[ONION_RELAYS_VIA_TOR_KEY] ?: true,
                    dmRelaysViaTor = preferences[DM_RELAYS_VIA_TOR_KEY] ?: true,
                    newRelaysViaTor = preferences[NEW_RELAYS_VIA_TOR_KEY] ?: true,
                    trustedRelaysViaTor = preferences[TRUSTED_RELAYS_VIA_TOR_KEY] ?: false,
                    urlPreviewsViaTor = preferences[URL_PREVIEWS_VIA_TOR_KEY] ?: false,
                    profilePicsViaTor = preferences[PROFILE_PICS_VIA_TOR_KEY] ?: false,
                    imagesViaTor = preferences[IMAGES_VIA_TOR_KEY] ?: false,
                    videosViaTor = preferences[VIDEOS_VIA_TOR_KEY] ?: false,
                    moneyOperationsViaTor = preferences[MONEY_OPERATIONS_VIA_TOR_KEY] ?: false,
                    nip05VerificationsViaTor = preferences[NIP05_VERIFICATIONS_VIA_TOR_KEY] ?: false,
                    mediaUploadsViaTor = preferences[MEDIA_UPLOADS_VIA_TOR_KEY] ?: false,
                    appletsAndWebsitesViaTor = preferences[APPLETS_AND_WEBSITES_VIA_TOR_KEY] ?: false,
                    progressNotification = preferences[PROGRESS_NOTIFICATION_KEY] ?: false,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Log any errors that occur while reading the DataStore.
                Log.e("TorSettingsStore") { "Error reading DataStore preferences: ${e.message}" }
                null
            }

        suspend fun save(
            torSettings: TorSettings,
            store: DataStore<Preferences>,
        ) {
            try {
                store.edit { preferences ->
                    preferences[TOR_TYPE_KEY] = torSettings.torType.name
                    preferences[EXTERNAL_SOCKS_PORT_KEY] = torSettings.externalSocksPort
                    preferences[ONION_RELAYS_VIA_TOR_KEY] = torSettings.onionRelaysViaTor
                    preferences[DM_RELAYS_VIA_TOR_KEY] = torSettings.dmRelaysViaTor
                    preferences[NEW_RELAYS_VIA_TOR_KEY] = torSettings.newRelaysViaTor
                    preferences[TRUSTED_RELAYS_VIA_TOR_KEY] = torSettings.trustedRelaysViaTor
                    preferences[URL_PREVIEWS_VIA_TOR_KEY] = torSettings.urlPreviewsViaTor
                    preferences[PROFILE_PICS_VIA_TOR_KEY] = torSettings.profilePicsViaTor
                    preferences[IMAGES_VIA_TOR_KEY] = torSettings.imagesViaTor
                    preferences[VIDEOS_VIA_TOR_KEY] = torSettings.videosViaTor
                    preferences[MONEY_OPERATIONS_VIA_TOR_KEY] = torSettings.moneyOperationsViaTor
                    preferences[NIP05_VERIFICATIONS_VIA_TOR_KEY] = torSettings.nip05VerificationsViaTor
                    preferences[MEDIA_UPLOADS_VIA_TOR_KEY] = torSettings.mediaUploadsViaTor
                    preferences[APPLETS_AND_WEBSITES_VIA_TOR_KEY] = torSettings.appletsAndWebsitesViaTor
                    preferences[PROGRESS_NOTIFICATION_KEY] = torSettings.progressNotification
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Log any errors that occur while reading the DataStore.
                Log.e("TorSettingsStore") { "Error saving DataStore preferences: ${e.message}" }
            }
        }

        suspend fun loadLastBypassApprovalMs(store: DataStore<Preferences>): Long =
            try {
                store.data.first()[LAST_BYPASS_APPROVAL_MS_KEY] ?: 0L
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("TorSettingsStore") { "Error reading lastBypassApprovalMs: ${e.message}" }
                0L
            }

        suspend fun saveLastBypassApprovalMs(
            value: Long,
            store: DataStore<Preferences>,
        ) {
            try {
                store.edit { prefs ->
                    prefs[LAST_BYPASS_APPROVAL_MS_KEY] = value
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("TorSettingsStore") { "Error saving lastBypassApprovalMs: ${e.message}" }
            }
        }
    }
}
