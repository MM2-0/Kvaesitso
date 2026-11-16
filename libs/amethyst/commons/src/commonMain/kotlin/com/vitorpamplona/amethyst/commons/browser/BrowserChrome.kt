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
package com.vitorpamplona.amethyst.commons.browser

/**
 * The single description of a running web surface's controls — the top pull-down "pill" drawn over every
 * embedded tab (Compose, in the main process) and every full-screen browser window (plain Views, in the
 * keyless `:napplet` process). Both renderers ask this object *which* actions to show and in *what* order,
 * so the two can no longer drift; they only decide how each action looks.
 *
 * Modelled on an installed Chrome PWA's app menu: a header naming the page and its origin (with its
 * security state), a row of icon buttons (back · forward · reload/stop · star · share), then the menu rows,
 * with the privacy rows and the developer console at the end.
 */
object BrowserChrome {
    /** What is being shown: a live website, a verified NIP-5A nsite, or a sandboxed NIP-5D napplet. */
    enum class Surface { WEB, NSITE, NAPPLET }

    /** Where it is shown: an in-app tab over the bottom bar, or its own full-screen window/task. */
    enum class Presentation { EMBEDDED, FULL_SCREEN }

    /** The connection badge on the origin chip. */
    enum class Security { TOR, HTTPS, HTTP, SANDBOX }

    /** Every control the pill can offer. Renderers map each to an icon + label. */
    enum class Action {
        // Icon row
        BACK,
        FORWARD,
        RELOAD,
        STOP,
        FAVORITE,
        SHARE,

        // Menu rows
        BACK_TO_APP,
        COPY_LINK,
        EDIT_ADDRESS,
        FIND_IN_PAGE,
        TEXT_SIZE,
        DESKTOP_SITE,
        ADD_TO_HOME_SCREEN,
        OPEN_IN_BROWSER_APP,
        OPEN_FULL_SCREEN,

        // Privacy
        TOR,
        ACCESS_INFO,
        SITE_SETTINGS,
        SITE_DATA,

        // Developer
        CONSOLE,
    }

    /** A group of menu rows; renderers draw a divider (and, for PRIVACY/DEVELOPER, a label) above each. */
    data class Section(
        val kind: SectionKind,
        val actions: List<Action>,
    )

    enum class SectionKind { PAGE, PRIVACY, DEVELOPER }

    /** Everything the menu layout depends on. Pure data, so the layout is testable off-device. */
    data class State(
        val surface: Surface,
        val presentation: Presentation,
        /** The page on screen now. */
        val url: String,
        /** The page this app/tab was opened with — its "scope", for the back-to-app action. */
        val startUrl: String,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val isLoading: Boolean = false,
        /** Tor routing state, or null when this surface offers no Tor choice. */
        val torOn: Boolean? = null,
        /**
         * The site is set to the open web ([torOn] false) but still goes through Tor, because another open page
         * needs Tor and the app's pages share one route (Tor always wins). Shown so the user knows why.
         */
        val torForced: Boolean = false,
        /** Whether the star is offered at all. */
        val canFavorite: Boolean = true,
        /** Whether an editable permissions screen exists for this surface. */
        val hasSiteSettings: Boolean = true,
        /** Whether a "what it can access" summary exists (sandboxed surfaces). */
        val hasAccessInfo: Boolean = false,
        /** Whether the console can be shown. */
        val hasConsole: Boolean = true,
        /** Whether this surface can search its page. */
        val hasFind: Boolean = true,
        /** Whether this surface can resize its text. */
        val hasTextSize: Boolean = true,
    ) {
        val isSandbox: Boolean get() = surface != Surface.WEB
    }

    /**
     * The icon row, left to right. A live website gets Chrome's full row. Sandboxed apps are served from
     * internal, verified content, so there is nothing meaningful to share or step through; they get reload
     * and the star.
     */
    fun iconRow(state: State): List<Action> =
        buildList {
            if (!state.isSandbox) {
                add(Action.BACK)
                add(Action.FORWARD)
            }
            add(if (state.isLoading) Action.STOP else Action.RELOAD)
            if (state.canFavorite) add(Action.FAVORITE)
            if (!state.isSandbox) add(Action.SHARE)
        }

    /** Whether an icon-row action is currently usable (back/forward follow the page history). */
    fun isEnabled(
        state: State,
        action: Action,
    ): Boolean =
        when (action) {
            Action.BACK -> state.canGoBack
            Action.FORWARD -> state.canGoForward
            else -> true
        }

    /** The menu rows under the icon row, grouped. Empty groups are dropped. */
    fun sections(state: State): List<Section> {
        val web = !state.isSandbox
        val page =
            buildList {
                if (web && isOutOfScope(state.url, state.startUrl)) add(Action.BACK_TO_APP)
                if (web) add(Action.COPY_LINK)
                if (web) add(Action.EDIT_ADDRESS)
                if (state.hasFind) add(Action.FIND_IN_PAGE)
                if (state.hasTextSize) add(Action.TEXT_SIZE)
                if (web) add(Action.DESKTOP_SITE)
                if (web) add(Action.ADD_TO_HOME_SCREEN)
                if (web) add(Action.OPEN_IN_BROWSER_APP)
                if (state.presentation == Presentation.EMBEDDED) add(Action.OPEN_FULL_SCREEN)
            }
        val privacy =
            buildList {
                if (state.torOn != null) add(Action.TOR)
                if (state.hasAccessInfo) add(Action.ACCESS_INFO)
                if (state.hasSiteSettings) add(Action.SITE_SETTINGS)
                if (web) add(Action.SITE_DATA)
            }
        val developer = if (state.hasConsole) listOf(Action.CONSOLE) else emptyList()
        return listOf(
            Section(SectionKind.PAGE, page),
            Section(SectionKind.PRIVACY, privacy),
            Section(SectionKind.DEVELOPER, developer),
        ).filter { it.actions.isNotEmpty() }
    }

    /** The badge for the origin chip. */
    fun security(state: State): Security =
        when {
            state.isSandbox -> Security.SANDBOX
            state.torOn == true -> Security.TOR
            state.url.startsWith("https://", ignoreCase = true) -> Security.HTTPS
            else -> Security.HTTP
        }

    /**
     * True when the page on screen left the app's origin — the case where Chrome shows its out-of-scope
     * bar. Blank pages and unparseable URLs are never "out of scope".
     */
    fun isOutOfScope(
        url: String,
        startUrl: String,
    ): Boolean {
        val here = originOf(url) ?: return false
        val home = originOf(startUrl) ?: return false
        return !here.equals(home, ignoreCase = true)
    }

    /**
     * `scheme://host[:port]` of an http(s) [url], lowercased, or null for anything else (about:, data:,
     * blank). The same shape the WebView reports as a page origin.
     */
    fun originOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = OmniboxInput.hostOf(url)?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val authority =
            url
                .substring(schemeEnd + 3)
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('@')
        val port =
            authority
                .substringAfterLast(']', authority)
                .substringAfter(':', "")
                .toIntOrNull()
                ?.takeIf { !(scheme == "https" && it == 443) && !(scheme == "http" && it == 80) }
        return "$scheme://$host" + (port?.let { ":$it" } ?: "")
    }

    /** The host shown on the origin chip, or the raw URL when it has none. */
    fun displayHost(url: String): String = OmniboxInput.hostOf(url)?.takeIf { it.isNotEmpty() } ?: url

    /** Text-size steps offered by the TEXT_SIZE row, in percent (Chrome's accessibility range, coarser). */
    val TEXT_ZOOM_STEPS = listOf(75, 90, 100, 115, 130, 150, 175, 200)

    const val DEFAULT_TEXT_ZOOM = 100

    /** The next larger (or smaller, with [larger] = false) step from [current], clamped to the range. */
    fun stepTextZoom(
        current: Int,
        larger: Boolean,
    ): Int =
        if (larger) {
            TEXT_ZOOM_STEPS.firstOrNull { it > current } ?: TEXT_ZOOM_STEPS.last()
        } else {
            TEXT_ZOOM_STEPS.lastOrNull { it < current } ?: TEXT_ZOOM_STEPS.first()
        }

    /**
     * Parses a computed CSS colour (`rgb(r, g, b)` / `rgba(r, g, b, a)`, what `getComputedStyle` returns)
     * into an opaque `0xFFRRGGBB`. Null for anything else, or a colour more than half transparent (a page
     * that "clears" its theme colour that way should get the default bars back).
     */
    fun parseCssRgb(css: String?): Int? {
        val match = CSS_RGB.matchEntire(css?.trim() ?: return null) ?: return null
        val (r, g, b) = match.destructured.let { (r, g, b, _) -> Triple(r.toInt(), g.toInt(), b.toInt()) }
        if (r > 255 || g > 255 || b > 255) return null
        val alpha = match.groupValues[4].takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: 1f
        if (alpha < 0.5f) return null
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private val CSS_RGB = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*([0-9.]+)\s*)?\)""")

    /**
     * The "desktop site" user agent for [mobileUserAgent]: Chrome's own trick — swap the Android platform
     * token for a Linux desktop one and drop the `Mobile` and WebView (`; wv`) markers, so servers that sniff
     * the UA send their desktop layout. Leaves the Chrome/WebKit version tokens intact.
     */
    fun desktopUserAgent(mobileUserAgent: String): String =
        mobileUserAgent
            .replace(Regex("""\(Linux; Android[^)]*\)"""), "(X11; Linux x86_64)")
            .replace("; wv)", ")")
            .replace(" Mobile Safari/", " Safari/")
            .replace(Regex("""\s+Mobile(?=\s|$)"""), "")
            .replace(Regex("""\s+Version/\d+(\.\d+)*"""), "")
}
