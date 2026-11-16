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
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.ConnectivityType
import com.vitorpamplona.amethyst.commons.model.FeatureSetType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.NoteLayoutType
import com.vitorpamplona.amethyst.commons.model.SpellCheckType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.model.UiSettings
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UiSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun stores(migrations: (String) -> List<androidx.datastore.core.DataMigration<Preferences>> = { emptyList() }) = AppPreferenceStores(rootFilesDir = { folder.root.toOkioPath() }, migrations = migrations)

    private fun rawStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { folder.root.toOkioPath() / "$name.preferences_pb" },
        )

    /**
     * An account that never opened Settings has no stored value for anything,
     * so every default here is what that install gets forever. Several of them
     * are `true` — reading `false` would quietly turn those features off for
     * everyone who never touched them.
     */
    @Test
    fun anEmptyStoreReadsTheSameDefaultsSharedPreferencesDid() =
        runTest {
            val settings = UiSettingsStore(rawStore("empty")).load()!!

            assertEquals(ThemeType.SYSTEM, settings.theme)
            assertNull(settings.preferredLanguage)
            assertEquals(ConnectivityType.ALWAYS, settings.automaticallyShowImages)
            assertEquals(FeatureSetType.SIMPLIFIED, settings.featureSet)
            assertEquals(AccentColorType.PURPLE, settings.accentColor)
            assertEquals(FontSizeType.NORMAL, settings.fontSize)
            assertEquals("", settings.composeSignature)
            assertEquals(NoteLayoutType.AUTHOR_COLUMN, settings.noteLayout)
            assertEquals(ChatLayoutType.BUBBLES, settings.chatLayout)
            assertEquals(ConnectivityType.WIFI_ONLY, settings.automaticallyDownloadTranslationModels)

            // Not a constant, and not the data class default either: UiSettings()
            // says ALWAYS, but a store with no feature set reads SIMPLIFIED, and
            // SIMPLIFIED means NEVER. Reading an empty store is therefore not the
            // same as constructing UiSettings() — pinned here because the two look
            // interchangeable at a call site.
            assertEquals(BooleanType.NEVER, settings.useTrackedBroadcasts)

            // the ones that default on
            assertTrue(settings.showHomeNewThreadsTab)
            assertTrue(settings.showHomeConversationsTab)
            assertTrue(settings.showProfileBadges)
            assertTrue(settings.showProfileAppRecommendations)
            assertTrue(settings.showProfileZapReceivedFeed)
            assertTrue(settings.showProfileFollowersFeed)
            assertTrue(settings.showOnchainWallet)
            assertTrue(settings.showPayToZapChip)

            // and the ones that default off
            assertFalse(settings.showHomeEverythingTab)
            assertFalse(settings.dontShowPushNotificationSelector)
            assertFalse(settings.dontAskForNotificationPermissions)
            assertFalse(settings.dontShowOnchainPublicWarning)
        }

    /**
     * `useTrackedBroadcasts` is the one default that is not a constant: it
     * follows the feature set when it has never been set explicitly, and an
     * explicit value wins over that.
     */
    @Test
    fun trackedBroadcastsFollowsTheFeatureSetUntilItIsSetExplicitly() =
        runTest {
            val complete = rawStore("complete")
            complete.edit { it[UiSettingsStore.UI_FEATURE_SET] = FeatureSetType.COMPLETE.name }
            assertEquals(BooleanType.ALWAYS, UiSettingsStore(complete).load()!!.useTrackedBroadcasts)

            val simplified = rawStore("simplified")
            simplified.edit { it[UiSettingsStore.UI_FEATURE_SET] = FeatureSetType.SIMPLIFIED.name }
            assertEquals(BooleanType.NEVER, UiSettingsStore(simplified).load()!!.useTrackedBroadcasts)

            val explicit = rawStore("explicit")
            explicit.edit {
                it[UiSettingsStore.UI_FEATURE_SET] = FeatureSetType.SIMPLIFIED.name
                it[UiSettingsStore.UI_USE_TRACKED_BROADCASTS] = BooleanType.ALWAYS.name
            }
            assertEquals(BooleanType.ALWAYS, UiSettingsStore(explicit).load()!!.useTrackedBroadcasts)
        }

    /** The deck was a switch of its own before the layout setting: whoever had it on starts in the deck layout. */
    @Test
    fun theOldDeckSwitchBecomesTheDeckLayout() =
        runTest {
            val deckOn = rawStore("deck-on")
            deckOn.edit { it[UiSettingsStore.UI_DECK_MODE] = true }
            assertEquals(NavigationLayoutType.DECK, UiSettingsStore(deckOn).load()!!.navigationLayout)

            val chosen = rawStore("deck-and-tabs")
            chosen.edit {
                it[UiSettingsStore.UI_DECK_MODE] = true
                it[UiSettingsStore.UI_NAVIGATION_LAYOUT] = NavigationLayoutType.TABS.name
            }
            assertEquals(NavigationLayoutType.TABS, UiSettingsStore(chosen).load()!!.navigationLayout)
        }

    /** Desktop moves every install to tabs once, whatever it had, and keeps what the user picks afterwards. */
    @Test
    fun desktopTabsLayoutOverridesOnceThenKeepsTheUsersPick() =
        runTest {
            val desktop = { name: String -> if (name == AppPreferenceStores.SHARED_SETTINGS) listOf(UiSettingsStore.desktopTabsLayout()) else emptyList() }

            // DataStore allows one live store per file, so each "start" opens a copy of the last one's files.
            var run = 0

            fun restart(from: File?): Pair<File, AppPreferenceStores> {
                val dir = folder.newFolder("run${run++}")
                from?.copyRecursively(dir, overwrite = true)
                return dir to AppPreferenceStores(rootFilesDir = { dir.toOkioPath() }, migrations = desktop)
            }

            // A fresh install starts on tabs.
            assertEquals(NavigationLayoutType.TABS, UiSettingsStore(restart(null).second.sharedSettings()).load()!!.navigationLayout)

            // An existing install on the deck is moved to tabs.
            val old = folder.newFolder("old")
            AppPreferenceStores(rootFilesDir = { old.toOkioPath() }).sharedSettings().edit {
                it[UiSettingsStore.UI_NAVIGATION_LAYOUT] = NavigationLayoutType.DECK.name
            }
            val (migratedDir, migrated) = restart(old)
            val store = UiSettingsStore(migrated.sharedSettings())
            assertEquals(NavigationLayoutType.TABS, store.load()!!.navigationLayout)

            // A pick made after the move survives the next start.
            store.save(store.load()!!.copy(navigationLayout = NavigationLayoutType.STACK))
            val afterRestart = restart(migratedDir).second.sharedSettings()
            assertEquals(NavigationLayoutType.STACK, UiSettingsStore(afterRestart).load()!!.navigationLayout)
        }

    @Test
    fun everySettingSurvivesARoundTrip() =
        runTest {
            val store = UiSettingsStore(rawStore("roundtrip"))
            val settings =
                UiSettings(
                    theme = ThemeType.DARK,
                    preferredLanguage = "pt-BR",
                    automaticallyShowImages = ConnectivityType.WIFI_ONLY,
                    automaticallyPlayVideos = BooleanType.NEVER,
                    featureSet = FeatureSetType.COMPLETE,
                    accentColor = AccentColorType.GREEN,
                    fontSize = FontSizeType.HUGE,
                    composeSignature = "— sent from Amethyst",
                    showHomeEverythingTab = true,
                    showProfileBadges = false,
                    showOnchainWallet = false,
                    showPayToZapChip = false,
                    spellCheck = SpellCheckType.BUILT_IN,
                    noteLayout = NoteLayoutType.EDGE_TO_EDGE,
                    automaticallyDownloadTranslationModels = ConnectivityType.ALWAYS,
                    hideWalletBalances = true,
                    chatLayout = ChatLayoutType.COMPACT,
                )

            store.save(settings)

            assertEquals(settings, store.load())
        }

    /**
     * A blank language is stored as "" (the key is written unconditionally) and
     * has to read back as null, or "no preference" turns into a locale tag the
     * `LocaleListCompat` call cannot parse.
     */
    @Test
    fun aBlankLanguageReadsBackAsNoPreference() =
        runTest {
            val store = UiSettingsStore(rawStore("blank"))
            store.save(UiSettings(preferredLanguage = null))

            assertNull(store.load()!!.preferredLanguage)
        }

    /**
     * The copy out of the old blob must not run on an install that already has
     * these settings here — that would undo every change made since this store
     * took over. `ui.theme` is the test, because [UiSettingsStore.write] sets
     * every key and is the only writer.
     *
     * Driven against the [androidx.datastore.core.DataMigration] directly rather
     * than through two DataStores over one file: DataStore keeps a process-wide
     * registry keyed by path and refuses the second one, so "reopen it and look"
     * is a crash, not a test.
     */
    @Test
    fun theLegacyCopyRunsOnlyWhenTheStoreHasNeverBeenSaved() =
        runTest {
            val legacy = UiSettings(theme = ThemeType.LIGHT, composeSignature = "from the old blob")
            val migration = UiSettingsStore.migrations { legacy }.single()

            // A store nothing has written: the copy lands.
            val fresh = emptyPreferences()
            assertTrue(migration.shouldMigrate(fresh))
            assertEquals(legacy, UiSettingsStore.read(migration.migrate(fresh)))

            // A store this app has already saved to: the copy must leave it alone.
            val mine = UiSettings(theme = ThemeType.DARK, composeSignature = "mine")
            val used = mutablePreferencesOf().apply { with(UiSettingsStore) { write(mine) } }.toPreferences()

            val after = migration.migrate(used)
            assertEquals(mine, UiSettingsStore.read(after))

            // ...and having run once, it never runs again.
            assertFalse(migration.shouldMigrate(after))
        }

    /** No legacy reader (desktop, CLI) is not an error — it just means no copy. */
    @Test
    fun aFrontEndWithoutALegacyFileGetsDefaults() =
        runTest {
            val subject = stores { UiSettingsStore.migrations { null } }

            assertEquals(
                UiSettings(useTrackedBroadcasts = BooleanType.NEVER),
                UiSettingsStore(subject.sharedSettings()).load(),
            )
        }
}
