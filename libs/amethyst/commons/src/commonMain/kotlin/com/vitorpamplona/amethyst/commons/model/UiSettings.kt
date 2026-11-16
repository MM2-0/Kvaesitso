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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.browser.SearchEngines
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class UiSettings(
    val theme: ThemeType = ThemeType.SYSTEM,
    val preferredLanguage: String? = null,
    val automaticallyShowImages: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyStartPlayback: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyPlayVideos: BooleanType = BooleanType.ALWAYS,
    val automaticallyShowUrlPreview: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyHideNavigationBars: BooleanType = BooleanType.ALWAYS,
    val automaticallyShowProfilePictures: ConnectivityType = ConnectivityType.ALWAYS,
    val dontShowPushNotificationSelector: Boolean = false,
    val dontAskForNotificationPermissions: Boolean = false,
    val featureSet: FeatureSetType = FeatureSetType.SIMPLIFIED,
    val gallerySet: ProfileGalleryType = ProfileGalleryType.CLASSIC,
    val automaticallyProposeAiImprovements: BooleanType = BooleanType.ALWAYS,
    val useTrackedBroadcasts: BooleanType = BooleanType.ALWAYS,
    val automaticallyCreateDrafts: BooleanType = BooleanType.ALWAYS,
    val showHomeNewThreadsTab: Boolean = true,
    val showHomeConversationsTab: Boolean = true,
    val showHomeEverythingTab: Boolean = false,
    val showProfileBadges: Boolean = true,
    val showProfileAppRecommendations: Boolean = true,
    val showProfileZapReceivedFeed: Boolean = true,
    val showProfileFollowersFeed: Boolean = true,
    val dontShowOnchainPublicWarning: Boolean = false,
    val suggestWorkoutsFromHealthConnect: BooleanType = BooleanType.ALWAYS,
    val accentColor: AccentColorType = AccentColorType.PURPLE,
    val fontFamily: FontFamilyType = FontFamilyType.SYSTEM,
    val fontSize: FontSizeType = FontSizeType.NORMAL,
    val composeSignature: String = "",
    // Whether the on-chain (Bitcoin/Taproot) wallet is shown across the app: the
    // "Bitcoin" card on the wallet screen, the on-chain chip on profiles, and the
    // on-chain rail in the Send Payment screen. Defaults to true (shown) so the
    // behavior is unchanged for everyone who doesn't turn it off.
    val showOnchainWallet: Boolean = true,
    // Whether the zap picker offers a NIP-A3 pay-to hand-off chip when the sender
    // and recipient share a payment protocol. Defaults to false: those targets can
    // be bank or Venmo handles carrying legal names, and this puts them one tap
    // from every note in the feed.
    val showPayToZapChip: Boolean = true,
    // The browser omnibox's search engine, by [SearchEngines] id. Unknown ids fall back to the default.
    val searchEngine: String = SearchEngines.DEFAULT.id,
    // Which engine underlines misspellings in the composers, on platforms that offer one (desktop).
    val spellCheck: SpellCheckType = SpellCheckType.AUTOMATIC,
    // How a note lays out its author: a picture column beside the text, or a header row above it.
    val noteLayout: NoteLayoutType = NoteLayoutType.AUTHOR_COLUMN,
    // When the on-device translator may download a language model (~30 MB each). Wi-Fi only by
    // default: a feed full of languages would otherwise pull several of them over mobile data.
    val automaticallyDownloadTranslationModels: ConnectivityType = ConnectivityType.WIFI_ONLY,
    val hideWalletBalances: Boolean = false,
    // How a wide window arranges screens: one stack (today), deck columns, one screen per tab, or one per window.
    val navigationLayout: NavigationLayoutType = NavigationLayoutType.STACK,
    // How a chat room draws its messages: bubbles, compact IRC-style lines, or open full-width text.
    val chatLayout: ChatLayoutType = ChatLayoutType.BUBBLES,
)

/**
 * How a window arranges several screens. [STACK] pushes them on one back stack. [DECK] adds columns
 * beside it on wide windows, each with a stack of its own. [TABS] gives each screen its own tab in
 * the main pane and [WINDOWS] its own OS window, with no back button: they are closed instead.
 * Only desktop-size front ends offer the last two.
 */
enum class NavigationLayoutType {
    STACK,
    DECK,
    TABS,
    WINDOWS,
}

/**
 * How chat rooms (DMs, public chats, groups) draw their messages.
 *
 * - [BUBBLES]: messenger bubbles, the user's own on the right and everyone else's on the left.
 * - [COMPACT]: one dense left-aligned line per message, `Name  text` with each author in their own
 *   color, like IRC and Twitch chat. Every line names its author, the user included.
 * - [OPEN]: the user's own messages in soft bubbles on the right and everyone else's as open,
 *   full-width text with no bubble, like the conversation view of AI assistant apps.
 */
enum class ChatLayoutType {
    BUBBLES,
    COMPACT,
    OPEN,
}

/**
 * How feed notes place their author and media. [AUTHOR_COLUMN] keeps the picture in a column to
 * the left of the whole note (Twitter style). [FULL_WIDTH] puts it in a header row with the name,
 * like the Picture feed cards, and lets the content use the full width below. [EDGE_TO_EDGE] is
 * [FULL_WIDTH] with the note's own images and videos running out to the screen edges.
 */
enum class NoteLayoutType {
    AUTHOR_COLUMN,
    FULL_WIDTH,
    EDGE_TO_EDGE,
}

/**
 * The spell checker the composers use. [AUTOMATIC] picks the operating system's checker (macOS,
 * Windows) when it works and the bundled dictionary otherwise; [BUILT_IN] always uses the bundled
 * dictionary.
 */
enum class SpellCheckType {
    AUTOMATIC,
    BUILT_IN,
    OFF,
}

enum class ThemeType(
    val screenCode: Int,
) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
}

fun parseThemeType(code: Int?): ThemeType =
    when (code) {
        ThemeType.SYSTEM.screenCode -> ThemeType.SYSTEM
        ThemeType.LIGHT.screenCode -> ThemeType.LIGHT
        ThemeType.DARK.screenCode -> ThemeType.DARK
        else -> ThemeType.SYSTEM
    }

enum class AccentColorType(
    val screenCode: Int,
) {
    PURPLE(0),
    BLUE(1),
    GREEN(2),
    ORANGE(3),
    RED(4),
    PINK(5),
}

fun parseAccentColorType(screenCode: Int): AccentColorType =
    when (screenCode) {
        AccentColorType.PURPLE.screenCode -> AccentColorType.PURPLE
        AccentColorType.BLUE.screenCode -> AccentColorType.BLUE
        AccentColorType.GREEN.screenCode -> AccentColorType.GREEN
        AccentColorType.ORANGE.screenCode -> AccentColorType.ORANGE
        AccentColorType.RED.screenCode -> AccentColorType.RED
        AccentColorType.PINK.screenCode -> AccentColorType.PINK
        else -> AccentColorType.PURPLE
    }

enum class FontFamilyType(
    val screenCode: Int,
) {
    SYSTEM(0),
    SANS_SERIF(1),
    SERIF(2),
    MONOSPACE(3),
}

fun parseFontFamilyType(screenCode: Int): FontFamilyType =
    when (screenCode) {
        FontFamilyType.SYSTEM.screenCode -> FontFamilyType.SYSTEM
        FontFamilyType.SANS_SERIF.screenCode -> FontFamilyType.SANS_SERIF
        FontFamilyType.SERIF.screenCode -> FontFamilyType.SERIF
        FontFamilyType.MONOSPACE.screenCode -> FontFamilyType.MONOSPACE
        else -> FontFamilyType.SYSTEM
    }

enum class FontSizeType(
    val scale: Float,
    val screenCode: Int,
) {
    SMALL(0.85f, 0),
    NORMAL(1.0f, 1),
    LARGE(1.15f, 2),
    HUGE(1.3f, 3),
}

fun parseFontSizeType(screenCode: Int): FontSizeType =
    when (screenCode) {
        FontSizeType.SMALL.screenCode -> FontSizeType.SMALL
        FontSizeType.NORMAL.screenCode -> FontSizeType.NORMAL
        FontSizeType.LARGE.screenCode -> FontSizeType.LARGE
        FontSizeType.HUGE.screenCode -> FontSizeType.HUGE
        else -> FontSizeType.NORMAL
    }

enum class ConnectivityType(
    val prefCode: Boolean?,
    val screenCode: Int,
) {
    ALWAYS(null, 0),
    WIFI_ONLY(true, 1),
    NEVER(false, 2),
    ;

    /** Whether this choice lets the download run on the current connection. */
    fun allows(isOnMobileOrMeteredConnection: Boolean): Boolean =
        when (this) {
            WIFI_ONLY -> !isOnMobileOrMeteredConnection
            NEVER -> false
            ALWAYS -> true
        }
}

enum class FeatureSetType(
    val screenCode: Int,
) {
    COMPLETE(0),
    SIMPLIFIED(1),
    PERFORMANCE(2),
}

enum class ProfileGalleryType(
    val screenCode: Int,
) {
    CLASSIC(0),
    MODERN(1),
}

fun parseConnectivityType(code: Boolean?): ConnectivityType =
    when (code) {
        ConnectivityType.ALWAYS.prefCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.prefCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.prefCode -> ConnectivityType.NEVER
        else -> ConnectivityType.ALWAYS
    }

fun parseConnectivityType(screenCode: Int): ConnectivityType =
    when (screenCode) {
        ConnectivityType.ALWAYS.screenCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.screenCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.screenCode -> ConnectivityType.NEVER
        else -> ConnectivityType.ALWAYS
    }

fun parseFeatureSetType(screenCode: Int): FeatureSetType =
    when (screenCode) {
        FeatureSetType.COMPLETE.screenCode -> FeatureSetType.COMPLETE
        FeatureSetType.SIMPLIFIED.screenCode -> FeatureSetType.SIMPLIFIED
        FeatureSetType.PERFORMANCE.screenCode -> FeatureSetType.PERFORMANCE
        else -> FeatureSetType.COMPLETE
    }

fun parseGalleryType(screenCode: Int): ProfileGalleryType =
    when (screenCode) {
        ProfileGalleryType.CLASSIC.screenCode -> ProfileGalleryType.CLASSIC
        ProfileGalleryType.MODERN.screenCode -> ProfileGalleryType.MODERN
        else -> ProfileGalleryType.CLASSIC
    }

enum class BooleanType(
    val prefCode: Boolean?,
    val screenCode: Int,
) {
    ALWAYS(null, 0),
    NEVER(false, 1),
}

fun parseBooleanType(code: Boolean?): BooleanType =
    when (code) {
        BooleanType.ALWAYS.prefCode -> BooleanType.ALWAYS
        BooleanType.NEVER.prefCode -> BooleanType.NEVER
        else -> BooleanType.ALWAYS
    }

fun parseBooleanType(screenCode: Int): BooleanType =
    when (screenCode) {
        BooleanType.ALWAYS.screenCode -> BooleanType.ALWAYS
        BooleanType.NEVER.screenCode -> BooleanType.NEVER
        else -> BooleanType.ALWAYS
    }

enum class WarningType(
    val prefCode: Boolean?,
    val screenCode: Int,
) {
    WARN(null, 0),
    SHOW(true, 1),
    HIDE(false, 2),
}

fun parseWarningType(screenCode: Int): WarningType =
    when (screenCode) {
        WarningType.WARN.screenCode -> WarningType.WARN
        WarningType.SHOW.screenCode -> WarningType.SHOW
        WarningType.HIDE.screenCode -> WarningType.HIDE
        else -> WarningType.WARN
    }

fun parseWarningType(code: Boolean?): WarningType =
    when (code) {
        WarningType.WARN.prefCode -> WarningType.WARN
        WarningType.HIDE.prefCode -> WarningType.HIDE
        WarningType.SHOW.prefCode -> WarningType.SHOW
        else -> WarningType.WARN
    }
