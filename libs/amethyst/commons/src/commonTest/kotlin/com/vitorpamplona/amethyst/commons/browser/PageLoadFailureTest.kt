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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageLoadFailureTest {
    @Test
    fun torSocksFailureIsUnreachableNotAProxyProblem() {
        // What the WebView reports over Tor for a host that does not exist.
        assertEquals(PageLoadFailure.UNREACHABLE, PageLoadFailure.classify(-1, "net::ERR_SOCKS_CONNECTION_FAILED"))
        assertEquals(PageLoadFailure.UNREACHABLE, PageLoadFailure.classify(-6, "net::ERR_SOCKS_CONNECTION_HOST_UNREACHABLE"))
    }

    @Test
    fun localProxyRefusalMeansTorIsDown() {
        assertEquals(PageLoadFailure.PROXY_DOWN, PageLoadFailure.classify(-6, "net::ERR_PROXY_CONNECTION_FAILED"))
    }

    @Test
    fun dnsMissIsNotFound() {
        assertEquals(PageLoadFailure.NOT_FOUND, PageLoadFailure.classify(-2, "net::ERR_NAME_NOT_RESOLVED"))
    }

    @Test
    fun cancelledNavigationShowsNoErrorPage() {
        assertNull(PageLoadFailure.classify(-1, "net::ERR_ABORTED"))
    }

    @Test
    fun commonCodesMapToTheirGroup() {
        assertEquals(PageLoadFailure.OFFLINE, PageLoadFailure.classify(-1, "net::ERR_INTERNET_DISCONNECTED"))
        assertEquals(PageLoadFailure.TIMED_OUT, PageLoadFailure.classify(-8, "net::ERR_CONNECTION_TIMED_OUT"))
        assertEquals(PageLoadFailure.UNREACHABLE, PageLoadFailure.classify(-6, "net::ERR_CONNECTION_REFUSED"))
        assertEquals(PageLoadFailure.INSECURE, PageLoadFailure.classify(-11, "net::ERR_CERT_AUTHORITY_INVALID"))
        assertEquals(PageLoadFailure.REDIRECT_LOOP, PageLoadFailure.classify(-9, "net::ERR_TOO_MANY_REDIRECTS"))
        assertEquals(PageLoadFailure.CLEARTEXT_BLOCKED, PageLoadFailure.classify(-1, "net::ERR_CLEARTEXT_NOT_PERMITTED"))
    }

    @Test
    fun onionOffTorNeedsTorInsteadOfNotFound() {
        val onion = "http://2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion/"
        assertEquals(PageLoadFailure.ONION_NEEDS_TOR, PageLoadFailure.forPage(-2, "net::ERR_NAME_NOT_RESOLVED", onion, viaTor = false))
        // Over Tor an onion failure is an ordinary reachability failure.
        assertEquals(PageLoadFailure.UNREACHABLE, PageLoadFailure.forPage(-1, "net::ERR_SOCKS_CONNECTION_FAILED", onion, viaTor = true))
        assertEquals(PageLoadFailure.NOT_FOUND, PageLoadFailure.forPage(-2, "net::ERR_NAME_NOT_RESOLVED", "https://example.com/", viaTor = false))
        assertNull(PageLoadFailure.forPage(-1, "net::ERR_ABORTED", onion, viaTor = false))
    }

    @Test
    fun onlyReachabilityFailuresSuggestTryingWithoutTor() {
        assertEquals(true, PageLoadFailure.mayBeTorBlocked(PageLoadFailure.UNREACHABLE))
        assertEquals(true, PageLoadFailure.mayBeTorBlocked(PageLoadFailure.TIMED_OUT))
        assertEquals(false, PageLoadFailure.mayBeTorBlocked(PageLoadFailure.NOT_FOUND))
        assertEquals(false, PageLoadFailure.mayBeTorBlocked(PageLoadFailure.INSECURE))
        assertEquals(false, PageLoadFailure.mayBeTorBlocked(PageLoadFailure.PROXY_DOWN))
    }

    @Test
    fun unknownDescriptionFallsBackToTheWebViewCode() {
        assertEquals(PageLoadFailure.NOT_FOUND, PageLoadFailure.classify(-2, null))
        assertEquals(PageLoadFailure.TIMED_OUT, PageLoadFailure.classify(-8, "net::ERR_SOMETHING_NEW"))
        assertEquals(PageLoadFailure.OTHER, PageLoadFailure.classify(-1, "net::ERR_SOMETHING_NEW"))
    }
}
