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

import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims.Route
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.Executor

/**
 * The single owner of the `:napplet` process's WebView proxy override. Every surface — embedded browser
 * tab, embedded nSite, full-screen browser or host — files a claim here instead of setting the override
 * itself; [NappletProxyClaims] derives the one route they all share (Tor always wins, see there).
 *
 * It fails CLOSED. A surface's page load waits for [claim]'s `onReady`, which only runs once the route is
 * actually in effect: if applying it fails, or this WebView can't take a proxy override at all while Tor is
 * wanted, the waiting loads get `onFailed` instead of going out directly, and the next claim tries again.
 * (A surface that wants Tor but has no Tor port yet must not claim at all — it blocks its own load.)
 *
 * Main thread only; the WebKit callback is delivered back to the main thread too.
 */
object WebViewProxyPolicy {
    private const val TAG = "WebViewProxyPolicy"

    /** Why a surface's load can't go ahead. */
    enum class Failure {
        /** This device's WebView can't route through a proxy, so Tor can't be honored. */
        TOR_UNSUPPORTED,

        /** Setting the proxy override failed. */
        APPLY_FAILED,
    }

    private class Waiter(
        val wantsTor: Boolean,
        val onReady: () -> Unit,
        val onFailed: (Failure) -> Unit,
    )

    private val claims = NappletProxyClaims()

    // What the WebView currently runs with (a fresh process has no override), and what is being applied.
    private var applied: Route = NappletProxyClaims.DIRECT
    private var applying: Route? = null
    private val waiting = mutableListOf<Waiter>()

    // Surfaces told which route is really in effect (an open-web page can be on Tor because another needs it).
    private val routeListeners = LinkedHashMap<Any, (Boolean) -> Unit>()

    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { if (Looper.myLooper() == Looper.getMainLooper()) it.run() else main.post(it) }

    private val supported by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) }

    /**
     * Files [owner]'s route: through Tor on [torPort] (> 0), or the open web ([NappletProxyClaims.NO_PROXY]).
     * [onReady] runs on the main thread once the resulting process route is in effect — immediately when
     * nothing had to change; [onFailed] instead when it can't be.
     */
    fun claim(
        owner: Any,
        torPort: Int,
        onFailed: (Failure) -> Unit = {},
        onReady: () -> Unit = {},
    ) {
        claims.claim(owner, torPort)
        sync(Waiter(torPort > 0, onReady, onFailed))
    }

    /** Withdraws [owner]'s claim and route listener; the route relaxes once no remaining surface needs it. */
    fun release(owner: Any) {
        claims.release(owner)
        routeListeners.remove(owner)
        sync(null)
    }

    /** Whether another live surface needs Tor, which keeps the shared route on Tor whatever [owner] picks. */
    fun torWantedByOthers(owner: Any): Boolean = claims.torWantedByOthers(owner)

    /** Tells [listener] (now, and on every change) whether the process currently routes through Tor. */
    fun observeRoute(
        owner: Any,
        listener: (usesTor: Boolean) -> Unit,
    ) {
        routeListeners[owner] = listener
        listener(applied.usesTor)
    }

    private fun sync(waiter: Waiter?) {
        val target = claims.route()
        if (!supported) {
            // Nothing can be proxied. The open web still works; a surface that wants Tor fails closed.
            if (waiter?.wantsTor == true) waiter.onFailed(Failure.TOR_UNSUPPORTED) else waiter?.onReady?.invoke()
            return
        }
        if (target == applied && applying == null) {
            waiter?.onReady?.invoke()
            return
        }
        waiter?.let { waiting += it }
        if (target == applying) return
        applying = target
        apply(target) { ok ->
            // A newer route superseded this one while it applied: its own callback settles the waiters.
            if (applying != target) {
                if (ok) applied = target
                return@apply
            }
            applying = null
            val settled = waiting.toList()
            waiting.clear()
            if (ok) {
                applied = target
                routeListeners.values.toList().forEach { it(target.usesTor) }
                settled.forEach { it.onReady() }
            } else {
                // Keep `applied` as it was, so the next claim tries again; nothing waiting goes out unrouted.
                settled.forEach { it.onFailed(Failure.APPLY_FAILED) }
            }
        }
    }

    private fun apply(
        route: Route,
        done: (ok: Boolean) -> Unit,
    ) {
        runCatching {
            if (route.usesTor) {
                val config = ProxyConfig.Builder().addProxyRule("socks5://127.0.0.1:${route.torPort}").build()
                ProxyController.getInstance().setProxyOverride(config, mainExecutor) { done(true) }
            } else {
                ProxyController.getInstance().clearProxyOverride(mainExecutor) { done(true) }
            }
        }.onFailure {
            Log.w(TAG, "Failed to apply WebView proxy override", it)
            done(false)
        }
    }
}
