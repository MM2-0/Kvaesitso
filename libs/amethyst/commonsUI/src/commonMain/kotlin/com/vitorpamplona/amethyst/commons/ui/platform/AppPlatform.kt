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
package com.vitorpamplona.amethyst.commons.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import com.vitorpamplona.amethyst.commons.account.ui.login.LoginViewModel
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.front_page_can_leave
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellCheckService
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.payments.PayToAppProbe
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.StringResource

/** A signer app that takes `nostrconnect://` addresses: [id] is what [AppPlatform.openNostrConnect] takes. */
@Immutable
data class NostrConnectApp(
    val id: String,
    val label: String,
)

/**
 * The app shell's pieces that shared screens embed but only the front end can draw yet: the
 * bottom navigation bar (its pinned favorites and groups read app stores) and the "around me"
 * location label (it asks for the location permission). Installed at the composition root
 * through [LocalAppPlatform]; each member draws nothing by default.
 */
@Stable
interface AppPlatform {
    /**
     * The navigation layouts this front end offers in Settings › App; the stack is always first.
     * The deck needs a wide window and tabs and windows a desktop-size front end; with only the
     * stack on offer the setting is not shown.
     */
    val navigationLayouts: List<NavigationLayoutType> get() = listOf(NavigationLayoutType.STACK)

    /** Whether this build can cast media to a TV (the Play flavour's Cast SDK). */
    val isCastingAvailable: Boolean get() = false

    /** This build's version name ("1.04.2"), shown in the donation card; empty where unknown. */
    val appVersionName: String get() = ""

    /** This build's distribution flavour ("play", "fdroid"), shown next to the version; empty where none. */
    val appFlavor: String get() = ""

    /** Whether this is a debug build, which unlocks the drawer's in-progress destinations. */
    val isDebugBuild: Boolean get() = false

    /** Which installed apps can take a `payto` hand-off from the zap picker. */
    val payToApps: PayToAppProbe get() = PayToAppProbe.None

    /** The note announcing this release, which the notifications tab's donation card links to; null shows no card. */
    val releaseNotesId: String? get() = null

    /** Dumps the app's runtime state (memory, caches, subscriptions) to the log, for the logo's debug tap. */
    fun logDebugState() {}

    /** Whether a first login must accept the terms of use (the Play Store build). */
    val requiresTermsAcceptance: Boolean get() = false

    /**
     * Whether the login screen offers NIP-46 remote signers (`bunker://` addresses and a
     * `nostrconnect://` code). Desktop and Android both do; Android is also a bunker for other apps.
     */
    val supportsRemoteSignerLogin: Boolean get() = false

    /**
     * Whether a mouse or trackpad is the main pointer (the desktop), so dense controls need not be
     * padded out to Material's 48dp touch target. False wherever a finger is the pointer, including
     * Android tablets on the wide layouts.
     */
    val hasFinePointer: Boolean get() = false

    /**
     * The signer apps that take a `nostrconnect://` address, for the login dialog to let the user
     * pick one when there are several. Empty where the platform picks the app itself (Desktop's
     * registered handler) or none is installed.
     */
    fun nostrConnectApps(uri: String): List<NostrConnectApp> = emptyList()

    /**
     * Hands a `nostrconnect://` address to a signer app so the user approves without scanning:
     * [appId], one of [nostrConnectApps], or else the only one there is (the system's registered
     * handler on Desktop, Opal on Linux). False where nothing took it.
     */
    fun openNostrConnect(
        uri: String,
        appId: String? = null,
    ): Boolean = false

    /**
     * Whether the platform tells metered and mobile connections from unmetered ones. Where it
     * cannot (Desktop reports every connection as unmetered), the "Wi-Fi" choice of the media
     * settings behaves exactly like "Always", so the settings leave it out.
     */
    val detectsMeteredConnections: Boolean get() = true

    /**
     * Underlines misspellings in the composers and offers suggestions on right-click. Null where
     * the keyboard already does this (Android, iOS), which also hides the setting.
     */
    val spellCheck: SpellCheckService? get() = null

    /** Linux on Wayland: native Wayland or XWayland, picked in settings. Null where there is no such choice. */
    val displayServerChoice: DisplayServerChoice? get() = null

    /** The terms-of-use checkbox the login and sign-up forms show where [requiresTermsAcceptance]. */
    @Composable
    fun TermsGate(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        showError: Boolean,
    ) {}

    /** Logs in through a signer app (Android's NIP-55 signers); shown where [rememberHasExternalSignerLogin]. */
    @Composable
    fun ExternalSignerLoginButton(loginViewModel: LoginViewModel) {}

    /** Whether a signer app can log in here: the form then leads with [ExternalSignerLoginButton]. */
    @Composable
    fun rememberHasExternalSignerLogin(): Boolean = false

    /** Hands the login form the keys the app is opened with (a `nostr:` link) while logged off. */
    @Composable
    fun IncomingLoginKeys(onKey: suspend (String) -> Unit) {}

    /** Shows brief messages over the app (Android's toasts); drops them where there is none. */
    @Composable
    fun rememberToaster(): Toaster = Toaster.None

    /** The settings screen's legal links, when the build's distribution calls for them. */
    fun legalSettingsCategory(uriHandler: UriHandler): SettingsCategory? = null

    /**
     * The languages the app is translated into, as display name ("Deutsch") to language tag
     * ("de"), for the language picker. Empty where the platform follows the system language only.
     */
    @Composable
    fun rememberAppLanguages(): ImmutableMap<String, String> = persistentMapOf()

    @Composable
    fun AppBottomBar(
        selectedRoute: Route?,
        nav: INav,
        accountViewModel: AccountViewModel,
        onClick: (Route) -> Unit,
    ) {}

    @Composable
    fun AroundMeLocationLabel() {}

    /**
     * Asks for the OS permission to post notifications and, where the build has a choice of push
     * servers (F-Droid's UnifiedPush), lets the user pick one. Shown when the notifications tab opens.
     */
    @Composable
    fun NotificationProviderPrompt(sharedPrefs: UiSettingsFlow) {}

    /**
     * How notifications reach this device: the push provider, the background service and its
     * per-account participation, battery optimization. Empty where the platform has none.
     */
    @Composable
    fun NotificationDeliverySettings(accountViewModel: AccountViewModel) {}

    /** The per-category notification settings the system owns (Android's channels). */
    @Composable
    fun NotificationCategorySettings() {}

    /**
     * Whether this platform can show a notification with Tor's startup progress
     * ([com.vitorpamplona.amethyst.commons.tor.TorSettings.progressNotification]). Only Android has the
     * notifier, so the Tor settings show the switch only where this is true.
     */
    val hasTorProgressNotification: Boolean get() = false

    /** A button that reads [name] aloud with the platform's text-to-speech; nothing where there is none. */
    @Composable
    fun SpeakNameButton(name: String) {}

    /**
     * A dialog to pick a place (map, search, "use my location"), as a geohash. The reader picks how
     * big an area it names, unless [precision] fixes its length: then the picker picks a point at
     * exactly that many characters (a geocache needs 9) and offers no area sizes.
     */
    @Composable
    fun GeohashLocationPickerDialog(
        initialGeohash: String?,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
        precision: Int?,
    ) {}

    /** The picker's body, for screens that host it in their own scaffold. */
    @Composable
    fun GeohashLocationPickerContent(
        initialGeohash: String?,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        modifier: Modifier,
    ) {}

    /** The camera QR scanner, full screen. [onScan] says whether the payload was used. */
    @Composable
    fun QrCodeScannerDialog(
        onDismiss: () -> Unit,
        onScan: (String) -> ScanOutcome,
    ) {}

    /** Opens favorite apps, web links, napplets and nSites in the platform's app surface. */
    @Composable
    fun rememberAppLauncher(): AppLauncher = AppLauncher.None

    /**
     * The live title of what a platform-run screen shows ([Route.WebPage], [Route.NostrApp]), for its
     * tab or window: the page's or the app's own name. Null where the platform runs none as screens.
     */
    @Composable
    fun platformScreenTitle(route: Route): String? = null

    /**
     * Starts a [callType] call with [peers], asking for the call permissions first where the
     * platform needs them. Null where calls are not supported, so callers hide their buttons.
     */
    @Composable
    fun rememberCallStarter(
        peers: Set<HexKey>,
        callType: CallType,
    ): (() -> Unit)? = null

    /** Whether [GeocacheMapTab] draws a map here; the geocaches screen hides its Map tab when not. */
    val hasGeocacheMap: Boolean get() = false

    /** The map view of the nearby geocaches. Empty where the platform has no map. */
    @Composable
    fun GeocacheMapTab(
        feedContentState: FeedContentState,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    /**
     * Recent workouts the device already recorded (Android's Health Connect), offered as a tap to
     * pre-fill the new-workout form. Empty where there is no health store.
     */
    @Composable
    fun DetectedWorkoutCarousel(
        accountViewModel: AccountViewModel,
        onPick: (Route.NewWorkout) -> Unit,
    ) {}

    /**
     * The image model for a CORD-02 community picture: the plain URL for a url-only pointer, or a
     * local copy of the decrypted blob for an encrypted one (null while it loads or if it fails).
     */
    @Composable
    fun rememberConcordImageModel(
        pointer: ImagePointer?,
        accountViewModel: AccountViewModel,
    ): String? = pointer?.takeIf { !it.isResolvable() }?.url?.ifBlank { null }

    /** The cached icon of the napplet or nsite at [coordinate], if one was fetched. */
    @Composable
    fun rememberNappletIconModel(coordinate: String): String? = null

    /**
     * Downloads a napplet's or nSite's files into the platform's blob cache while its card is on
     * screen, so Open starts at once. Never on the open web while the user wants Tor and Tor isn't
     * running yet: the launch fetches them then, on the right route.
     */
    @Composable
    fun PrefetchNappletFiles(
        author: String,
        identifier: String,
        paths: List<PathTag>,
        servers: List<String>,
    ) {}

    /** Whether [InlineNappletSurface] runs a napplet inside a note here. */
    val runsNappletsInline: Boolean get() = false

    /**
     * Runs [manifest] inside a note as its own instance [instanceId], filling [modifier], in the
     * platform's sandbox. It may use what the user already granted the app and never asks anything
     * ([com.vitorpamplona.amethyst.commons.napplet.NappletIdentity.inline]). The page gets taps and
     * keys; the feed keeps the scroll. It runs while it is composed and on screen, and ends when it
     * leaves the composition. [onStatus] follows its loading.
     */
    @Composable
    fun InlineNappletSurface(
        manifest: NappletManifest,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
    ) {}

    /**
     * Runs the webxdc [app] posted in [postId] by [poster] inside its note, as instance [instanceId],
     * where [runsNappletsInline]: offline at its own origin, the same storage its window has.
     */
    @Composable
    fun InlineWebxdcSurface(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
    ) {}

    /** Whether [InlinePaperSurface] shows the reader's front page here: wherever napplets run inline. */
    val showsPapersInline: Boolean get() = runsNappletsInline

    /**
     * What The Observer says under a running print about leaving it: on a phone the reader can leave
     * the app ("You can leave…"); on a desktop, whose window closing would quit, they can use the app
     * meanwhile, and closing the window asks first.
     */
    val printLeaveNote: StringResource get() = Res.string.front_page_can_leave

    /**
     * Shows the reader's front page [html] — written, resolved and checked by commons `frontpage` —
     * as instance [instanceId], filling [modifier]. It runs on the same sandboxed web surface as an
     * nSite, under [com.vitorpamplona.amethyst.commons.napplet.HostProfile.PAPER]: no script runs,
     * its art loads from where it was published, and its links open in the system browser.
     *
     * [takesWheel]: the paper fills the screen, so the mouse wheel scrolls it straight away. Inline,
     * among other content, it takes the wheel only once clicked, and the screen scrolls otherwise.
     */
    @Composable
    fun InlinePaperSurface(
        html: String,
        title: String,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
        takesWheel: Boolean = false,
    ) {}

    /** The icon a napplet manifest by [author] / [identifier] declares, if cached. */
    @Composable
    fun rememberManifestIconModel(
        author: String,
        identifier: String,
    ): String? = null

    /** The favicon the browser captured for [url]'s host, if any. */
    @Composable
    fun rememberWebAppIconModel(url: String): String? = null

    /**
     * Opens the audio room at [addressValue] (a kind-30312 address) in its own window; callers
     * hand the account over through `NestBridge` first. Does nothing where rooms are not wired.
     */
    fun openNestRoom(addressValue: String) {}

    /** Draws nothing: previews, and front ends still wiring their pieces. */
    object None : AppPlatform
}

/** The front end's [AppPlatform]. Static: it is set once at the root and never changes. */
val LocalAppPlatform = staticCompositionLocalOf<AppPlatform> { AppPlatform.None }

@Composable
fun AppBottomBar(
    selectedRoute: Route?,
    nav: INav,
    accountViewModel: AccountViewModel,
    onClick: (Route) -> Unit,
) = LocalAppPlatform.current.AppBottomBar(selectedRoute, nav, accountViewModel, onClick)

@Composable
fun AroundMeLocationLabel() = LocalAppPlatform.current.AroundMeLocationLabel()

@Composable
fun SpeakNameButton(name: String) = LocalAppPlatform.current.SpeakNameButton(name)

@Composable
fun NotificationProviderPrompt(sharedPrefs: UiSettingsFlow) = LocalAppPlatform.current.NotificationProviderPrompt(sharedPrefs)

@Composable
fun NotificationDeliverySettings(accountViewModel: AccountViewModel) = LocalAppPlatform.current.NotificationDeliverySettings(accountViewModel)

@Composable
fun NotificationCategorySettings() = LocalAppPlatform.current.NotificationCategorySettings()

@Composable
fun GeohashLocationPickerDialog(
    initialGeohash: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    precision: Int? = null,
) = LocalAppPlatform.current.GeohashLocationPickerDialog(initialGeohash, onDismiss, onConfirm, precision)

@Composable
fun GeohashLocationPickerContent(
    initialGeohash: String?,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) = LocalAppPlatform.current.GeohashLocationPickerContent(initialGeohash, confirmLabel, onConfirm, modifier)

@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) = LocalAppPlatform.current.QrCodeScannerDialog(onDismiss, onScan)

@Composable
fun rememberAppLauncher(): AppLauncher = LocalAppPlatform.current.rememberAppLauncher()

@Composable
fun rememberConcordImageModel(
    pointer: ImagePointer?,
    accountViewModel: AccountViewModel,
): String? = LocalAppPlatform.current.rememberConcordImageModel(pointer, accountViewModel)

@Composable
fun rememberNappletIconModel(coordinate: String): String? = LocalAppPlatform.current.rememberNappletIconModel(coordinate)

/** Prefetches the files of the site [author] / [identifier] (as its launch names it, `""` for a root). */
@Composable
fun PrefetchNappletFiles(
    author: String,
    identifier: String,
    paths: List<PathTag>,
    servers: List<String>,
) = LocalAppPlatform.current.PrefetchNappletFiles(author, identifier, paths, servers)

val runsNappletsInline: Boolean
    @Composable get() = LocalAppPlatform.current.runsNappletsInline

@Composable
fun InlineNappletSurface(
    manifest: NappletManifest,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier = Modifier,
) = LocalAppPlatform.current.InlineNappletSurface(manifest, instanceId, onStatus, modifier)

@Composable
fun InlineWebxdcSurface(
    app: WebxdcApp,
    postId: HexKey,
    poster: HexKey,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier = Modifier,
) = LocalAppPlatform.current.InlineWebxdcSurface(app, postId, poster, instanceId, onStatus, modifier)

/** Where a napplet running inside a note is. */
enum class InlineNappletStatus {
    /** Fetching and verifying its files, or starting its page. */
    LOADING,

    /** Its page has painted. */
    SHOWING,

    /** It could not start here: no server had its files, or the sandbox did not come up. */
    FAILED,

    /** The user routes this content through Tor and Tor is still starting: nothing is fetched yet. */
    WAITING_FOR_TOR,

    /** Tor did not connect in time, so it was not opened rather than fetched around Tor. */
    NO_TOR,

    /** Too large to hand to the sandbox in one message (Android's binder limit): never sent. */
    TOO_LARGE,
}

@Composable
fun rememberManifestIconModel(
    author: String,
    identifier: String,
): String? = LocalAppPlatform.current.rememberManifestIconModel(author, identifier)

@Composable
fun rememberWebAppIconModel(url: String): String? = LocalAppPlatform.current.rememberWebAppIconModel(url)

/**
 * Opens a favorite app, a web link, a napplet or an nSite in the platform's app surface (on Android,
 * the sandboxed full-screen WebView in its own task; on the desktop, a web page is a screen that
 * [nav] places as the navigation layout says, and a napplet or nSite a Chromium window).
 */
interface AppLauncher {
    /**
     * [stillLoading] is shown when a Nostr app's defining event has not arrived yet. [nav] is the
     * calling screen's: a platform that draws the app as a screen opens it there.
     */
    fun launch(
        app: FavoriteApp,
        stillLoading: String,
        nav: INav? = null,
    )

    /**
     * [preferTor] forces Tor when available, e.g. for `.onion` hosts. [nav] is the calling screen's:
     * a platform that draws web pages as screens opens one there ([Route.WebPage]).
     */
    fun launchUrl(
        url: String,
        preferTor: Boolean = false,
        nav: INav? = null,
    )

    /** Whether [openNapplet] and [openSite] run anything here; cards offer Open only where they do. */
    val opensNapplets: Boolean get() = false

    /**
     * Runs a NIP-5D napplet in the platform's sandbox, or tells the user why it can't. [nav] is the
     * calling screen's: a platform that runs apps as screens opens it there ([Route.NostrApp]).
     */
    fun openNapplet(
        manifest: NappletManifest,
        nav: INav? = null,
    ) {}

    /**
     * Opens a NIP-5A nSite ([RootSiteEvent] or [NamedSiteEvent]), or tells the user why it can't.
     * [nav] is the calling screen's, as for [openNapplet].
     */
    fun openSite(
        site: Event,
        nav: INav? = null,
    ) {}

    /**
     * Runs the webxdc [app] attached to post [postId] by [poster] in the platform's sandbox (when
     * [opensNapplets]), or tells the user why it can't.
     */
    fun openWebxdc(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
    ) {}

    /** Launches nothing: front ends without an app surface yet. */
    object None : AppLauncher {
        override fun launch(
            app: FavoriteApp,
            stillLoading: String,
            nav: INav?,
        ) {}

        override fun launchUrl(
            url: String,
            preferTor: Boolean,
            nav: INav?,
        ) {}
    }
}

@Composable
fun rememberCallStarter(
    peers: Set<HexKey>,
    callType: CallType,
): (() -> Unit)? = LocalAppPlatform.current.rememberCallStarter(peers, callType)

@Composable
fun GeocacheMapTab(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalAppPlatform.current.GeocacheMapTab(feedContentState, accountViewModel, nav)

@Composable
fun DetectedWorkoutCarousel(
    accountViewModel: AccountViewModel,
    onPick: (Route.NewWorkout) -> Unit,
) = LocalAppPlatform.current.DetectedWorkoutCarousel(accountViewModel, onPick)

/** Shows a brief message over the app; [long] keeps it up longer. */
fun interface Toaster {
    fun show(
        message: String,
        long: Boolean,
    )

    companion object {
        val None = Toaster { _, _ -> }
    }
}
