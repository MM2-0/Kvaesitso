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
import android.net.Uri
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.frontpage.PaperLinks
import com.vitorpamplona.amethyst.commons.napplet.HostProfile

/**
 * Where a link tapped in the sandbox goes. A front page's own links (a citation, a byline, a
 * stream, a listing, a calendar event) open in Amethyst itself: a `nostr:` VIEW aimed at this
 * package, so the main process routes it like any other nostr link and no other Nostr client
 * on the device can answer it. Everything else goes to the system browser, as before.
 */
internal fun Context.linkIntent(
    profile: HostProfile,
    uri: Uri,
): Intent {
    val nostr = if (profile == HostProfile.PAPER) PaperLinks.toNostrUri(uri.toString()) else null
    return if (nostr != null) {
        Intent(Intent.ACTION_VIEW, nostr.toUri()).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
