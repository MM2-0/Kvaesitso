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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.tor.TorServiceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The target of every web-app launcher shortcut ([WebShortcuts]). Runs in the **main** process, so the app
 * (account, broker, Tor) is up before the page opens, then hands off to the full-screen browser through
 * [FavoriteAppLauncher.launchUrl] — the same path the Browser tab uses — and finishes without drawing.
 *
 * On a cold start from the launcher Tor is usually still bootstrapping. Opening straight away would load a
 * Tor-routed site over the open web, so when Tor is configured this waits for it; if it doesn't come up in
 * time, the user lands in Amethyst (where Tor's state is visible) rather than on the open web.
 */
class WebShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        if (url == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val app = Amethyst.instance
            val torWanted = app.torPrefs.torType.value != TorType.OFF && app.torManager.activePortOrNull.value == null
            val torReady =
                !torWanted ||
                    withTimeoutOrNull(TOR_WAIT_MS) { app.torManager.status.first { it is TorServiceStatus.Active } } != null
            if (torReady) {
                FavoriteAppLauncher.launchUrl(this@WebShortcutActivity, url)
            } else {
                startActivity(Intent(this@WebShortcutActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            finish()
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val TOR_WAIT_MS = 30_000L

        fun intent(
            context: Context,
            url: String,
        ): Intent =
            Intent(context, WebShortcutActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_URL, url)
    }
}
