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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.napplet.NappletLaunchRegistry
import com.vitorpamplona.amethyst.napplet.WebAppNetworkRegistry
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.EmbeddedWebAppController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.EmbeddedNostrAppController

/**
 * The single place that builds a warm embedded-tab controller, so the favorite screens and the
 * [EmbeddedTabPreloader] produce byte-for-byte identical sessions keyed by the same id. Whoever calls
 * first (the preloader at startup, or the screen on first visit) wins; the other call returns the
 * already-warm controller via [EmbeddedTabHost.acquire].
 *
 * The ids ("url:<url>" / "nostr:<coordinate>") match [FavoriteApp.id], so warm-keep, bottom-bar
 * membership, and the screens all line up on the same session.
 */
@RequiresApi(Build.VERSION_CODES.R)
object EmbeddedTabFactory {
    fun webAppId(url: String) = "url:$url"

    fun nostrAppId(coordinate: String) = "nostr:$coordinate"

    /** Tor's SOCKS port right now, or -1 while it is off or still starting. */
    fun currentTorPort(): Int = Amethyst.instance.torManager.activePortOrNull.value ?: -1

    /** Acquires (or returns) the warm browser controller for [url], routing over Tor per the site's choice. */
    fun acquireWebApp(
        context: Context,
        url: String,
        backgroundColor: Int,
    ): EmbeddedWebAppController =
        EmbeddedTabHost.acquire(webAppId(url)) {
            // Tor ON, not "port known": the provider blocks a Tor page until the port is there (fails closed).
            val initialUseTor = Amethyst.instance.torPrefs.torType.value != TorType.OFF && WebAppNetworkRegistry.useTor(url)
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
            EmbeddedWebAppController(
                context.applicationContext,
                ::currentTorPort,
                initialUseTor,
                backgroundColor,
                theme,
                searchEngine = { Amethyst.instance.uiPrefs.value.searchEngine.value },
            ).also {
                EmbeddedTabHost.takePageSnapshot(webAppId(url))?.let(it::restore)
                it.bind(url)
            }
        } as EmbeddedWebAppController

    /**
     * Acquires (or returns) the warm nsite/napplet controller for [coordinate] using already-resolved
     * launch [params] (the caller has the metadata anyway). [backgroundColor] is stamped into the params
     * so the sandbox WebView doesn't flash white before paint.
     */
    fun acquireNostrApp(
        context: Context,
        coordinate: String,
        params: Bundle,
        backgroundColor: Int,
    ): EmbeddedNostrAppController {
        params.putInt(NappletHostContract.EXTRA_BG_COLOR, backgroundColor)
        // The screen mints fresh params on every visit, but a warm tab keeps the session (and token) it was
        // built with — give the unused fresh token back instead of leaving it registered.
        if (EmbeddedTabHost.isWarm(nostrAppId(coordinate))) {
            NappletLaunchRegistry.unregister(params.getString(NappletHostContract.EXTRA_LAUNCH_TOKEN))
        }
        return EmbeddedTabHost.acquire(nostrAppId(coordinate)) {
            EmbeddedNostrAppController(context.applicationContext, params).also { it.bind() }
        } as EmbeddedNostrAppController
    }

    /**
     * Preload entry for a single favorite: builds its warm session if not already warm. Returns true once
     * the favorite is warm (or already was), false if it can't be warmed yet (a napplet whose event
     * hasn't loaded), so the preloader can retry.
     */
    fun preload(
        context: Context,
        app: FavoriteApp,
        backgroundColor: Int,
    ): Boolean =
        when (app) {
            is FavoriteApp.WebApp -> {
                // Privacy: never preload a Tor-routed site over clearnet just because Tor hasn't finished
                // connecting yet. A site only "wants Tor" when Tor is enabled AND not opted out for it; in
                // that case wait until the proxy port is actually up (the preloader retries). When Tor is
                // off, or the user opted this site out, clearnet IS the real route — preload immediately.
                val torEnabled = Amethyst.instance.torPrefs.torType.value != TorType.OFF
                val wantsTor = torEnabled && WebAppNetworkRegistry.useTor(app.url)
                val torReady = Amethyst.instance.torManager.activePortOrNull.value != null
                if (wantsTor && !torReady) {
                    false
                } else {
                    acquireWebApp(context, app.url, backgroundColor)
                    true
                }
            }
            is FavoriteApp.NostrApp -> {
                if (EmbeddedTabHost.isWarm(nostrAppId(app.coordinate))) {
                    true
                } else {
                    val params = FavoriteAppLauncher.embedParams(context, app.coordinate)
                    if (params == null) {
                        false
                    } else {
                        acquireNostrApp(context, app.coordinate, params, backgroundColor)
                        true
                    }
                }
            }
        }
}
