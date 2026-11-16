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
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.content.edit
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.quartz.utils.Log

/**
 * Partitions WebView storage (cookies, localStorage, IndexedDB, service workers) per Nostr account.
 *
 * Without this, every account on the device shares one cookie/storage jar, so a web app stays logged
 * in as account A after the user switches to account B — leaking A's session to B and letting a site
 * correlate a user's separate pseudonymous npubs.
 *
 * The name is an OPAQUE token minted by the trusted main process (a truncated SHA-256 of the account
 * pubkey — see `NappletWebViewProfiles` in `:amethyst`). This keyless `:napplet` process must never
 * learn which account it is running for, so it only ever sees the hash, validates its shape, and uses
 * it as a storage key. Same account always maps to the same name, which is what makes a switch away
 * and back restore the earlier session intact.
 */
object NappletWebViewProfile {
    private const val TAG = "NappletWebViewProfile"

    /** Exactly the shape the main process mints: a 32-char lowercase hex slice of a SHA-256. */
    private val VALID_NAME = Regex("[0-9a-f]{32}")

    /** Storage-key for the fallback path's "which account did this process last serve" marker. */
    private const val FALLBACK_PREFS = "napplet_webview_profile"
    private const val FALLBACK_KEY = "last_profile"

    /** Profile name we use when there is no account scope (logged out) or the extra is malformed. */
    private const val NO_ACCOUNT = "default"

    /**
     * Points [webView] at [profileName]'s own storage jar.
     *
     * MUST be called immediately after the WebView is constructed and BEFORE anything touches its
     * profile — any `loadUrl`/`loadDataWithBaseURL`, or the settings/bridge calls that follow it in
     * our creation paths. `WebViewCompat.setProfile` throws once the WebView has loaded content or
     * has been destroyed, so every caller places it as the first statement after the constructor.
     */
    fun apply(
        context: Context,
        webView: WebView,
        profileName: String?,
    ) {
        // Never hand an attacker-influenced string to getOrCreateProfile: anything that isn't the
        // exact minted shape is treated as "no account" instead.
        val name = profileName?.takeIf(VALID_NAME::matches)

        if (name != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                ProfileStore.getInstance().getOrCreateProfile(name)
                WebViewCompat.setProfile(webView, name)
            }.onFailure {
                Log.w(TAG, "Failed to apply the per-account WebView profile; clearing instead", it)
                clearIfAccountChanged(context, name)
            }
        } else {
            // FALLBACK (old WebView without MULTI_PROFILE, or no account scope). We must not silently
            // share the one jar across accounts, so isolation degrades to "lossy but safe": whenever
            // the account behind these WebViews changes, wipe the shared jar instead of partitioning
            // it. Sessions don't survive a switch, but they never cross accounts either.
            clearIfAccountChanged(context, name ?: NO_ACCOUNT)
        }
    }

    /**
     * Wipes the shared cookie/storage jar when [name] differs from the account this process last
     * served. Persisted (not just in-memory) because `:napplet` is killed and respawned constantly —
     * an in-memory marker would wipe the user's sessions on every cold start.
     */
    private fun clearIfAccountChanged(
        context: Context,
        name: String,
    ) {
        val prefs = context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(FALLBACK_KEY, null) == name) return
        prefs.edit { putString(FALLBACK_KEY, name) }

        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }.onFailure { Log.w(TAG, "Failed to clear WebView storage on account change", it) }
    }
}
