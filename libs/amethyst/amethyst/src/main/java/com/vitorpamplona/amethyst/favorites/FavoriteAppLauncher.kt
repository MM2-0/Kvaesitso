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
package com.vitorpamplona.amethyst.favorites

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.napplet.NappletLauncher
import com.vitorpamplona.amethyst.napplet.NappletWebViewProfiles
import com.vitorpamplona.amethyst.napplet.WebAppNetworkRegistry
import com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.utils.Log

/**
 * Turns a [FavoriteApp] back into a running app. The two cases map to the two launch paths in the
 * codebase, nothing more:
 *
 * - [FavoriteApp.WebApp] → a full-screen direct-WebView
 *   [NappletBrowserActivity][com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity] (its own
 *   task/recents entry), so the web client owns the whole screen and scrolls/zooms natively.
 * - [FavoriteApp.NostrApp] → re-resolve the live event from [LocalCache] by coordinate, read its
 *   `requires`/website-mode off the event, then hand to [NappletLauncher] (the sandboxed `:napplet`
 *   host). nsite vs napplet is decided *here*, from the event, never from stored state.
 *
 * A [FavoriteApp.NostrApp] whose event hasn't loaded yet can't launch; we surface that instead of
 * failing silently.
 */
object FavoriteAppLauncher {
    /**
     * [stillLoading] is the toast shown when a NostrApp's defining event hasn't
     * arrived yet. It is passed in already resolved because the launcher runs from
     * plain onClick lambdas, where neither string accessor - composable or
     * suspend - can be called.
     */
    fun launch(
        context: Context,
        app: FavoriteApp,
        stillLoading: String,
    ) {
        when (app) {
            is FavoriteApp.WebApp -> launchUrl(context, app.url)
            is FavoriteApp.NostrApp -> launchNostrApp(context, app.coordinate, stillLoading)
        }
    }

    /**
     * Opens [url] full-screen in its own task, so back/recents treat it like a separate app. Uses the
     * direct-WebView [NappletBrowserActivity] (page scrolls/zooms and the keyboard resizes natively),
     * resolving the proxy port + this site's remembered Tor choice here in the main process. [preferTor]
     * forces Tor (when available) regardless of the remembered choice — used for `.onion`, which only
     * resolves over Tor.
     */
    fun launchUrl(
        context: Context,
        url: String,
        preferTor: Boolean = false,
    ) {
        val proxyPort = Amethyst.instance.torManager.activePortOrNull.value ?: -1
        // Whether Tor is ON, not whether its port is known yet: a surface that wants Tor with no port refuses
        // to load (fails closed) rather than quietly going out on the open web while Tor is still starting.
        val torEnabled = Amethyst.instance.torPrefs.torType.value != TorType.OFF
        val useTor = torEnabled && (preferTor || WebAppNetworkRegistry.useTor(url))
        val themeType = Amethyst.instance.uiPrefs.value.theme.value
        val theme =
            when (themeType) {
                ThemeType.DARK -> "DARK"
                ThemeType.LIGHT -> "LIGHT"
                ThemeType.SYSTEM -> {
                    val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightMask == Configuration.UI_MODE_NIGHT_YES) "DARK" else "LIGHT"
                }
            }
        val isFavorite = Amethyst.instance.favoriteApps.isFavorite("url:$url")
        val intent =
            NappletBrowserActivity
                .intent(
                    context,
                    url,
                    proxyPort,
                    useTor,
                    theme = theme,
                    isFavorite = isFavorite,
                    // Opaque per-account storage partition, so a web app can't carry one npub's session
                    // into another. Derived here (the sandbox never sees the pubkey).
                    webViewProfile = NappletWebViewProfiles.current(),
                    searchEngine = Amethyst.instance.uiPrefs.value.searchEngine.value,
                ).apply {
                    if (context !is Activity) addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        context.startActivity(intent)
    }

    private fun launchNostrApp(
        context: Context,
        coordinate: String,
        stillLoading: String,
    ) {
        val event = LocalCache.getAddressableNoteIfExists(coordinate)?.event
        when (event) {
            is RootNappletEvent ->
                NappletLauncher.launch(context, event, event.pubKey, "")
            is NamedNappletEvent ->
                NappletLauncher.launch(context, event, event.pubKey, event.identifier())
            is RootSiteEvent ->
                NappletLauncher.launchSite(context, event)
            is NamedSiteEvent ->
                NappletLauncher.launchSite(context, event)
            else -> {
                Log.w("FavoriteAppLauncher") { "Favorited app not resolvable yet: $coordinate" }
                Toast.makeText(context, stillLoading, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Builds the main-process-minted launch parameters for embedding the nsite/napplet at [coordinate]
     * as an in-app tab (see `NappletHostService`). Returns null when the event isn't resolvable in
     * [LocalCache] yet — the caller shows a loading state. nsite vs napplet (and website mode) is decided
     * here from the live event, exactly as in [launchNostrApp].
     */
    fun embedParams(
        context: Context,
        coordinate: String,
    ): Bundle? {
        val event = LocalCache.getAddressableNoteIfExists(coordinate)?.event
        return when (event) {
            is RootNappletEvent ->
                NappletLauncher.buildLaunchParams(
                    context = context,
                    manifest = event,
                    authorPubKey = event.pubKey,
                    identifier = "",
                )
            is NamedNappletEvent ->
                NappletLauncher.buildLaunchParams(
                    context = context,
                    manifest = event,
                    authorPubKey = event.pubKey,
                    identifier = event.identifier(),
                )
            is RootSiteEvent ->
                NappletLauncher.buildSiteLaunchParams(context, event)
            is NamedSiteEvent ->
                NappletLauncher.buildSiteLaunchParams(context, event)
            else -> null
        }
    }

    /** See [favoriteCoordinateOf]. */
    fun coordinateOf(event: Event): String = favoriteCoordinateOf(event)
}
