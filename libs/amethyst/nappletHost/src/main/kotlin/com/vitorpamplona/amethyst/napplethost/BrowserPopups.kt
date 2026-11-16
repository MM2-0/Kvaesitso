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

import android.content.Context
import android.content.MutableContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import java.util.UUID

/**
 * New windows a page opens (`target="_blank"` links and user-initiated `window.open()`), handed from the
 * opener's WebView to the full-screen browser window that will show them — Chrome opens these as a new
 * tab, we open a new [NappletBrowserActivity] task.
 *
 * WebView requires the popup's WebView to exist *inside* `onCreateWindow`, before the Activity that will
 * show it has started, and that WebView is what keeps `window.opener` / `postMessage` wired for OAuth
 * popups. So the opener builds it here, on a [MutableContextWrapper] (re-pointed at the adopting Activity
 * later), with the same storage profile, settings and scripts as any browser WebView, and parks it under a
 * one-shot token passed in the launch intent. Bridge messages that arrive before the Activity adopts it are
 * queued, then replayed. Unclaimed popups are destroyed after [CLAIM_TIMEOUT_MS].
 *
 * Opener and popup always share the `:napplet` process (both browser surfaces live there), which is what
 * makes handing a live WebView across possible.
 */
object BrowserPopups {
    private const val CLAIM_TIMEOUT_MS = 30_000L

    fun interface BridgeTarget {
        fun onMessage(
            view: WebView,
            message: WebMessageCompat,
            sourceOrigin: Uri,
            isMainFrame: Boolean,
            replyProxy: JavaScriptReplyProxy,
        )
    }

    class Pending internal constructor(
        val webView: WebView,
        val context: MutableContextWrapper,
        val proxyPort: Int,
        val useTor: Boolean,
        val themeType: String,
        val webViewProfile: String?,
        val searchEngine: String?,
    ) {
        private var target: BridgeTarget? = null
        private val queued = mutableListOf<() -> Unit>()

        internal fun dispatch(
            view: WebView,
            message: WebMessageCompat,
            sourceOrigin: Uri,
            isMainFrame: Boolean,
            replyProxy: JavaScriptReplyProxy,
        ) {
            val t = target
            if (t != null) {
                t.onMessage(view, message, sourceOrigin, isMainFrame, replyProxy)
            } else {
                queued += { target?.onMessage(view, message, sourceOrigin, isMainFrame, replyProxy) }
            }
        }

        /** Called by the adopting Activity: from now on bridge messages go to [bridge]; queued ones replay. */
        fun adopt(bridge: BridgeTarget) {
            target = bridge
            queued.toList().forEach { it() }
            queued.clear()
        }
    }

    private val pending = mutableMapOf<String, Pending>()
    private val main = Handler(Looper.getMainLooper())

    /**
     * Builds the popup WebView for `onCreateWindow` and parks it. Returns the token for the launch intent and
     * the WebView to put on the `WebViewTransport`.
     */
    fun create(
        context: Context,
        shimJs: String,
        proxyPort: Int,
        useTor: Boolean,
        themeType: String,
        webViewProfile: String?,
        searchEngine: String?,
    ): Pair<String, WebView> {
        val app = context.applicationContext
        val wrapper = MutableContextWrapper(nightThemedContext(app, themeType))
        val webView = WebView(wrapper)
        // Same partition as the opener: WebView only links a popup to its opener within one profile, and
        // the popup must see the same logged-in session anyway.
        NappletWebViewProfile.apply(app, webView, webViewProfile)
        BrowserWebTools.applyBrowserSettings(webView)
        // A popup opened from a page on Tor is on Tor too: no WebRTC around the proxy.
        NappletWebRtcGuard.sync(webView, useTor)
        val entry = Pending(webView, wrapper, proxyPort, useTor, themeType, webViewProfile, searchEngine)
        WebViewCompat.addWebMessageListener(webView, NappletWebContract.BRIDGE_NAME, setOf("*")) { view, message, origin, isMainFrame, reply ->
            entry.dispatch(view, message, origin, isMainFrame, reply)
        }
        WebViewCompat.addDocumentStartJavaScript(webView, BrowserWebTools.browserStartScript(shimJs, imeProxy = false), setOf("*"))
        val token = UUID.randomUUID().toString()
        // Until an Activity adopts it (and installs its own client), a parked popup still shares the one
        // `:napplet` renderer. With no client its renderer death falls to the default — returning false, which
        // kills the whole process and every embedded tab in it. Drop just the popup instead.
        webView.webViewClient =
            object : WebViewClient() {
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    pending.remove(token)
                    view.destroy()
                    return true
                }
            }
        pending[token] = entry
        main.postDelayed({
            pending.remove(token)?.let { orphan ->
                orphan.webView.stopLoading()
                orphan.webView.destroy()
            }
        }, CLAIM_TIMEOUT_MS)
        return token to webView
    }

    /** Hands the parked popup to the Activity that was launched for [token] (once). */
    fun take(token: String?): Pending? = token?.let { pending.remove(it) }
}
