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
package com.vitorpamplona.amethyst.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.util.Consumer
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.ui.AddAccountDialog
import com.vitorpamplona.amethyst.commons.model.navigation.MediaFeedRoute
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.isSameRoute
import com.vitorpamplona.amethyst.commons.model.navigation.limitToRouteTextArg
import com.vitorpamplona.amethyst.commons.model.navigation.uri.findQueryParameterValue
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.invalid_nip19_uri
import com.vitorpamplona.amethyst.commons.resources.invalid_nip19_uri_description
import com.vitorpamplona.amethyst.commons.ui.navigation.ShareIntentRouting
import com.vitorpamplona.amethyst.commons.ui.navigation.ShareTarget
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromBottomArgs
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromEnd
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromEndArgs
import com.vitorpamplona.amethyst.commons.ui.navigation.host.jvmDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.consumesSharesInPlace
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.getRouteWithArguments
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.isBaseRoute
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.NowPlayingSettingsScreen
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.nowPlaying.AndroidAppIcon
import com.vitorpamplona.amethyst.service.nowPlaying.AndroidNowPlayingAccess
import com.vitorpamplona.amethyst.ui.call.CallActivity
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.WebAppScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.NostrAppScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.ConnectedAppDetailScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.ScanQrImageScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.ResourceUsageScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.NewHlsVideoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.fitness.MyFitnessScreen
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip84Highlights.parse.SharedHighlightParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI

@Composable
internal fun ObserveIncomingCalls(accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val callState by accountViewModel.callManager.state.collectAsState()

    LaunchedEffect(callState) {
        val state = callState
        if (state is CallState.IncomingCall || state is CallState.Offering) {
            CallActivity.launch(context)
        }
    }
}

/** The destinations whose screens only the Android app has; the shared ones register in commonsUI. */
internal fun NavDestinations.androidDestinations(accountViewModel: AccountViewModel) {
    jvmDestinations(accountViewModel)
    composableFromEnd<Route.MyFitness> { MyFitnessScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.WebApp>(capWidth = false) { WebAppScreen(it.url, accountViewModel, nav) }
    composableFromEndArgs<Route.NostrApp>(capWidth = false) { NostrAppScreen(it.coordinate, accountViewModel, nav) }
    composableFromEndArgs<Route.ConnectedAppDetail> { ConnectedAppDetailScreen(it.coordinate, accountViewModel, nav) }
    composableFromEnd<Route.NewHlsVideo> { NewHlsVideoScreen(accountViewModel, nav) }
    composableFromBottomArgs<Route.ScanQrImage> { ScanQrImageScreen(it.uri, accountViewModel, nav) }
    composableFromEnd<Route.NowPlayingSettings> {
        val context = LocalContext.current
        val access = remember(context) { AndroidNowPlayingAccess(context.applicationContext) }
        NowPlayingSettingsScreen(accountViewModel, nav, access) { appId, label -> AndroidAppIcon(appId, label) }
    }
    composableFromEnd<Route.ResourceUsage> { ResourceUsageScreen(accountViewModel, nav) }
}

/** True for both share flavors: a single file/text (SEND) and a multi-file selection (SEND_MULTIPLE). */
private fun Intent.isShareAction(): Boolean = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE

/**
 * The text Android sent with the share — a caption, a URL, or the whole payload of a text-only
 * share. Capped on the way in: a share carries whatever the other app put in it (Binder allows
 * hundreds of kilobytes), and every share target below hands this straight to a route argument,
 * where an oversized value makes the destination unmatchable. See [limitToRouteTextArg].
 */
private fun Intent.sharedText(): String? = getStringExtra(Intent.EXTRA_TEXT)?.ifBlank { null }?.limitToRouteTextArg()

/**
 * Every content URI the share carries, in the order the sending app listed them. SEND_MULTIPLE
 * puts them in a parcelable ArrayList; plain SEND has at most one. Only the media targets declare
 * SEND_MULTIPLE, so every other caller can just take the first.
 */
private fun Intent.sharedStreamUris(): List<String> =
    if (action == Intent.ACTION_SEND_MULTIPLE) {
        IntentCompat
            .getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
            ?.map { it.toString() }
            .orEmpty()
    } else {
        listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.toString())
    }

/**
 * Opens a media feed on a share. Sharing again while an earlier share is still on screen replaces
 * it instead of stacking a second copy of the feed — but only when the entry on top is itself a
 * share (it carries attachments). A feed the user opened from the bottom bar carries none, so it
 * stays put underneath and keeps its tab-root marker.
 */
private inline fun <reified T> Nav.navToSharedFeed(route: T) where T : Route, T : MediaFeedRoute {
    val current = getRouteWithArguments(T::class, this)
    if (current is MediaFeedRoute && current.attachments.isNotEmpty()) {
        popUpTo(route, T::class)
    } else {
        newStack(route)
    }
}

@Composable
internal fun NavigateIfIntentRequested(
    nav: Nav,
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    val activity = LocalContext.current.getActivity()

    if (activity.intent.isShareAction()) {
        val target = ShareIntentRouting.targetOf(activity.intent.component?.className)

        // avoids restarting the destination screen when the intent is for the screen.
        // Microsoft's swift key sends Gifs as new actions.
        // The media targets land on a feed the user may well be standing on already, so they can't
        // guard on the destination — they rely on the intent being consumed below instead.
        //
        // Read without observing: the current route is snapshot state, and subscribing to it here
        // would re-run this whole block on every navigation, which is exactly when the guard stops
        // guarding (the user has left the composer).
        val alreadyOnDestination =
            Snapshot.withoutReadObservation {
                when (target) {
                    ShareTarget.HIGHLIGHT -> isBaseRoute<Route.NewHighlight>(nav)
                    ShareTarget.DIRECT_MESSAGE -> isBaseRoute<Route.ShareToDM>(nav)
                    ShareTarget.NEW_POST -> isBaseRoute<Route.NewShortNote>(nav)
                    ShareTarget.PICTURE, ShareTarget.SHORT_VIDEO, ShareTarget.VIDEO -> false
                    // Always re-runs: the route carries the image, so a second share of a different
                    // picture must decode that one rather than sit on the previous result.
                    ShareTarget.SCAN_QR -> false
                }
            }
        if (alreadyOnDestination) {
            // The composer this share opened is already up (the activity was restored with the
            // share still in its intent). Consume the intent, so a later pass can't reopen the
            // composer once the user leaves it, and so the listener below takes over.
            activity.intent.action = null
            return
        }

        // saves the intent to avoid processing again
        val message = remember { activity.intent.sharedText() }
        val attachments = remember { activity.intent.sharedStreamUris() }

        when (target) {
            ShareTarget.HIGHLIGHT -> {
                val parsed = message?.let { SharedHighlightParser.parse(it) }
                nav.newStack(
                    Route.NewHighlight(
                        quote = parsed?.quote,
                        url = parsed?.url,
                        prefix = parsed?.prefix,
                        suffix = parsed?.suffix,
                    ),
                )
            }
            // The single-file composers take the first URI: only the media targets declare
            // SEND_MULTIPLE, so anything else can only be carrying one.
            ShareTarget.DIRECT_MESSAGE -> nav.newStack(Route.ShareToDM(message = message, attachment = attachments.firstOrNull()))
            ShareTarget.PICTURE -> nav.navToSharedFeed(Route.Pictures(attachments = attachments, message = message))
            ShareTarget.SHORT_VIDEO -> nav.navToSharedFeed(Route.Shorts(attachments = attachments, message = message))
            ShareTarget.VIDEO -> nav.navToSharedFeed(Route.Video(attachments = attachments, message = message))
            ShareTarget.NEW_POST -> nav.newStack(Route.NewShortNote(message = message, attachment = attachments.firstOrNull()))
            ShareTarget.SCAN_QR ->
                attachments.firstOrNull()?.let { nav.newStack(Route.ScanQrImage(it)) }
        }

        // Consume the launch intent so a later recomposition can't re-fire
        // newStack for the same share (the isBaseRoute guard is a non-reactive
        // snapshot and stops guarding once we navigate past the destination,
        // e.g. into a chat via the one-shot picker). Clearing the action also
        // lets the else-branch register the onNewIntent listener for the rest
        // of this session.
        activity.intent.action = null
    } else {
        var newAccount by remember { mutableStateOf<String?>(null) }

        var currentIntentNextPage by remember {
            mutableStateOf(
                activity.intent
                    ?.data
                    ?.toString()
                    ?.ifBlank { null },
            )
        }

        currentIntentNextPage?.let { intentNextPage ->
            var actionableNextPage by remember {
                mutableStateOf(uriToRoute(intentNextPage, accountViewModel.account))
            }

            LaunchedEffect(intentNextPage) {
                if (actionableNextPage != null) {
                    actionableNextPage?.let { nextRoute ->
                        val npub = intentNextPage.findQueryParameterValue("account")
                        if (npub != null && accountSessionManager.currentAccountNPub() != npub) {
                            accountSessionManager.checkAndSwitchUserSync(npub) { account ->
                                uriToRoute(intentNextPage, account)
                            }
                        } else {
                            val currentRoute = getRouteWithArguments(nextRoute::class, nav)
                            if (!isSameRoute(currentRoute, nextRoute)) {
                                nav.newStack(nextRoute)
                            }
                            actionableNextPage = null
                        }
                    }
                } else if (intentNextPage.contains("ncryptsec1")) {
                    // login functions
                    Nip19Parser.tryParseAndClean(intentNextPage)?.let {
                        newAccount = it
                    }

                    actionableNextPage = null
                } else {
                    accountViewModel.toastManager.toast(
                        Res.string.invalid_nip19_uri,
                        Res.string.invalid_nip19_uri_description,
                        intentNextPage,
                    )
                }

                currentIntentNextPage = null
            }
        }

        val scope = rememberCoroutineScope()

        DisposableEffect(nav, activity) {
            val consumer =
                Consumer<Intent> { intent ->
                    if (intent.isShareAction()) {
                        val target = ShareIntentRouting.targetOf(intent.component?.className)
                        val message = intent.sharedText()
                        val attachments = intent.sharedStreamUris()
                        val attachment = attachments.firstOrNull()

                        // avoids restarting the destination screen when the intent is for the screen.
                        // Microsoft's swift key sends Gifs as new actions.
                        // The media targets land on a feed the user may well be standing on already, so
                        // they always navigate: the route carries the attachment, so the composer opens
                        // on the newly shared file even when the feed itself is already on screen.
                        when (target) {
                            ShareTarget.HIGHLIGHT ->
                                if (!isBaseRoute<Route.NewHighlight>(nav)) {
                                    val parsed = message?.let { SharedHighlightParser.parse(it) }
                                    nav.newStack(
                                        Route.NewHighlight(
                                            quote = parsed?.quote,
                                            url = parsed?.url,
                                            prefix = parsed?.prefix,
                                            suffix = parsed?.suffix,
                                        ),
                                    )
                                }

                            ShareTarget.DIRECT_MESSAGE ->
                                if (!isBaseRoute<Route.ShareToDM>(nav)) {
                                    nav.newStack(Route.ShareToDM(message = message, attachment = attachment))
                                }

                            ShareTarget.PICTURE -> nav.navToSharedFeed(Route.Pictures(attachments = attachments, message = message))
                            ShareTarget.SHORT_VIDEO -> nav.navToSharedFeed(Route.Shorts(attachments = attachments, message = message))
                            ShareTarget.VIDEO -> nav.navToSharedFeed(Route.Video(attachments = attachments, message = message))

                            ShareTarget.NEW_POST ->
                                if (!consumesSharesInPlace(nav) && (message != null || attachment != null)) {
                                    nav.newStack(Route.NewShortNote(message = message, attachment = attachment))
                                }

                            ShareTarget.SCAN_QR -> attachment?.let { nav.newStack(Route.ScanQrImage(it)) }
                        }
                    } else {
                        val uri = intent.data?.toString()

                        if (!uri.isNullOrBlank()) {
                            // navigation functions
                            val newPage = uriToRoute(uri, accountViewModel.account)

                            if (newPage != null) {
                                scope.launch {
                                    val npub = uri.findQueryParameterValue("account")
                                    if (npub != null && accountSessionManager.currentAccountNPub() != npub) {
                                        accountSessionManager.checkAndSwitchUserSync(npub) { newAccount ->
                                            uriToRoute(uri, newAccount)
                                        }
                                    } else {
                                        val currentRoute = getRouteWithArguments(newPage::class, nav)
                                        if (!isSameRoute(currentRoute, newPage)) {
                                            nav.newStack(newPage)
                                        }
                                    }
                                }
                            } else if (uri.contains("ncryptsec")) {
                                // login functions
                                Nip19Parser.tryParseAndClean(uri)?.let {
                                    newAccount = it
                                }
                            } else {
                                scope.launch {
                                    delay(1000)
                                    accountViewModel.toastManager.toast(
                                        Res.string.invalid_nip19_uri,
                                        Res.string.invalid_nip19_uri_description,
                                        uri,
                                    )
                                }
                            }
                        }
                    }
                }
            activity.addOnNewIntentListener(consumer)
            onDispose { activity.removeOnNewIntentListener(consumer) }
        }

        if (newAccount != null) {
            AddAccountDialog(newAccount, accountSessionManager) { newAccount = null }
        }
    }
}

fun URI.findParameterValue(parameterName: String): String? =
    rawQuery
        ?.split('&')
        ?.map {
            val parts = it.split('=')
            val name = parts.firstOrNull() ?: ""
            val value = parts.drop(1).firstOrNull() ?: ""
            Pair(name, value)
        }?.firstOrNull { it.first == parameterName }
        ?.second
