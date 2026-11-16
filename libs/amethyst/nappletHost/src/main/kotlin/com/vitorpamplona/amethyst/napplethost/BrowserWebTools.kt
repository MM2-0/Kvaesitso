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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.ui.pill.CertificateInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_all_cleared
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URISyntaxException
import java.text.DateFormat
import java.util.Collections
import java.util.WeakHashMap
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * WebView-side behaviours shared by the full-screen browser ([NappletBrowserActivity]) and the embedded
 * one ([NappletBrowserService]), so the two surfaces act the same: non-web schemes, desktop mode, text
 * size, the out-of-scope "back to app" walk, site-data clearing, page info, copy and share.
 *
 * Everything here runs in the keyless `:napplet` process and touches only the WebView it is given.
 */
object BrowserWebTools {
    private const val TAG = "BrowserWebTools"

    // ---- setup ----

    /**
     * The settings every browser WebView gets (full-screen, embedded, and popups), so a page behaves the
     * same wherever it opens. New windows are enabled — `onCreateWindow` turns them into new browser
     * windows — but `window.open()` still needs a user gesture (Blink's popup blocker, as in Chrome).
     * Geolocation is enabled at the WebView level; every request still goes through the per-site prompt.
     */
    @Suppress("SetJavaScriptEnabled")
    fun applyBrowserSettings(webView: WebView) {
        liveWebViews.add(webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                safeBrowsingEnabled = true
            }
        }
        WebView.setWebContentsDebuggingEnabled(false)
    }

    /**
     * The document-start script for a browser WebView: the direct-bridge flags, the NIP-07 [shimJs], and
     * [BrowserExtrasScript]. [imeProxy] is set only for the embedded surface, which has no native keyboard.
     *
     * The script is registered for every frame, but only the top frame has the bridge, so the shim runs
     * there alone. In a subframe it has nothing to talk to, and its shell transport would post a
     * `napplet.hello` to whatever third-party page embeds the frame.
     */
    fun browserStartScript(
        shimJs: String,
        imeProxy: Boolean,
    ): String {
        val flags = if (imeProxy) " window.__nappletImeProxy = true;" else ""
        return "if (window.top === window) { window.__nappletDirectBridge = true; window.__nappletNip07 = true;$flags\n$shimJs\n}\n${BrowserExtrasScript.JS}"
    }

    // ---- non-web schemes ----

    /**
     * Handles a navigation to a non-http(s) [uri] the way Chrome does: `intent:` URIs are parsed (component
     * and selector stripped so a page can't aim at a private activity) and fall back to their
     * `browser_fallback_url` in-page when no app takes them; other schemes (`mailto:`, `tel:`, `geo:`,
     * `nostr:`, …) go to the system. Only acts on a user gesture, like Chrome, so a page can't bounce the
     * user into another app on load. Always consumes the navigation.
     */
    fun openExternal(
        context: Context,
        uri: Uri,
        hasGesture: Boolean,
        loadInPage: (String) -> Unit,
    ): Boolean {
        if (!hasGesture) return true
        val intent =
            if (uri.scheme.equals("intent", ignoreCase = true)) {
                parseIntentUri(uri.toString()) ?: return true
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                loadInPage(fallback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open ${uri.scheme} link", e)
        }
        return true
    }

    /** Parses an `intent:` URI with the same hardening Chrome applies. Null when it's malformed. */
    fun parseIntentUri(uri: String): Intent? =
        try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                // A web page may only ask for something any app could handle: never an explicit
                // component, never a selector (which could smuggle one in).
                component = null
                selector = null
                // No grants of our own content to whatever answers.
                flags = flags and
                    (
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    ).inv()
            }
        } catch (_: URISyntaxException) {
            null
        }

    // ---- desktop site / text size ----

    private val mobileUserAgents = WeakHashMap<WebView, String>()

    fun isDesktopMode(webView: WebView): Boolean = mobileUserAgents.containsKey(webView)

    /** Switches [webView] between its own mobile UA and [BrowserChrome.desktopUserAgent], then reloads. */
    fun setDesktopMode(
        webView: WebView,
        desktop: Boolean,
    ) {
        if (desktop == isDesktopMode(webView)) return
        val settings = webView.settings
        if (desktop) {
            val mobile = settings.userAgentString
            mobileUserAgents[webView] = mobile
            settings.userAgentString = BrowserChrome.desktopUserAgent(mobile)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = mobileUserAgents.remove(webView)
        }
        webView.reload()
    }

    fun setTextZoom(
        webView: WebView,
        percent: Int,
    ) {
        webView.settings.textZoom = percent.coerceIn(BrowserChrome.TEXT_ZOOM_STEPS.first(), BrowserChrome.TEXT_ZOOM_STEPS.last())
    }

    // ---- scope ----

    /**
     * Chrome's out-of-scope bar ✕: step back to the most recent history entry on [startUrl]'s origin, or
     * reload [startUrl] when there is none.
     */
    fun backToScope(
        webView: WebView,
        startUrl: String,
    ) {
        val home = BrowserChrome.originOf(startUrl) ?: return
        val history = webView.copyBackForwardList()
        for (i in history.currentIndex - 1 downTo 0) {
            if (BrowserChrome.originOf(history.getItemAtIndex(i).url).equals(home, ignoreCase = true)) {
                webView.goBackOrForward(i - history.currentIndex)
                return
            }
        }
        webView.loadUrl(startUrl)
    }

    // ---- storage ----

    /** The cookie jar of [webView]'s own storage profile (the per-account one), or the default jar. */
    fun cookieManager(webView: WebView): CookieManager =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.getProfile(webView).cookieManager }.getOrNull() ?: CookieManager.getInstance()
        } else {
            CookieManager.getInstance()
        }

    private fun webStorage(webView: WebView): WebStorage =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.getProfile(webView).webStorage }.getOrNull() ?: WebStorage.getInstance()
        } else {
            WebStorage.getInstance()
        }

    /**
     * Clears what the site behind [url] stored in [webView]'s profile — its origin's web storage (local
     * storage, IndexedDB, cache storage, service workers) and its cookies — then reloads it logged out.
     * Other sites and other accounts are untouched.
     */
    fun clearSiteData(
        context: Context,
        webView: WebView,
        url: String,
    ) {
        val origin = BrowserChrome.originOf(url) ?: return
        runCatching { webStorage(webView).deleteOrigin(origin) }
        val cookies = cookieManager(webView)
        val names =
            cookies
                .getCookie(url)
                .orEmpty()
                .split(';')
                .mapNotNull { it.substringBefore('=').trim().takeIf(String::isNotEmpty) }
        val host = BrowserChrome.displayHost(url)
        // A cookie can be scoped to the host or to any parent domain; expire it on each so it really goes.
        val domains = host.split('.').let { parts -> (0 until (parts.size - 1).coerceAtLeast(1)).map { parts.drop(it).joinToString(".") } }
        // `__Secure-` / `__Host-` cookies (where Google keeps its sign-in) are only accepted with `Secure`, so
        // an expiry without it is silently dropped. Secure isn't part of a cookie's identity, so adding it
        // still expires the plain ones; it can only be set from https.
        val secure = if (url.startsWith("https://", ignoreCase = true)) "; Secure" else ""
        names.forEach { name ->
            cookies.setCookie(url, "$name=; Max-Age=0; Path=/$secure")
            domains.forEach { domain -> cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$domain$secure") }
        }
        cookies.flush()
        Toast.makeText(context, CommonsR.string.browser_site_data_cleared, Toast.LENGTH_SHORT).show()
        // Every open tab or window on this site reloads, not just this one: one left running would stay
        // signed in from memory, and could write its session back.
        reloadSameProfile(webView) { BrowserChrome.originOf(it.url.orEmpty()).equals(origin, ignoreCase = true) }
    }

    /**
     * Clears every site's cookies and web storage (local storage, IndexedDB, cache storage, service workers)
     * in [webView]'s profile — the account's whole browser jar — plus the HTTP cache, then reloads the page
     * logged out. Unlike [clearSiteData] this also reaches cookies a site keeps on another domain (YouTube's
     * sign-in lives on google.com). Other accounts' profiles are untouched.
     *
     * Napplets and nSites share this profile (on `napplet.local` origins), so their saved state goes too;
     * the confirmation says so. They can't be spared reliably: [WebStorage.getOrigins] only lists
     * quota-managed storage, so deleting origin by origin could miss a site that keeps its login in
     * localStorage alone.
     */
    fun clearAllSiteData(
        context: Context,
        webView: WebView,
    ) {
        runCatching { webStorage(webView).deleteAllData() }.onFailure { Log.w(TAG, "Failed to clear web storage", it) }
        webView.clearCache(true)
        val cookies = cookieManager(webView)
        cookies.removeAllCookies {
            cookies.flush()
            // Every open tab and window of this account reloads signed out, not just the one asking.
            reloadSameProfile(webView) { true }
        }
        val appContext = context.applicationContext
        // The embedded provider is a service that never composes, so resources may not be wired up yet.
        SandboxComposeResources.ensure(appContext)
        uiScope.launch {
            runCatchingCancellable { loadStringRes(Res.string.browser_pill_info_all_cleared) }
                .onSuccess { Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show() }
                .onFailure { Log.w(TAG, "Could not load the cleared-data toast", it) }
        }
    }

    /**
     * Every browser WebView this process created ([applyBrowserSettings] registers it), held weakly so a
     * closed tab or window just drops out. Main thread only, like the WebViews themselves.
     */
    private val liveWebViews: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap())

    /**
     * Reloads [source] and every other live browser WebView that shares its storage profile (the same
     * account) and matches [filter]. A WebView already destroyed ignores the call.
     */
    private fun reloadSameProfile(
        source: WebView,
        filter: (WebView) -> Boolean,
    ) {
        val profile = profileName(source)
        source.reload()
        liveWebViews.toList().forEach { other ->
            if (other !== source && profileName(other) == profile && filter(other)) other.reload()
        }
    }

    /** [webView]'s storage profile name, or null on a WebView without profiles (one shared jar). */
    private fun profileName(webView: WebView): String? =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.getProfile(webView).name }.getOrNull()
        } else {
            null
        }

    /** Process-long scope for the toasts that need a Compose resource string loaded first. */
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ---- page info ----

    /** The certificate of the page in [webView], for page info; null for a page without one. */
    fun certificateInfo(webView: WebView): CertificateInfo? =
        webView.certificate?.let { cert ->
            CertificateInfo(
                issuedTo = cert.issuedTo?.cName?.takeIf { it.isNotBlank() } ?: cert.issuedTo?.oName.orEmpty(),
                issuedBy = cert.issuedBy?.oName?.takeIf { it.isNotBlank() } ?: cert.issuedBy?.cName.orEmpty(),
                validUntil = cert.validNotAfterDate?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(it) }.orEmpty(),
            )
        }

    // ---- copy / share / other browser ----

    /** Copies [text] with no confirmation of our own (Android 13+ shows one). */
    fun copyText(
        context: Context,
        label: String,
        text: String,
    ) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * Whether "Paste and go" can be offered. Checks the clip's type only, never its contents, so Android
     * doesn't toast a clipboard read every time the pill opens.
     */
    fun clipboardHasText(context: Context): Boolean {
        val description = context.getSystemService(ClipboardManager::class.java)?.primaryClipDescription ?: return false
        return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
    }

    /** The clipboard's text, trimmed; read only when the user asks to paste. */
    fun clipboardText(context: Context): String? =
        context
            .getSystemService(ClipboardManager::class.java)
            ?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun copyToClipboard(
        context: Context,
        text: String,
    ) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
        // Android 13+ shows its own clipboard confirmation; a toast on top of it would be noise.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, CommonsR.string.browser_link_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private var lastShareAt = 0L

    /**
     * Opens the Android share sheet for a page or a `navigator.share()` call. Throttled: a page can post
     * share requests straight to the bridge (bypassing the polyfill's user-activation check), so at most
     * one sheet per [SHARE_COOLDOWN_MS] is honoured.
     */
    fun share(
        context: Context,
        title: String?,
        text: String?,
        url: String?,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastShareAt < SHARE_COOLDOWN_MS) return
        lastShareAt = now
        val body = listOfNotNull(text?.takeIf { it.isNotBlank() }, url?.takeIf { it.isNotBlank() }).joinToString("\n")
        if (body.isEmpty()) return
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
                title?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            }
        val chooser = Intent.createChooser(send, context.getString(CommonsR.string.browser_share_chooser))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }.onFailure { Log.w(TAG, "Share failed", it) }
    }

    /**
     * Hands [url] to a browser other than Amethyst — the escape hatch for sites that refuse embedded
     * browsers (Google sign-in, some banks). Our own activities are excluded from the chooser.
     */
    fun openInOtherBrowser(
        context: Context,
        url: String,
    ) {
        val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val chooser =
            Intent.createChooser(view, null).apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownBrowsableComponents(context, view))
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, CommonsR.string.browser_no_other_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun ownBrowsableComponents(
        context: Context,
        intent: Intent,
    ): Array<ComponentName> =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager
                .queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName == context.packageName }
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .toTypedArray()
        }.getOrDefault(emptyArray())

    private const val SHARE_COOLDOWN_MS = 1_000L
}
