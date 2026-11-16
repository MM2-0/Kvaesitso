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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The five per-account setting groups.
 *
 * The defaults are the point: they must match what the SharedPreferences
 * implementation returned for a missing key, because an account that never
 * touched a setting has no stored value and gets the default forever after.
 * Several are `true`, so a group that defaulted everything to `false` would
 * silently turn features off for every existing user.
 */
class AccountSettingStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun raw(): DataStore<Preferences> {
        val file = File(folder.root, "group_${seq++}.preferences_pb")
        return PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file.toOkioPath() },
        )
    }

    @Test
    fun uploadSettingsDefaultsMatchTheLegacyOnes() =
        runTest {
            val loaded = UploadSettingsStore(raw()).load()

            assertEquals(true, loaded.stripLocationOnUpload)
            assertEquals(false, loaded.optimizeMediaOnUpload)
            assertEquals(true, loaded.mirrorUploadsToAllServers)
            assertEquals(true, loaded.useLocalBlossomCache)
            assertEquals(false, loaded.localBlossomCacheProfilePicturesOnly)
            assertNull(loaded.defaultFileServerJson)
        }

    @Test
    fun uploadSettingsRoundTrip() =
        runTest {
            val store = UploadSettingsStore(raw())
            val value =
                UploadSettings(
                    stripLocationOnUpload = false,
                    optimizeMediaOnUpload = true,
                    mirrorUploadsToAllServers = false,
                    useLocalBlossomCache = false,
                    localBlossomCacheProfilePicturesOnly = true,
                    defaultFileServerJson = """{"name":"x"}""",
                )

            store.save(value)

            assertEquals(value, store.load())
        }

    @Test
    fun dialogDismissalDefaultsToNothingDismissed() =
        runTest {
            val loaded = DialogDismissalStore(raw()).load()

            assertEquals(false, loaded.hideDeleteRequestDialog)
            assertEquals(false, loaded.hideBlockAlertDialog)
            assertEquals(false, loaded.hideNip17WarningDialog)
            assertEquals(false, loaded.hideCommunityRulesViolations)
            assertTrue(loaded.dismissedPollNoteIds.isEmpty())
            assertTrue(loaded.dismissedChannelInvites.isEmpty())
            assertTrue(loaded.mutedPublicChats.isEmpty())
            assertTrue(loaded.hasDonatedInVersion.isEmpty())
        }

    @Test
    fun dialogDismissalRoundTripsSets() =
        runTest {
            val store = DialogDismissalStore(raw())
            val value =
                DialogDismissal(
                    hideDeleteRequestDialog = true,
                    dismissedPollNoteIds = setOf("a", "b"),
                    mutedPublicChats = setOf("chat1"),
                    hasDonatedInVersion = setOf("1.2.3"),
                )

            store.save(value)

            assertEquals(value, store.load())
        }

    @Test
    fun nwcBackupPrefsDefaultToNoBackup() =
        runTest {
            val loaded = NwcBackupPrefsStore(raw()).load()

            assertEquals(false, loaded.autoBackup)
            assertEquals(false, loaded.cardDismissed)
            assertTrue(loaded.relays.isEmpty())
            assertTrue(loaded.walletIds.isEmpty())
        }

    @Test
    fun nwcBackupPrefsRoundTrip() =
        runTest {
            val store = NwcBackupPrefsStore(raw())
            val value =
                NwcBackupPrefs(
                    autoBackup = true,
                    cardDismissed = true,
                    relays = setOf("wss://nos.lol/", "wss://relay.damus.io/"),
                    walletIds = setOf("wallet-1", "wallet-2"),
                )

            store.save(value)

            assertEquals(value, store.load())
        }

    @Test
    fun nwcBackupPrefsClearedSetsStayEmpty() =
        runTest {
            val store = NwcBackupPrefsStore(raw())
            store.save(NwcBackupPrefs(relays = setOf("wss://nos.lol/"), walletIds = setOf("wallet-1")))

            store.save(NwcBackupPrefs())

            assertEquals(NwcBackupPrefs(), store.load())
        }

    /** Unset must read as null, not "": null lets the app-wide signature apply, "" turns it off. */
    @Test
    fun composePrefsKeepUnsetApartFromEmpty() =
        runTest {
            val store = ComposePrefsStore(raw())
            assertNull(store.load().signature)

            store.save(ComposePrefs(signature = "— sent from my relay"))
            assertEquals("— sent from my relay", store.load().signature)

            store.save(ComposePrefs(signature = ""))
            assertEquals("", store.load().signature)

            store.save(ComposePrefs(signature = null))
            assertNull(store.load().signature)
        }

    /** Three of these default to true — trusting by default — so a wrong default weakens AUTH behaviour. */
    @Test
    fun relayAuthDefaults() =
        runTest {
            val loaded = RelayAuthStore(raw()).load()

            assertNull("absent means CUSTOM, decided by the caller", loaded.policyName)
            assertEquals(true, loaded.trustMyRelays)
            assertEquals(true, loaded.trustReadFollows)
            assertEquals(true, loaded.trustMessageFollows)
            assertEquals(false, loaded.trustMessageStrangers)
        }

    @Test
    fun relayAuthRoundTrip() =
        runTest {
            val store = RelayAuthStore(raw())
            val value = RelayAuth("ALWAYS", trustMyRelays = false, trustMessageStrangers = true)

            store.save(value)

            assertEquals(value, store.load())
        }

    /** The disabled-feed keys store what is OFF, so absent must mean everything on. */
    @Test
    fun feedVisibilityDefaultsToEverythingEnabled() =
        runTest {
            val loaded = FeedVisibilityStore(raw()).load()

            assertNull(loaded.disabledChatFeeds)
            assertNull(loaded.disabledHomeFeedTypes)
            assertEquals(true, loaded.callsEnabled)
        }

    /** No stored accent means the account follows the app-wide one; a pick survives a reload and can be cleared. */
    @Test
    fun feedVisibilityAccentColorRoundTrip() =
        runTest {
            val store = FeedVisibilityStore(raw())
            assertNull(store.load().accentColor)

            store.save(FeedVisibility(accentColor = "GREEN"))
            assertEquals("GREEN", store.load().accentColor)

            store.save(FeedVisibility(accentColor = null))
            assertNull(store.load().accentColor)
        }

    @Test
    fun notificationPrefsDefaults() =
        runTest {
            val loaded = NotificationPrefsStore(raw()).load()

            assertEquals(false, loaded.alwaysOnService)
            assertEquals(true, loaded.showMessagesInNotifications)
            assertEquals(false, loaded.splitNotificationsEnabled)
        }

    /** A null string field must clear its key rather than leave the old value behind. */
    @Test
    fun aNullStringFieldClearsTheKey() =
        runTest {
            val store = FeedVisibilityStore(raw())

            store.save(FeedVisibility(disabledChatFeeds = "a,b"))
            store.save(FeedVisibility(disabledChatFeeds = null))

            assertNull(store.load().disabledChatFeeds)
        }

    /** Key strings are a compatibility surface — renaming one resets that setting for everyone. */
    @Test
    fun keyNamesMatchTheLegacyOnes() {
        assertEquals("stripLocationOnUpload", UploadSettingsStore.stripLocationOnUpload.name)
        assertEquals("hide_delete_request_dialog", DialogDismissalStore.hideDeleteRequestDialog.name)
        assertEquals("hide_nip24_warning_dialog", DialogDismissalStore.hideNip17WarningDialog.name)
        assertEquals("default_relay_auth_policy", RelayAuthStore.policyName.name)
        assertEquals("disabled_chat_feeds", FeedVisibilityStore.disabledChatFeeds.name)
        assertEquals("always_on_notification_service", NotificationPrefsStore.alwaysOnService.name)
    }
}
