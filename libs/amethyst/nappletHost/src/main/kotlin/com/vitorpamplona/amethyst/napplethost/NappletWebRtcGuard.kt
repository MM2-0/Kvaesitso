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

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.quartz.utils.Log
import java.util.WeakHashMap

/**
 * Takes WebRTC away from every frame of a WebView that must not have it. The WebView's proxy covers
 * HTTP, not WebRTC: its UDP goes straight out, so a napplet or webxdc app would have a network, and a site the user
 * put on Tor would show its real address. A site on the open web keeps WebRTC; its traffic is its own.
 *
 * The napplet's own frame loses it in the prelude too ([NappletWebContract.preludePrefix]); this also
 * reaches the frames a page makes, which no prelude does.
 */
object NappletWebRtcGuard {
    private const val TAG = "NappletWebRtcGuard"

    // The guard script of each browser WebView that has one, so it can be taken off again when the
    // user moves the site back to the open web. Touched on the main thread only.
    private val installed = WeakHashMap<WebView, ScriptHandler>()

    fun isNeeded(
        profile: HostProfile,
        useTor: Boolean,
    ): Boolean = profile.runsScripts && (profile != HostProfile.WEBSITE || useTor)

    /** Whether this WebView can run a script in every frame, which the guard needs. */
    fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /**
     * Takes WebRTC away from every frame [webView] loads from now on. Returns false when the WebView
     * can't (no [WebViewFeature.DOCUMENT_START_SCRIPT]): a host that must not have a network then
     * refuses to load rather than run with it.
     */
    fun install(webView: WebView): Boolean {
        if (!isSupported()) {
            Log.w(TAG, "This WebView can't run a script in every frame; WebRTC can't be taken away")
            return false
        }
        if (webView !in installed) installed[webView] = WebViewCompat.addDocumentStartJavaScript(webView, NappletWebContract.WEBRTC_GUARD_JS, setOf("*"))
        return true
    }

    /**
     * Takes cookies away from every frame [webView] loads ([NappletWebContract.COOKIE_GUARD_JS]): its
     * pages all live under napplet.local, so a cookie one app set there would reach every other. The
     * served pages carry it in their prelude; this reaches the frames a page makes, which no prelude does.
     */
    fun installCookieGuard(webView: WebView) {
        if (isSupported()) WebViewCompat.addDocumentStartJavaScript(webView, NappletWebContract.COOKIE_GUARD_JS, setOf("*"))
    }

    /**
     * The in-app browser's guard follows the page's route: a site the user put on Tor loses WebRTC,
     * whose UDP goes around the WebView's proxy and would show the site the user's real address while
     * the chrome says "over Tor"; a site on the open web keeps it. Takes effect from the next load, so
     * call it before the reload that a route change does anyway. Returns false when the guard was
     * needed and couldn't be installed.
     */
    fun sync(
        webView: WebView,
        useTor: Boolean,
    ): Boolean =
        if (useTor) {
            install(webView)
        } else {
            installed.remove(webView)?.remove()
            true
        }
}
