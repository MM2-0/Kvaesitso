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

import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims.Companion.DIRECT
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims.Companion.NO_PROXY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NappletProxyClaimsTest {
    private val claims = NappletProxyClaims()
    private val torTab = Any()
    private val openTab = Any()

    @Test
    fun torWantedByOthersIgnoresTheAskingSurface() {
        claims.claim(torTab, 9050)
        assertEquals(false, claims.torWantedByOthers(torTab))
        assertEquals(true, claims.torWantedByOthers(openTab))
        claims.claim(openTab, NappletProxyClaims.NO_PROXY)
        assertEquals(false, claims.torWantedByOthers(torTab))
        claims.release(torTab)
        assertEquals(false, claims.torWantedByOthers(openTab))
    }

    @Test
    fun noClaimsMeansNoProxy() {
        assertEquals(DIRECT, claims.route())
    }

    @Test
    fun anOpenWebSurfaceDoesNotDowngradeATorOne() {
        claims.claim(torTab, 9050)
        // The open-web page opening later must not clear Tor for the page already on it.
        assertEquals(9050, claims.claim(openTab, NO_PROXY).torPort)
    }

    @Test
    fun orderDoesNotMatter() {
        claims.claim(openTab, NO_PROXY)
        assertTrue(claims.claim(torTab, 9050).usesTor)
    }

    @Test
    fun releasingTheLastTorSurfaceGoesDirect() {
        claims.claim(torTab, 9050)
        claims.claim(openTab, NO_PROXY)
        assertEquals(DIRECT, claims.release(torTab))
    }

    @Test
    fun switchingASurfaceOffTorReleasesTheProxy() {
        claims.claim(torTab, 9050)
        assertEquals(DIRECT, claims.claim(torTab, NO_PROXY))
    }

    @Test
    fun theLatestTorPortWins() {
        claims.claim(torTab, 9050)
        val other = Any()
        assertEquals(9150, claims.claim(other, 9150).torPort)
        // Re-claiming moves an owner to the end, making its port the latest.
        assertEquals(9050, claims.claim(torTab, 9050).torPort)
    }

    @Test
    fun aTorClaimWithoutAPortBlocksInsteadOfGoingDirect() {
        claims.claim(openTab, NO_PROXY)
        val route = claims.claim(torTab, NappletProxyClaims.TOR_NOT_READY)
        assertTrue(route.blocked)
        assertFalse(route.usesTor)
    }

    @Test
    fun aKnownTorPortWinsOverOneThatIsNotReady() {
        claims.claim(torTab, NappletProxyClaims.TOR_NOT_READY)
        val route = claims.claim(openTab, 9050)
        assertEquals(9050, route.torPort)
        assertFalse(route.blocked)
    }

    @Test
    fun emptyOnlyWhenNoSurfaceClaims() {
        assertTrue(claims.isEmpty())
        claims.claim(openTab, NO_PROXY)
        assertFalse(claims.isEmpty())
        claims.release(openTab)
        assertTrue(claims.isEmpty())
    }
}
