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
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Launcher shortcuts for web apps — the PWA "Add to Home screen" (pinned) and the long-press list on
 * Amethyst's own icon (dynamic, from the favorites). Every shortcut opens the page **full screen in
 * Amethyst's own browser** through [WebShortcutActivity], never the system browser, so the user's NIP-07
 * signer is there.
 *
 * Main process only: the icon comes from [BrowserIconRegistry]'s captured favicon when it is a raster
 * image, else Amethyst's launcher icon.
 */
object WebShortcuts {
    private const val TAG = "WebShortcuts"
    private const val MAX_DYNAMIC = 4
    private const val DYNAMIC_PREFIX = "fav:"
    private const val ICON_PX = 192

    /** Asks the launcher to pin a shortcut to [url] labelled [title]. */
    fun requestPin(
        context: Context,
        url: String,
        title: String,
    ) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, CommonsR.string.browser_home_shortcut_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        val info = build(context, "web:" + idOf(url), url, title)
        runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }
            .onFailure { Log.w(TAG, "Pin shortcut request failed", it) }
    }

    /** Mirrors the first few web-app favorites into Amethyst's long-press shortcut list. */
    fun publishFavorites(
        context: Context,
        favorites: List<FavoriteApp>,
    ) {
        val shortcuts =
            favorites
                .filterIsInstance<FavoriteApp.WebApp>()
                .take(MAX_DYNAMIC)
                .map { build(context, DYNAMIC_PREFIX + idOf(it.url), it.url, it.label) }
        runCatching {
            val stale =
                ShortcutManagerCompat
                    .getDynamicShortcuts(context)
                    .map { it.id }
                    .filter { it.startsWith(DYNAMIC_PREFIX) && it !in shortcuts.map { s -> s.id } }
            if (stale.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(context, stale)
            shortcuts.forEach { ShortcutManagerCompat.pushDynamicShortcut(context, it) }
        }.onFailure { Log.w(TAG, "Could not publish favorite shortcuts", it) }
    }

    private fun build(
        context: Context,
        id: String,
        url: String,
        title: String,
    ): ShortcutInfoCompat {
        val label = title.ifBlank { BrowserChrome.displayHost(url) }
        return ShortcutInfoCompat
            .Builder(context, id)
            .setShortLabel(label.take(24))
            .setLongLabel(label.take(48))
            .setIcon(iconFor(context, url))
            .setIntent(WebShortcutActivity.intent(context, url))
            .build()
    }

    private fun iconFor(
        context: Context,
        url: String,
    ): IconCompat {
        val bitmap =
            Amethyst.instance.browserIcons
                .iconModelFor(BrowserChrome.displayHost(url))
                ?.removePrefix("file://")
                ?.let { path -> runCatching { BitmapFactory.decodeFile(File(path).absolutePath) }.getOrNull() }
        return if (bitmap != null) {
            IconCompat.createWithBitmap(if (bitmap.width < ICON_PX) bitmap.scale(ICON_PX, ICON_PX, filter = false) else bitmap)
        } else {
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        }
    }

    private fun idOf(url: String): String = sha256(url.encodeToByteArray()).take(12).joinToString("") { "%02x".format(it) }
}
