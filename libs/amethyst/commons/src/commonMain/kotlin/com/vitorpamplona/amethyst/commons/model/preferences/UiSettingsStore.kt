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

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.browser.SearchEngines
import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.ConnectivityType
import com.vitorpamplona.amethyst.commons.model.FeatureSetType
import com.vitorpamplona.amethyst.commons.model.FontFamilyType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.NoteLayoutType
import com.vitorpamplona.amethyst.commons.model.ProfileGalleryType
import com.vitorpamplona.amethyst.commons.model.SpellCheckType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.model.UiSettings
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The app-wide UI settings — theme, language, what loads on cellular, which
 * profile and home tabs are shown.
 *
 * Lives on the [AppPreferenceStores.SHARED_SETTINGS] file under the `ui.` key
 * prefix, one key per setting rather than a single blob: a DataStore read is
 * whole-file anyway, but individual keys mean a setting added later does not
 * invalidate the ones already stored.
 *
 * Headless on purpose. Applying a theme or a locale is platform work — on
 * Android it is `UiModeManager` and `AppCompatDelegate`, which have no desktop
 * equivalent — so that half stays in each front end and only the persistence
 * is shared.
 *
 * @param loadLegacy reads the single `shared_settings` JSON blob these settings
 *   used to live in. Injected because the legacy file is Android's, and this
 *   store is not. Front ends without one pass nothing.
 */
class UiSettingsStore(
    private val store: DataStore<Preferences>,
    private val loadLegacy: suspend () -> UiSettings? = { null },
) {
    /**
     * Reads every setting, falling back to the legacy blob if the store itself
     * cannot be read.
     *
     * Returns null when neither can be read, which the caller shows as "still
     * loading" rather than as defaults — writing defaults over an unreadable
     * store would make a transient failure permanent on the next save.
     */
    suspend fun load(): UiSettings? =
        try {
            read(store.data.first())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UiSettingsStore") { "Error reading DataStore preferences: ${e.message}" }

            try {
                loadLegacy()?.also { save(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    suspend fun save(settings: UiSettings) {
        try {
            store.edit { it.write(settings) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UiSettingsStore") { "Error saving DataStore preferences: ${e.message}" }
        }
    }

    companion object {
        // loads faster when individualized
        val UI_THEME = stringPreferencesKey("ui.theme")
        val UI_LANGUAGE = stringPreferencesKey("ui.language")
        val UI_SHOW_IMAGES = stringPreferencesKey("ui.show_images")
        val UI_START_PLAYBACK = stringPreferencesKey("ui.start_playback")
        val UI_PLAY_VIDEOS = stringPreferencesKey("ui.play_videos")
        val UI_SHOW_URL_PREVIEW = stringPreferencesKey("ui.show_url_preview")
        val UI_HIDE_NAVIGATION_BARS = stringPreferencesKey("ui.hide_navigation_bars")
        val UI_SHOW_PROFILE_PICTURES = stringPreferencesKey("ui.show_profile_pictures")
        val UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR = booleanPreferencesKey("ui.dont_show_push_notification_selector")
        val UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS = booleanPreferencesKey("ui.dont_ask_for_notification_permissions")
        val UI_FEATURE_SET = stringPreferencesKey("ui.feature_set")
        val UI_GALLERY_SET = stringPreferencesKey("ui.gallery_set")
        val UI_PROPOSE_AI_IMPROVEMENTS = stringPreferencesKey("ui.propose_ai_improvements")
        val UI_USE_TRACKED_BROADCASTS = stringPreferencesKey("ui.use_tracked_broadcasts")
        val UI_AUTOMATICALLY_CREATE_DRAFTS = stringPreferencesKey("ui.automatically_create_drafts")
        val UI_SHOW_HOME_NEW_THREADS_TAB = booleanPreferencesKey("ui.show_home_new_threads_tab")
        val UI_SHOW_HOME_CONVERSATIONS_TAB = booleanPreferencesKey("ui.show_home_conversations_tab")
        val UI_SHOW_HOME_EVERYTHING_TAB = booleanPreferencesKey("ui.show_home_everything_tab")
        val UI_SHOW_PROFILE_BADGES = booleanPreferencesKey("ui.show_profile_badges")
        val UI_SHOW_PROFILE_APP_RECOMMENDATIONS = booleanPreferencesKey("ui.show_profile_app_recommendations")
        val UI_SHOW_PROFILE_ZAP_RECEIVED_FEED = booleanPreferencesKey("ui.show_profile_zap_received_feed")
        val UI_SHOW_PROFILE_FOLLOWERS_FEED = booleanPreferencesKey("ui.show_profile_followers_feed")
        val UI_DONT_SHOW_ONCHAIN_PUBLIC_WARNING = booleanPreferencesKey("ui.dont_show_onchain_public_warning")
        val UI_SUGGEST_WORKOUTS_FROM_HEALTH_CONNECT = stringPreferencesKey("ui.suggest_workouts_from_health_connect")
        val UI_ACCENT_COLOR = stringPreferencesKey("ui.accent_color")
        val UI_FONT_FAMILY = stringPreferencesKey("ui.font_family")
        val UI_FONT_SIZE = stringPreferencesKey("ui.font_size")
        val UI_COMPOSE_SIGNATURE = stringPreferencesKey("ui.compose_signature")
        val UI_SHOW_ONCHAIN_WALLET = booleanPreferencesKey("ui.show_onchain_wallet")
        val UI_SHOW_PAYTO_ZAP_CHIP = booleanPreferencesKey("ui.show_payto_zap_chip")
        val UI_SEARCH_ENGINE = stringPreferencesKey("ui.search_engine")

        /** The deck switch from before [UI_NAVIGATION_LAYOUT]: read once, to start such a user in the deck layout. */
        val UI_DECK_MODE = booleanPreferencesKey("ui.deck_mode")
        val UI_SPELL_CHECK = stringPreferencesKey("ui.spell_check")
        val UI_NOTE_LAYOUT = stringPreferencesKey("ui.note_layout")
        val UI_DOWNLOAD_TRANSLATION_MODELS = stringPreferencesKey("ui.download_translation_models")
        val UI_HIDE_WALLET_BALANCES = booleanPreferencesKey("ui.hide_wallet_balances")
        val UI_NAVIGATION_LAYOUT = stringPreferencesKey("ui.navigation_layout")
        val UI_CHAT_LAYOUT = stringPreferencesKey("ui.chat_layout")

        /**
         * Every setting's default matches what the old `getBoolean(key, default)`
         * call returned for a missing key. Several of them are `true`, so reading
         * a default of `false` here would silently turn features off for every
         * install that never touched them.
         */
        fun read(preferences: Preferences): UiSettings {
            val featureSet = preferences[UI_FEATURE_SET]?.let { FeatureSetType.valueOf(it) } ?: FeatureSetType.SIMPLIFIED

            return UiSettings(
                theme = preferences[UI_THEME]?.let { ThemeType.valueOf(it) } ?: ThemeType.SYSTEM,
                preferredLanguage = preferences[UI_LANGUAGE]?.ifBlank { null },
                automaticallyShowImages = preferences[UI_SHOW_IMAGES]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                automaticallyStartPlayback = preferences[UI_START_PLAYBACK]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                automaticallyPlayVideos = preferences[UI_PLAY_VIDEOS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                automaticallyShowUrlPreview = preferences[UI_SHOW_URL_PREVIEW]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                automaticallyHideNavigationBars = preferences[UI_HIDE_NAVIGATION_BARS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                automaticallyShowProfilePictures = preferences[UI_SHOW_PROFILE_PICTURES]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                dontShowPushNotificationSelector = preferences[UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR] ?: false,
                dontAskForNotificationPermissions = preferences[UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS] ?: false,
                featureSet = featureSet,
                gallerySet = preferences[UI_GALLERY_SET]?.let { ProfileGalleryType.valueOf(it) } ?: ProfileGalleryType.CLASSIC,
                automaticallyProposeAiImprovements = preferences[UI_PROPOSE_AI_IMPROVEMENTS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                useTrackedBroadcasts =
                    preferences[UI_USE_TRACKED_BROADCASTS]?.let { BooleanType.valueOf(it) }
                        ?: if (featureSet == FeatureSetType.COMPLETE) BooleanType.ALWAYS else BooleanType.NEVER,
                automaticallyCreateDrafts = preferences[UI_AUTOMATICALLY_CREATE_DRAFTS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                showHomeNewThreadsTab = preferences[UI_SHOW_HOME_NEW_THREADS_TAB] ?: true,
                showHomeConversationsTab = preferences[UI_SHOW_HOME_CONVERSATIONS_TAB] ?: true,
                showHomeEverythingTab = preferences[UI_SHOW_HOME_EVERYTHING_TAB] ?: false,
                showProfileBadges = preferences[UI_SHOW_PROFILE_BADGES] ?: true,
                showProfileAppRecommendations = preferences[UI_SHOW_PROFILE_APP_RECOMMENDATIONS] ?: true,
                showProfileZapReceivedFeed = preferences[UI_SHOW_PROFILE_ZAP_RECEIVED_FEED] ?: true,
                showProfileFollowersFeed = preferences[UI_SHOW_PROFILE_FOLLOWERS_FEED] ?: true,
                dontShowOnchainPublicWarning = preferences[UI_DONT_SHOW_ONCHAIN_PUBLIC_WARNING] ?: false,
                suggestWorkoutsFromHealthConnect =
                    preferences[UI_SUGGEST_WORKOUTS_FROM_HEALTH_CONNECT]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                accentColor = preferences[UI_ACCENT_COLOR]?.let { AccentColorType.valueOf(it) } ?: AccentColorType.PURPLE,
                fontFamily = preferences[UI_FONT_FAMILY]?.let { FontFamilyType.valueOf(it) } ?: FontFamilyType.SYSTEM,
                fontSize = preferences[UI_FONT_SIZE]?.let { FontSizeType.valueOf(it) } ?: FontSizeType.NORMAL,
                composeSignature = preferences[UI_COMPOSE_SIGNATURE] ?: "",
                showOnchainWallet = preferences[UI_SHOW_ONCHAIN_WALLET] ?: true,
                showPayToZapChip = preferences[UI_SHOW_PAYTO_ZAP_CHIP] ?: true,
                searchEngine = preferences[UI_SEARCH_ENGINE] ?: SearchEngines.DEFAULT.id,
                spellCheck =
                    preferences[UI_SPELL_CHECK]?.let { name -> SpellCheckType.entries.firstOrNull { it.name == name } }
                        ?: SpellCheckType.AUTOMATIC,
                noteLayout =
                    preferences[UI_NOTE_LAYOUT]?.let { name -> NoteLayoutType.entries.firstOrNull { it.name == name } }
                        ?: NoteLayoutType.AUTHOR_COLUMN,
                automaticallyDownloadTranslationModels =
                    preferences[UI_DOWNLOAD_TRANSLATION_MODELS]?.let { name -> ConnectivityType.entries.firstOrNull { it.name == name } }
                        ?: ConnectivityType.WIFI_ONLY,
                hideWalletBalances = preferences[UI_HIDE_WALLET_BALANCES] ?: false,
                // Before the layout setting, the deck was a switch of its own: on, it is the deck layout now.
                navigationLayout =
                    preferences[UI_NAVIGATION_LAYOUT]?.let { name -> NavigationLayoutType.entries.firstOrNull { it.name == name } }
                        ?: if (preferences[UI_DECK_MODE] == true) NavigationLayoutType.DECK else NavigationLayoutType.STACK,
                chatLayout =
                    preferences[UI_CHAT_LAYOUT]?.let { name -> ChatLayoutType.entries.firstOrNull { it.name == name } }
                        ?: ChatLayoutType.BUBBLES,
            )
        }

        /**
         * Writes every UI setting into [this].
         *
         * Shared by [save] and by the one-shot copy out of the old
         * `shared_settings` blob, so the two cannot come to disagree about
         * which keys a complete set of UI settings has.
         */
        fun MutablePreferences.write(sharedSettings: UiSettings) {
            val preferences = this
            preferences[UI_THEME] = sharedSettings.theme.name
            preferences[UI_LANGUAGE] = sharedSettings.preferredLanguage ?: ""
            preferences[UI_SHOW_IMAGES] = sharedSettings.automaticallyShowImages.name
            preferences[UI_START_PLAYBACK] = sharedSettings.automaticallyStartPlayback.name
            preferences[UI_PLAY_VIDEOS] = sharedSettings.automaticallyPlayVideos.name
            preferences[UI_SHOW_URL_PREVIEW] = sharedSettings.automaticallyShowUrlPreview.name
            preferences[UI_HIDE_NAVIGATION_BARS] = sharedSettings.automaticallyHideNavigationBars.name
            preferences[UI_SHOW_PROFILE_PICTURES] = sharedSettings.automaticallyShowProfilePictures.name
            preferences[UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR] = sharedSettings.dontShowPushNotificationSelector
            preferences[UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS] = sharedSettings.dontAskForNotificationPermissions
            preferences[UI_FEATURE_SET] = sharedSettings.featureSet.name
            preferences[UI_GALLERY_SET] = sharedSettings.gallerySet.name
            preferences[UI_PROPOSE_AI_IMPROVEMENTS] = sharedSettings.automaticallyProposeAiImprovements.name
            preferences[UI_USE_TRACKED_BROADCASTS] = sharedSettings.useTrackedBroadcasts.name
            preferences[UI_AUTOMATICALLY_CREATE_DRAFTS] = sharedSettings.automaticallyCreateDrafts.name
            preferences[UI_SHOW_HOME_NEW_THREADS_TAB] = sharedSettings.showHomeNewThreadsTab
            preferences[UI_SHOW_HOME_CONVERSATIONS_TAB] = sharedSettings.showHomeConversationsTab
            preferences[UI_SHOW_HOME_EVERYTHING_TAB] = sharedSettings.showHomeEverythingTab
            preferences[UI_SHOW_PROFILE_BADGES] = sharedSettings.showProfileBadges
            preferences[UI_SHOW_PROFILE_APP_RECOMMENDATIONS] = sharedSettings.showProfileAppRecommendations
            preferences[UI_SHOW_PROFILE_ZAP_RECEIVED_FEED] = sharedSettings.showProfileZapReceivedFeed
            preferences[UI_SHOW_PROFILE_FOLLOWERS_FEED] = sharedSettings.showProfileFollowersFeed
            preferences[UI_DONT_SHOW_ONCHAIN_PUBLIC_WARNING] = sharedSettings.dontShowOnchainPublicWarning
            preferences[UI_SUGGEST_WORKOUTS_FROM_HEALTH_CONNECT] = sharedSettings.suggestWorkoutsFromHealthConnect.name
            preferences[UI_ACCENT_COLOR] = sharedSettings.accentColor.name
            preferences[UI_FONT_FAMILY] = sharedSettings.fontFamily.name
            preferences[UI_FONT_SIZE] = sharedSettings.fontSize.name
            preferences[UI_COMPOSE_SIGNATURE] = sharedSettings.composeSignature
            preferences[UI_SHOW_ONCHAIN_WALLET] = sharedSettings.showOnchainWallet
            preferences[UI_SHOW_PAYTO_ZAP_CHIP] = sharedSettings.showPayToZapChip
            preferences[UI_SEARCH_ENGINE] = sharedSettings.searchEngine
            preferences[UI_SPELL_CHECK] = sharedSettings.spellCheck.name
            preferences[UI_NOTE_LAYOUT] = sharedSettings.noteLayout.name
            preferences[UI_DOWNLOAD_TRANSLATION_MODELS] = sharedSettings.automaticallyDownloadTranslationModels.name
            preferences[UI_HIDE_WALLET_BALANCES] = sharedSettings.hideWalletBalances
            preferences[UI_NAVIGATION_LAYOUT] = sharedSettings.navigationLayout.name
            preferences[UI_CHAT_LAYOUT] = sharedSettings.chatLayout.name
        }

        /**
         * Puts the desktop app on the [NavigationLayoutType.TABS] layout, once: new installs start
         * there and every existing install is moved to it, whatever layout it had. The marker keeps
         * it to one run, so a layout the user picks afterwards sticks.
         *
         * A migration on the file rather than a step after [load], so it runs before any store
         * sharing the file reads it, and never again.
         */
        fun desktopTabsLayout(): DataMigration<Preferences> =
            CopyOnceMigration("migrated.desktopTabsLayout") { out ->
                out[UI_NAVIGATION_LAYOUT] = NavigationLayoutType.TABS.name
            }

        /**
         * The one-shot copy out of the single `shared_settings` JSON blob these
         * settings used to be kept as, in the global encrypted file.
         *
         * Guarded, and it has to be. Unlike the per-account migrations, this
         * store has been the real home of these settings for a while, so most
         * installs already have a populated one — and copying an old blob over
         * it would undo every UI change the user has made since. [UI_THEME] is
         * the test: [write] sets every key unconditionally and is the only
         * writer, so its absence means this store has never been saved, which
         * is exactly the install whose settings are still only in the legacy
         * file.
         *
         * Must be handed to the store at construction, so every consumer of the
         * shared file gets it no matter which one opens the file first.
         */
        fun migrations(loadLegacy: suspend () -> UiSettings?): List<DataMigration<Preferences>> =
            listOf(
                CopyOnceMigration("migrated.sharedSettings") { out ->
                    if (out[UI_THEME] == null) {
                        withContext(Dispatchers.IO) {
                            loadLegacy()?.let { out.write(it) }
                        }
                    }
                },
            )
    }
}
