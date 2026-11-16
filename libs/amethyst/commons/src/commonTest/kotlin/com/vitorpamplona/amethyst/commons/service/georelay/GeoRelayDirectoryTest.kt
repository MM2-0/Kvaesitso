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
package com.vitorpamplona.amethyst.commons.service.georelay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoRelayDirectoryTest {
    private val csv =
        """
        Relay URL,Latitude,Longitude
        relay.berlin.example,52.5200,13.4050
        relay.paris.example:443,48.8566,2.3522
        relay.nyc.example,40.7128,-74.0060
        relay.sf.example,37.7749,-122.4194
        relay.tokyo.example,35.6762,139.6503
        broken-row-no-coords
        """.trimIndent()

    @Test
    fun parsesCsvSkippingHeaderAndBadRows() {
        val relays = GeoRelayDirectory.parseCsv(csv)
        assertEquals(5, relays.size)
        assertEquals("relay.paris.example:443", relays[1].host)
        // host with :443 still normalizes to a wss url
        assertTrue(relays[1].relay.url.startsWith("wss://relay.paris.example"))
    }

    @Test
    fun closestReturnsNearestFirst() {
        val relays = GeoRelayDirectory.parseCsv(csv)
        // Amsterdam-ish point → Paris and Berlin are nearest, Tokyo farthest.
        val closest = GeoRelayDirectory.closest(52.3676, 4.9041, relays, count = 2)
        val hosts = closest.map { it.url }
        assertTrue(hosts.any { "berlin" in it })
        assertTrue(hosts.any { "paris" in it })
    }

    @Test
    fun closestByGeohashIsDeterministic() {
        val dir = GeoRelayDirectory(GeoRelayDirectory.parseCsv(csv))
        // A geohash near New York.
        val a = dir.closestRelays("dr5reg", count = 3)
        val b = dir.closestRelays("dr5reg", count = 3)
        assertEquals(a, b)
        assertTrue(a.first().url.contains("nyc"))
    }

    @Test
    fun invalidGeohashYieldsEmpty() {
        val dir = GeoRelayDirectory(GeoRelayDirectory.parseCsv(csv))
        assertTrue(dir.closestRelays("", count = 3).isEmpty())
        assertTrue(dir.closestRelays("!!!", count = 3).isEmpty())
    }

    @Test
    fun tieBreakIsStableByHost() {
        // Two relays at the exact same coordinates → deterministic order by host.
        val sameSpot =
            GeoRelayDirectory.parseCsv(
                """
                Relay URL,Latitude,Longitude
                zzz.example,10.0,10.0
                aaa.example,10.0,10.0
                """.trimIndent(),
            )
        val closest = GeoRelayDirectory.closest(10.0, 10.0, sameSpot, count = 2)
        assertTrue(closest[0].url.contains("aaa.example"))
        assertTrue(closest[1].url.contains("zzz.example"))
    }

    @Test
    fun fallbackIsNonEmpty() {
        assertTrue(GeoRelayDirectory().size >= 10)
    }

    @Test
    fun bareAndColon443HostCollapseToOneRelay() {
        val relays =
            GeoRelayDirectory.parseCsv(
                """
                Relay URL,Latitude,Longitude
                fanfares.example,40.0,-74.0
                fanfares.example:443,40.0,-74.0
                other.example,41.0,-75.0
                """.trimIndent(),
            )
        // Both fanfares rows normalize to the same endpoint, so closest() must not
        // spend two slots on it.
        val closest = GeoRelayDirectory.closest(40.0, -74.0, relays, count = 3)
        assertEquals(closest.size, closest.distinct().size)
        assertEquals(2, closest.size)
        assertTrue(closest.any { it.url.contains("fanfares.example") })
        assertTrue(closest.none { it.url.contains(":443") })
    }
}
