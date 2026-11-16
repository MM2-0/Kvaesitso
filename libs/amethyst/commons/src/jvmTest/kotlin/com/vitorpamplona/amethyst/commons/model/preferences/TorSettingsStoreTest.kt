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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorType
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TorSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun rawStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { folder.root.toOkioPath() / "$name.preferences_pb" },
        )

    /**
     * These defaults decide what an install that never opened the privacy screen
     * sends over Tor, so getting one wrong is a privacy regression in one
     * direction and a connectivity regression in the other.
     *
     * Three are on — onion, DM and new relays — and the rest are off. Note that
     * the desktop front end's own `DesktopTorPreferences` reads most of these as
     * `true` instead; the two have never agreed, and this pins the Android
     * behaviour that the shared store inherits.
     */
    @Test
    fun anEmptyStoreReadsTheDefaultsTheAndroidStoreAlwaysHad() =
        runTest {
            val settings = TorSettingsStore.torPreferences(rawStore("empty"))!!

            assertEquals(TorType.INTERNAL, settings.torType)
            assertEquals(9050, settings.externalSocksPort)

            assertTrue(settings.onionRelaysViaTor)
            assertTrue(settings.dmRelaysViaTor)
            assertTrue(settings.newRelaysViaTor)

            assertFalse(settings.trustedRelaysViaTor)
            assertFalse(settings.urlPreviewsViaTor)
            assertFalse(settings.profilePicsViaTor)
            assertFalse(settings.imagesViaTor)
            assertFalse(settings.videosViaTor)
            assertFalse(settings.moneyOperationsViaTor)
            assertFalse(settings.nip05VerificationsViaTor)
            assertFalse(settings.mediaUploadsViaTor)
            assertFalse(settings.appletsAndWebsitesViaTor)
        }

    /** The data class and the store must agree, or "unset" and "default" drift apart. */
    @Test
    fun theEmptyStoreMatchesTheDataClassDefaults() =
        runTest {
            assertEquals(TorSettings(), TorSettingsStore.torPreferences(rawStore("match")))
        }

    @Test
    fun everySettingSurvivesARoundTrip() =
        runTest {
            val store = rawStore("roundtrip")
            val settings =
                TorSettings(
                    torType = TorType.EXTERNAL,
                    externalSocksPort = 9150,
                    onionRelaysViaTor = false,
                    dmRelaysViaTor = false,
                    newRelaysViaTor = false,
                    trustedRelaysViaTor = true,
                    urlPreviewsViaTor = true,
                    profilePicsViaTor = true,
                    imagesViaTor = true,
                    videosViaTor = true,
                    moneyOperationsViaTor = true,
                    nip05VerificationsViaTor = true,
                    mediaUploadsViaTor = true,
                    appletsAndWebsitesViaTor = true,
                    progressNotification = true,
                )

            TorSettingsStore.save(settings, store)

            assertEquals(settings, TorSettingsStore.torPreferences(store))
        }

    /**
     * The bypass timestamp is deliberately not part of [TorSettings] — it is
     * bookkeeping for the connection-failure dialog, not a user setting — so it
     * has its own pair of accessors and its own key.
     */
    @Test
    fun theBypassApprovalTimestampIsStoredApartFromTheSettings() =
        runTest {
            val store = rawStore("bypass")

            assertEquals(0L, TorSettingsStore.loadLastBypassApprovalMs(store))

            TorSettingsStore.saveLastBypassApprovalMs(1_700_000_000_000L, store)
            assertEquals(1_700_000_000_000L, TorSettingsStore.loadLastBypassApprovalMs(store))

            // and a settings save must not clear it
            TorSettingsStore.save(TorSettings(torType = TorType.OFF), store)
            assertEquals(1_700_000_000_000L, TorSettingsStore.loadLastBypassApprovalMs(store))
        }

    /**
     * An unreadable enum name must not take the whole settings object down with
     * it — a store written by a newer build that added a TorType would otherwise
     * strand the user with no Tor settings at all.
     */
    @Test
    fun anUnknownTorTypeFallsBackRatherThanThrowing() =
        runTest {
            val store = rawStore("garbage")
            store.edit { it[TorSettingsStore.TOR_TYPE_KEY] = "SOMETHING_NEWER" }

            assertNull(TorSettingsStore.torPreferences(store))
        }

    /** The keys are the ones the Android store has always written. */
    @Test
    fun theKeyNamesAreUnchanged() {
        assertEquals("tor.torType", TorSettingsStore.TOR_TYPE_KEY.name)
        assertEquals("tor.externalSocksPort", TorSettingsStore.EXTERNAL_SOCKS_PORT_KEY.name)
        assertEquals("tor.lastBypassApprovalMs", TorSettingsStore.LAST_BYPASS_APPROVAL_MS_KEY.name)
        assertEquals("tor.onionRelaysViaTor", TorSettingsStore.ONION_RELAYS_VIA_TOR_KEY.name)
        assertEquals("tor.mediaUploadsViaTor", TorSettingsStore.MEDIA_UPLOADS_VIA_TOR_KEY.name)
        assertEquals("tor.appletsAndWebsitesViaTor", TorSettingsStore.APPLETS_AND_WEBSITES_VIA_TOR_KEY.name)
    }
}
