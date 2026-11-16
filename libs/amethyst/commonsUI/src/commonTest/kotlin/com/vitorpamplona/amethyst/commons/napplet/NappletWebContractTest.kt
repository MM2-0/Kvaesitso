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
package com.vitorpamplona.amethyst.commons.napplet

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NappletWebContractTest {
    @Test
    fun lockedPreludeIsTheFirstHeadContentAndRunsBeforeAuthoredCode() {
        val authored = "<html><head><script id=\"authored\">run()</script></head><body></body></html>"
        val injected =
            NappletWebContract
                .injectPrelude(
                    html = authored.encodeToByteArray(),
                    shimJs = "window.__shimRan=true;",
                    declaredDomains = listOf("identity", "relay", "shell", "Relay", "bad\"domain", "relay"),
                    locked = true,
                ).decodeToString()

        val head = injected.indexOf("<head>") + "<head>".length
        val csp = injected.indexOf("<meta http-equiv=\"Content-Security-Policy\"")
        val domains = injected.indexOf("window.__nappletDomains=[\"identity\", \"relay\"]")
        val shim = injected.indexOf("window.__shimRan=true")
        val authoredScript = injected.indexOf("id=\"authored\"")

        assertTrue(head == csp, "the CSP meta must be the first element in head")
        assertTrue(csp < domains && domains < shim && shim < authoredScript)
        assertContains(injected, "window.__nappletNip07=false")
        assertFalse(injected.contains("\"shell\""))
        assertFalse(injected.contains("bad\"domain"))
    }

    @Test
    fun preludePrecedesScriptsEvenWithMisleadingOrMissingHeadMarkup() {
        for (html in listOf(
            "<script>early()</script><head></head>",
            "<!-- <head> --> <script>early()</script>",
            "<html data-text='> <head>'><script>early()</script>",
            "<!doctype html><HTML><HEAD><script>early()</script></HEAD>",
        )) {
            val injected = NappletWebContract.injectPrelude(html.encodeToByteArray(), "injected()", emptyList(), true).decodeToString()
            assertTrue(injected.indexOf("Content-Security-Policy") < injected.indexOf("injected()"))
            assertTrue(injected.indexOf("injected()") < injected.indexOf("early()"))
            assertTrue(injected.endsWith(html))
        }
    }

    @Test
    fun lockedCspMatchesTheConservativeOpaqueOriginPosture() {
        val csp = NappletWebContract.APP_CSP

        assertContains(csp, "script-src 'unsafe-inline' 'wasm-unsafe-eval'")
        assertFalse(csp.contains("'unsafe-eval'"))
        assertContains(csp, "default-src 'none'")
        assertContains(csp, "connect-src 'none'")
        assertContains(csp, "frame-src 'none'")
        assertContains(csp, "object-src 'none'")
        assertContains(csp, "base-uri 'none'")
        assertContains(csp, "form-action 'none'")
        assertFalse(csp.contains("'self'"))
    }

    // Every page is under napplet.local: a cookie one app kept there would reach every other app.
    @Test
    fun everyServedPageLosesCookiesBeforeItsOwnScripts() {
        for (locked in listOf(true, false)) {
            val page = NappletWebContract.injectPrelude("<script>document.cookie='x=1'</script>".encodeToByteArray(), "", emptyList(), locked).decodeToString()
            val guard = page.indexOf(NappletWebContract.COOKIE_GUARD_JS)
            assertTrue(guard in 0 until page.indexOf("document.cookie='x=1'"), "locked=$locked: $page")
        }
        assertContains(NappletWebContract.webxdcPrelude().decodeToString(), NappletWebContract.COOKIE_GUARD_JS)
    }
}
