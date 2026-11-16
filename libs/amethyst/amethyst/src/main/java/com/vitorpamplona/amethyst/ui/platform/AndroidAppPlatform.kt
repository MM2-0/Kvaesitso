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
package com.vitorpamplona.amethyst.ui.platform

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.commons.account.ui.login.LoginViewModel
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobPrefetcher
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.payments.PayToAppProbe
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.amethyst.commons.ui.platform.NostrConnectApp
import com.vitorpamplona.amethyst.commons.ui.platform.Toaster
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.rememberDecryptedConcordImageModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.debugState
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.napplet.NappletLauncher
import com.vitorpamplona.amethyst.napplet.NappletWebViewProfiles
import com.vitorpamplona.amethyst.ui.call.CallActivity
import com.vitorpamplona.amethyst.ui.call.rememberCallWithPermission
import com.vitorpamplona.amethyst.ui.components.SelectNotificationProvider
import com.vitorpamplona.amethyst.ui.navigation.topbars.AndroidAroundMeLocationLabel
import com.vitorpamplona.amethyst.ui.note.DrawPlayName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.InlineNappletView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity.NestActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.AndroidNotificationCategorySettings
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.AndroidNotificationDeliverySettings
import com.vitorpamplona.amethyst.ui.screen.loggedOff.login.ExternalSignerButton
import com.vitorpamplona.amethyst.ui.screen.loggedOff.login.OpenURIIfNotLoggedIn
import com.vitorpamplona.amethyst.ui.screen.loggedOff.login.nostrConnectSignerApps
import com.vitorpamplona.amethyst.ui.screen.loggedOff.login.openNostrConnectInSigner
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip55AndroidSigner.client.isExternalSignerInstalled
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.AppBottomBar as AppBottomBarImpl
import com.vitorpamplona.amethyst.favorites.rememberManifestIconModel as AppRememberManifestIconModel
import com.vitorpamplona.amethyst.favorites.rememberNappletIconModel as AppRememberNappletIconModel
import com.vitorpamplona.amethyst.favorites.rememberWebAppIconModel as AppRememberWebAppIconModel
import com.vitorpamplona.amethyst.ui.note.creators.location.GeohashLocationPickerContent as AppGeohashLocationPickerContent
import com.vitorpamplona.amethyst.ui.note.creators.location.GeohashLocationPickerDialog as AppGeohashLocationPickerDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geocaches.map.GeocacheMapTab as AndroidGeocacheMapTab
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.QrCodeScannerDialog as AppQrCodeScannerDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.legalSettingsCategory as flavorLegalSettingsCategory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion.DetectedWorkoutCarousel as AndroidDetectedWorkoutCarousel
import com.vitorpamplona.amethyst.ui.screen.loggedOff.legal.TermsGate as FlavorTermsGate

/** Android's [AppPlatform]: the app's own shell pieces, camera scanner, app launcher and icon caches. */
object AndroidAppPlatform : AppPlatform {
    override val isCastingAvailable: Boolean get() = BuildConfig.IS_CASTING_AVAILABLE

    // The deck works on any window wide enough for it (tablets, Chromebooks); tabs and windows are desktop's for now.
    override val navigationLayouts: List<NavigationLayoutType> get() = listOf(NavigationLayoutType.STACK, NavigationLayoutType.DECK)

    // NestActivity.launch adds FLAG_ACTIVITY_NEW_TASK, so the application context can start it.
    override fun openNestRoom(addressValue: String) = NestActivity.launch(Amethyst.instance.appContext, addressValue)

    // Per flavour: Play links the hosted policies, F-Droid surfaces none.
    override val appVersionName: String get() = BuildConfig.VERSION_NAME

    override val appFlavor: String get() = BuildConfig.FLAVOR

    override val isDebugBuild: Boolean get() = isDebug

    override val payToApps: PayToAppProbe get() = Amethyst.instance.payToApps

    override val releaseNotesId: String get() = BuildConfig.RELEASE_NOTES_ID

    override fun logDebugState() = debugState(Amethyst.instance.appContext)

    // The Play Store build asks a first login to accept the terms of use; F-Droid ships none.
    override val requiresTermsAcceptance: Boolean get() = BuildConfig.FLAVOR == "play"

    @Composable
    override fun TermsGate(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        showError: Boolean,
    ) = FlavorTermsGate(checked, onCheckedChange, showError)

    // NIP-46: a bunker:// address in the key field, or a nostrconnect:// code for a signer to scan.
    override val supportsRemoteSignerLogin: Boolean get() = true

    override fun nostrConnectApps(uri: String): List<NostrConnectApp> = nostrConnectSignerApps(Amethyst.instance.appContext, uri)

    override fun openNostrConnect(
        uri: String,
        appId: String?,
    ): Boolean = openNostrConnectInSigner(Amethyst.instance.appContext, uri, appId)

    @Composable
    override fun ExternalSignerLoginButton(loginViewModel: LoginViewModel) = ExternalSignerButton(loginViewModel)

    @Composable
    override fun rememberHasExternalSignerLogin(): Boolean {
        // A PackageManager query: once per screen, not on every keystroke of the key field.
        val context = LocalContext.current
        return remember(context) { isExternalSignerInstalled(context) }
    }

    @Composable
    override fun IncomingLoginKeys(onKey: suspend (String) -> Unit) = OpenURIIfNotLoggedIn(onKey)

    @Composable
    override fun rememberToaster(): Toaster {
        val context = LocalContext.current
        // Callers may report from any thread; a toast needs the main one.
        val scope = rememberCoroutineScope()
        return remember(context, scope) {
            Toaster { message, long ->
                scope.launch {
                    Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun legalSettingsCategory(uriHandler: UriHandler): SettingsCategory? = flavorLegalSettingsCategory(uriHandler)

    @Composable
    override fun SpeakNameButton(name: String) = DrawPlayName(name)

    @Composable
    override fun NotificationProviderPrompt(sharedPrefs: UiSettingsFlow) = SelectNotificationProvider(sharedPrefs)

    @Composable
    override fun NotificationDeliverySettings(accountViewModel: AccountViewModel) = AndroidNotificationDeliverySettings(accountViewModel)

    @Composable
    override fun NotificationCategorySettings() = AndroidNotificationCategorySettings()

    override val hasTorProgressNotification: Boolean = true

    @Composable
    override fun rememberAppLanguages(): ImmutableMap<String, String> {
        val context = LocalContext.current
        return remember(context) { context.getLangPreferenceDropdownEntries() }
    }

    @Composable
    override fun AppBottomBar(
        selectedRoute: Route?,
        nav: INav,
        accountViewModel: AccountViewModel,
        onClick: (Route) -> Unit,
    ) = AppBottomBarImpl(selectedRoute, nav, accountViewModel, onClick)

    @Composable
    override fun AroundMeLocationLabel() = AndroidAroundMeLocationLabel()

    @Composable
    override fun GeohashLocationPickerDialog(
        initialGeohash: String?,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
        precision: Int?,
    ) = AppGeohashLocationPickerDialog(initialGeohash, onDismiss, onConfirm, precision)

    @Composable
    override fun GeohashLocationPickerContent(
        initialGeohash: String?,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        modifier: Modifier,
    ) = AppGeohashLocationPickerContent(initialGeohash, confirmLabel, onConfirm, modifier)

    @Composable
    override fun QrCodeScannerDialog(
        onDismiss: () -> Unit,
        onScan: (String) -> ScanOutcome,
    ) = AppQrCodeScannerDialog(onDismiss, onScan)

    @Composable
    override fun rememberCallStarter(
        peers: Set<HexKey>,
        callType: CallType,
    ): (() -> Unit)? {
        val context = LocalContext.current
        return rememberCallWithPermission(context, isVideo = callType == CallType.VIDEO) {
            CallActivity.launchForOutgoingCall(context, peers, callType)
        }
    }

    override val hasGeocacheMap: Boolean get() = true

    @Composable
    override fun GeocacheMapTab(
        feedContentState: FeedContentState,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AndroidGeocacheMapTab(feedContentState, accountViewModel, nav)

    @Composable
    override fun DetectedWorkoutCarousel(
        accountViewModel: AccountViewModel,
        onPick: (Route.NewWorkout) -> Unit,
    ) = AndroidDetectedWorkoutCarousel(accountViewModel = accountViewModel, onPick = onPick)

    @Composable
    override fun rememberAppLauncher(): AppLauncher {
        val context = LocalContext.current
        return remember(context) { AndroidAppLauncher(context) }
    }

    @Composable
    override fun rememberConcordImageModel(
        pointer: ImagePointer?,
        accountViewModel: AccountViewModel,
    ): String? {
        // getCacheDir() takes a lock and stats the folder: once per call site, not per recomposition.
        val context = LocalContext.current
        val cacheDir = remember(context) { context.cacheDir }
        return rememberDecryptedConcordImageModel(pointer, accountViewModel, cacheDir)
    }

    @Composable
    override fun rememberNappletIconModel(coordinate: String): String? = AppRememberNappletIconModel(coordinate)

    // The same cross-process surface the favorite tabs use, which needs Android 11.
    override val runsNappletsInline: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @Composable
    override fun InlineNappletSurface(
        manifest: NappletManifest,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) InlineNappletView(manifest, instanceId, onStatus, modifier)
    }

    @Composable
    override fun InlinePaperSurface(
        html: String,
        title: String,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
        // Touch scrolls the WebView directly; there is no wheel to route.
        takesWheel: Boolean,
    ) {
        // The :napplet sandbox renders it, as it does an nSite: no WebView ever boots in this process.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val tooLarge = remember(html) { html.encodeToByteArray().size > NappletLauncher.MAX_PAPER_BYTES }
            if (tooLarge) {
                // Said as what it is, not as a failure Retry could fix.
                LaunchedEffect(instanceId) { onStatus(InlineNappletStatus.TOO_LARGE) }
                Box(modifier)
                return
            }
            val context = LocalContext.current
            InlineNappletView(html to instanceId, instanceId, onStatus, modifier) {
                NappletLauncher.buildInlinePaperParams(context, html, title, instanceId)
            }
        }
    }

    @Composable
    override fun InlineWebxdcSurface(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val context = LocalContext.current
            InlineNappletView(app.url to postId, instanceId, onStatus, modifier) {
                NappletLauncher.buildInlineWebxdcParams(context, app, postId, poster, instanceId)
            }
        }
    }

    @Composable
    override fun PrefetchNappletFiles(
        author: String,
        identifier: String,
        paths: List<PathTag>,
        servers: List<String>,
    ) {
        val context = LocalContext.current
        LaunchedEffect(paths, servers) {
            // Off the main thread: context.cacheDir touches disk (ensurePrivateCacheDirExists), so reading
            // it on the composition dispatcher trips StrictMode. The prefetch itself is already IO-bound.
            withContext(Dispatchers.IO) {
                // A card that scrolls by downloads nothing the user's media setting (never, or Wi-Fi
                // only on a metered connection) wouldn't load for an image.
                if (!Amethyst.instance.uiState.showImages()) return@withContext
                val torPort = Amethyst.instance.torManager.activePortOrNull.value
                if (torPort == null && Amethyst.instance.torPrefs.torType.value != TorType.OFF) return@withContext
                // The partition its launch will use: the per-account WebView profile and the site.
                val partition = NappletBlobCache.partitionOf(NappletWebViewProfiles.current().orEmpty(), author, identifier)
                runCatchingCancellable { NappletBlobPrefetcher.prefetch(paths, servers, context.cacheDir, torPort ?: -1, partition) }
            }
        }
    }

    @Composable
    override fun rememberManifestIconModel(
        author: String,
        identifier: String,
    ): String? = AppRememberManifestIconModel(author, identifier)

    @Composable
    override fun rememberWebAppIconModel(url: String): String? = AppRememberWebAppIconModel(url)
}

private class AndroidAppLauncher(
    private val context: Context,
) : AppLauncher {
    // Android's browser is an activity of its own, in its own task: no screen of the app's to open on.
    override fun launch(
        app: FavoriteApp,
        stillLoading: String,
        nav: INav?,
    ) = FavoriteAppLauncher.launch(context, app, stillLoading)

    override fun launchUrl(
        url: String,
        preferTor: Boolean,
        nav: INav?,
    ) = FavoriteAppLauncher.launchUrl(context, url, preferTor)

    // Napplets and nSites hand off to the sandboxed :napplet process.
    override val opensNapplets: Boolean get() = true

    override fun openNapplet(
        manifest: NappletManifest,
        nav: INav?,
    ) {
        when (manifest) {
            is RootNappletEvent -> NappletLauncher.launch(context, manifest, manifest.pubKey, "")
            is NamedNappletEvent -> NappletLauncher.launch(context, manifest, manifest.pubKey, manifest.identifier())
        }
    }

    override fun openSite(
        site: Event,
        nav: INav?,
    ) = NappletLauncher.launchSite(context, site)

    override fun openWebxdc(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
    ) = NappletLauncher.launchWebxdc(context, app, postId, poster)
}
