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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission

/**
 * Everything the redesigned browser pill draws, as plain immutable data. [chrome] decides *which* controls
 * exist (the same [BrowserChrome] layout both browsers already share); the rest is what they show.
 *
 * The pill composables are stateless: a host (embedded tab, full-screen window, desktop) builds this from
 * its live page and handles [BrowserPillEvent]s.
 */
@Immutable
data class BrowserPillUi(
    val title: String,
    val chrome: BrowserChrome.State,
    val isFavorite: Boolean = false,
    val desktopSite: Boolean = false,
    val textZoom: Int = BrowserChrome.DEFAULT_TEXT_ZOOM,
    /** 0..1 while the main frame loads; null when idle. */
    val loadProgress: Float? = null,
    val consoleShowing: Boolean = false,
    val consoleErrors: Int = 0,
    /** Answers this site already has (camera / mic / location), for the site-settings summary. */
    val sitePermissions: Map<BrowserSitePermission, BrowserSitePermission.Decision> = emptyMap(),
    /**
     * The default browser's name, when the system will name one and it is not us.
     *
     * Null means the hand-off shows a chooser, and the tile has to stay "Open in browser":
     * no default is set, the device hides it, or the only handler is Amethyst itself.
     */
    val defaultBrowserName: String? = null,
) {
    val security: BrowserChrome.Security get() = BrowserChrome.security(chrome)
    val host: String get() = BrowserChrome.displayHost(chrome.url)
}

/** What the user did in the pill. Hosts map these onto the WebView / broker. */
sealed interface BrowserPillEvent {
    /** A navigation-capsule button, a tile, or a privacy/developer row. */
    data class Action(
        val action: BrowserChrome.Action,
    ) : BrowserPillEvent

    data class TextZoom(
        val percent: Int,
    ) : BrowserPillEvent

    data class Navigate(
        val input: String,
    ) : BrowserPillEvent

    /** The origin field was long-pressed (copy link). */
    data object CopyOrigin : BrowserPillEvent

    /** The security badge at the start of the origin field was tapped: show page info. */
    data object PageInfo : BrowserPillEvent

    data object Close : BrowserPillEvent
}

/** One omnibox suggestion for the address editor. */
@Immutable
data class AddressSuggestion(
    val title: String,
    val url: String,
    val isFavorite: Boolean = false,
)

/** One console line. */
@Immutable
data class ConsoleLine(
    val level: Level,
    val message: String,
    val source: String = "",
    val line: Int = 0,
) {
    enum class Level { LOG, INFO, WARNING, ERROR, DEBUG }
}
