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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map

import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeocacheMapMarksTest {
    private fun cache(
        n: Int,
        lat: Double,
        lon: Double,
    ) = GeocacheListingEvent(
        id = n.toString().padStart(64, '0'),
        pubKey = "a".repeat(64),
        createdAt = 1L,
        tags = arrayOf(arrayOf("d", "cache-$n"), arrayOf("g", GeoHash.encode(lat, lon, 9).toString())),
        content = "",
        sig = "b".repeat(128),
    )

    @Test
    fun aFewCachesAreOnePinEach() {
        val marks = geocacheMapMarks(listOf(cache(1, 41.07, -85.21), cache(2, 41.08, -85.22)), 13.0)

        assertEquals(2, marks.size)
        assertTrue(marks.all { it is GeocacheMapMark.Cache })
    }

    @Test
    fun aBusyCityFoldsIntoCountsThatSplitAsYouZoomIn() {
        // Sixty caches a few hundred metres apart around one spot.
        val city = (0 until 60).map { cache(it, 41.07 + (it % 6) * 0.004, -85.21 + (it / 6) * 0.004) }

        val far = geocacheMapMarks(city, 8.0)
        val near = geocacheMapMarks(city, 16.0)

        assertTrue(far.any { it is GeocacheMapMark.Cluster })
        assertEquals(60, far.sumOf { if (it is GeocacheMapMark.Cluster) it.size else 1 })
        assertTrue(near.size > far.size, "zooming in should split clusters: ${far.size} -> ${near.size}")
    }

    @Test
    fun aCacheWithoutAPlaceIsNotOnTheMap() {
        val placeless = GeocacheListingEvent("c".repeat(64), "a".repeat(64), 1L, arrayOf(arrayOf("d", "nowhere")), "", "b".repeat(128))

        assertTrue(geocacheMapMarks(listOf(placeless), 13.0).isEmpty())
    }
}
