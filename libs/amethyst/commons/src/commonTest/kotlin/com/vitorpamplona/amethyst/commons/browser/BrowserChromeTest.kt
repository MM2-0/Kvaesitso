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

import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Presentation
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.SectionKind
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Security
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.State
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserChromeTest {
    private fun web(
        presentation: Presentation = Presentation.FULL_SCREEN,
        url: String = "https://example.com/a",
        startUrl: String = "https://example.com/",
        torOn: Boolean? = null,
        isLoading: Boolean = false,
    ) = State(Surface.WEB, presentation, url, startUrl, isLoading = isLoading, torOn = torOn)

    @Test
    fun webIconRowMirrorsChrome() {
        assertEquals(listOf(Action.BACK, Action.FORWARD, Action.RELOAD, Action.FAVORITE, Action.SHARE), BrowserChrome.iconRow(web()))
    }

    @Test
    fun reloadBecomesStopWhileLoading() {
        assertEquals(Action.STOP, BrowserChrome.iconRow(web(isLoading = true))[2])
    }

    @Test
    fun sandboxIconRowHasNoHistoryOrShare() {
        val state = State(Surface.NAPPLET, Presentation.EMBEDDED, "https://x.napplet.local/", "https://x.napplet.local/")
        assertEquals(listOf(Action.RELOAD, Action.FAVORITE), BrowserChrome.iconRow(state))
    }

    @Test
    fun backAndForwardFollowHistory() {
        val state = web().copy(canGoBack = true, canGoForward = false)
        assertTrue(BrowserChrome.isEnabled(state, Action.BACK))
        assertFalse(BrowserChrome.isEnabled(state, Action.FORWARD))
        assertTrue(BrowserChrome.isEnabled(state, Action.SHARE))
    }

    @Test
    fun fullScreenWebMenuOrder() {
        val sections = BrowserChrome.sections(web(torOn = true))
        assertEquals(listOf(SectionKind.PAGE, SectionKind.PRIVACY, SectionKind.DEVELOPER), sections.map { it.kind })
        assertEquals(
            listOf(
                Action.COPY_LINK,
                Action.EDIT_ADDRESS,
                Action.FIND_IN_PAGE,
                Action.TEXT_SIZE,
                Action.DESKTOP_SITE,
                Action.ADD_TO_HOME_SCREEN,
                Action.OPEN_IN_BROWSER_APP,
            ),
            sections[0].actions,
        )
        assertEquals(listOf(Action.TOR, Action.SITE_SETTINGS, Action.SITE_DATA), sections[1].actions)
        assertEquals(listOf(Action.CONSOLE), sections[2].actions)
    }

    @Test
    fun embeddedWebAddsOpenFullScreenLast() {
        val page = BrowserChrome.sections(web(presentation = Presentation.EMBEDDED))[0].actions
        assertEquals(Action.OPEN_FULL_SCREEN, page.last())
    }

    @Test
    fun noTorRowWithoutTor() {
        val privacy = BrowserChrome.sections(web(torOn = null)).first { it.kind == SectionKind.PRIVACY }
        assertFalse(Action.TOR in privacy.actions)
    }

    @Test
    fun leavingTheAppOriginOffersBackToApp() {
        val page = BrowserChrome.sections(web(url = "https://accounts.other.com/login"))[0].actions
        assertEquals(Action.BACK_TO_APP, page.first())
    }

    @Test
    fun sandboxMenuKeepsOnlyApplicableRows() {
        val state =
            State(Surface.NSITE, Presentation.FULL_SCREEN, "https://a.napplet.local/", "https://a.napplet.local/", torOn = false, hasAccessInfo = true)
        val sections = BrowserChrome.sections(state)
        assertEquals(listOf(Action.FIND_IN_PAGE, Action.TEXT_SIZE), sections[0].actions)
        assertEquals(listOf(Action.TOR, Action.ACCESS_INFO, Action.SITE_SETTINGS), sections[1].actions)
    }

    @Test
    fun surfacesWithoutFindOrTextSizeDropThoseRows() {
        val state =
            State(Surface.NAPPLET, Presentation.EMBEDDED, "", "", hasFind = false, hasTextSize = false, hasAccessInfo = true)
        assertEquals(listOf(Action.OPEN_FULL_SCREEN), BrowserChrome.sections(state)[0].actions)
    }

    @Test
    fun securityBadge() {
        assertEquals(Security.HTTPS, BrowserChrome.security(web()))
        assertEquals(Security.HTTP, BrowserChrome.security(web(url = "http://example.com")))
        assertEquals(Security.TOR, BrowserChrome.security(web(torOn = true)))
        assertEquals(Security.SANDBOX, BrowserChrome.security(State(Surface.NAPPLET, Presentation.EMBEDDED, "x", "x")))
    }

    @Test
    fun originOfNormalizes() {
        assertEquals("https://example.com", BrowserChrome.originOf("https://Example.com/path?q=1"))
        assertEquals("https://example.com", BrowserChrome.originOf("https://example.com:443/"))
        assertEquals("http://example.com:8080", BrowserChrome.originOf("http://user@example.com:8080/x"))
        assertNull(BrowserChrome.originOf("about:blank"))
        assertNull(BrowserChrome.originOf("data:text/html,hi"))
    }

    @Test
    fun scopeIgnoresPathsAndBlankPages() {
        assertFalse(BrowserChrome.isOutOfScope("https://example.com/deep/page", "https://example.com/"))
        assertTrue(BrowserChrome.isOutOfScope("https://sub.example.com/", "https://example.com/"))
        assertFalse(BrowserChrome.isOutOfScope("about:blank", "https://example.com/"))
    }

    @Test
    fun textZoomSteps() {
        assertEquals(115, BrowserChrome.stepTextZoom(100, larger = true))
        assertEquals(90, BrowserChrome.stepTextZoom(100, larger = false))
        assertEquals(200, BrowserChrome.stepTextZoom(200, larger = true))
        assertEquals(75, BrowserChrome.stepTextZoom(75, larger = false))
        assertEquals(115, BrowserChrome.stepTextZoom(105, larger = true))
    }

    @Test
    fun parsesComputedCssColors() {
        assertEquals(0xFF0C2238.toInt(), BrowserChrome.parseCssRgb("rgb(12, 34, 56)"))
        assertEquals(0xFFFFFFFF.toInt(), BrowserChrome.parseCssRgb("rgba(255, 255, 255, 0.9)"))
        assertNull(BrowserChrome.parseCssRgb("rgba(0, 0, 0, 0)"))
        assertNull(BrowserChrome.parseCssRgb("#ffffff"))
        assertNull(BrowserChrome.parseCssRgb(""))
        assertNull(BrowserChrome.parseCssRgb("rgb(300, 0, 0)"))
    }

    @Test
    fun desktopUserAgentDropsMobileMarkers() {
        val mobile =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Safari/537.36",
            BrowserChrome.desktopUserAgent(mobile),
        )
    }
}
