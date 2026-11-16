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
package com.vitorpamplona.amethyst.commons.ui.note.creators.location

import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeohashPickerDefaultsTest {
    @Test
    fun aSeedOfAKnownLengthKeepsItsLevel() {
        GeohashChannelLevel.ordered.forEach { level ->
            assertEquals(level, GeohashPickerDefaults.nearestLevel(level.chars))
        }
    }

    @Test
    fun aGeocacheSeedShowsBuildingNotCity() {
        // A geocache seeds the picker with 9 characters; falling back to City showed a ~5 km cell.
        assertEquals(GeohashChannelLevel.BUILDING, GeohashPickerDefaults.nearestLevel(9))
        assertEquals(GeohashChannelLevel.BUILDING, GeohashPickerDefaults.nearestLevel(12))
    }

    @Test
    fun aThreeCharacterSeedShowsANeighbouringLevel() {
        val level = GeohashPickerDefaults.nearestLevel(3)
        assertEquals(true, level == GeohashChannelLevel.REGION || level == GeohashChannelLevel.PROVINCE)
    }

    // A geocache's location from a long press on the map, and the centre the picker opens on.
    private val cacheSeed = "sr4hbn81b"
    private val cacheCenter = GeoHash.decode(cacheSeed)!!

    private fun pick(
        seedText: String? = cacheSeed,
        level: GeohashChannelLevel = GeohashChannelLevel.BUILDING,
        precision: Int? = null,
        moved: Boolean = false,
        lat: Double? = cacheCenter.centerLat,
        lon: Double? = cacheCenter.centerLon,
        zoom: Double = 17.0,
    ) = GeohashPickerDefaults.pickedCell(
        seedText = seedText,
        seedLevel = seedText?.let { GeohashPickerDefaults.nearestLevel(it.length) },
        level = level,
        precision = precision,
        moved = moved,
        lat = lat,
        lon = lon,
        zoom = zoom,
    )

    @Test
    fun anUntouchedSeedComesBackExactly() {
        // The original bug: the 9-character seed was re-encoded at a coarser level on confirm.
        assertEquals(cacheSeed, pick())
        assertEquals(cacheSeed, pick(precision = 9))
    }

    @Test
    fun anUntouchedSeedShorterThanTheFixedPrecisionIsNotAPick() {
        assertNull(pick(seedText = "sr4hbn81", precision = 9))
    }

    @Test
    fun choosingAnotherLevelReEncodesTheSeedsCentre() {
        assertEquals("sr4hb", pick(level = GeohashChannelLevel.CITY))
    }

    @Test
    fun nothingIsPickedUntilTheReaderMovesAnUnseededPicker() {
        assertNull(pick(seedText = null, lat = 20.0, lon = -30.0, zoom = 3.0))
    }

    @Test
    fun aFixedPrecisionPickNeedsTheMapZoomedIn() {
        // At world zoom a 9-character cell would be a made-up place, not one the reader chose.
        assertNull(pick(seedText = null, precision = 9, moved = true, zoom = 3.0))
        assertTrue(GeohashPickerDefaults.needsZoomIn(precision = 9, moved = true, zoom = 3.0))

        val zoomedIn = pick(seedText = null, precision = 9, moved = true, zoom = 15.0)
        assertEquals(9, zoomedIn?.length)
        assertFalse(GeohashPickerDefaults.needsZoomIn(precision = 9, moved = true, zoom = 15.0))
    }

    @Test
    fun anAreaPickTakesTheChosenLevelsLengthAtAnyZoom() {
        assertEquals(5, pick(seedText = null, level = GeohashChannelLevel.CITY, moved = true, zoom = 3.0)?.length)
        assertFalse(GeohashPickerDefaults.needsZoomIn(precision = null, moved = true, zoom = 3.0))
    }
}
