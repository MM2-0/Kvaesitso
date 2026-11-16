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
package com.vitorpamplona.amethyst.napplethost

import android.os.Bundle
import com.vitorpamplona.amethyst.commons.napplet.WebxdcLaunch

/**
 * Intent-extra contract for launching [NappletHostActivity]. Lives in the sandbox module so the
 * activity (here) and the launcher (in `:amethyst`) share the keys without a dependency cycle —
 * `:amethyst` depends on `:nappletHost`, never the other way around.
 */
object NappletHostContract {
    /**
     * How long a napplet or website page keeps running after the app leaves the screen before its WebView is
     * paused — embedded tabs and the full-screen hosts alike. The same grace the rest of the app gets: relays
     * disconnect 30 s after the UI stops (RelayProxyClientConnector's `WhileSubscribed(30000)`), so a quick
     * trip to another app (a 2FA code, a password manager) doesn't interrupt a page, while one left behind
     * stops running. Requests that act for the user are held from the first moment regardless.
     *
     * An upper bound, not a promise: on Android 14+ the cached-app freezer suspends `:napplet` (and its
     * renderers) about 10 s after the app leaves the screen, since nothing keeps that process in the
     * foreground. Measured on an API 36 emulator: frozen 12 s after Home, media stopped with it. So on those
     * devices a page stops within seconds, and this pause lands (on the next unfreeze) on a page that
     * already stopped.
     */
    const val BACKGROUND_PAUSE_MS = 30_000L

    const val EXTRA_PATHS = "napplet_paths"
    const val EXTRA_HASHES = "napplet_hashes"
    const val EXTRA_SERVERS = "napplet_servers"
    const val EXTRA_AUTHOR = "napplet_author"
    const val EXTRA_IDENTIFIER = "napplet_identifier"
    const val EXTRA_AGGREGATE_HASH = "napplet_aggregate_hash"
    const val EXTRA_TITLE = "napplet_title"

    /** Bare NAP capability domains the manifest declared (empty for a plain nSite). */
    const val EXTRA_REQUIRES = "napplet_requires"

    /**
     * Pre-localized capability labels for the "what it can access" sheet, resolved by the launcher
     * (which has the app's resources) so the sandbox module needs no capability string resources.
     */
    const val EXTRA_CAP_LABELS = "napplet_cap_labels"

    /**
     * Unguessable token for this launch. The sandbox relays it to the broker, which resolves it back
     * to the trusted identity + declared capabilities — the sandbox never carries (and so can never
     * forge) its own coordinate.
     */
    const val EXTRA_LAUNCH_TOKEN = "napplet_launch_token"

    /** SOCKS proxy port to route blob fetches through, or -1 for a direct connection. */
    const val EXTRA_PROXY_PORT = "napplet_proxy_port"

    /**
     * ARGB of Amethyst's theme background, passed from the main process so the keyless `:napplet` WebView
     * (and the surface before its first frame) paints the app's background instead of the default white
     * while the shell/app is still loading.
     */
    const val EXTRA_BG_COLOR = "napplet_bg_color"

    /**
     * The host posture this launch renders under — a [HostProfile] name. WEBSITE treats the content as
     * a normal web app (NIP-07 `window.nostr` provider, normal network, no app CSP); NAPPLET is the
     * locked sandbox. Resolved in the trusted main process; the sandbox derives all coupled policy from it.
     */
    const val EXTRA_HOST_PROFILE = "napplet_host_profile"

    /**
     * Whether this site's traffic routes through Tor (true, the default when Tor is active) or over the
     * open web (false). The main process resolves the per-site preference; the sandbox applies it to
     * both the WebView proxy and the blob-fetch client. Always true for locked napplets.
     */
    const val EXTRA_USE_TOR = "napplet_use_tor"

    /**
     * The user's theme preference name ("DARK", "LIGHT", or "SYSTEM"), passed from the main process so
     * the keyless `:napplet` process — which never runs the full app init — can apply the same night mode
     * and let WebView content respond correctly to `prefers-color-scheme`.
     */
    const val EXTRA_THEME = "napplet_theme"

    /**
     * Opaque per-account WebView storage-profile name (a truncated SHA-256 of the account pubkey,
     * minted in the main process — see `NappletWebViewProfiles`). Partitions cookies/localStorage/
     * IndexedDB/service workers per account so a web app can't carry one npub's session into another,
     * while a switch away and back restores the earlier session. Deliberately a hash: the keyless
     * sandbox must learn nothing about which account it runs for.
     *
     * NB: unrelated to [EXTRA_HOST_PROFILE], which is the WEBSITE/NAPPLET host posture.
     */
    const val EXTRA_WEBVIEW_PROFILE = "napplet_webview_profile"

    /**
     * A webxdc launch ([HostProfile.WEBXDC]): its archive URL and sha256 (absent when the post gives
     * none), and the user as its `webxdc.js` shows them. Absent for a napplet or site.
     */
    const val EXTRA_WEBXDC_URL = "napplet_webxdc_url"
    const val EXTRA_WEBXDC_HASH = "napplet_webxdc_hash"
    const val EXTRA_WEBXDC_SELF_ADDR = "napplet_webxdc_self_addr"
    const val EXTRA_WEBXDC_SELF_NAME = "napplet_webxdc_self_name"
    const val EXTRA_WEBXDC_IS_SENDER = "napplet_webxdc_is_sender"

    /** Writes [webxdc] into launch [params]. */
    fun putWebxdc(
        params: Bundle,
        webxdc: WebxdcLaunch,
    ) {
        params.putString(EXTRA_WEBXDC_URL, webxdc.url)
        params.putString(EXTRA_WEBXDC_HASH, webxdc.hash)
        params.putString(EXTRA_WEBXDC_SELF_ADDR, webxdc.selfAddr)
        params.putString(EXTRA_WEBXDC_SELF_NAME, webxdc.selfName)
        params.putBoolean(EXTRA_WEBXDC_IS_SENDER, webxdc.isAppSender)
    }

    /**
     * A front page launch ([HostProfile.PAPER]): the one checked document it serves, as bytes. The
     * trusted main process wrote and checked it; this process only serves it.
     */
    const val EXTRA_PAPER_HTML = "napplet_paper_html"

    /** The front page in [params], or null when the launch isn't one. */
    fun paperOf(params: Bundle?): ByteArray? = params?.getByteArray(EXTRA_PAPER_HTML)?.takeIf { it.isNotEmpty() }

    /** The webxdc launch in [params], or null when it isn't one. */
    fun webxdcOf(params: Bundle?): WebxdcLaunch? {
        val url = params?.getString(EXTRA_WEBXDC_URL)?.ifBlank { null } ?: return null
        return WebxdcLaunch(
            url = url,
            hash = params.getString(EXTRA_WEBXDC_HASH)?.ifBlank { null },
            selfAddr = params.getString(EXTRA_WEBXDC_SELF_ADDR).orEmpty(),
            selfName = params.getString(EXTRA_WEBXDC_SELF_NAME).orEmpty(),
            isAppSender = params.getBoolean(EXTRA_WEBXDC_IS_SENDER, false),
        )
    }

    /**
     * FQN of the main-process broker service (in `:amethyst`). The sandbox binds it by name so it
     * needs no compile-time reference to `:amethyst`. Must match the manifest `<service>` declaration.
     */
    const val BROKER_SERVICE_CLASS = "com.vitorpamplona.amethyst.napplet.NappletBrokerService"
}
