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
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.core.net.toUri

/**
 * Who "open in a browser" would actually hand the page to.
 *
 * Only used to say so on the tile. The hand-off itself still goes through a chooser, so a wrong
 * or missing answer here costs a label, never a mis-launch.
 */
object DefaultBrowser {
    /** A probe URL: the scheme is what selects browsers, the host is never contacted. */
    private val PROBE = Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

    /**
     * The default browser's app label, or null when the tile should stay generic.
     *
     * Null in three cases that all mean the same thing to a reader — you are going to get a
     * chooser, so do not promise a name:
     *  - no default is set, and the system resolves to its own picker;
     *  - the only handler is Amethyst, so "open in a browser" means anything but us;
     *  - package visibility hides it. Android 11+ answers `resolveActivity` with nothing unless
     *    the manifest declares a matching `<queries>` entry, which is why one exists for
     *    http/https alongside the payment schemes.
     */
    fun label(context: Context): String? =
        runCatching {
            val pm = context.packageManager

            @Suppress("DEPRECATION")
            val match: ResolveInfo = pm.resolveActivity(PROBE, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
            val pkg = match.activityInfo?.packageName ?: return null
            // The system picker resolves for everything; naming it would be a lie.
            if (pkg == context.packageName || isResolver(match)) return null
            match.loadLabel(pm).toString().takeIf { it.isNotBlank() }
        }.getOrNull()

    /**
     * Whether this is Android's own chooser rather than a browser.
     *
     * `resolveActivity` returns the resolver when several apps match and none is default; it
     * reports `exported=false` from the `android` package, which is the cheap way to tell.
     */
    private fun isResolver(info: ResolveInfo): Boolean = info.activityInfo?.packageName == "android"
}
